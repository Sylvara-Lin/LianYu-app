#!/usr/bin/env python3
"""
Strip kotlin.Metadata annotations from DEX files.
Removes the annotation that leaks original Kotlin class/function names
while keeping other runtime annotations (Retrofit, etc.) intact.

Usage: python3 strip_metadata.py <classes.dex> [classes2.dex ...]
  Modifies files in-place. Backup first.
"""

import struct
import sys
import os

def read_uleb128(data, offset):
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7f) << shift
        shift += 7
        if (byte & 0x80) == 0:
            break
    return result, offset

def write_uleb128(value):
    result = bytearray()
    while True:
        byte = value & 0x7f
        value >>= 7
        if value != 0:
            byte |= 0x80
        result.append(byte)
        if value == 0:
            break
    return bytes(result)

def parse_dex_header(data):
    if len(data) < 0x70:
        raise ValueError("DEX too small")
    hdr = {}
    hdr['string_ids_size'], hdr['string_ids_off'] = struct.unpack_from('<II', data, 56)
    hdr['type_ids_size'], hdr['type_ids_off'] = struct.unpack_from('<II', data, 64)
    hdr['class_defs_size'], hdr['class_defs_off'] = struct.unpack_from('<II', data, 96)
    return hdr

def get_string(data, hdr, idx):
    """Get string at string_id[idx]."""
    off = hdr['string_ids_off'] + idx * 4
    str_off = struct.unpack_from('<I', data, off)[0]
    # Read MUTF-8 length prefix
    # Simple: read until null (works for ASCII subset)
    end = data.find(b'\x00', str_off)
    if end < 0:
        return b""
    # MUTF-8 has length as uleb128 before the string
    _, data_start = read_uleb128(data, str_off)
    return data[data_start:end]

def get_type_string(data, hdr, type_idx):
    """Get the string for a type_id."""
    off = hdr['type_ids_off'] + type_idx * 4
    str_idx = struct.unpack_from('<I', data, off)[0]
    return get_string(data, hdr, str_idx)

def strip_metadata_from_dex(dex_data):
    """
    Remove kotlin.Metadata annotations from all class_defs.
    Returns modified DEX data, or original if no changes needed.
    """
    hdr = parse_dex_header(dex_data)
    num_classes = hdr['class_defs_size']
    
    # First, find the type_idx for "Lkotlin/Metadata;"
    metadata_type_idx = -1
    for i in range(hdr['type_ids_size']):
        type_str = get_type_string(dex_data, hdr, i)
        if type_str == b'Lkotlin/Metadata;':
            metadata_type_idx = i
            break
    
    if metadata_type_idx < 0:
        print("  kotlin.Metadata type not found in DEX — may already be stripped")
        return dex_data
    
    print(f"  Found kotlin.Metadata at type_idx={metadata_type_idx}")
    
    # Convert to mutable bytearray
    data = bytearray(dex_data)
    
    stripped_count = 0
    annotation_set_modified = set()
    
    CLASS_DEF_SIZE = 32
    ANNOTATION_ITEM_SIZE = 8  # type_idx(1) + visibility(1) + annotation_off(4)
    
    for class_idx in range(num_classes):
        class_def_off = hdr['class_defs_off'] + class_idx * CLASS_DEF_SIZE
        # annotations_off is at offset 20 within class_def_item
        annotations_off = struct.unpack_from('<I', data, class_def_off + 20)[0]
        
        if annotations_off == 0:
            continue
        
        # annotation_set_item: size(u4) + entries[annotation_off_item]
        if annotations_off + 4 > len(data):
            continue
        ann_set_size = struct.unpack_from('<I', data, annotations_off)[0]
        if ann_set_size == 0 or ann_set_size > 1000:  # sanity check
            continue
        
        # Iterate over annotation offsets in the set
        entries_off = annotations_off + 4
        new_entries = []
        for i in range(ann_set_size):
            entry_pos = entries_off + i * 4
            if entry_pos + 4 > len(data):
                break  # truncated data, skip
            ann_off = struct.unpack_from('<I', data, entry_pos)[0]
            if ann_off == 0:
                new_entries.append(ann_off)
                continue
            
            # Read annotation_item: visibility(u1) + type_idx(uleb128) + ...
            vis = data[ann_off]
            # Skip visibility byte, decode type_idx (uleb128 encoded)
            ann_type_idx, _ = read_uleb128(data, ann_off + 1)
            
            if ann_type_idx == metadata_type_idx:
                # Found kotlin.Metadata — skip this entry
                stripped_count += 1
                continue
            
            new_entries.append(ann_off)
        
        if len(new_entries) != ann_set_size:
            # Update annotation_set_item size
            struct.pack_into('<I', data, annotations_off, len(new_entries))
            # Rewrite entries
            for i, off in enumerate(new_entries):
                struct.pack_into('<I', data, entries_off + i * 4, off)
            annotation_set_modified.add(annotations_off)
    
    if stripped_count == 0:
        print("  No kotlin.Metadata annotations found to strip")
        return dex_data
    
    print(f"  Stripped {stripped_count} kotlin.Metadata annotations from {len(annotation_set_modified)} class_defs")
    return bytes(data)


def strip_dex_in_apk(apk_path):
    """Strip @Metadata from all DEX files in an APK."""
    import zipfile
    import tempfile
    import shutil
    
    tmp_path = apk_path + ".tmp_meta"
    
    with zipfile.ZipFile(apk_path, 'r') as zin:
        dex_files = [f for f in zin.namelist() if f.endswith('.dex')]
        
        if not dex_files:
            print("No DEX files found in APK")
            return False
        
        with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename.endswith('.dex'):
                    print(f"Processing {item.filename}...")
                    data = strip_metadata_from_dex(data)
                zout.writestr(item, data)
    
    shutil.move(tmp_path, apk_path)
    print(f"Updated: {apk_path}")
    return True


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <dex_file_or_apk> [...]")
        sys.exit(1)
    
    for path in sys.argv[1:]:
        if not os.path.exists(path):
            print(f"File not found: {path}")
            continue
        
        if path.endswith('.apk'):
            strip_dex_in_apk(path)
        elif path.endswith('.dex'):
            print(f"Processing {path}...")
            with open(path, 'rb') as f:
                data = f.read()
            data = strip_metadata_from_dex(data)
            # Backup
            bak = path + ".bak"
            if not os.path.exists(bak):
                os.rename(path, bak)
            with open(path, 'wb') as f:
                f.write(data)
            print(f"  Updated: {path} (backup: {bak})")
        else:
            print(f"Skipping non-DEX/APK file: {path}")

if __name__ == '__main__':
    main()
