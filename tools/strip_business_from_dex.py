#!/usr/bin/env python3
"""
Strip ALL non-shell classes from a DEX file, keeping only:
  - com/lianyu/ai/security/**  (shell/security bootstrap)
  - Android/Kotlin framework classes needed by the shell
  - R$ and BuildConfig classes (resources)

Works with R8-minified DEX where shell classes are preserved by ProGuard keep rules.

Usage:
  python3 tools/strip_business_from_dex.py classes.dex -o classes-shell.dex
"""

import argparse, os, shutil, subprocess, sys, tempfile
from pathlib import Path

BAKSMALI = Path.home() / ".cache" / "lianyu_tools" / "baksmali-2.5.2.jar"
SMALI = Path.home() / ".cache" / "lianyu_tools" / "smali-2.5.2.jar"

# Business packages to STRIP (everything else is kept).
# All non-com.lianyu.ai classes (AndroidX, Kotlin, OkHttp, Room, Compose, etc.)
# are implicitly kept — they're framework/library dependencies.
BUSINESS_PACKAGES = [
    "com/lianyu/ai/feature",
    "com/lianyu/ai/network",
    "com/lianyu/ai/database",
    "com/lianyu/ai/uicommon",
    "com/lianyu/ai/common",
    "com/lianyu/ai/internal",
]

# Packages under com/lianyu/ai to ALWAYS KEEP (not stripped even if in BUSINESS_PACKAGES)
KEEP_PACKAGES = [
    "com/lianyu/ai/security",
]

# Individual classes to KEEP (full smali path without .smali)
# These are the shell bootstrap classes that must survive stripping.
SHELL_CLASSES = [
    # Application entry (manifest references this class by name)
    "com/lianyu/ai/security/LianYuShellApplication",
    # R classes (resource IDs needed by the shell)
    "com/lianyu/ai/R",
    # BuildConfig
    "com/lianyu/ai/BuildConfig",
    # Shell-facing shim classes that reference business classes via ::class.java
    # These must survive stripping so ART can resolve them during verification
    "com/lianyu/ai/MainActivity",
    "com/lianyu/ai/feature/notification/CompanionKeepAliveService",
    "com/lianyu/ai/feature/notification/BootReceiver",
    "com/lianyu/ai/feature/wechat/service/WeChatBootReceiver",
    "com/lianyu/ai/feature/wechat/service/WeChatPollingService",
    "com/lianyu/ai/feature/wechat/service/WeChatProactiveMessageReceiver",
]


def main():
    parser = argparse.ArgumentParser(description="Strip business classes from DEX")
    parser.add_argument("dex", type=Path, help="Input DEX file")
    parser.add_argument("--output", "-o", type=Path, default=Path("classes-shell.dex"))
    parser.add_argument("--keep-root-classes", action="store_true",
                        help="Keep ALL non-package classes (R8-obfuscated). Use when shell classes have no ProGuard keep rules.")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory(prefix="lianyu_strip_") as tmp:
        work = Path(tmp)

        # Step 1: Disassemble
        smali_dir = work / "smali"
        print(f"Disassembling {args.dex}...")
        subprocess.run(["java", "-jar", str(BAKSMALI), "d", str(args.dex), "-o", str(smali_dir)],
                       check=True, capture_output=True, text=True)
        total = sum(1 for _ in smali_dir.rglob("*.smali"))
        print(f"  Total: {total:,} smali files")

        # Step 2: Parse shell smali files to find ALL type references
        # We keep every class the shell directly references, even if in business packages.
        shell_refs = set()  # set of smali paths the shell depends on
        
        # Find shell smali files (classes we definitely keep)
        shell_dir = smali_dir / "com" / "lianyu" / "ai" / "security"
        shell_files = list(shell_dir.rglob("*.smali")) if shell_dir.exists() else []
        # Also check the root app package for shell shims (SActivity, SReceiver, etc.)
        app_root = smali_dir / "com" / "lianyu" / "ai"
        for f in app_root.rglob("*.smali") if app_root.exists() else []:
            if any(kw in str(f) for kw in ["LianYuShellApplication", "SActivity", "SService",
                   "SReceiver", "SWechatBootReceiver", "SWechatPollingService",
                   "SWechatProactiveMessageReceiver", "OnePieceShellGate",
                   "CompositeVmpRuntime", "DexFragmentLoader"]):
                shell_files.append(f)
        
        # Extract type references from shell smali files
        import re
        type_pattern = re.compile(r'L([^;]+);')
        for sf in shell_files:
            try:
                content = sf.read_text()
                for m in type_pattern.finditer(content):
                    shell_refs.add(m.group(1))
            except Exception:
                pass
        
        # Also add explicit keep classes (R, BuildConfig, etc.) and their inner classes
        for cls in SHELL_CLASSES:
            shell_refs.add(cls)
            # Guess inner class patterns from smali directory
            cls_path = cls.replace('.', '/') + ".smali"
            for f in smali_dir.rglob("*.smali"):
                rel = str(f.relative_to(smali_dir).with_suffix("")).replace(os.sep, "/")
                if rel == cls or rel.startswith(cls + "$"):
                    shell_refs.add(rel)
        
        print(f"  Shell references: {len(shell_refs)} unique types")
        
        # Step 3: Strip business classes (keep all framework/library deps + shell refs)
        removed = 0
        kept = 0

        for smali_file in list(smali_dir.rglob("*.smali")):
            rel = smali_file.relative_to(smali_dir)
            rel_str = str(rel.with_suffix("")).replace(os.sep, "/")

            # Decide: strip this class or keep it?
            should_strip = False

            # Only strip classes under known business packages
            for bp in BUSINESS_PACKAGES:
                if rel_str.startswith(bp + "/") or rel_str == bp:
                    should_strip = True
                    break

            # NEVER strip explicitly kept packages (overrides business packages)
            if should_strip:
                for kp in KEEP_PACKAGES:
                    if rel_str.startswith(kp + "/") or rel_str == kp:
                        should_strip = False
                        break

            # NEVER strip classes referenced by the shell
            if should_strip and rel_str in shell_refs:
                should_strip = False

            if should_strip:
                smali_file.unlink()
                removed += 1
            else:
                kept += 1

        # Clean empty directories
        for d in sorted(smali_dir.rglob("*"), reverse=True):
            if d.is_dir() and not any(d.iterdir()):
                d.rmdir()

        print(f"  Kept:   {kept:,} smali files (shell + framework)")
        print(f"  Removed: {removed:,} smali files (business)")

        if kept == 0:
            print("ERROR: No shell classes found! Check ProGuard keep rules.")
            sys.exit(1)

        # Step 3: Reassemble
        print(f"Reassembling to {args.output}...")
        subprocess.run(["java", "-jar", str(SMALI), "a", str(smali_dir), "-o", str(args.output)],
                       check=True, capture_output=True, text=True)
        size = args.output.stat().st_size
        print(f"  Shell DEX: {args.output} ({size:,} bytes, {kept} classes)")


if __name__ == "__main__":
    main()
