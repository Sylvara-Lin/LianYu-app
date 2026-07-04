#!/usr/bin/env python3
"""Strip business classes from classes.dex in an APK, keeping only shell + security.

Post-build step for the one-piece shell pipeline. After encrypted payload fragments
are generated, this removes business classes from classes.dex so the APK's DEX
contains ONLY the shell wrapper. All business logic lives in encrypted payload.

Usage:
  python3 tools/strip_classes_dex.py app-release.apk -o app-release-stripped.apk

The tool disassembles classes.dex with baksmali, removes smali for business
packages, reassembles with smali, and repackages the APK.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from urllib.request import urlretrieve

BAKSMALI_VERSION = "2.5.2"
SMALI_VERSION = "2.5.2"
BAKSMALI_URL = f"https://bitbucket.org/JesusFreke/smali/downloads/baksmali-{BAKSMALI_VERSION}.jar"
SMALI_URL = f"https://bitbucket.org/JesusFreke/smali/downloads/smali-{SMALI_VERSION}.jar"

# Business packages to strip from classes.dex
STRIP_PACKAGES = [
    "com/lianyu/ai/database",
    "com/lianyu/ai/feature",
    "com/lianyu/ai/network",
    "com/lianyu/ai/uicommon",
]

# Individual classes to strip
STRIP_CLASSES = [
    "Lcom/lianyu/ai/LianYuApplication;",
    "Lcom/lianyu/ai/MainActivity;",
    "Lcom/lianyu/ai/internal/DexPadding;",
]

# R classes are KEPT — resource IDs only, no business logic


def ensure_jars(cache_dir: Path) -> tuple[Path, Path]:
    cache_dir.mkdir(parents=True, exist_ok=True)
    baksmali_jar = cache_dir / f"baksmali-{BAKSMALI_VERSION}.jar"
    smali_jar = cache_dir / f"smali-{SMALI_VERSION}.jar"
    for jar, url, name in [(baksmali_jar, BAKSMALI_URL, "baksmali"), (smali_jar, SMALI_URL, "smali")]:
        if not jar.exists() or jar.stat().st_size < 1000:
            print(f"  Downloading {name} {SMALI_VERSION}...")
            urlretrieve(url, jar)
            if jar.stat().st_size < 1000:
                raise SystemExit(f"ERROR: Failed to download {name}")
        print(f"  {name}: {jar} ({jar.stat().st_size:,} bytes)")
    return baksmali_jar, smali_jar


def disassemble(dex_path: Path, out_dir: Path, baksmali_jar: Path) -> None:
    print(f"  Disassembling {dex_path}...")
    subprocess.run(
        ["java", "-jar", str(baksmali_jar), "d", str(dex_path), "-o", str(out_dir)],
        check=True, capture_output=True, text=True,
    )
    count = sum(1 for _ in out_dir.rglob("*.smali"))
    print(f"  Disassembled: {count:,} smali files")


def strip_business_classes(smali_dir: Path) -> int:
    removed = 0
    for pkg in STRIP_PACKAGES:
        pkg_dir = smali_dir / pkg
        if pkg_dir.exists():
            files = sum(1 for _ in pkg_dir.rglob("*.smali"))
            shutil.rmtree(pkg_dir)
            removed += files
            print(f"  Removed package {pkg}/ ({files} files)")
    for cls_name in STRIP_CLASSES:
        cls_path = cls_name.removeprefix("L").removesuffix(";")
        smali_file = smali_dir / f"{cls_path}.smali"
        if smali_file.exists():
            smali_file.unlink(); removed += 1
            print(f"  Removed class {cls_name}")
        # Inner classes
        inner_dir = smali_dir / cls_path.rsplit("/", 1)[0]
        base = cls_path.rsplit("/", 1)[-1]
        if inner_dir.exists():
            for inner in inner_dir.glob(f"{base}$*.smali"):
                inner.unlink(); removed += 1
                print(f"  Removed inner class {inner}")
    return removed


def reassemble(smali_dir: Path, out_dex: Path, smali_jar: Path) -> None:
    print(f"  Reassembling to {out_dex}...")
    subprocess.run(
        ["java", "-jar", str(smali_jar), "a", str(smali_dir), "-o", str(out_dex)],
        check=True, capture_output=True, text=True,
    )
    print(f"  Reassembled: {out_dex.stat().st_size:,} bytes")


def repackage_apk(apk_path: Path, stripped_dex: Path, out_apk: Path) -> None:
    print(f"  Repackaging {apk_path} -> {out_apk}...")
    with zipfile.ZipFile(apk_path, "r") as zin:
        with zipfile.ZipFile(out_apk, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if item.filename == "classes.dex":
                    zout.writestr(item, stripped_dex.read_bytes())
                    print(f"  Replaced classes.dex")
                elif item.filename.startswith("META-INF/"):
                    continue  # strip old signatures
                else:
                    zout.writestr(item, zin.read(item.filename))
    print(f"  Repackaged: {out_apk.stat().st_size:,} bytes")


def main() -> None:
    parser = argparse.ArgumentParser(description="Strip business classes from classes.dex")
    parser.add_argument("apk", type=Path, help="APK path")
    parser.add_argument("--output", "-o", type=Path, help="Output APK")
    parser.add_argument("--cache-dir", type=Path,
                        default=Path.home() / ".cache" / "lianyu_tools")
    args = parser.parse_args()

    if not args.apk.exists():
        raise SystemExit(f"ERROR: APK not found: {args.apk}")
    output = args.output or args.apk.parent / f"{args.apk.stem}_stripped.apk"

    print("=" * 60)
    print("LianYu DEX Stripper — One-Piece Shell")
    print("=" * 60)

    baksmali_jar, smali_jar = ensure_jars(args.cache_dir)

    with tempfile.TemporaryDirectory(prefix="lianyu_strip_") as tmp:
        work_dir = Path(tmp)
        print(f"\n[*] Work directory: {work_dir}")

        dex_input = work_dir / "classes_original.dex"
        print(f"  Extracting classes.dex from {args.apk}...")
        with zipfile.ZipFile(args.apk, "r") as z:
            dex_input.write_bytes(z.read("classes.dex"))
        print(f"  Original DEX: {dex_input.stat().st_size:,} bytes")

        smali_dir = work_dir / "smali"
        disassemble(dex_input, smali_dir, baksmali_jar)

        print("  Stripping business classes...")
        removed = strip_business_classes(smali_dir)
        remaining = sum(1 for _ in smali_dir.rglob("*.smali"))
        print(f"  Stripped: {removed} removed, {remaining} remaining")

        stripped_dex = work_dir / "classes_stripped.dex"
        reassemble(smali_dir, stripped_dex, smali_jar)

        print(f"\n[*] Final APK: {output}")
        repackage_apk(args.apk, stripped_dex, output)

    print("\n" + "=" * 60)
    print(f"Done! Next: re-sign the APK -> {output}")
    print("=" * 60)


if __name__ == "__main__":
    main()
