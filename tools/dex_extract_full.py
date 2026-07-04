#!/usr/bin/env python3
"""
dex_extract_full.py — Complete function extraction for ALL methods.

Properly parses DEX 035/038 format, extracts every code_item,
NOP-fills original, recomputes checksum, stores encrypted recovery blob.
"""

import struct, sys, os, zipfile, hashlib, zlib

KEY = bytes([0x4C,0x69,0x61,0x6E,0x59,0x75,0x53,0x68,0x65,0x6C,0x6C,0x4B,0x65,0x79,0x32,0x35])

def read_uleb128(data, offset):
    """Read unsigned LEB128, safely bounded."""
    r, shift, end = 0, 0, len(data)
    while offset < end and shift < 35:
        b = data[offset]; offset += 1
        r |= (b & 0x7F) << shift
        if (b & 0x80) == 0: return r, offset
        shift += 7
    return 0, min(offset, end)

def read_mutf8(data, offset):
    """Read MUTF-8 string from string_data_item at offset. Returns (string_bytes, new_offset)."""
    if offset >= len(data): return b'', offset
    # Read uleb128 length (utf16 code units count, not byte count)
    utf16_len, offset = read_uleb128(data, offset)
    # Read until null terminator
    end = data.find(b'\x00', offset)
    if end < 0: end = min(offset + utf16_len * 3 + 1, len(data))  # fallback
    result = data[offset:end]
    # Skip null terminator
    return result, end + 1

def parse_dex(data):
    """Parse DEX header and return section offsets."""
    d = {}
    magic = data[:4]
    if magic not in (b'dex\n',): raise ValueError(f"Invalid DEX: {magic}")
    
    d['file_size'] = struct.unpack_from('<I', data, 32)[0]
    
    t = struct.unpack_from('<IIIIIIIIIIIIIIIIII', data, 44)
    keys = ['link_size','link_off','map_off',
            'string_ids_size','string_ids_off',
            'type_ids_size','type_ids_off',
            'proto_ids_size','proto_ids_off',
            'field_ids_size','field_ids_off',
            'method_ids_size','method_ids_off',
            'class_defs_size','class_defs_off',
            'data_size','data_off']
    for i, k in enumerate(keys): d[k] = t[i]
    
    # Read string table
    d['strings'] = []
    for i in range(d['string_ids_size']):
        off = struct.unpack_from('<I', data, d['string_ids_off'] + i * 4)[0]
        s, _ = read_mutf8(data, off)
        d['strings'].append(s)
    
    # Read type table (index into strings)
    d['types'] = []
    for i in range(d['type_ids_size']):
        idx = struct.unpack_from('<I', data, d['type_ids_off'] + i * 4)[0]
        d['types'].append(d['strings'][idx] if idx < len(d['strings']) else b'?')
    
    return d

def extract_all(data):
    """Extract ALL code_items from all classes. Returns (list of (offset,size,bytes), modified_data)."""
    d = parse_dex(data)
    data = bytearray(data)  # mutable copy
    extracted = []
    dlen = len(data)
    
    cd_off = d['class_defs_off']
    cd_size = d['class_defs_size']
    
    for ci in range(cd_size):
        entry_off = cd_off + ci * 32
        if entry_off + 32 > dlen: break
        entry = struct.unpack_from('<IIIIIIII', data, entry_off)
        class_idx, _, _, _, _, _, _, class_data_off = entry
        
        if class_data_off == 0 or class_data_off >= dlen:
            continue
        
        # Parse class_data_item
        off = class_data_off
        sizes = []
        for _ in range(4):
            v, off = read_uleb128(data, off)
            sizes.append(v)
        
        static_f, instance_f, direct_m, virtual_m = sizes
        
        # Skip fields
        for _ in range(static_f + instance_f):
            _, off = read_uleb128(data, off)
            _, off = read_uleb128(data, off)
        
        # Process methods
        for _ in range(direct_m + virtual_m):
            if off >= dlen: break
            _, off = read_uleb128(data, off)  # method_idx_diff
            _, off = read_uleb128(data, off)  # access_flags
            code_off, off = read_uleb128(data, off)
            
            if code_off == 0 or code_off + 16 > dlen:
                continue
            
            co = code_off
            # Read code_item header
            regs = struct.unpack_from('<H', data, co)[0]
            ins = struct.unpack_from('<H', data, co+2)[0]
            outs = struct.unpack_from('<H', data, co+4)[0]
            tries = struct.unpack_from('<H', data, co+6)[0]
            insns_count = struct.unpack_from('<I', data, co+12)[0]
            
            # Calculate code_item size
            cs = 16 + insns_count * 2
            if tries > 0:
                cs += tries * 8
                tmp = co + cs
                if tmp + 4 < dlen:
                    hcount, tmp = read_uleb128(data, tmp)
                    for _ in range(min(hcount, 50)):
                        cc, tmp = read_uleb128(data, tmp)
                        tmp += cc * 8 + (8 if cc <= 0 else 0)
                    cs = tmp - co
            
            cs = min(cs, dlen - co)
            if cs <= 0: continue
            
            # Save original bytes, then NOP-fill
            original = bytes(data[co:co+cs])
            extracted.append((co, cs, original))
            for i in range(cs):
                data[co + i] = 0
    
    return extracted, bytes(data)

def encrypt(data):
    return bytes(b ^ KEY[i % 16] ^ (i * 0x9D & 0xFF) for i, b in enumerate(data))

def recompute_dex_checksum(data):
    data = bytearray(data)
    for i in range(8, 32): data[i] = 0
    struct.pack_into('<I', data, 8, zlib.adler32(bytes(data)) & 0xFFFFFFFF)
    sha = hashlib.sha1(bytes(data)).digest()
    for i in range(20): data[12 + i] = sha[i]
    return bytes(data)

def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/lianyu_shell"
    os.makedirs(out_dir, exist_ok=True)
    
    with zipfile.ZipFile(apk, 'r') as zf:
        dex_files = sorted(n for n in zf.namelist() if n.endswith('.dex'))
        total = 0
        all_code = []  # (offset, size, bytes)
        
        for dex_name in dex_files:
            print(f"Parsing {dex_name} ({zf.getinfo(dex_name).file_size:,} bytes)...")
            raw = zf.read(dex_name)
            
            extracted, modified = extract_all(raw)
            total += len(extracted)
            print(f"  Extracted {len(extracted)} code items")
            
            # Recobuild checksum
            modified = recompute_dex_checksum(modified)
            
            # Save NOP-filled DEX
            with open(os.path.join(out_dir, dex_name), 'wb') as f:
                f.write(modified)
            
            all_code.extend(extracted)
        
        # Build recovery blob: [count:4][entries: off:4,size:4,blob_off:4][code_bytes...]
        blob = bytearray()
        entries = []
        boff = 0
        for co, cs, cb in all_code:
            entries.append(struct.pack('<III', co, cs, boff))
            blob.extend(cb)
            boff += cs
        
        header = struct.pack('<I', total)
        raw_blob = header + b''.join(entries) + bytes(blob)
        encrypted = encrypt(raw_blob)
        
        bpath = os.path.join(out_dir, 'code_items.bin')
        with open(bpath, 'wb') as f:
            f.write(encrypted)
        
        print(f"\nTotal: {total} methods extracted from {len(dex_files)} DEX files")
        print(f"Recovery blob: {bpath} ({len(encrypted):,}B)")
        print(f"Modified DEX: {out_dir}/")

if __name__ == '__main__':
    main()
