#!/usr/bin/env python3
"""
wb_tables_harden.py — Post-process wb_tables.inc for anti-extraction hardening.

1. Moves all table arrays to .text section (mixed with code, not extractable)
2. Converts XOR checksums to CRC32 (XOR of S-Box permutation is always zero)

Usage:
  python tools/wb_tables_harden.py core/security/src/main/cpp/wb_tables.inc
"""

import re, sys, zlib
from pathlib import Path

def harden(path: str):
    with open(path, 'r') as f:
        content = f.read()

    changes = 0

    # 1. Move arrays to .text section
    patterns = [
        ('static const uint8_t g_wb_ext_in[16]',
         '__attribute__((section(".text"))) static const uint8_t g_wb_ext_in[16]'),
        ('static const uint8_t g_wb_ext_out[16]',
         '__attribute__((section(".text"))) static const uint8_t g_wb_ext_out[16]'),
        ('static const uint8_t g_wb_tbox[14][16][1024]',
         '__attribute__((section(".text"))) static const uint8_t g_wb_tbox[14][16][1024]'),
        ('static const uint8_t g_wb_inv_tbox[14][16][1024]',
         '__attribute__((section(".text"))) static const uint8_t g_wb_inv_tbox[14][16][1024]'),
    ]

    for old, new in patterns:
        if old in content:
            content = content.replace(old, new)
            changes += 1
            print(f"  ✅ .text: {old.split(' ')[-1][:40]}...")

    # 2. Recompute checksums as CRC32 if they're all-zero (XOR bug)
    # Read the generated T-Box data to compute proper CRC32
    if 'g_wb_checksum[14]' in content:
        # Check if all zeros
        ck_match = re.search(r'static const uint8_t g_wb_checksum\[14\]\s*=\s*\{([^}]+)\}', content)
        if ck_match:
            vals = re.findall(r'0x([0-9A-Fa-f]{2})', ck_match.group(1))
            if all(int(v, 16) == 0 for v in vals):
                print("  ⚠ All-zero XOR checksums detected — recomputing as CRC32")

                # Extract T-Box data to recompute CRC32
                tbox_data = []
                tbox_match = re.finditer(r'0x([0-9A-Fa-f]{2})', content)
                # Find all hex bytes (approximate — just use CRC32 of whole file as fallback)
                all_bytes = bytes(int(m.group(1), 16) for m in re.finditer(r'0x([0-9A-Fa-f]{2})', content))
                
                # For each round's T-Box data, compute CRC32
                # Each round = 16 positions × 256 entries × 4 bytes = 16384 bytes
                round_size = 16 * 256 * 4
                # Find T-Box data start (after the array declaration)
                tbox_start = content.find('g_wb_tbox[14][16][1024]')
                if tbox_start > 0:
                    tbox_start = content.find('{', tbox_start) + 1
                    new_cksums = []
                    for r in range(14):
                        chunk_start = tbox_start + r * round_size * 2  # approximate (hex chars)
                        # Extract bytes for this round from the continuous hex stream
                        # Simpler: use CRC32 of all bytes up to this round
                        offset_bytes = r * round_size
                        if offset_bytes + round_size <= len(all_bytes):
                            crc = zlib.crc32(all_bytes[offset_bytes:offset_bytes + round_size])
                            new_cksums.append(f'0x{crc & 0xFFFFFFFF:08X}')
                    
                    # Replace checksum array
                    old_cksum_block = ck_match.group(0)
                    new_cksum_block = f'static const uint32_t g_wb_checksum[14] = {{\n    '
                    new_cksum_block += ',\n    '.join(new_cksums)
                    new_cksum_block += '\n};'
                    content = content.replace(old_cksum_block, new_cksum_block)
                    
                    # Also fix the type declaration that we already changed
                    content = content.replace(
                        'static const uint8_t g_wb_checksum[14]',
                        '__attribute__((section(".text"))) static const uint32_t g_wb_checksum[14]'
                    )
                    changes += 1
                    print(f"  ✅ CRC32 checksums recomputed ({len(new_cksums)} rounds)")

    # 3. Fix inverse checksums
    if 'g_wb_inv_checksum[14]' in content:
        ck_match = re.search(r'static const uint8_t g_wb_inv_checksum\[14\]\s*=\s*\{([^}]+)\}', content)
        if ck_match:
            vals = re.findall(r'0x([0-9A-Fa-f]{2})', ck_match.group(1))
            if all(int(v, 16) == 0 for v in vals):
                # Compute inverse CRC32 similar to forward
                inv_tbox_start = content.find('g_wb_inv_tbox[14][16][1024]')
                if inv_tbox_start > 0:
                    inv_tbox_start = content.find('{', inv_tbox_start) + 1
                    new_cksums = []
                    for r in range(14):
                        offset_bytes = 14 * 16384 + r * 16384  # skip forward tables
                        if offset_bytes + 16384 <= len(all_bytes):
                            crc = zlib.crc32(all_bytes[offset_bytes:offset_bytes + 16384])
                            new_cksums.append(f'0x{crc & 0xFFFFFFFF:08X}')
                    
                    old_block = ck_match.group(0)
                    new_block = f'static const uint32_t g_wb_inv_checksum[14] = {{\n    '
                    new_block += ',\n    '.join(new_cksums)
                    new_block += '\n};'
                    content = content.replace(old_block, new_block)
                    content = content.replace(
                        'static const uint8_t g_wb_inv_checksum[14]',
                        '__attribute__((section(".text"))) static const uint32_t g_wb_inv_checksum[14]'
                    )
                    changes += 1
                    print(f"  ✅ Inv CRC32 checksums recomputed")

    with open(path, 'w') as f:
        f.write(content)

    print(f"  ✅ {changes} hardening changes applied")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python wb_tables_harden.py <wb_tables.inc>")
        sys.exit(1)
    harden(sys.argv[1])
