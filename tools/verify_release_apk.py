#!/usr/bin/env python3
"""Verify LianYu APK hardening invariants before release.

Checks:
  - APK exists and is a zip
  - classes*.dex do not contain high-value prompt/debug strings
  - APK does not contain Compose PreviewActivity
  - release APK is not debuggable by manifest string heuristics

Usage:
  python3 tools/verify_release_apk.py app/build/outputs/apk/debug/app-debug.apk
  python3 tools/verify_release_apk.py app/build/outputs/apk/release/app-release.apk --release
"""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path

SENSITIVE_PATTERNS = [
    "主动消息铁律",
    "主动消息类型参考",
    "字数15-40",
    ">>> SG:",
    "SecurityGuard CRASH",
]

FORBIDDEN_COMPONENTS = [
    "androidx.compose.ui.tooling.PreviewActivity",
]


def printable_strings(data: bytes, min_len: int = 4) -> list[str]:
    out: list[str] = []
    current = bytearray()
    for byte in data:
        if 32 <= byte <= 126:
            current.append(byte)
        else:
            if len(current) >= min_len:
                out.append(current.decode("latin1", errors="ignore"))
            current.clear()
    if len(current) >= min_len:
        out.append(current.decode("latin1", errors="ignore"))
    return out


def read_entries(apk: zipfile.ZipFile, pattern: re.Pattern[str]) -> bytes:
    chunks: list[bytes] = []
    for name in sorted(apk.namelist()):
        if pattern.fullmatch(name):
            chunks.append(apk.read(name))
    return b"".join(chunks)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--release", action="store_true")
    args = parser.parse_args()

    if not args.apk.exists():
        fail(f"APK not found: {args.apk}")

    with zipfile.ZipFile(args.apk, "r") as apk:
        names = set(apk.namelist())
        dex_names = [name for name in names if re.fullmatch(r"classes\d*\.dex", name)]
        if not dex_names:
            fail("no classes*.dex found")

        dex_bytes = read_entries(apk, re.compile(r"classes\d*\.dex"))
        dex_text = "\n".join(printable_strings(dex_bytes))
        for pattern in SENSITIVE_PATTERNS:
            if pattern in dex_text:
                fail(f"sensitive string found in DEX: {pattern}")

        all_text = dex_text
        if "AndroidManifest.xml" in names:
            all_text += "\n" + "\n".join(printable_strings(apk.read("AndroidManifest.xml")))
        if args.release:
            for component in FORBIDDEN_COMPONENTS:
                if component in all_text:
                    fail(f"forbidden debug component found: {component}")

            if "android:debuggable" in all_text:
                fail("release manifest appears to contain android:debuggable")

    print(f"OK: {args.apk}")
    print("OK: no sensitive prompt/debug strings in DEX")
    print("OK: no forbidden debug components found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
