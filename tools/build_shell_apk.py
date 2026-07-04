#!/usr/bin/env python3
"""
build_shell_apk.py — LianYu 三代壳完整构造流水线

Phase 1: Gradle 构建 Release APK
Phase 2: 提取 DEX → 生成壳载荷 (code_items.bin + shell_payload.bin)
Phase 3: VMP 加固 (opcode 随机化 + 字节码混淆)
Phase 4: Dex2C 转译安全方法
Phase 5: ndk-build 编译所有 SO (含加密载荷)
Phase 6: SO .text 段加密
Phase 7: 构建最小壳 DEX (javac + d8)
Phase 8: 替换 classes.dex + 签名

用法: python tools/build_shell_apk.py [--clean] [--skip-gradle]
"""

import os, sys, struct, shutil, hashlib, zlib, zipfile
import argparse, tempfile, subprocess
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
SDK = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk"
NDK = SDK / "ndk" / "30.0.14904198"
BT_DIR = sorted((SDK / "build-tools").glob("*"))[-1] if (SDK / "build-tools").exists() else None
ANDROID_JAR = SDK / "platforms" / "android-34" / "android.jar"
CPP_DIR = PROJECT / "core" / "security" / "src" / "main" / "cpp"
APK_OUT = PROJECT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
KEYSTORE = PROJECT / "release.keystore"
STORE_PASS = os.environ.get("LIANYU_STORE_PASSWORD", "")
KEY_PASS = os.environ.get("LIANYU_KEY_PASSWORD", "")
KEY_ALIAS = os.environ.get("LIANYU_KEY_ALIAS", "your_alias")
SHELL_SRC = PROJECT / "app" / "build" / "tmp" / "shell_src"
SHELL_CLASSES = PROJECT / "app" / "build" / "tmp" / "shell_classes"
MINIMAL_DEX = PROJECT / "app" / "build" / "tmp" / "minimal_dex_out"
PYTHON = sys.executable

# ── Helpers ──
def run(cmd, **kw):
    print(f"  $ {' '.join(str(c) for c in cmd)}")
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        print(f"  FAILED (exit={r.returncode})", file=sys.stderr)
        sys.exit(1)
    return r

def step(title):
    print(f"\n{'='*60}\n  {title}\n{'='*60}")

# ═══════════════════════════════════════════════════════
# Phase 1: Gradle
# ═══════════════════════════════════════════════════════
def phase1_gradle(skip=False):
    step("Phase 1: Gradle assembleRelease")
    if skip:
        print("  Skipped (--skip-gradle)")
        return APK_OUT if APK_OUT.exists() else None
    env = os.environ.copy()
    env.update({k: v for k, v in [
        ("LIANYU_STORE_PASSWORD", STORE_PASS),
        ("LIANYU_KEY_PASSWORD", KEY_PASS),
        ("LIANYU_KEY_ALIAS", KEY_ALIAS),
    ] if v})
    for d in ["core/security/build", "core/security/.cxx", "app/build/outputs/apk/release"]:
        p = PROJECT / d
        if p.exists(): shutil.rmtree(p, ignore_errors=True)
    run(["./gradlew", "assembleRelease",
         "-x", "lintVitalAnalyzeRelease",
         "-x", "lintVitalRelease",
         "-x", "lintVitalReportRelease",
         "--no-daemon"],
        cwd=str(PROJECT), env=env, timeout=600)
    if not APK_OUT.exists():
        apk_u = APK_OUT.parent / "app-release-unsigned.apk"
        if apk_u.exists():
            shutil.copy(apk_u, APK_OUT)
    print(f"  ✅ APK: {APK_OUT.stat().st_size/1024/1024:.1f} MB")
    return APK_OUT

# ═══════════════════════════════════════════════════════
# Phase 2: Shell Payload
# ═══════════════════════════════════════════════════════
def phase2_payload(apk_path):
    step("Phase 2: Shell Payload (code_items.bin + shell_payload.bin)")
    assets = PROJECT / "app" / "src" / "main" / "assets" / "lianyu_shell"
    assets.mkdir(parents=True, exist_ok=True)

    # 2a: Extract code_items (function extraction)
    extractor = PROJECT / "tools" / "dex_extract_full.py"
    if extractor.exists():
        run([PYTHON, str(extractor), str(apk_path), str(assets.parent)],
            cwd=str(PROJECT), timeout=120)
        ci = assets / "code_items.bin"
        print(f"  ✅ code_items.bin: {ci.stat().st_size/1024/1024:.1f} MB" if ci.exists() else "  ⚠ code_items.bin missing")

    # 2b: Package shell payload (whole-DEX encryption)
    packager = PROJECT / "tools" / "package_shell_payload.py"
    if packager.exists():
        run([PYTHON, str(packager), str(apk_path), "--dev", "--output", str(assets.parent)],
            cwd=str(PROJECT), timeout=120)
        sp = assets / "shell_payload.bin"
        print(f"  ✅ shell_payload.bin: {sp.stat().st_size/1024/1024:.1f} MB" if sp.exists() else "  ⚠ shell_payload.bin missing")

# ═══════════════════════════════════════════════════════
# Phase 3: VMP Hardening
# ═══════════════════════════════════════════════════════
def phase3_vmp():
    step("Phase 3: VMP Hardening")
    seed = get_cert_crc32()

    # 3a: Opcode randomization
    rand_ops = PROJECT / "tools" / "randomize_vmp_ops.py"
    if rand_ops.exists():
        run([PYTHON, str(rand_ops), "--seed", f"0x{seed:08X}"],
            cwd=str(PROJECT), timeout=30)
        print("  ✅ VMP opcodes randomized")

    # 3b: Bytecode obfuscation (opaque predicates + register shuffle)
    obf = PROJECT / "tools" / "obfuscate_bytecode.py"
    if obf.exists():
        run([PYTHON, str(obf), "--seed", f"0x{seed:08X}"],
            cwd=str(PROJECT), timeout=30)
        print("  ✅ VMP bytecode obfuscated")

# ═══════════════════════════════════════════════════════
# Phase 4: Dex2C
# ═══════════════════════════════════════════════════════
def phase4_dex2c():
    step("Phase 4: Dex2C Transpile")
    transpiler = PROJECT / "tools" / "dex2c_transpile.py"
    whitelist = PROJECT / "tools" / "dex2c_whitelist.txt"
    out_cpp = CPP_DIR / "generated" / "dex2c_methods.cpp"
    out_h = CPP_DIR / "generated" / "dex2c_registry.h"
    dex_input = PROJECT / "app" / "build" / "intermediates" / "dex" / "release" / "minifyReleaseWithR8" / "classes.dex"

    if not transpiler.exists() or not dex_input.exists():
        print("  ⚠ Skipped (missing transpiler or DEX)")
        return

    out_cpp.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        [PYTHON, str(transpiler), str(dex_input),
         "--whitelist", str(whitelist),
         "--out-cpp", str(out_cpp), "--out-h", str(out_h)],
        capture_output=True, text=True, timeout=300, cwd=str(PROJECT)
    )
    if result.returncode == 0:
        methods = result.stdout.count("→")
        print(f"  ✅ Dex2C: {methods} methods transpiled ({out_cpp.stat().st_size} bytes)")
    else:
        print(f"  ⚠ Dex2C failed (exit={result.returncode}), using stub")

# ═══════════════════════════════════════════════════════
# Phase 5: ndk-build
# ═══════════════════════════════════════════════════════
def phase5_ndk_build():
    step("Phase 5: ndk-build SOs")
    ndk_build = NDK / "ndk-build.cmd"
    if not ndk_build.exists():
        print("  ⚠ ndk-build not found, skipping")
        return

    run([str(ndk_build),
         f"NDK_PROJECT_PATH={CPP_DIR}",
         f"APP_BUILD_SCRIPT={CPP_DIR / 'Android.mk'}",
         f"NDK_APPLICATION_MK={CPP_DIR / 'Application.mk'}",
         "APP_ABI=all", "-j4"],
        cwd=str(CPP_DIR), timeout=600)
    print("  ✅ SOs built")

# ═══════════════════════════════════════════════════════
# Phase 6: SO .text Encryption
# ═══════════════════════════════════════════════════════
def phase6_so_encrypt():
    step("Phase 6: SO .text Encryption")
    enc_tool = PROJECT / "tools" / "encrypt_text_section.py"
    if not enc_tool.exists():
        print("  ⚠ encrypt_text_section.py not found, skipping")
        return
    seed = get_cert_crc32()
    for abi in ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"]:
        so = CPP_DIR / "libs" / abi / "liblianyu_security.so"
        if so.exists():
            run([PYTHON, str(enc_tool), str(so), f"0x{seed:08X}"],
                cwd=str(PROJECT), timeout=60)
    print("  ✅ .text encrypted")

# ═══════════════════════════════════════════════════════
# Phase 7: Minimal Shell DEX
# ═══════════════════════════════════════════════════════
def phase7_minimal_dex():
    step("Phase 7: Minimal Shell DEX")
    builder = PROJECT / "tools" / "build_shell_dex.py"
    if builder.exists():
        run([PYTHON, str(builder)], cwd=str(PROJECT), timeout=60)
        dex = PROJECT / "app" / "build" / "tmp" / "minimal.dex"
        if dex.exists():
            compressed = len(zlib.compress(dex.read_bytes(), 9))
            print(f"  ✅ Minimal DEX: {dex.stat().st_size}B uncompressed / ~{compressed}B compressed")
            return dex
    print("  ⚠ Using manual javac+d8 fallback...")
    # Fallback: compile shell stubs manually
    SHELL_CLASSES.mkdir(parents=True, exist_ok=True)
    MINIMAL_DEX.mkdir(parents=True, exist_ok=True)
    shutil.rmtree(str(SHELL_CLASSES), ignore_errors=True)
    shutil.rmtree(str(MINIMAL_DEX), ignore_errors=True)
    SHELL_CLASSES.mkdir(parents=True, exist_ok=True)
    MINIMAL_DEX.mkdir(parents=True, exist_ok=True)
    java_files = list(SHELL_SRC.rglob("*.java"))
    if java_files:
        run(["javac", "-d", str(SHELL_CLASSES), "-cp", str(ANDROID_JAR),
             "-source", "1.8", "-target", "1.8"] + [str(f) for f in java_files],
            timeout=30)
        class_files = list(SHELL_CLASSES.rglob("*.class"))
        if class_files and BT_DIR:
            d8 = BT_DIR / "d8.bat"
            run(["cmd.exe", "/c", str(d8), "--lib", str(ANDROID_JAR),
                 "--output", str(MINIMAL_DEX), "--min-api", "26"] +
                [str(c) for c in class_files],
                timeout=30)
    dex = MINIMAL_DEX / "classes.dex"
    if dex.exists():
        final = PROJECT / "app" / "build" / "tmp" / "minimal.dex"
        shutil.copy(dex, final)
        print(f"  ✅ Fallback DEX: {final.stat().st_size}B")
        return final
    return None

# ═══════════════════════════════════════════════════════
# Phase 8: Replace DEX + Sign
# ═══════════════════════════════════════════════════════
def phase8_sign(minimal_dex):
    step("Phase 8: Replace DEX + Sign")
    if not minimal_dex or not minimal_dex.exists():
        print("  ⚠ No minimal DEX, keeping original")
        return

    dex_data = minimal_dex.read_bytes()
    tmp_apk = str(APK_OUT) + ".tmp"
    with zipfile.ZipFile(str(APK_OUT), 'r') as zin:
        with zipfile.ZipFile(tmp_apk, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                zout.writestr(item, dex_data if item.filename == 'classes.dex' else zin.read(item.filename))
    shutil.move(tmp_apk, str(APK_OUT))
    print(f"  ✅ classes.dex → {len(dex_data)}B")

    # Sign
    apksigner = BT_DIR / "apksigner.bat"
    signed = str(APK_OUT).replace(".apk", "-signed.apk")
    run(["cmd.exe", "/c", str(apksigner), "sign",
         "--ks", str(KEYSTORE),
         "--ks-pass", f"pass:{STORE_PASS}",
         "--key-pass", f"pass:{KEY_PASS}",
         "--ks-key-alias", KEY_ALIAS,
         "--out", signed, str(APK_OUT)],
        timeout=60)
    shutil.move(signed, str(APK_OUT))
    print(f"  ✅ Signed: {Path(APK_OUT).stat().st_size/1024/1024:.1f} MB")

# ═══════════════════════════════════════════════════════
# Utils
# ═══════════════════════════════════════════════════════
def get_cert_crc32() -> int:
    if not KEYSTORE.exists():
        return int.from_bytes(os.urandom(4), 'little')
    r = subprocess.run(
        ["keytool", "-exportcert", "-keystore", str(KEYSTORE),
         "-storepass", STORE_PASS, "-alias", KEY_ALIAS],
        capture_output=True)
    return zlib.crc32(r.stdout) & 0xFFFFFFFF if r.returncode == 0 else int.from_bytes(os.urandom(4), 'little')

# ═══════════════════════════════════════════════════════
def main():
    parser = argparse.ArgumentParser(description="LianYu 三代壳完整流水线")
    parser.add_argument("--clean", action="store_true", help="清空构建缓存")
    parser.add_argument("--skip-gradle", action="store_true", help="跳过 Gradle 构建")
    args = parser.parse_args()

    missing = [name for name, value in [
        ("LIANYU_STORE_PASSWORD", STORE_PASS),
        ("LIANYU_KEY_PASSWORD", KEY_PASS),
    ] if not value]
    if missing:
        sys.exit(f"Release signing requires: {', '.join(missing)}")
    if not KEYSTORE.exists():
        sys.exit(f"Release keystore not found: {KEYSTORE}")

    if args.clean:
        for d in ["core/security/build", "core/security/.cxx", "app/build"]:
            p = PROJECT / d
            if p.exists(): shutil.rmtree(p, ignore_errors=True)
        print("✅ Cleaned")

    apk = phase1_gradle(args.skip_gradle)
    phase2_payload(apk)
    phase3_vmp()
    phase4_dex2c()
    phase5_ndk_build()
    phase6_so_encrypt()
    minimal = phase7_minimal_dex()
    phase8_sign(minimal)

    print(f"\n{'='*60}")
    print(f"  ✅ Pipeline complete")
    print(f"  APK: {APK_OUT} ({APK_OUT.stat().st_size/1024/1024:.1f} MB)")
    print(f"{'='*60}")

if __name__ == "__main__":
    main()
