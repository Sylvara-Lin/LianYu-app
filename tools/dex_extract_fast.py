#!/usr/bin/env python3
"""
dex_extract_fast.py — Fast code item extraction without string parsing.

Reads DEX, finds all code_items via class_defs, extracts + NOP-fills,
recomputes checksum. No string/type/proto parsing — just code offsets.
"""

import struct, sys, os, zipfile

DEX_MAGIC_PREFIX = b'dex\n0'
KEY = bytes([0x4C,0x69,0x61,0x6E,0x59,0x75,0x53,0x68,0x65,0x6C,0x6C,0x4B,0x65,0x79,0x32,0x35])

def read_uleb128(data, offset):
    result = shift = 0
    end = len(data)
    while offset < end and shift < 35:
        b = data[offset]; offset += 1
        result |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            return result, offset
        shift += 7
    return 0, end

def extract_code_items(dex_data):
    """Extract all code_item offsets and sizes from DEX data. No string parsing."""
    extracted = []
    dlen = len(dex_data)
    
    # Parse header to find class_defs
    h = struct.unpack_from('<8sI20sIII', dex_data, 0)
    h2 = struct.unpack_from('<III III III III III III III III', dex_data, 44)
    class_defs_size = h2[13]
    class_defs_off = h2[14]
    
    if class_defs_size == 0 or class_defs_off == 0:
        return extracted
    
    # Iterate class_defs
    for ci in range(min(class_defs_size, 10000)):
        cd_off = class_defs_off + ci * 32
        if cd_off + 32 > dlen:
            break
        entry = struct.unpack_from('<IIIIIIII', dex_data, cd_off)
        class_data_off = entry[7]
        if class_data_off == 0 or class_data_off >= dlen:
            continue
        
        off = class_data_off
        # Skip 4 uleb128 sizes
        for _ in range(4):
            _, off = read_uleb128(dex_data, off)
        
        # Process direct + virtual methods (max 500 per class)
        for _ in range(500):
            if off >= dlen:
                break
            # encoded_method: method_idx_diff, access_flags, code_off
            _, off = read_uleb128(dex_data, off)
            _, off = read_uleb128(dex_data, off)
            code_off_val, off = read_uleb128(dex_data, off)
            
            if code_off_val == 0:
                continue
            if code_off_val + 16 > dlen:
                break
            
            # Read code_item header
            co = code_off_val
            regs = struct.unpack_from('<H', dex_data, co)[0]
            ins_size = struct.unpack_from('<H', dex_data, co+2)[0]
            outs = struct.unpack_from('<H', dex_data, co+4)[0]
            tries = struct.unpack_from('<H', dex_data, co+6)[0]
            insns_count = struct.unpack_from('<I', dex_data, co+12)[0]
            
            code_size = 16 + insns_count * 2
            if tries > 0:
                code_size += tries * 8
                # Skip handler data (approximate)
                tmp = co + code_size
                if tmp + 4 < dlen:
                    hcount, tmp = read_uleb128(dex_data, tmp)
                    for _ in range(min(hcount, 100)):
                        cc, tmp = read_uleb128(dex_data, tmp)
                        tmp += cc * 8 + (8 if cc <= 0 else 0)
                    code_size = tmp - co
            
            code_size = min(code_size, dlen - co)
            if code_size > 0:
                extracted.append((co, code_size))
    
    return extracted

def encrypt(data):
    result = bytearray(data)
    for i in range(len(result)):
        result[i] ^= KEY[i % 16] ^ (i * 0x9D & 0xFF)
    return bytes(result)

def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/lianyu_shell"
    os.makedirs(out_dir, exist_ok=True)
    
    with zipfile.ZipFile(apk, 'r') as zf:
        dex_files = sorted(n for n in zf.namelist() if n.endswith('.dex'))
        all_code = []
        
        for dex_name in dex_files:
            data = bytearray(zf.read(dex_name))
            items = extract_code_items(bytes(data))
            all_code.extend(items)
            print(f"[{dex_name}] {len(items)} code_items extracted ({len(data):,}B)")
            
            # NOP fill
            for co, cs in items:
                for i in range(cs):
                    data[co + i] = 0
            
            # Recompute checksum (Adler32)
            import zlib
            for i in range(8, 32):
                data[i] = 0
            adler = zlib.adler32(bytes(data)) & 0xFFFFFFFF
            struct.pack_into('<I', data, 8, adler)
            import hashlib
            sha = hashlib.sha1(bytes(data)).digest()
            for i in range(20):
                data[12 + i] = sha[i]
            
            with open(os.path.join(out_dir, dex_name), 'wb') as f:
                f.write(data)
        
        # Build recovery blob
        blob = bytearray()
        index_entries = []
        offset = 0
        for co, cs in all_code:
            blob.extend(bytes(data[co:co+cs]))  # uses last DEX data
            index_entries.append((co, cs, offset))
            offset += cs
        
        # Binary format: [count:4][entries: code_off:4, code_size:4, offset_in_blob:4]
        header = struct.pack('<I', len(index_entries))
        body = b''
        for co, cs, off in index_entries:
            body += struct.pack('<III', co, cs, off)
        
        # Encrypt
        raw = header + body + bytes(blob)
        encrypted = encrypt(raw)
        
        blob_path = os.path.join(out_dir, 'code_items.bin')
        with open(blob_path, 'wb') as f:
            f.write(encrypted)
        
        print(f"\nTotal: {len(all_code)} methods extracted")
        print(f"Recovery blob: {blob_path} ({len(encrypted):,}B)")

if __name__ == '__main__':
    main()
