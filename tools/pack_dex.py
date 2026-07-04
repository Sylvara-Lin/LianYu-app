#!/usr/bin/env python3
"""
pack_dex.py — VMP DEX Packer Post-Build Script
===============================================
Reads the built APK, extracts classes.dex, SM4-ECB encrypts it,
and generates a C source file (g_vmp_payload_data.cpp) with:
  - Encrypted DEX payload (g_vmp_payload[])
  - Obfuscated SM4 key (g_vmp_sm4_key[])
  - CRC32 of plaintext (g_vmp_payload_crc32)

Usage:
  python3 tools/pack_dex.py \
    --apk app/build/outputs/apk/release/app-release.apk \
    --out core/security/src/main/cpp/g_vmp_payload_data.cpp

The generated file is compiled into liblianyu_security.so.
At runtime, dex-packer.cpp decrypts and loads the DEX.
"""

import os
import sys
import struct
import hashlib
import zipfile
import argparse
import zlib


# ── S-Box ──────────────────────────────────────────────────────
SBOX = [
    0xd6,0x90,0xe9,0xfe,0xcc,0xe1,0x3d,0xb7,0x16,0xb6,0x14,0xc2,0x28,0xfb,0x2c,0x05,
    0x2b,0x67,0x9a,0x76,0x2a,0xbe,0x04,0xc3,0xaa,0x44,0x13,0x26,0x49,0x86,0x06,0x99,
    0x9c,0x42,0x50,0xf4,0x91,0xef,0x98,0x7a,0x33,0x54,0x0b,0x43,0xed,0xcf,0xac,0x62,
    0xe4,0xb3,0x1c,0xa9,0xc9,0x08,0xe8,0x95,0x80,0xdf,0x94,0xfa,0x75,0x8f,0x3f,0xa6,
    0x47,0x07,0xa7,0xfc,0xf3,0x73,0x17,0xba,0x83,0x59,0x3c,0x19,0xe6,0x85,0x4f,0xa8,
    0x68,0x6b,0x81,0xb2,0x71,0x64,0xda,0x8b,0xf8,0xeb,0x0f,0x4b,0x70,0x56,0x9d,0x35,
    0x1e,0x24,0x0e,0x5e,0x63,0x58,0xd1,0xa2,0x25,0x22,0x7c,0x3b,0x01,0x21,0x78,0x87,
    0xd4,0x00,0x46,0x57,0x9f,0xd3,0x27,0x52,0x4c,0x36,0x02,0xe7,0xa0,0xc4,0xc8,0x9e,
    0xea,0xbf,0x8a,0xd2,0x40,0xc7,0x38,0xb5,0xa3,0xf7,0xf2,0xce,0xf9,0x61,0x15,0xa1,
    0xe0,0xae,0x5d,0xa4,0x9b,0x34,0x1a,0x55,0xad,0x93,0x32,0x30,0xf5,0x8c,0xb1,0xe3,
    0x1d,0xf6,0xe2,0x2e,0x82,0x66,0xca,0x60,0xc0,0x29,0x23,0xab,0x0d,0x53,0x4e,0x6f,
    0xd5,0xdb,0x37,0x45,0xde,0xfd,0x8e,0x2f,0x03,0xff,0x6a,0x72,0x6d,0x6c,0x5b,0x51,
    0x8d,0x1b,0xaf,0x92,0xbb,0xdd,0xbc,0x7f,0x11,0xd9,0x5c,0x41,0x1f,0x10,0x5a,0xd8,
    0x0a,0xc1,0x31,0x88,0xa5,0xcd,0x7b,0xbd,0x2d,0x74,0xd0,0x12,0xb8,0xe5,0xb4,0xb0,
    0x89,0x69,0x97,0x4a,0x0c,0x96,0x77,0x7e,0x65,0xb9,0xf1,0x09,0xc5,0x6e,0xc6,0x84,
    0x18,0xf0,0x7d,0xec,0x3a,0xdc,0x4d,0x20,0x79,0xee,0x5f,0x3e,0xd7,0xcb,0x39,0x48,
]


def sm4_rotl(x, n):
    return ((x << n) | (x >> (32 - n))) & 0xFFFFFFFF


def sm4_t(x):
    t = 0
    t |= SBOX[(x >> 24) & 0xFF]; t <<= 8
    t |= SBOX[(x >> 16) & 0xFF]; t <<= 8
    t |= SBOX[(x >> 8) & 0xFF];  t <<= 8
    t |= SBOX[x & 0xFF]
    return t ^ sm4_rotl(t, 13) ^ sm4_rotl(t, 23)


def sm4_key_schedule(key_bytes):
    """Expand 16-byte key into 32 round keys."""
    FK = [0xa3b1bac6, 0x56aa3350, 0x677d9197, 0xb27022dc]
    CK = [
        0x00070e15,0x1c232a31,0x383f464d,0x545b6269,
        0x70777e85,0x8c939aa1,0xa8afb6bd,0xc4cbd2d9,
        0xe0e7eef5,0xfc030a11,0x181f262d,0x343b4249,
        0x50575e65,0x6c737a81,0x888f969d,0xa4abb2b9,
        0xc0c7ced5,0xdce3eaf1,0xf8ff060d,0x141b2229,
        0x30373e45,0x4c535a61,0x686f767d,0x848b9299,
        0xa0a7aeb5,0xbcc3cad1,0xd8dfe6ed,0xf4fb0209,
        0x10171e25,0x2c333a41,0x484f565d,0x646b7279,
    ]
    mk = [struct.unpack('>I', key_bytes[i*4:(i+1)*4])[0] for i in range(4)]
    k = [0] * 36
    for i in range(4):
        k[i] = mk[i] ^ FK[i]
    rk = [0] * 32
    for i in range(32):
        k[i+4] = k[i] ^ sm4_t(k[i+1] ^ k[i+2] ^ k[i+3] ^ CK[i])
        rk[i] = k[i+4]
    return rk


def sm4_encrypt_block(block, rk):
    """Encrypt one 16-byte block, return 16 bytes."""
    X = [struct.unpack('>I', block[i*4:(i+1)*4])[0] for i in range(4)]
    for i in range(32):
        X.append(X[i] ^ sm4_t(X[i+1] ^ X[i+2] ^ X[i+3] ^ rk[i]))
    out = b''
    for i in range(35, 31, -1):
        out += struct.pack('>I', X[i])
    return out


def sm4_ecb_encrypt(data, key_bytes):
    """SM4-ECB encrypt. data must be padded to 16-byte multiple."""
    assert len(data) % 16 == 0, f"data length {len(data)} not multiple of 16"
    rk = sm4_key_schedule(key_bytes)
    result = b''
    for i in range(0, len(data), 16):
        result += sm4_encrypt_block(data[i:i+16], rk)
    return result


def xor_obfuscate_key(key_bytes):
    """XOR-obfuscate SM4 key for storage in .rodata."""
    XOR_KEY_SEED = 0x9E3A7C51
    seed_bytes = struct.pack('<I', XOR_KEY_SEED)
    return bytes([(key_bytes[i] ^ seed_bytes[i % 4] ^ (i * 0x5D)) & 0xFF for i in range(16)])


def generate_c_source(plaintext, key_bytes, output_path):
    """Generate g_vmp_payload_data.cpp with encrypted payload + key + CRC32."""
    # Pad to 16-byte boundary
    pad_len = (16 - (len(plaintext) % 16)) % 16
    if pad_len > 0:
        plaintext += b'\x00' * pad_len

    # Encrypt
    encrypted = sm4_ecb_encrypt(plaintext, key_bytes)

    # CRC32 of plaintext (standard zlib CRC32)
    crc32_val = zlib.crc32(plaintext) & 0xFFFFFFFF

    # Obfuscate key
    obfuscated_key = xor_obfuscate_key(key_bytes)

    # Format as C arrays
    payload_hex = ',\n    '.join(
        ', '.join(f'0x{b:02X}' for b in encrypted[i:i+16])
        for i in range(0, len(encrypted), 16)
    )

    c_source = f'''/*
 * AUTO-GENERATED by tools/pack_dex.py — DO NOT EDIT
 *
 * Encrypted DEX payload for VMP DEX Packer.
 * SM4-ECB encrypted, CRC32 verified at runtime.
 *
 * Plaintext size: {len(plaintext)} bytes (padded from original)
 * Encrypted size: {len(encrypted)} bytes
 * CRC32:          0x{crc32_val:08X}
 * Generated at:   {__import__('datetime').datetime.now().isoformat()}
 */

#include "dex-packer.h"

/* SM4 key (XOR-obfuscated with compile-time seed) */
const uint8_t g_vmp_sm4_key[16] = {{
    {', '.join(f'0x{b:02X}' for b in obfuscated_key)}
}};

/* CRC32 of decrypted payload */
const uint32_t g_vmp_payload_crc32 = 0x{crc32_val:08X}U;

/* Encrypted DEX payload */
const uint8_t g_vmp_payload[] = {{
    {payload_hex}
}};

const uint32_t g_vmp_payload_size = {len(encrypted)}U;
'''

    with open(output_path, 'w') as f:
        f.write(c_source)

    print(f"✅ Generated {output_path}")
    print(f"   Original: {len(plaintext) - pad_len} bytes + {pad_len} padding")
    print(f"   Encrypted: {len(encrypted)} bytes")
    print(f"   CRC32: 0x{crc32_val:08X}")
    return len(encrypted)


def main():
    parser = argparse.ArgumentParser(description='VMP DEX Packer')
    parser.add_argument('--apk', required=True, help='Path to APK')
    parser.add_argument('--out', required=True, help='Output C source file')
    parser.add_argument('--key', help='SM4 key (hex, 32 chars). Random if omitted.')
    args = parser.parse_args()

    # Read APK and extract classes.dex
    if not os.path.exists(args.apk):
        print(f"❌ APK not found: {args.apk}", file=sys.stderr)
        sys.exit(1)

    with zipfile.ZipFile(args.apk, 'r') as zf:
        try:
            dex_data = zf.read('classes.dex')
        except KeyError:
            # Try multi-dex
            for name in zf.namelist():
                if name.endswith('.dex'):
                    dex_data = zf.read(name)
                    print(f"📦 Found {name} ({len(dex_data)} bytes)")
                    break
            else:
                print("❌ No DEX file found in APK", file=sys.stderr)
                sys.exit(1)

    print(f"📦 DEX extracted: {len(dex_data)} bytes")

    # Generate or parse SM4 key
    if args.key:
        key_bytes = bytes.fromhex(args.key)
        if len(key_bytes) != 16:
            print(f"❌ Key must be 16 bytes (32 hex chars), got {len(key_bytes)}", file=sys.stderr)
            sys.exit(1)
        print(f"🔑 Using provided SM4 key")
    else:
        key_bytes = os.urandom(16)
        print(f"🔑 Generated random SM4 key: {key_bytes.hex()}")

    # Generate C source
    size = generate_c_source(dex_data, key_bytes, args.out)

    print(f"\n📋 Next steps:")
    print(f"   1. Rebuild native library (./gradlew assembleRelease)")
    print(f"   2. The encrypted DEX ({size} bytes) is now in liblianyu_security.so")
    print(f"   3. At runtime, LianYuShellApplication calls NativeBridge.nativeLoadPayload()")
    print(f"   4. dex_packer_decrypt() → SM4 decrypt → CRC32 verify → InMemoryDexClassLoader")


if __name__ == '__main__':
    main()
