#!/usr/bin/env python3
"""strip_dex.py — Remove plaintext classes*.dex from signed APK (post-build)."""
import zipfile, sys, os, shutil

apk_path = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
tmp = apk_path + ".tmp"

with zipfile.ZipFile(apk_path, 'r') as zin:
    with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename.endswith('.dex') and '/' not in item.filename:
                print(f"  REMOVED: {item.filename}")
                continue
            zout.writestr(item, zin.read(item.filename))

os.replace(tmp, apk_path)
print(f"Stripped APK: {apk_path}")
