#!/usr/bin/env python3
"""Fast code_item scanner using memoryview + large skips."""
import struct, sys, os, zipfile, hashlib, zlib

KEY = bytes([0x4C,0x69,0x61,0x6E,0x59,0x75,0x53,0x68,0x65,0x6C,0x6C,0x4B,0x65,0x79,0x32,0x35])

def scan_fast(data):
    """Memoryview-based fast scan for code_items."""
    mv = memoryview(data)
    dlen = len(data)
    results = []
    seen = set()
    
    off = 112  # start after header
    while off < dlen - 20:
        # Quick check: registers and insns must be reasonable
        regs = mv[off] | (mv[off+1] << 8)
        if regs == 0 or regs > 256:
            off += 2; continue
        
        ins = mv[off+2] | (mv[off+3] << 8)
        if ins > 50:
            off += 2; continue
        
        outs = mv[off+4] | (mv[off+5] << 8)
        if outs > 50:
            off += 2; continue
        
        tries = mv[off+6] | (mv[off+7] << 8)
        if tries > 100:
            off += 2; continue
        
        insns = mv[off+12] | (mv[off+13] << 8) | (mv[off+14] << 16) | (mv[off+15] << 24)
        if insns < 1 or insns > 50000:
            off += 2; continue
        
        cs = 16 + insns * 2
        if tries > 0: cs += tries * 8 + 20
        cs = min(cs, dlen - off)
        
        if off not in seen:
            results.append((off, cs))
            seen.add(off)
        
        off += max(cs, 2)  # skip past this code item
    
    return results

def encrypt(data):
    return bytes(b ^ KEY[i % 16] ^ (i * 0x9D & 0xFF) for i, b in enumerate(data))

def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/lianyu_shell"
    os.makedirs(out_dir, exist_ok=True)
    
    with zipfile.ZipFile(apk, 'r') as zf:
        dex_files = sorted(n for n in zf.namelist() if n.endswith('.dex'))
        total = 0; all_code = []
        
        for dex_name in dex_files:
            raw = bytearray(zf.read(dex_name))
            items = scan_fast(bytes(raw))
            
            for off, cs in items:
                all_code.append((off, cs, bytes(raw[off:off+cs])))
                for i in range(cs):
                    raw[off + i] = 0
            
            for i in range(8, 32): raw[i] = 0
            struct.pack_into('<I', raw, 8, zlib.adler32(bytes(raw)) & 0xFFFFFFFF)
            sha = hashlib.sha1(bytes(raw)).digest()
            for i in range(20): raw[12 + i] = sha[i]
            
            with open(os.path.join(out_dir, dex_name), 'wb') as f:
                f.write(raw)
            
            total += len(items)
            print(f"[{dex_name}] {len(items)} code_items ({len(raw):,}B)")
        
        blob = bytearray(); entries = []; boff = 0
        for co, cs, cb in all_code:
            entries.append(struct.pack('<III', co, cs, boff))
            blob.extend(cb); boff += cs
        
        raw_blob = struct.pack('<I', total) + b''.join(entries) + bytes(blob)
        encrypted = encrypt(raw_blob)
        
        bpath = os.path.join(out_dir, 'code_items.bin')
        with open(bpath, 'wb') as f:
            f.write(encrypted)
        
        print(f"\nTotal: {total} methods | blob: {bpath} ({len(encrypted):,}B)")

if __name__ == '__main__':
    main()
