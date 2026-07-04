#!/usr/bin/env python3
"""
Post-build DEX splitter: repacks signed APK with balanced DEX split.
Usage: python3 split_dex.py --apk <signed_apk> --splits 4 --keystore <path> \
           --storepass <pw> --keyalias <alias> --keypass <pw>
"""
import sys, os, struct, glob, tempfile, shutil, subprocess, zipfile
from collections import defaultdict

def parse_dex_header(data):
    if len(data) < 0x70: raise ValueError("DEX too small")
    return {
        'file_size': struct.unpack_from('<I', data, 32)[0],
        'header_size': struct.unpack_from('<I', data, 36)[0],
        'map_off': struct.unpack_from('<I', data, 52)[0],
        'class_defs_size': struct.unpack_from('<I', data, 96)[0],
        'class_defs_off': struct.unpack_from('<I', data, 100)[0],
    }
CLASS_DEF_ITEM_SIZE = 32

def get_class_def(data, hdr, idx):
    off = hdr['class_defs_off'] + idx * CLASS_DEF_ITEM_SIZE
    return data[off:off + CLASS_DEF_ITEM_SIZE]

def build_split_dex(template, template_hdr, class_defs_list):
    orig_cd_start = template_hdr['class_defs_off']
    orig_cd_end = orig_cd_start + template_hdr['class_defs_size'] * CLASS_DEF_ITEM_SIZE
    pre, post = template[:orig_cd_start], template[orig_cd_end:]
    new = bytearray(pre)
    new_cd_off = len(new)
    new.extend(b''.join(class_defs_list))
    new.extend(post)
    struct.pack_into('<I', new, 32, len(new))
    struct.pack_into('<I', new, 96, len(class_defs_list))
    struct.pack_into('<I', new, 100, new_cd_off)
    shift = new_cd_off - orig_cd_start
    if template_hdr['map_off'] >= orig_cd_start:
        struct.pack_into('<I', new, 52, template_hdr['map_off'] + shift)
    struct.pack_into('<I', new, 8, 0)
    for i in range(20): new[12 + i] = 0
    return bytes(new)

def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--apk', required=True)
    ap.add_argument('--splits', type=int, default=4)
    ap.add_argument('--keystore', required=True)
    ap.add_argument('--storepass', required=True)
    ap.add_argument('--keyalias', required=True)
    ap.add_argument('--keypass', required=True)
    args = ap.parse_args()

    print(f"DEX splitter: {args.apk} → {args.splits} balanced DEX files")

    tmp = tempfile.mkdtemp()
    try:
        # Extract all classes*.dex
        dex_data = {}
        with zipfile.ZipFile(args.apk, 'r') as zf:
            for name in zf.namelist():
                if name.startswith('classes') and name.endswith('.dex'):
                    dex_data[name] = zf.read(name)

        if not dex_data:
            print("  No DEX files found"); sys.exit(1)

        print(f"  Found {len(dex_data)} DEX file(s) in APK")

        # Collect all class_defs
        all_cds = []
        template = None
        template_hdr = None
        for name, data in sorted(dex_data.items()):
            hdr = parse_dex_header(data)
            if template is None:
                template, template_hdr = data, hdr
            for i in range(hdr['class_defs_size']):
                all_cds.append(get_class_def(data, hdr, i))

        total = len(all_cds)
        splits = min(args.splits, total)
        if splits < 2: splits = 2
        print(f"  Total class_defs: {total} → {splits} splits")

        # Round-robin
        buckets = [[] for _ in range(splits)]
        for i, cd in enumerate(all_cds):
            buckets[i % splits].append(cd)

        # Build output DEX
        split_files = {}
        for idx, bucket in enumerate(buckets):
            if not bucket: continue
            name = f"classes{'' if idx == 0 else idx + 1}.dex"
            new_dex = build_split_dex(template, template_hdr, bucket)
            path = os.path.join(tmp, name)
            with open(path, 'wb') as f: f.write(new_dex)
            split_files[name] = path
            print(f"    {name}: {len(new_dex):,} bytes ({len(bucket)} classes)")

        # Repack APK
        repacked = args.apk + ".repacked"
        with zipfile.ZipFile(args.apk, 'r') as zin:
            with zipfile.ZipFile(repacked, 'w', zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    if item.filename.startswith('classes') and item.filename.endswith('.dex'):
                        continue
                    zout.writestr(item, zin.read(item.filename))
                for name, path in split_files.items():
                    zout.write(path, name)
        print(f"  Repacked: {repacked}")

        # Find SDK tools
        sdk_build_tools = '/opt/android-sdk/build-tools/35.0.0'
        zipalign = os.path.join(sdk_build_tools, 'zipalign')
        apksigner = os.path.join(sdk_build_tools, 'apksigner')

        # Zipalign
        aligned = args.apk + ".aligned"
        subprocess.run([zipalign, '-f', '-p', '4', repacked, aligned], check=True)
        print(f"  Aligned: {aligned}")

        # Re-sign
        subprocess.run([
            apksigner, 'sign',
            '--ks', args.keystore,
            '--ks-pass', f'pass:{args.storepass}',
            '--ks-key-alias', args.keyalias,
            '--key-pass', f'pass:{args.keypass}',
            '--v1-signing-enabled', 'false',
            '--v2-signing-enabled', 'true',
            '--v3-signing-enabled', 'true',
            aligned
        ], check=True)
        print(f"  Signed: {aligned}")

        # Replace original
        os.rename(aligned, args.apk)
        os.remove(repacked)
        print(f"  Done: {args.apk}")

    finally:
        shutil.rmtree(tmp, ignore_errors=True)

if __name__ == '__main__':
    main()
