#!/usr/bin/env python3
"""
gen_class_map.py — Generate encrypted class_map.bin for DynamicClassLoader.

Reads an Android APK, extracts class names from all classes.dex files,
assigns each class to one of N fragments, encrypts each class individually
(via SM4/KMS), and outputs the encrypted binary mapping index.

Usage:
    python gen_class_map.py --apk app-release.apk --key <hex-key> \
        --output assets/lianyu_shell/class_map.bin

Binary format of class_map.bin (before outer encryption):
    [classCount:4 (LE)]
    For each class:
        nameLen:2 (LE)
        name (UTF-8, nameLen bytes)
        fragmentIndex:1 (0-3)
        offsetInFragment:4 (LE)
        encryptedLength:4 (LE)
        iv:16 (per-class random IV)

The entire mapping body is then encrypted with KMS/SM4 and prefixed
with a 16-byte metadata header (salt[8] || counter[8]).
"""

import argparse
import hashlib
import os
import random
import struct
import sys
import zipfile
from io import BytesIO

# ──────────────────────────────────────────────────────────────────────────────
# DEX File Format Constants
# ──────────────────────────────────────────────────────────────────────────────

DEX_MAGIC = b"dex\n"
DEX_HEADER_SIZE = 0x70  # 112 bytes

# Offsets within DEX header
ENDIAN_CONSTANT = 0x12345678
REVERSE_ENDIAN_CONSTANT = 0x78563412


def read_uleb128(data: bytes, offset: int) -> tuple:
    """Read an unsigned LEB128 value from bytes starting at offset.
    Returns (value, new_offset)."""
    result = 0
    shift = 0
    while offset < len(data):
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if (byte & 0x80) == 0:
            break
        shift += 7
    return result, offset


def read_string(data: bytes, string_ids_off: int, string_idx: int) -> str:
    """Read a UTF-8 string from DEX string table given its index."""
    # Each string_id is 4 bytes: offset into data section
    sid_off = string_ids_off + string_idx * 4
    if sid_off + 4 > len(data):
        return ""
    str_data_off = struct.unpack_from("<I", data, sid_off)[0]

    # string_data: uleb128_size + UTF-8 bytes + null terminator
    utf16_len, cursor = read_uleb128(data, str_data_off)
    # The actual UTF-8 bytes follow (the MUTF-8 encoded string)
    # Find the null terminator
    end = data.find(b"\x00", cursor)
    if end == -1:
        end = len(data)
    return data[cursor:end].decode("utf-8", errors="replace")


def extract_class_names_from_dex(dex_data: bytes) -> list:
    """Parse a DEX file and return a list of fully-qualified class names."""
    if len(dex_data) < DEX_HEADER_SIZE:
        return []
    if dex_data[:4] != DEX_MAGIC[:4]:
        return []

    # Determine endianness from endian_tag at offset 40
    endian_tag = struct.unpack_from("<I", dex_data, 40)[0]
    if endian_tag == ENDIAN_CONSTANT:
        endian = "<"
    elif endian_tag == REVERSE_ENDIAN_CONSTANT:
        endian = ">"
    else:
        return []

    # Read counts and offsets from header
    string_ids_size = struct.unpack_from(endian + "I", dex_data, 56)[0]
    string_ids_off = struct.unpack_from(endian + "I", dex_data, 60)[0]
    type_ids_size = struct.unpack_from(endian + "I", dex_data, 64)[0]
    type_ids_off = struct.unpack_from(endian + "I", dex_data, 68)[0]
    class_defs_size = struct.unpack_from(endian + "I", dex_data, 96)[0]
    class_defs_off = struct.unpack_from(endian + "I", dex_data, 100)[0]

    class_names = []

    for i in range(class_defs_size):
        # Each class_def_item is 32 bytes
        cd_off = class_defs_off + i * 32
        if cd_off + 4 > len(dex_data):
            break
        # class_idx is the first 4 bytes of class_def_item
        type_idx = struct.unpack_from(endian + "I", dex_data, cd_off)[0]

        if type_idx >= type_ids_size:
            continue

        # type_id_item is 4 bytes: descriptor_idx (index into string_ids)
        tid_off = type_ids_off + type_idx * 4
        if tid_off + 4 > len(dex_data):
            continue
        descriptor_idx = struct.unpack_from(endian + "I", dex_data, tid_off)[0]

        if descriptor_idx >= string_ids_size:
            continue

        # Read the type descriptor string (e.g., "Lcom/example/Foo;")
        descriptor = read_string(dex_data, string_ids_off, descriptor_idx)

        # Convert descriptor to fully-qualified class name
        if descriptor.startswith("L") and descriptor.endswith(";"):
            class_name = descriptor[1:-1].replace("/", ".")
            class_names.append(class_name)

    return class_names


# ──────────────────────────────────────────────────────────────────────────────
# Encryption Stub (replace with SM4/KMS integration)
# ──────────────────────────────────────────────────────────────────────────────

def encrypt_class_data(class_bytes: bytes, key: bytes) -> tuple:
    """
    Encrypt a single class's DEX data.

    Returns (encrypted_bytes, iv: bytes(16))

    TODO: Replace this stub with SM4-ECB or SM4-CBC encryption.
    The current stub uses a simple XOR obfuscation for development only.
    For production:
      - Use SM4-CBC with a random 16-byte IV per class
      - Use the KMS provider's key derivation for the SM4 key
      - Ensure 16-byte block alignment (PKCS7 padding)

    Args:
        class_bytes: Raw DEX bytes for this class
        key: 16-byte encryption key (from CLI --key argument, hex-encoded)

    Returns:
        (encrypted_data, iv)
    """
    # ── STUB: XOR obfuscation (NOT SECURE — replace for production) ──
    # TODO: Implement SM4-CBC encryption:
    #   from gmssl.sm4 import CryptSM4, SM4_ENCRYPT, SM4_DECRYPT
    #   crypt_sm4 = CryptSM4()
    #   crypt_sm4.set_key(key, SM4_ENCRYPT)
    #   iv = os.urandom(16)
    #   padded = pkcs7_pad(class_bytes, 16)
    #   encrypted = crypt_sm4.crypt_cbc(iv, padded)

    iv = os.urandom(16)
    # Simple repeating-key XOR with the key (development only)
    encrypted = bytearray(len(class_bytes))
    for i, b in enumerate(class_bytes):
        encrypted[i] = b ^ key[i % len(key)] ^ iv[i % 16]
    return bytes(encrypted), iv


def encrypt_mapping_body(body: bytes, key: bytes) -> bytes:
    """
    Encrypt the entire mapping index body with metadata header.

    The outer format is:
        [metadata:16] [encrypted_body:N]

    Metadata: salt[8] || counter[8]

    TODO: Replace with SM4/KMS encryption matching decryptWithMetadata.
    For production:
      - Use SM4-ECB or SM4-CBC with metadata-derived key
      - The metadata (salt+counter) is generated randomly
      - The encrypted body should be 16-byte aligned

    Args:
        body: Plaintext mapping body to encrypt
        key: 16-byte encryption key

    Returns:
        Full encrypted mapping: [metadata:16] [encrypted_body:N]
    """
    # ── STUB: XOR obfuscation (NOT SECURE — replace for production) ──
    # TODO: Implement KMS-compatible encryption:
    #   1. Generate metadata: salt[8] = os.urandom(8), counter[8] = struct.pack("<Q", 1)
    #   2. Derive SM4 key from master key + metadata (via KDF or HKDF)
    #   3. Encrypt body with SM4-CBC using derived key and metadata as IV
    #   4. Return metadata + ciphertext

    salt = os.urandom(8)
    counter = struct.pack("<Q", 1)
    metadata = salt + counter  # 16 bytes

    # Simple repeating-key XOR (development only — NOT SECURE)
    encrypted = bytearray(len(body))
    for i, b in enumerate(body):
        encrypted[i] = b ^ key[i % len(key)] ^ metadata[i % 16]

    return metadata + bytes(encrypted)


# ──────────────────────────────────────────────────────────────────────────────
# Fragment Assignment
# ──────────────────────────────────────────────────────────────────────────────

FRAGMENT_COUNT = 4


def assign_fragment(class_name: str) -> int:
    """
    Assign a class to a fragment (0-3) based on its package.

    Fragment 0: shell/bootstrap classes (com.lianyu.ai.security, com.lianyu.ai.shell)
    Fragment 1: network + crypto (com.lianyu.ai.network, com.lianyu.ai.security.kms)
    Fragment 2: UI/business (com.lianyu.ai.ui, com.lianyu.ai.feature.*, com.lianyu.ai.companion)
    Fragment 3: AI dialog core (com.lianyu.ai.chat, com.lianyu.ai.ai, com.lianyu.ai.memory)

    Returns fragment index (0-3).
    """
    # Fragment 0: shell/security bootstrap
    if ".security." in class_name or ".shell" in class_name:
        return 0
    # Fragment 1: network + crypto
    if ".network." in class_name or ".crypto" in class_name or ".kms" in class_name:
        return 1
    # Fragment 3: AI dialog
    if ".chat." in class_name or ".ai." in class_name or ".memory." in class_name or \
       ".localmodel." in class_name or ".llm." in class_name:
        return 3
    # Fragment 2: everything else (UI, features, common)
    return 2


# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Generate encrypted class_map.bin for DynamicClassLoader"
    )
    parser.add_argument(
        "--apk", required=True,
        help="Path to the APK file (e.g., app-release.apk)"
    )
    parser.add_argument(
        "--key", required=True,
        help="Hex-encoded 16-byte encryption key (32 hex chars)"
    )
    parser.add_argument(
        "--output", default="assets/lianyu_shell/class_map.bin",
        help="Output path for class_map.bin (default: assets/lianyu_shell/class_map.bin)"
    )
    parser.add_argument(
        "--fragment-dir", default=None,
        help="Output directory for encrypted fragment payloads (default: same dir as --output)"
    )
    parser.add_argument(
        "--dev", action="store_true",
        help="Use dev-mode key (bypasses --key, uses built-in dev key)"
    )
    args = parser.parse_args()

    # ── Key setup ──
    if args.dev:
        # Dev AES-256 key matching KmsProvider.DEV_AES_KEY
        key = bytes([
            0x4f, 0xf8, 0xfd, 0xb2, 0xf6, 0xe7, 0x2b, 0xa0,
            0x3a, 0x21, 0xa4, 0x64, 0x58, 0x70, 0x64, 0x80,
            0xd5, 0x1f, 0xdb, 0xbc, 0x32, 0xf9, 0x6e, 0x39,
            0x8f, 0xf9, 0xb4, 0x33, 0x06, 0x16, 0x19, 0xe6,
        ])
    else:
        key_hex = args.key.replace(" ", "").replace("0x", "").replace("0X", "")
        if len(key_hex) != 64:
            print(f"ERROR: --key must be 64 hex characters (32 bytes), got {len(key_hex)}", file=sys.stderr)
            sys.exit(1)
        key = bytes.fromhex(key_hex)

    # ── Parse APK and extract class names ──
    if not os.path.isfile(args.apk):
        print(f"ERROR: APK file not found: {args.apk}", file=sys.stderr)
        sys.exit(1)

    print(f"Reading APK: {args.apk}")
    all_class_names = []
    dex_files = {}

    with zipfile.ZipFile(args.apk, "r") as zf:
        # Find all classes.dex files (classes.dex, classes2.dex, ...)
        for name in sorted(zf.namelist()):
            if name.startswith("classes") and name.endswith(".dex"):
                dex_data = zf.read(name)
                dex_files[name] = dex_data
                class_names = extract_class_names_from_dex(dex_data)
                print(f"  {name}: {len(class_names)} classes")
                all_class_names.extend(class_names)

    print(f"\nTotal classes extracted: {len(all_class_names)}")

    # ── Assign classes to fragments ──
    fragment_classes = {i: [] for i in range(FRAGMENT_COUNT)}
    for name in sorted(set(all_class_names)):
        frag = assign_fragment(name)
        fragment_classes[frag].append(name)

    print("\nFragment distribution:")
    for frag_idx in range(FRAGMENT_COUNT):
        print(f"  Fragment {frag_idx}: {len(fragment_classes[frag_idx])} classes")

    # ── Encrypt each class and build fragment payloads ──
    # For the stub, we use the original DEX data to extract per-class data.
    # In production, each class's DEX data would be extracted from the DEX file
    # using the class_data_item encoding.
    #
    # STUB: We use class name hash + fragment assignment to derive deterministic
    # per-class data. Real implementation extracts actual DEX class_data_items.

    output_dir = args.fragment_dir or os.path.dirname(args.output) or "."
    mapping_entries = []  # (name, fragIndex, offset, length, iv)
    fragment_payloads = {i: bytearray() for i in range(FRAGMENT_COUNT)}

    print("\nEncrypting classes and building fragments...")

    for frag_idx in range(FRAGMENT_COUNT):
        for class_name in sorted(fragment_classes[frag_idx]):
            # TODO: Extract actual DEX class_data for this class from the DEX file.
            # Currently uses a stub: class name bytes as placeholder data.
            # Real implementation:
            #   1. Find class_def for this class in the DEX
            #   2. Read class_data_item (fields, methods) from data section
            #   3. Serialize just the class structure
            class_data_stub = class_name.encode("utf-8")

            # Encrypt the class data with a per-class random IV
            encrypted_class, iv = encrypt_class_data(class_data_stub, key[:16])

            # Record mapping entry
            offset = len(fragment_payloads[frag_idx])
            mapping_entries.append((class_name, frag_idx, offset, len(encrypted_class), iv))

            # Append encrypted class data to fragment payload
            fragment_payloads[frag_idx].extend(encrypted_class)

    # ── Build mapping index body (binary format) ──
    # [classCount:4 (LE)] [for each: nameLen:2, name, fragIndex:1, offset:4, length:4, iv:16]
    body = bytearray()
    body.extend(struct.pack("<I", len(mapping_entries)))

    for name, frag_idx, offset, length, iv in mapping_entries:
        name_bytes = name.encode("utf-8")
        body.extend(struct.pack("<H", len(name_bytes)))
        body.extend(name_bytes)
        body.extend(struct.pack("<B", frag_idx))
        body.extend(struct.pack("<I", offset))
        body.extend(struct.pack("<I", length))
        body.extend(iv)

    print(f"Mapping body size: {len(body)} bytes, {len(mapping_entries)} entries")

    # ── Encrypt mapping body with outer encryption layer ──
    encrypted_map = encrypt_mapping_body(bytes(body), key[:32])
    print(f"Encrypted class_map.bin size: {len(encrypted_map)} bytes")

    # ── Write class_map.bin ──
    os.makedirs(os.path.dirname(args.output) or ".", exist_ok=True)
    with open(args.output, "wb") as f:
        f.write(encrypted_map)
    print(f"\nWritten: {args.output}")

    # ── Write fragment payloads ──
    for frag_idx in range(FRAGMENT_COUNT):
        if fragment_payloads[frag_idx]:
            frag_path = os.path.join(output_dir, f"shell_payload_{frag_idx}.bin")
            with open(frag_path, "wb") as f:
                f.write(fragment_payloads[frag_idx])
            print(f"Written: {frag_path} ({len(fragment_payloads[frag_idx])} bytes)")

    # ── Summary ──
    print(f"\n{'='*60}")
    print(f"Generation complete!")
    print(f"  Total classes mapped: {len(mapping_entries)}")
    print(f"  Mapping file: {args.output}")
    print(f"  Fragment payloads: {output_dir}/shell_payload_*.bin")
    print()
    print(f"WARNING: Encryption stubs are NOT secure. Replace with SM4/KMS")
    print(f"  for production use. See TODO comments in encrypt_class_data()")
    print(f"  and encrypt_mapping_body().")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
