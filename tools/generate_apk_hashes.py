#!/usr/bin/env python3
"""
APK Integrity Hash Generator for LianYu Security Module

Usage:
    python3 generate_apk_hashes.py <path/to/release.apk> <path/to/liblianyu_security.so>

This script computes SM3 hashes of:
    1. DEX files inside the APK
    2. The .text section of liblianyu_security.so
    3. First 64KB of the APK (contains resources.arsc header)

Then generates C++ code to embed these hashes as XOR-obfuscated constants
in native-bridge.cpp for runtime integrity verification.

IMPORTANT: Run this AFTER every release build and BEFORE distribution.
"""

import sys
import struct
import hashlib
import os
import subprocess

try:
    from gmssl import sm3
except ImportError:
    print("Installing gmssl for SM3 support...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "gmssl"])
    from gmssl import sm3


def sm3_hash(data: bytes) -> bytes:
    """Compute SM3 hash of data."""
    h = sm3.sm3_hash(list(data))
    return bytes.fromhex(h)


def extract_dex_data(apk_path: str) -> bytes:
    """Extract concatenated DEX files from APK (ZIP format)."""
    with open(apk_path, 'rb') as f:
        apk_data = f.read()

    dex_parts = []
    pos = 0
    while pos + 30 <= len(apk_data):
        # Check ZIP local file header signature
        if apk_data[pos:pos+4] != b'PK\x03\x04':
            break

        name_len = struct.unpack('<H', apk_data[pos+26:pos+28])[0]
        extra_len = struct.unpack('<H', apk_data[pos+28:pos+30])[0]
        comp_size = struct.unpack('<I', apk_data[pos+18:pos+22])[0]

        name = apk_data[pos+30:pos+30+name_len]
        data_start = pos + 30 + name_len + extra_len

        # Check if this is a classes*.dex entry
        is_dex = False
        if name.startswith(b'classes') and name.endswith(b'.dex'):
            is_dex = True
        elif name.startswith(b'classes') and len(name) > 11:
            # classes2.dex, classes3.dex, etc.
            base = name[7:]
            if base[0:1].isdigit() and base[1:] == b'.dex':
                is_dex = True

        if is_dex and data_start + comp_size <= len(apk_data):
            dex_parts.append(apk_data[data_start:data_start + comp_size])

        pos = data_start + comp_size

    if dex_parts:
        return b''.join(dex_parts)
    else:
        # Fallback: return whole APK if no DEX found
        return apk_data


def zero_elf_notes_in_image(image: bytearray) -> None:
    """Zero ELF64 PT_NOTE segments in-place to neutralize build-id self-reference."""
    if len(image) < 64 or image[:4] != b"\x7fELF":
        return
    if image[4] != 2 or image[5] != 1:
        return
    try:
        e_phoff = struct.unpack_from("<Q", image, 32)[0]
        e_phentsize = struct.unpack_from("<H", image, 54)[0]
        e_phnum = struct.unpack_from("<H", image, 56)[0]
    except struct.error:
        return
    if not e_phoff or e_phentsize < 56 or not e_phnum:
        return
    if e_phoff + e_phentsize * e_phnum > len(image):
        return
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        try:
            p_type = struct.unpack_from("<I", image, off)[0]
            p_offset = struct.unpack_from("<Q", image, off + 8)[0]
            p_filesz = struct.unpack_from("<Q", image, off + 32)[0]
        except struct.error:
            continue
        if p_type == 4 and p_offset < len(image):
            end = min(len(image), p_offset + p_filesz)
            image[p_offset:end] = b"\x00" * (end - p_offset)


def _find_digests_table_offset(text_bytes: bytes) -> int | None:
    """Find g_apk_digests_obs offset within .text section by matching obfuscated prefix.

    Reads the current g_apk_digests_obs from native-bridge.cpp and searches for
    the first 8 bytes in the .text section.
    """
    import re as _re

    nb_path = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "core", "security", "src", "main", "cpp", "native-bridge.cpp",
    )
    try:
        with open(nb_path, "r") as f_nb:
            nb = f_nb.read()
    except Exception:
        return None

    m = _re.search(
        r"static const uint8_t g_apk_digests_obs\[96\]\s*=\s*\{[^}]*?"
        r"0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),"
        r"\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2})",
        nb,
        _re.DOTALL,
    )
    if not m:
        return None

    prefix = bytes(int(m.group(i), 16) for i in range(1, 9))
    offset = text_bytes.find(prefix)
    if offset >= 0:
        return offset

    prefix4 = prefix[:4]
    offset = text_bytes.find(prefix4)
    return offset if offset >= 0 else None


def extract_so_text(so_path: str) -> bytes:
    """Extract .text section from ELF .so file, with fallback to executable segment via PHDR.

    Returns the raw .text bytes directly (NOT modified). Zeroing is done by the caller.
    """
    with open(so_path, "rb") as f:
        so_bytes = f.read()

    if len(so_bytes) < 64 or so_bytes[:4] != b"\x7fELF":
        return so_bytes

    if so_bytes[4] != 2 or so_bytes[5] != 1:
        print("WARNING: SO is not ELF64 LE — hashing entire file")
        return so_bytes

    endian = "<"
    e_phoff = struct.unpack_from(endian + "Q", so_bytes, 32)[0]
    e_phentsize = struct.unpack_from(endian + "H", so_bytes, 54)[0]
    e_phnum = struct.unpack_from(endian + "H", so_bytes, 56)[0]
    e_shoff = struct.unpack_from(endian + "Q", so_bytes, 40)[0]
    e_shentsize = struct.unpack_from(endian + "H", so_bytes, 58)[0]
    e_shnum = struct.unpack_from(endian + "H", so_bytes, 60)[0]

    if e_shoff > 0 and e_shnum > 0 and e_shentsize > 0:
        e_shstrndx = struct.unpack_from(endian + "H", so_bytes, 62)[0]
        if e_shstrndx < e_shnum:
            shstr_off = e_shoff + e_shstrndx * e_shentsize
            sh_name_strtab = struct.unpack_from(endian + "I", so_bytes, shstr_off)[0]
            sh_name_size = struct.unpack_from(endian + "Q", so_bytes, shstr_off + 24)[0]
            sh_name_offset = struct.unpack_from(endian + "Q", so_bytes, shstr_off + 32)[0]
            for i in range(e_shnum):
                sh_off = e_shoff + i * e_shentsize
                sh_name = struct.unpack_from(endian + "I", so_bytes, sh_off)[0]
                sh_type = struct.unpack_from(endian + "I", so_bytes, sh_off + 4)[0]
                sh_size = struct.unpack_from(endian + "Q", so_bytes, sh_off + 32)[0]
                sh_offset = struct.unpack_from(endian + "Q", so_bytes, sh_off + 24)[0]
                if sh_name < sh_name_size and sh_name_offset + sh_name < len(so_bytes):
                    try:
                        name_end = so_bytes.index(b"\x00", sh_name_offset + sh_name)
                        name = so_bytes[sh_name_offset + sh_name:name_end].decode("ascii", errors="ignore")
                    except (ValueError, UnicodeDecodeError):
                        continue
                    if name == ".text" and sh_type == 1:
                        print(f"  .text section: offset=0x{sh_offset:x}, size={sh_size}")
                        return so_bytes[sh_offset:sh_offset + sh_size]

    if e_phoff > 0 and e_phnum > 0 and e_phentsize > 0:
        for i in range(e_phnum):
            ph_off = e_phoff + i * e_phentsize
            p_type = struct.unpack_from(endian + "I", so_bytes, ph_off)[0]
            if p_type != 1:
                continue
            p_flags = struct.unpack_from(endian + "I", so_bytes, ph_off + 4)[0]
            p_offset = struct.unpack_from(endian + "Q", so_bytes, ph_off + 8)[0]
            p_filesz = struct.unpack_from(endian + "Q", so_bytes, ph_off + 32)[0]
            if (p_flags & 1) and p_filesz > 0:
                print(f"  .text via PHDR: offset=0x{p_offset:x}, filesz={p_filesz}")
                return so_bytes[p_offset:p_offset + p_filesz]

    return so_bytes


def obfuscate_hash(hash_bytes: bytes, offset: int = 0) -> list:
    """XOR-obfuscate hash bytes with per-byte key derived from offset."""
    result = []
    for i, b in enumerate(hash_bytes):
        key = 0xC3 ^ ((offset + i) * 0x9D & 0xFF)
        result.append(b ^ key)
    return result


def generate_cpp_code(dex_hash: bytes, so_hash: bytes, arsc_hash: bytes) -> str:
    """Generate the C++ array definition for native-bridge.cpp."""
    all_hashes = dex_hash + so_hash + arsc_hash
    obfuscated = obfuscate_hash(all_hashes)

    lines = [
        "/* AUTO-GENERATED by tools/generate_apk_hashes.py */",
        "/* Run this script after every release build to update integrity hashes. */",
        "",
        "static const uint8_t g_apk_digests_obs[96] = {",
    ]

    for i in range(0, len(obfuscated), 16):
        row = obfuscated[i:i+16]
        hex_vals = ', '.join(f'0x{b:02X}' for b in row)
        if i + 16 < len(obfuscated):
            lines.append(f"    {hex_vals},")
        else:
            lines.append(f"    {hex_vals}")

    lines.append("};")
    lines.append("")

    return '\n'.join(lines)


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 generate_apk_hashes.py <release.apk> [liblianyu_security.so]")
        print("")
        print("If .so path is omitted, DEX-only hashes are generated.")
        sys.exit(1)

    apk_path = sys.argv[1]
    so_path = sys.argv[2] if len(sys.argv) > 2 else None

    if not os.path.exists(apk_path):
        print(f"ERROR: APK not found: {apk_path}")
        sys.exit(1)

    print(f"[*] Processing APK: {apk_path}")

    # 1. DEX hash
    dex_data = extract_dex_data(apk_path)
    dex_hash = sm3_hash(dex_data)
    print(f"[+] DEX SM3: {dex_hash.hex()}")

    # 2. SO .text hash
    if so_path and os.path.exists(so_path):
        so_data = extract_so_text(so_path)
        so_copy = bytearray(so_data)

        zero_elf_notes_in_image(so_copy)

        obs_offset = _find_digests_table_offset(bytes(so_copy))
        if obs_offset is not None:
            for i in range(obs_offset, min(obs_offset + 96, len(so_copy))):
                so_copy[i] = 0
            print(f"  SO hash: zeroed g_apk_digests_obs at offset=0x{obs_offset:x}")
        else:
            print("  WARNING: could not locate g_apk_digests_obs in SO — hashing raw .text")

        so_hash = sm3_hash(bytes(so_copy))
        print(f"[+] SO .text SM3: {so_hash.hex()}")
    else:
        so_hash = bytes(32)  # placeholder
        print("[!] SO path not provided, using placeholder")

    # 3. ARSC hash (first 64KB of APK)
    with open(apk_path, 'rb') as f:
        arsc_data = f.read(65536)
    arsc_hash = sm3_hash(arsc_data)
    print(f"[+] ARSC SM3: {arsc_hash.hex()}")

    # Generate output
    cpp_code = generate_cpp_code(dex_hash, so_hash, arsc_hash)

    print("\n" + "="*60)
    print("Generated C++ code (replace g_apk_digests_obs in native-bridge.cpp):")
    print("="*60)
    print(cpp_code)

    # Also write to a file
    output_file = os.path.join(os.path.dirname(apk_path), "apk_hashes.inc")
    with open(output_file, 'w') as f:
        f.write(cpp_code)
    print(f"\n[+] Written to: {output_file}")


if __name__ == '__main__':
    main()
