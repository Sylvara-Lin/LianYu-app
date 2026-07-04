#!/usr/bin/env python3
"""encrypt_assets.py — XOR-encrypt sensitive asset files for runtime decryption.

Usage:
    python3 tools/encrypt_assets.py <assets_dir> [--key <32-hex>]
    
Output:
    Original file → file.enc (XOR-encrypted), original deleted
"""
import os, sys, argparse

def xor_crypt(data: bytes, key: bytes) -> bytes:
    return bytes(data[i] ^ key[i % len(key)] for i in range(len(data)))

def main():
    parser = argparse.ArgumentParser(description='XOR-encrypt sensitive assets')
    parser.add_argument('assets_dir', help='Path to assets directory')
    parser.add_argument('--key', help='32-char hex key (random if not provided)')
    args = parser.parse_args()
    
    if args.key:
        key = bytes.fromhex(args.key)
    else:
        key = os.urandom(32)
        print(f"Generated key: {key.hex()}")
    
    # Files to encrypt
    targets = [
        'content_filter_keywords.json',
    ]
    
    for fn in targets:
        src = os.path.join(args.assets_dir, fn)
        if not os.path.exists(src):
            print(f"⚠ {fn} not found, skipping")
            continue
        
        with open(src, 'rb') as f:
            data = f.read()
        
        encrypted = xor_crypt(data, key)
        dst = src + '.enc'
        
        with open(dst, 'wb') as f:
            f.write(encrypted)
        
        os.remove(src)
        print(f"✅ {fn}: {len(data):,} bytes → {len(encrypted):,} bytes (.enc)")

if __name__ == '__main__':
    main()
