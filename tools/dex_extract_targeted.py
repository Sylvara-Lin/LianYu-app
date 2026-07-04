#!/usr/bin/env python3
"""
dex_extract_targeted.py — Extract ONLY security-critical methods.

Targets: NativeBridge, KmsProvider, SecurityGuard, CompositeVmpRuntime methods.
Fast: regex-based class finding, no full DEX parse.
"""

import struct, sys, os, zipfile, re

KEY = bytes([0x4C,0x69,0x61,0x6E,0x59,0x75,0x53,0x68,0x65,0x6C,0x6C,0x4B,0x65,0x79,0x32,0x35])
# Match by class short name (inside DEX descriptor or string table)
TARGETS = [
    b'Lcom/lianyu/ai/security/NativeBridge;',
    b'Lcom/lianyu/ai/security/KmsProvider;',
    b'Lcom/lianyu/ai/security/SecurityGuard;',
    b'Lcom/lianyu/ai/security/CompositeVmpRuntime;',
    b'Lcom/lianyu/ai/security/DexFragmentLoader;',
    b'Lcom/lianyu/ai/security/DynamicClassLoader;',
    b'Lcom/lianyu/ai/security/MethodRecoveryEngine;',
    b'Lcom/lianyu/ai/security/VmpDex2cDispatcher;',
]
TARGETS_SHORT = [b'NativeBridge', b'KmsProvider', b'SecurityGuard',
                 b'CompositeVmpRuntime', b'DexFragmentLoader', b'DynamicClassLoader',
                 b'MethodRecoveryEngine', b'VmpDex2cDispatcher']

def read_uleb128(data, offset):
    r = shift = 0
    end = len(data)
    while offset < end and shift < 35:
        b = data[offset]; offset += 1
        r |= (b & 0x7F) << shift
        if (b & 0x80) == 0: return r, offset
        shift += 7
    return 0, end

def find_class_offsets(dex, targets):
    """Find class_data_off for target classes via regex scan."""
    results = {}
    for name in targets:
        pos = 0
        while True:
            pos = dex.find(name, pos)
            if pos < 0: break
            # Check if it's a class name in type_ids format
            prefix = dex[max(0,pos-8):pos]
            if any(b in prefix for b in [b'Lcom/', b'Lcom.', b'/ai/']):
                results[name.decode()] = pos
            pos += 1
    return results

def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/lianyu_shell"
    os.makedirs(out_dir, exist_ok=True)

    with zipfile.ZipFile(apk, 'r') as zf:
        dex_files = sorted(n for n in zf.namelist() if n.endswith('.dex'))
        total = 0
        blob_parts = []
        
        for dex_name in dex_files:
            data = bytearray(zf.read(dex_name))
            dlen = len(data)
            
            # Parse class_defs to find class_data_off for target classes
            h2 = struct.unpack_from('<III III III III III III III III', data, 44)
            class_defs_size = h2[13]; class_defs_off = h2[14]
            type_ids_size = h2[5]; type_ids_off = h2[6]
            string_ids_size = h2[3]; string_ids_off = h2[4]
            
            extracted_this_dex = 0
            
            for ci in range(min(class_defs_size, 10000)):
                cd_off = class_defs_off + ci * 32
                if cd_off + 32 > dlen: break
                entry = struct.unpack_from('<IIIIIIII', data, cd_off)
                class_idx = entry[0]
                class_data_off = entry[7]
                if class_data_off == 0 or class_data_off >= dlen: continue
                
                # Get class name via type_ids -> string_ids
                type_str_off = struct.unpack_from('<I', data, type_ids_off + class_idx * 4)[0]
                # Skip uleb128 length prefix in string_data_item
                _, str_start = read_uleb128(data, type_str_off)
                name_end = data.find(0, str_start)
                if name_end < 0: continue
                class_name = bytes(data[str_start:name_end])
                
                is_target = any(t in class_name for t in TARGETS_SHORT)
                if not is_target: continue
                
                # Extract methods from this target class
                off = class_data_off
                for _ in range(4):
                    _, off = read_uleb128(data, off)
                
                for _ in range(500):
                    if off >= dlen: break
                    _, off = read_uleb128(data, off)  # method_idx_diff
                    _, off = read_uleb128(data, off)  # access_flags
                    code_off_val, off = read_uleb128(data, off)
                    if code_off_val == 0 or code_off_val + 16 > dlen: continue
                    
                    co = code_off_val
                    insns_count = struct.unpack_from('<I', data, co+12)[0]
                    tries = struct.unpack_from('<H', data, co+6)[0]
                    
                    cs = 16 + insns_count * 2
                    if tries > 0: cs += tries * 8 + 16  # approximate
                    cs = min(cs, dlen - co)
                    
                    # NOP fill
                    for i in range(cs):
                        data[co + i] = 0
                    
                    blob_parts.append((co, cs, bytes(data[co:co+cs])))
                    extracted_this_dex += 1
            
            # Recompute checksum
            import zlib, hashlib
            for i in range(8, 32): data[i] = 0
            struct.pack_into('<I', data, 8, zlib.adler32(bytes(data)) & 0xFFFFFFFF)
            sha = hashlib.sha1(bytes(data)).digest()
            for i in range(20): data[12 + i] = sha[i]
            
            with open(os.path.join(out_dir, dex_name), 'wb') as f:
                f.write(data)
            
            total += extracted_this_dex
            print(f"[{dex_name}] {extracted_this_dex} methods from {len(TARGETS)} target classes")
        
        # Build recovery blob
        blob = bytearray()
        entries = []
        off = 0
        for co, cs, body in blob_parts:
            entries.append((co, cs, off))
            blob.extend(body); off += cs
        
        hdr = struct.pack('<I', len(entries))
        bdy = b''
        for co, cs, boff in entries:
            bdy += struct.pack('<III', co, cs, boff)
        
        raw = hdr + bdy + bytes(blob)
        encrypted = bytes(b ^ KEY[i % 16] ^ (i * 0x9D & 0xFF) for i, b in enumerate(raw))
        
        bpath = os.path.join(out_dir, 'code_items.bin')
        with open(bpath, 'wb') as f:
            f.write(encrypted)
        
        print(f"\nTotal: {total} methods from {len(TARGETS)} security classes")
        print(f"Recovery blob: {bpath} ({len(encrypted):,}B)")

if __name__ == '__main__':
    main()
