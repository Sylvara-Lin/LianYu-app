#!/usr/bin/env python3
"""
build_ultimate_shell.py — 一键构建极简壳 APK

流水线:
  1. Gradle assembleDebug → 全量 debug APK
  2. 提取全部业务 DEX → assets/shell/
  3. javac 编译纯 Java 壳类 → d8 转 DEX (6KB)
  4. apktool 解码 → 编辑 manifest → 重打包
  5. 替换 classes.dex + hardened SOs
  6. 签名

产出: app/build/outputs/apk/release/app-release.apk
"""

import sys, os, subprocess, shutil, zipfile, re, zlib
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
SDK = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk"
BT = max((SDK / "build-tools").glob("*"))
ANDROID_JAR = SDK / "platforms" / "android-35" / "android.jar"
D8 = BT / "d8.bat"
APKSIGNER = BT / "apksigner.bat"
APKTOOL = PROJECT / "tools" / "apktool.jar"
KEYSTORE = PROJECT / "release.keystore"
STORE_PASS = os.environ.get("LIANYU_STORE_PASSWORD", "")
KEY_PASS = os.environ.get("LIANYU_KEY_PASSWORD", "")
KS_ALIAS = "your_alias"

SHELL_SRC = PROJECT / "app/build/tmp/ultimate_shell/src"
BUILD_DIR = PROJECT / "app/build/tmp/ultimate_shell/build"
DEX_OUT = PROJECT / "app/build/tmp/ultimate_shell/dex_final"
SHELL_DECODED = PROJECT / "app/build/tmp/shell_decoded"
ASSETS_SHELL = SHELL_DECODED / "assets" / "shell"
SO_DIR = PROJECT / "core/security/build/intermediates/cxx/Release"
RELEASE_APK = PROJECT / "app/build/outputs/apk/release/app-release.apk"
DEBUG_APK = PROJECT / "app/build/outputs/apk/debug/app-debug.apk"


def run(cmd, desc=""):
    print(f"  {desc}...", end=" ", flush=True)
    if isinstance(cmd, str):
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=str(PROJECT))
    else:
        result = subprocess.run(cmd, capture_output=True, text=True, cwd=str(PROJECT))
    if result.returncode != 0:
        print(f"FAILED")
        print(result.stderr[-500:])
        sys.exit(1)
    print("OK")
    return result


def phase1_gradle():
    """Build release APK with Gradle (includes NDK hardened SOs + R8 minification)."""
    print("\n═══ Phase 1: Gradle assembleRelease ═══")
    run('cmd.exe /c "gradlew.bat assembleRelease --no-daemon -q"', "Gradle release build")
    assert DEBUG_APK.exists() or (PROJECT / "app/build/outputs/apk/release/app-release.apk").exists(), \
        f"No APK found"


def phase2_extract_dex():
    """Extract all business DEX files to assets/shell/.
       Source: Gradle release output (R8-minified single DEX or multi-DEX)."""
    print("\n═══ Phase 2: Extract business DEX ═══")
    # Find release APK
    src_apk = RELEASE_APK
    if not src_apk.exists():
        src_apk = PROJECT / "app/build/outputs/apk/debug/app-debug.apk"
    if not src_apk.exists():
        sys.exit("No APK found. Run Gradle first.")

    ASSETS_SHELL.mkdir(parents=True, exist_ok=True)
    # Clean old DEX files
    for old in ASSETS_SHELL.glob("*.dex"):
        old.unlink()

    count = 0
    with zipfile.ZipFile(src_apk, 'r') as z:
        for f in sorted(z.namelist()):
            if f.endswith('.dex'):
                data = z.read(f)
                name = f.replace('/', '_')
                (ASSETS_SHELL / name).write_bytes(data)
                count += 1
    print(f"  Extracted {count} business DEX files → assets/shell/")


def phase3_compile_shell():
    """javac → d8: produce ~6KB shell DEX."""
    print("\n═══ Phase 3: Compile shell DEX ═══")
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    shutil.rmtree(BUILD_DIR, ignore_errors=True)
    BUILD_DIR.mkdir()

    java_files = list(SHELL_SRC.rglob("*.java"))
    run(["javac", "-cp", str(ANDROID_JAR), "-d", str(BUILD_DIR)] + [str(f) for f in java_files], "javac")

    # Skip MainActivity stub — business DEX provides it
    for stub in BUILD_DIR.rglob("MainActivity*.class"):
        stub.unlink()
        print("  Removed MainActivity stub")

    jar_path = BUILD_DIR / "shell.jar"
    run(f'cd "{BUILD_DIR}" && jar cf "{jar_path}" .', "jar")

    DEX_OUT.mkdir(parents=True, exist_ok=True)
    shutil.rmtree(DEX_OUT, ignore_errors=True)
    DEX_OUT.mkdir()
    run([str(D8), "--lib", str(ANDROID_JAR), "--release", "--output", str(DEX_OUT), str(jar_path)], "d8")

    dex = DEX_OUT / "classes.dex"
    size = dex.stat().st_size
    compressed = len(zlib.compress(dex.read_bytes(), 9))
    print(f"  Shell DEX: {size}B (~{compressed}B compressed)")


def phase3b_harden_so():
    """Post-Gradle: encrypt SO .text with pack_so.py for self-decrypt at runtime."""
    print("\n═══ Phase 3b: SO .text Encryption ═══")
    pack_so_py = PROJECT / "tools" / "pack_so.py"
    so_base = PROJECT / "core/security/build/intermediates/cxx/Release"
    so_dirs = sorted(so_base.glob("*"))
    if not so_dirs:
        print("  Skipped — no SO build output")
        return
    so_dir = so_dirs[-1] / "obj" / "local"
    for abi in os.listdir(str(so_dir)):
        so_path = so_dir / abi / "liblianyu_security.so"
        if not so_path.exists():
            continue
        print(f"  [{abi}] encrypting .text...", end=" ", flush=True)
        tmp = so_path.with_suffix(".packed.so")
        run([sys.executable, str(pack_so_py),
             "--input", str(so_path), "--output", str(tmp),
             "--project-root", str(PROJECT)], "pack_so")
        shutil.move(str(tmp), str(so_path))
        print(f"  OK ({so_path.stat().st_size/1024:.1f}KB)")


def phase0_generate_payload():
    """Pre-Gradle: generate g_vmp_payload_data.cpp from encrypted DEX."""
    print("\n═══ Phase 0: VMP Payload Generation ═══")
    gen_py = PROJECT / "tools" / "gen_payload_cpp.py"
    # Input: encrypted DEX from previous build's assets
    enc_dex = PROJECT / "app/src/main/assets/lianyu_shell/classes.bin"
    if not enc_dex.exists():
        print("  Skipped — no encrypted DEX found (run with --generate-payload later)")
        return
    out_cpp = PROJECT / "core/security/src/main/cpp/g_vmp_payload_data.cpp"
    crc = hex(zlib.crc32(enc_dex.read_bytes()) & 0xFFFFFFFF)
    run([sys.executable, str(gen_py),
         str(enc_dex), str(out_cpp), crc,
         "a1b2c3d4e5f60708112233445566778899", "0xDEADBEEF"], "gen_payload")
    print(f"  Generated {os.path.getsize(str(out_cpp))/1024/1024:.1f}MB")


def phase4_apktool():
    """Decode release APK, extract business DEX to assets, edit manifest, repack."""
    print("\n═══ Phase 4: apktool + manifest edit ═══")
    shutil.rmtree(SHELL_DECODED, ignore_errors=True)
    src_apk = RELEASE_APK
    if not src_apk.exists():
        src_apk = PROJECT / "app/build/outputs/apk/debug/app-debug.apk"
    if not src_apk.exists():
        sys.exit("No APK found. Run Gradle first.")

    # Save original Gradle APK before modification — needed for DEX extraction
    gradle_apk = PROJECT / "app/build/tmp/gradle_original.apk"
    shutil.copy(src_apk, str(gradle_apk))
    print(f"  Saved original: {gradle_apk.stat().st_size/1024/1024:.1f}MB")

    run(["java", "-jar", str(APKTOOL), "d", "-f", "-o", str(SHELL_DECODED), str(src_apk)], "apktool decode")

    # ── Extract business DEX to assets/shell/ (after decode, before repack) ──
    # Encrypt with XOR to prevent static jadx analysis
    ASSETS_SHELL.mkdir(parents=True, exist_ok=True)
    for old in ASSETS_SHELL.glob("*"):
        old.unlink()
    count = 0
    # Key matches nativeDeriveDexKey: SHELL_INTEGRITY_COOKIE XOR g_vmp_config
    # Key = HMAC-SHA256(cert_SHA256, maps_crc64 || "lianyu_dex_v3___")
    # maps_crc64 = 0x8e7beee5d9b3c6e4 (reference device, LE)
    # Matches nativeDeriveDexKey() in dex-extractor.cpp
    XOR_KEY = bytes([0xb6, 0x14, 0xbd, 0xe8, 0x48, 0x68, 0xa2, 0xe8,
                     0x68, 0x68, 0x37, 0x60, 0x4b, 0x69, 0x1d, 0x64,
                     0x2c, 0x8d, 0xd8, 0x2a, 0x23, 0x42, 0x63, 0xd7,
                     0x29, 0xb4, 0x1d, 0x36, 0x50, 0x12, 0xda, 0x17])
    with zipfile.ZipFile(str(gradle_apk), 'r') as z:
        for f in sorted(z.namelist()):
            if f.endswith('.dex'):
                data = bytearray(z.read(f))
                # XOR encrypt — prevents static jadx analysis
                for i in range(len(data)):
                    data[i] ^= XOR_KEY[i % len(XOR_KEY)]
                name = f.replace('/', '_').replace('.dex', '.dat')
                (ASSETS_SHELL / name).write_bytes(data)
                count += 1
    print(f"  Encrypted {count} business DEX → assets/shell/ (*.dat)")

    manifest = SHELL_DECODED / "AndroidManifest.xml"
    mf = manifest.read_text(encoding='utf-8')

    # Remove all ContentProviders (crash before attachBaseContext)
    mf = re.sub(r'<provider[^>]*>.*?</provider>', '', mf, flags=re.DOTALL)
    mf = re.sub(r'<provider[^/]*/>', '', mf)
    print("  Stripped ContentProviders")

    # Remove appComponentFactory (CoreComponentFactory not in shell DEX)
    mf = re.sub(r'\s+android:appComponentFactory="[^"]*"', '', mf)
    print("  Stripped appComponentFactory")

    # Move LAUNCHER intent-filter from SActivity to MainActivity
    mf = re.sub(
        r'(<activity[^>]*android:name="com\.lianyu\.ai\.security\.SActivity"[^>]*>)\s*<intent-filter>.*?</intent-filter>',
        r'\1', mf, flags=re.DOTALL
    )
    mf = mf.replace(
        '<activity android:exported="false" android:hardwareAccelerated="true" android:launchMode="singleTask" android:name="com.lianyu.ai.MainActivity"',
        '<activity android:exported="true" android:hardwareAccelerated="true" android:launchMode="singleTask" android:name="com.lianyu.ai.MainActivity"'
    )
    # Fix self-closing tag → open/close with intent-filter
    mf = re.sub(
        r'(<activity[^>]*android:name="com\.lianyu\.ai\.MainActivity"[^>]*)/>',
        r'\1>\n            <intent-filter>\n                <action android:name="android.intent.action.MAIN"/>\n                <category android:name="android.intent.category.LAUNCHER"/>\n            </intent-filter>\n        </activity>',
        mf
    )
    print("  Moved LAUNCHER intent → MainActivity")

    # Add meta-data for potential real_app_class override
    if '<meta-data android:name="real_application_class"' not in mf:
        mf = mf.replace('<uses-native-library',
            '<meta-data android:name="real_application_class" android:value="com.lianyu.ai.LianYuApplication"/>\n        <uses-native-library', 1)
    print("  Added real_application_class meta-data")

    manifest.write_text(mf, encoding='utf-8')
    print("  Manifest saved")

    # Repack
    repacked = PROJECT / "app/build/outputs/apk/release/app-release-repacked.apk"
    if repacked.exists():
        repacked.unlink()
    run(["java", "-jar", str(APKTOOL), "b", "-f", "-o", str(repacked), str(SHELL_DECODED)], "apktool repack")


def phase5_assemble():
    """Replace DEX + SOs + sign."""
    print("\n═══ Phase 5: Assemble final APK ═══")
    repacked = PROJECT / "app/build/outputs/apk/release/app-release-repacked.apk"
    shell_dex = DEX_OUT / "classes.dex"
    shell_data = shell_dex.read_bytes()

    # Find SO dir (latest build)
    so_dirs = sorted(SO_DIR.glob("*/obj/local"))
    so_base = so_dirs[-1] if so_dirs else SO_DIR

    tmp = str(RELEASE_APK) + '.tmp'
    replaced = 0
    with zipfile.ZipFile(repacked, 'r') as zin:
        with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename == 'classes.dex':
                    data = shell_data
                    replaced += 1
                elif item.filename.startswith('META-INF/'):
                    continue
                elif item.filename.startswith('classes') and item.filename.endswith('.dex'):
                    continue
                else:
                    for abi in ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86']:
                        for so in ['liblianyu_shell.so','liblianyu_security.so']:
                            if item.filename == f'lib/{abi}/{so}':
                                src = so_base / abi / so
                                if src.exists():
                                    data = src.read_bytes()
                                    replaced += 1
                                break
                        else:
                            continue
                        break
                zout.writestr(item, data)

    shutil.move(tmp, str(RELEASE_APK))
    print(f"  Replaced {replaced} files")

    # Sign
    signed = str(RELEASE_APK).replace('.apk', '-signed.apk')
    run([
        'cmd', '/c', str(APKSIGNER), 'sign',
        '--ks', str(KEYSTORE), '--ks-pass', f'pass:{STORE_PASS}',
        '--key-pass', f'pass:{KEY_PASS}', '--ks-key-alias', KS_ALIAS,
        '--out', signed, str(RELEASE_APK)
    ], "Sign")
    shutil.move(signed, str(RELEASE_APK))

    size_mb = RELEASE_APK.stat().st_size / 1024 / 1024
    print(f"\n✅ Final APK: {size_mb:.1f}MB")
    print(f"   Shell DEX: {len(shell_data)}B pure Java, zero Kotlin")
    print(f"   Path: {RELEASE_APK}")


def phase6_patch_crc32():
    """Post-build: inject SO .text CRC32 via patch_so_crc32.py, then re-sign."""
    print("\n═══ Phase 6: SO .text CRC32 Injection ═══")
    patch_py = PROJECT / "tools" / "patch_so_crc32.py"
    if not patch_py.exists():
        print("  Skipped — patch_so_crc32.py not found")
        return
    # Remove signature, patch, re-sign
    tmp_unsigned = RELEASE_APK.with_suffix(".unsigned.apk")
    with zipfile.ZipFile(RELEASE_APK, 'r') as zin:
        with zipfile.ZipFile(tmp_unsigned, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if not item.filename.startswith('META-INF/'):
                    zout.writestr(item, zin.read(item.filename))
    shutil.move(str(tmp_unsigned), str(RELEASE_APK))
    run([sys.executable, str(patch_py), "--apk", str(RELEASE_APK),
         "--config-dir", str(PROJECT / "core/security/src/main/cpp")], "patch_crc32")
    # Re-sign
    signed = str(RELEASE_APK).replace('.apk', '-signed.apk')
    run(['cmd', '/c', str(APKSIGNER), 'sign',
            '--ks', str(KEYSTORE), '--ks-pass', f'pass:{STORE_PASS}',
            '--key-pass', f'pass:{KEY_PASS}', '--ks-key-alias', KS_ALIAS,
         '--out', signed, str(RELEASE_APK)], "sign")
    shutil.move(signed, str(RELEASE_APK))
    print(f"  CRC32 patched + re-signed. APK: {os.path.getsize(str(RELEASE_APK))/1024/1024:.1f}MB")


def main():
    print("=" * 60)
    print("LianYu Ultimate Shell — 一键构建")
    print("=" * 60)

    missing = [name for name, value in [
        ("LIANYU_STORE_PASSWORD", STORE_PASS),
        ("LIANYU_KEY_PASSWORD", KEY_PASS),
    ] if not value]
    if missing:
        sys.exit(f"Release signing requires: {', '.join(missing)}")
    if not KEYSTORE.exists():
        sys.exit(f"Release keystore not found: {KEYSTORE}")

    if not APKTOOL.exists():
        print("❌ apktool.jar not found. Download from: https://github.com/iBotPeaches/Apktool/releases")
        print("   Save as: tools/apktool.jar")
        sys.exit(1)

    phase0_generate_payload()  # Pre-Gradle: generate VMP payload
    phase1_gradle()
    phase3b_harden_so()         # Post-Gradle: encrypt SO .text
    phase3_compile_shell()
    phase4_apktool()
    phase5_assemble()
    phase6_patch_crc32()

    print("\n" + "=" * 60)
    print("Build complete. Install:")
    print(f"  adb install -r \"{RELEASE_APK}\"")
    print("=" * 60)


if __name__ == "__main__":
    main()
