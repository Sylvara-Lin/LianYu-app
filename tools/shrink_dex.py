#!/usr/bin/env python3
"""
shrink_dex.py — Reduce classes.dex to minimal shell by NOP-filling all code_items.

Uses pattern scanning (memoryview) to find code_item headers in the DEX data section,
then NOP-fills every valid code_item.

Strategy:
  1. Read DEX header to find data section bounds
  2. Pattern-scan the data section for code_item headers at 2-byte alignment:
     [regs:2][ins:2][outs:2][tries:2][debug:4][insns:4]
     Valid: regs <= 500, insns 1..50000
  3. NOP-fill each valid code_item (zero the entire item including header)
  4. Recompute Adler32 + SHA-1
  5. Output shrunk DEX

In the APK (ZIP), NOP-filled DEX compresses to almost nothing (~50KB after deflate).

Usage:
  python tools/shrink_dex.py <input.apk> <output.apk>
"""

import struct
import sys
import os
import zipfile
import hashlib
import zlib
import argparse
from typing import List, Tuple


def scan_code_items(data: bytes, data_off: int) -> List[Tuple[int, int]]:
    """
    Pattern-scan the DEX data section for valid code_item headers.
    Returns list of (offset, size) for each code_item found.
    
    code_item header layout:
      ushort registers_size
      ushort ins_size
      ushort outs_size
      ushort tries_size
      uint   debug_info_off
      uint   insns_size
    Total header = 2+2+2+2+4+4 = 16 bytes
    Total code_item = 16 + insns_size*2 + (tries_size > 0 ? tries processing)
    
    Valid code_item:
      - registers_size > 0 (must have at least 1 register)
      - registers_size <= 500
      - insns_size >= 1 and <= 50000
      - tries_size <= 200
    """
    code_items = []
    dlen = len(data)
    mv = memoryview(data)
    
    # Scan at byte alignment within data section, skip by code_item size after match
    off = data_off
    last_code_end = data_off
    
    while off <= dlen - 16:
        regs = mv[off] | (mv[off + 1] << 8)
        
        # Quick filter: registers_size is typically 1-50
        if regs == 0 or regs > 200:
            off += 1
            continue
        
        insns = mv[off + 12] | (mv[off + 13] << 8) | (mv[off + 14] << 16) | (mv[off + 15] << 24)
        
        # insns_size must be reasonable (largest known method is ~10000 insns)
        if insns < 1 or insns > 10000:
            off += 1
            continue
        
        tries = mv[off + 6] | (mv[off + 7] << 8)
        if tries > 50:
            off += 1
            continue
        
        # Found a plausible code_item header
        cs = 16 + insns * 2
        
        # Handle tries (catch handlers)
        if tries > 0:
            cs += tries * 8  # try_item: 4+2+2 = 8 bytes
            tmp = off + cs
            if tmp + 4 <= dlen:
                try:
                    # encoded_catch_handler_list: uleb128 size, then `size` (type_idx,addr) pairs
                    hcount, n = read_uleb128_fast(data, tmp)
                    tmp += n
                    # Each handler is one (type_idx, addr) pair
                    for _ in range(min(hcount, 500)):
                        if tmp + 2 > dlen:
                            break
                        _, n = read_uleb128_fast(data, tmp)
                        tmp += n
                        _, n = read_uleb128_fast(data, tmp)
                        tmp += n
                    # catch_all_addr only if hcount <= 0 (signed)
                    if hcount <= 0:
                        if tmp < dlen:
                            _, n = read_uleb128_fast(data, tmp)
                            tmp += n
                    cs = tmp - off
                except Exception:
                    pass
        
        cs = min(cs, dlen - off)
        # Sanity: code_item larger than 100KB is almost certainly a false positive
        if cs > 16 and cs <= 100000:
            code_items.append((off, cs))
            off += cs  # skip past this code_item
        else:
            off += 1
    
    return code_items


def read_uleb128_fast(data: bytes, offset: int) -> Tuple[int, int]:
    """Fast uleb128 reader for bytearray/bytes."""
    result = 0
    shift = 0
    end = len(data)
    while offset < end and shift < 35:
        b = data[offset]
        offset += 1
        result |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            return result, offset
        shift += 7
    return 0, offset


def shrink_dex(dex_data: bytes) -> bytes:
    """NOP-fill all code_items in DEX. Returns shrunk DEX."""
    data = bytearray(dex_data)
    dlen = len(data)
    
    magic = data[:4]
    if magic not in (b'dex\n',):
        raise ValueError(f"Invalid DEX magic: {magic}")
    
    # Parse DEX header to find data section
    # data_off is at header byte 108
    data_off = struct.unpack_from('<I', data, 108)[0]
    if data_off == 0 or data_off >= dlen:
        print(f"  WARNING: data_off=0x{data_off:X}, using full scan")
        data_off = 0x70  # fallback to after header
    
    print(f"  Scanning data section from 0x{data_off:X}...")
    code_items = scan_code_items(data, data_off)
    print(f"  Found {len(code_items):,} code_items")
    
    total_zeroed = 0
    for off, size in code_items:
        for i in range(size):
            data[off + i] = 0
        total_zeroed += size
    
    print(f"  NOP-filled: {total_zeroed:,} bytes ({len(code_items):,} methods)")
    
    # Recompute checksum
    for i in range(8, 32):
        data[i] = 0
    struct.pack_into('<I', data, 8, zlib.adler32(bytes(data)) & 0xFFFFFFFF)
    sha = hashlib.sha1(bytes(data)).digest()
    for i in range(20):
        data[12 + i] = sha[i]
    data_bytes = bytes(data)
    # Update file_size
    struct.pack_into('<I', data, 32, len(data_bytes))
    
    return bytes(data)


def shrink_apk(input_apk: str, output_apk: str):
    """Shrink all DEX files in an APK by NOP-filling code_items."""
    import tempfile, shutil
    
    with zipfile.ZipFile(input_apk, 'r') as zin:
        dex_files = sorted(n for n in zin.namelist() if n.endswith('.dex') and '/' not in n)
        
        if not dex_files:
            print("ERROR: No classes*.dex found in APK root")
            sys.exit(1)
        
        # Write to temp file first, then replace (can't do in-place zip modification)
        tmp_fd, tmp_path = tempfile.mkstemp(suffix='.apk', dir=os.path.dirname(output_apk))
        os.close(tmp_fd)
        
        try:
            with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    if item.filename in dex_files:
                        raw = zin.read(item.filename)
                        orig_size = len(raw)
                        shrunk = shrink_dex(raw)
                        print(f"  {item.filename}: {orig_size:,} → {len(shrunk):,} bytes")
                        zout.writestr(item, shrunk)
                    else:
                        zout.writestr(item, zin.read(item.filename))
            
            # Replace original
            shutil.move(tmp_path, output_apk)
        except Exception:
            os.unlink(tmp_path)
            raise
    
    out_size = os.path.getsize(output_apk)
    in_size = os.path.getsize(input_apk)
    print(f"\nAPK: {in_size/1024/1024:.1f} MB → {out_size/1024/1024:.1f} MB "
          f"(saved {(in_size-out_size)/1024:.1f} MB)")


def main():
    parser = argparse.ArgumentParser(description="Shrink DEX by NOP-filling code_items")
    parser.add_argument('input_apk', help='Input APK path')
    parser.add_argument('output_apk', help='Output APK path')
    args = parser.parse_args()
    
    if not os.path.exists(args.input_apk):
        print(f"ERROR: Input APK not found: {args.input_apk}")
        sys.exit(1)
    
    shrink_apk(args.input_apk, args.output_apk)


if __name__ == '__main__':
    main()
