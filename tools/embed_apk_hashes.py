#!/usr/bin/env python3
"""
embed_apk_hashes.py — Compute SM3 hashes of APK components and embed them
into native-bridge.cpp's g_apk_digests_obs XOR-obfuscated table.

Usage:
    python3 tools/embed_apk_hashes.py app/build/outputs/apk/release/app-release.apk

This computes 3 × 32-byte SM3 digests:
    [0..31]   — SM3 of entire APK file (DEX integrity baseline)
    [32..63]  — SM3 of liblianyu_security.so .text section (SO integrity)
    [64..95]  — SM3 of first 64KB of APK (resources.arsc integrity)

Then XOR-obfuscates each byte with key = 0xC3 ^ (i * 0x9D) and patches
the g_apk_digests_obs[] array in native-bridge.cpp.

Must be re-run after every release build that changes DEX, native code,
or resources.
"""

import hashlib
import os
import struct
import sys
import zipfile


NATIVE_BRIDGE_CPP = "core/security/src/main/cpp/native-bridge.cpp"
OBS_KEY_BASE = 0xC3
OBS_KEY_MUL = 0x9D
SM3_DIGEST_SIZE = 32
TABLE_SIZE = 96  # 3 × 32


def sm3_hash(data: bytes) -> bytes:
    """Compute SM3 digest (GB/T 32905-2016) via OpenSSL."""
    return hashlib.new("sm3", data).digest()  # 32 bytes


def obfuscate(plain: bytes) -> bytes:
    """XOR-obfuscate with per-byte key: key[i] = 0xC3 ^ (i * 0x9D)."""
    return bytes(b ^ (OBS_KEY_BASE ^ ((i % 256) * OBS_KEY_MUL) & 0xFF)
                 for i, b in enumerate(plain))


def hash_dex(apk_path: str) -> bytes:
    """SM3 of concatenated compressed classes*.dex local-entry payloads.
    Matches runtime check_dex_integrity(), which scans APK local file headers
    and hashes the bytes immediately after each classes*.dex header.
    """
    apk_data = open(apk_path, "rb").read()
    pos = 0
    chunks: list[bytes] = []
    while pos + 30 <= len(apk_data):
        if apk_data[pos:pos + 4] != b"PK\x03\x04":
            break
        comp_size = struct.unpack_from("<I", apk_data, pos + 18)[0]
        name_len = struct.unpack_from("<H", apk_data, pos + 26)[0]
        extra_len = struct.unpack_from("<H", apk_data, pos + 28)[0]
        name_start = pos + 30
        data_start = name_start + name_len + extra_len
        data_end = data_start + comp_size
        if data_end > len(apk_data):
            break
        name = apk_data[name_start:name_start + name_len]
        if name.startswith(b"classes") and name.endswith(b".dex"):
            chunks.append(apk_data[data_start:data_end])
        pos = data_end

    if chunks:
        return sm3_hash(b"".join(chunks))

    # Runtime fallback hashes the full APK when no DEX local entries are found.
    return sm3_hash(apk_data)
def hash_so_text(apk_path: str) -> bytes:
    """
    Extract liblianyu_security.so for arm64-v8a from APK, then SM3-hash
    its .text section — matches runtime check_so_integrity() which hashes
    the mmap'd r-xp code segment with g_apk_digests_obs zeroed.
    """
    so_bytes = _extract_so_from_apk(apk_path)
    if so_bytes is None:
        print("WARNING: Could not extract .so from APK — SO digest will be zeros")
        return b"\x00" * SM3_DIGEST_SIZE

    text_bytes = _parse_elf_text_section(so_bytes)
    if text_bytes is None:
        print("WARNING: Could not parse .text section — hashing entire .so")
        return sm3_hash(so_bytes)

    text_copy = bytearray(text_bytes)

    # Zero ELF note/build-id data so the self-hash remains stable after embedding.
    zero_elf_notes_in_image(text_copy)

    # Zero out g_apk_digests_obs in a copy to match runtime behaviour
    obs_offset = _find_digests_table_offset(text_bytes, so_bytes)
    if obs_offset is not None:
        for i in range(obs_offset, min(obs_offset + 96, len(text_copy))):
            text_copy[i] = 0
        print(f"  SO hash: zeroed g_apk_digests_obs at .text+0x{obs_offset:x}")
    else:
        print("  WARNING: could not locate g_apk_digests_obs — hashing raw .text")

    return sm3_hash(bytes(text_copy))


def zero_elf_notes_in_image(image: bytearray) -> None:
    """Zero ELF64 PT_NOTE segments in-place to neutralize build-id self-reference."""
    if len(image) < 64 or image[:4] != b"\x7fELF":
        return
    if image[4] != 2 or image[5] != 1:  # ELFCLASS64 + little-endian
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
        if p_type == 4 and p_offset < len(image):  # PT_NOTE
            end = min(len(image), p_offset + p_filesz)
            image[p_offset:end] = b"\x00" * (end - p_offset)


def _find_digests_table_offset(text_bytes: bytes, so_bytes: bytes) -> int | None:
    """Find g_apk_digests_obs offset within .text section.

    Strategy: read the current obfuscated prefix from native-bridge.cpp,
    then search for it in the .text bytes.
    """
    import re as _re, os as _os

    nb_path = _os.path.join(
        _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__))),
        NATIVE_BRIDGE_CPP,
    )
    try:
        with open(nb_path, "r") as f_nb:
            nb = f_nb.read()
    except Exception:
        return None

    # Extract first 8 bytes from the array initializer
    m = _re.search(
        r"static const uint8_t g_apk_digests_obs\[96\]\s*=\s*\{[^}]*?0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2}),\s*0x([0-9A-Fa-f]{2})",
        nb,
        _re.DOTALL,
    )
    if not m:
        return None

    prefix = bytes(int(m.group(i), 16) for i in range(1, 9))
    offset = text_bytes.find(prefix)
    if offset >= 0:
        return offset

    # Try variable-length prefix (4 bytes)
    prefix4 = prefix[:4]
    offset = text_bytes.find(prefix4)
    if offset >= 0:
        return offset

    return None


def hash_arsc(apk_path: str) -> bytes:
    """SM3 of first 64KB of APK file.
    Matches runtime check_resources_integrity().
    """
    with open(apk_path, "rb") as f:
        return sm3_hash(f.read(65536))
def _extract_so_from_apk(apk_path: str) -> bytes | None:
    """Extract liblianyu_security.so for arm64-v8a from APK zip."""
    SO_PATH = "lib/arm64-v8a/liblianyu_security.so"
    try:
        with zipfile.ZipFile(apk_path, "r") as z:
            if SO_PATH in z.namelist():
                return z.read(SO_PATH)
            # Try alternate ABIs
            for abi in ("armeabi-v7a", "x86_64", "x86"):
                alt = f"lib/{abi}/liblianyu_security.so"
                if alt in z.namelist():
                    print(f"NOTE: Using {abi} .so (arm64-v8a not found)")
                    return z.read(alt)
            print(f"ERROR: liblianyu_security.so not found in APK")
            return None
    except Exception as e:
        print(f"ERROR reading APK zip: {e}")
        return None


def _parse_elf_text_section(so_bytes: bytes) -> bytes | None:
    """
    Parse ELF64 to find the .text section and return its raw bytes.
    Falls back to readelf if available.
    """
    # Manual ELF64 parsing
    try:
        return _parse_elf64_text_manual(so_bytes)
    except Exception as e:
        print(f"ELF parse error: {e}")
        return None


def _parse_elf64_text_manual(so_bytes: bytes) -> bytes | None:
    """Manual ELF64 parsing to find .text section offset and size.
    Falls back to program headers for stripped binaries."""
    if len(so_bytes) < 64:
        return None

    # ELF header
    e_ident = so_bytes[:16]
    if e_ident[:4] != b"\x7fELF":
        return None
    is_64bit = e_ident[4] == 2  # ELFCLASS64
    is_le = e_ident[5] == 1     # ELFDATA2LSB

    if not (is_64bit and is_le):
        print("WARNING: SO is not ELF64 LE — hashing entire file")
        return None

    endian = "<"  # little-endian

    # ELF64 header
    e_phoff = struct.unpack_from(endian + "Q", so_bytes, 32)[0]   # program header offset
    e_phentsize = struct.unpack_from(endian + "H", so_bytes, 54)[0]  # program header entry size
    e_phnum = struct.unpack_from(endian + "H", so_bytes, 56)[0]    # program header count
    e_shoff = struct.unpack_from(endian + "Q", so_bytes, 40)[0]   # section header offset
    e_shentsize = struct.unpack_from(endian + "H", so_bytes, 58)[0]  # section header entry size
    e_shnum = struct.unpack_from(endian + "H", so_bytes, 60)[0]    # section header count

    # Try section headers first (non-stripped binaries)
    if e_shoff > 0 and e_shnum > 0 and e_shentsize > 0:
        text = _find_text_via_sections(so_bytes, endian, e_shoff, e_shentsize, e_shnum)
        if text is not None:
            return text

    # Fallback: use program headers (works for stripped binaries)
    # Find the executable load segment (PT_LOAD with PF_X)
    if e_phoff > 0 and e_phnum > 0 and e_phentsize > 0:
        for i in range(e_phnum):
            ph_off = e_phoff + i * e_phentsize
            p_type = struct.unpack_from(endian + "I", so_bytes, ph_off)[0]
            if p_type != 1:  # PT_LOAD
                continue
            p_flags = struct.unpack_from(endian + "I", so_bytes, ph_off + 4)[0]
            p_offset = struct.unpack_from(endian + "Q", so_bytes, ph_off + 8)[0]
            p_filesz = struct.unpack_from(endian + "Q", so_bytes, ph_off + 32)[0]
            p_memsz = struct.unpack_from(endian + "Q", so_bytes, ph_off + 40)[0]

            # PF_X (executable) segment — this is the code segment
            if (p_flags & 1) and p_filesz > 0:
                print(f"  .text via PHDR: offset=0x{p_offset:x}, filesz={p_filesz}")
                return so_bytes[p_offset:p_offset + p_filesz]

    return None


def _find_text_via_sections(so_bytes: bytes, endian: str,
                            e_shoff: int, e_shentsize: int, e_shnum: int) -> bytes | None:
    """Find .text section via ELF section headers."""
    e_shstrndx = struct.unpack_from(endian + "H", so_bytes, 62)[0]
    if e_shstrndx >= e_shnum:
        return None

    # Read section name string table
    shstr_off = e_shoff + e_shstrndx * e_shentsize
    sh_name_strtab = struct.unpack_from(endian + "I", so_bytes, shstr_off)[0]
    sh_name_size = struct.unpack_from(endian + "Q", so_bytes, shstr_off + 24)[0]
    sh_name_offset = struct.unpack_from(endian + "Q", so_bytes, shstr_off + 32)[0]

    # Iterate section headers to find .text
    for i in range(e_shnum):
        sh_off = e_shoff + i * e_shentsize
        sh_name = struct.unpack_from(endian + "I", so_bytes, sh_off)[0]
        sh_type = struct.unpack_from(endian + "I", so_bytes, sh_off + 4)[0]
        sh_size = struct.unpack_from(endian + "Q", so_bytes, sh_off + 32)[0]
        sh_offset = struct.unpack_from(endian + "Q", so_bytes, sh_off + 24)[0]

        # Read section name
        if sh_name < sh_name_size and sh_name_offset + sh_name < len(so_bytes):
            try:
                name_end = so_bytes.index(b"\x00", sh_name_offset + sh_name)
                name = so_bytes[sh_name_offset + sh_name:name_end].decode("ascii", errors="ignore")
            except (ValueError, UnicodeDecodeError):
                continue
        else:
            continue

        if name == ".text" and sh_type == 1:  # SHT_PROGBITS
            print(f"  .text section: offset=0x{sh_offset:x}, size={sh_size}")
            return so_bytes[sh_offset:sh_offset + sh_size]

    return None


def format_byte_array(data: bytes) -> str:
    """Format bytes as C array initializer with hex values."""
    lines = []
    for i in range(0, len(data), 16):
        chunk = data[i:i + 16]
        hex_vals = ", ".join(f"0x{b:02X}" for b in chunk)
        lines.append(f"    {hex_vals}")
    return ",\n".join(lines)


def patch_native_bridge(project_root: str, obfuscated: bytes):
    """Replace g_apk_digests_obs array in native-bridge.cpp."""
    cpp_path = os.path.join(project_root, NATIVE_BRIDGE_CPP)

    with open(cpp_path, "r") as f:
        content = f.read()

    # Locate the array
    marker = "static const uint8_t g_apk_digests_obs[96] = {"
    start = content.index(marker)
    end = content.index("};", start) + 2

    new_array = (
        f"{marker}\n"
        f"    /* [0..31]   DEX digest (SM3 of APK)       */ {', '.join(f'0x{obfuscated[i]:02X}' for i in range(0, 16))},\n"
        f"    /*                                        */ {', '.join(f'0x{obfuscated[i]:02X}' for i in range(16, 32))},\n"
        f"    /* [32..63]  SO .text digest (SM3)         */ {', '.join(f'0x{obfuscated[i]:02X}' for i in range(32, 48))},\n"
        f"    /*                                        */ {', '.join(f'0x{obfuscated[i]:02X}' for i in range(48, 64))},\n"
        f"    /* [64..95]  ARSC digest (SM3 of 1st 64KB) */ {', '.join(f'0x{obfuscated[i]:02X}' for i in range(64, 80))},\n"
        f"    /*                                        */ {', '.join(f'0x{obfuscated[i]:02X}' for i in range(80, 96))}\n"
        f"}};"
    )

    content = content[:start] + new_array + content[end:]

    with open(cpp_path, "w") as f:
        f.write(content)

    print(f"  ✅ Patched {cpp_path}")
    print(f"     g_apk_digests_obs now contains obfuscated SM3 hashes")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        print("ERROR: APK path required")
        sys.exit(1)

    apk_path = sys.argv[1]
    if not os.path.isfile(apk_path):
        print(f"ERROR: APK not found: {apk_path}")
        sys.exit(1)

    # Find project root (where tools/embed_apk_hashes.py lives)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)

    apk_size = os.path.getsize(apk_path)
    print(f"📦 APK: {apk_path} ({apk_size / 1024 / 1024:.1f} MB)")

    # 1. DEX hash — entire APK
    print("\n🔹 Computing DEX digest (SM3 of entire APK)…")
    dex_digest = hash_dex(apk_path)
    print(f"   SM3: {dex_digest.hex()}")

    # 2. SO .text hash
    print("\n🔹 Computing SO .text digest…")
    so_digest = hash_so_text(apk_path)
    print(f"   SM3: {so_digest.hex()}")

    # 3. ARSC hash — first 64KB
    print("\n🔹 Computing ARSC digest (SM3 of first 64KB)…")
    arsc_digest = hash_arsc(apk_path)
    print(f"   SM3: {arsc_digest.hex()}")

    # Combine and obfuscate
    plain = dex_digest + so_digest + arsc_digest
    obfuscated = obfuscate(plain)

    print(f"\n🔹 Obfuscated table ({len(obfuscated)} bytes):")
    for i in range(0, len(obfuscated), 32):
        chunk = obfuscated[i:i + 32]
        label = ["DEX ", "SO  ", "ARSC"][i // 32]
        print(f"   [{label}] {chunk.hex()}")

    # Patch native-bridge.cpp
    print("\n🔹 Patching native-bridge.cpp…")
    patch_native_bridge(project_root, obfuscated)

    print("\n✅ Done! g_apk_digests_obs is now populated with real hashes.")
    print("   Rebuild with: ./gradlew assembleRelease")


if __name__ == "__main__":
    main()
