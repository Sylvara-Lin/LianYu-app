#!/usr/bin/env python3
"""
patch_so_crc32.py — Post-build SO .text CRC32 injector

After the APK is built, this script:
1. Extracts liblianyu_security.so for each ABI from the APK
2. Computes CRC32 of the .text section
3. XORs with VMP_BUILD_SEED (from g_vmp_config.h)
4. Patches IG_EXPECTED_TEXT_CRC32_OBF in .rodata
5. Updates the SO inside the APK zip

Usage:
  python3 tools/patch_so_crc32.py --apk <path_to_apk>

Requires: pyelftools, zipfile (stdlib)
"""

import os
import sys
import struct
import zipfile
import argparse
import tempfile
import shutil

from elftools.elf.elffile import ELFFile

CRC32_TABLE = [
    0x00000000,0x77073096,0xEE0E612C,0x990951BA,0x076DC419,0x706AF48F,0xE963A535,0x9E6495A3,
    0x0EDB8832,0x79DCB8A4,0xE0D5E91E,0x97D2D988,0x09B64C2B,0x7EB17CBD,0xE7B82D07,0x90BF1D91,
    0x1DB71064,0x6AB020F2,0xF3B97148,0x84BE41DE,0x1ADAD47D,0x6DDDE4EB,0xF4D4B551,0x83D385C7,
    0x136C9856,0x646BA8C0,0xFD62F97A,0x8A65C9EC,0x14015C4F,0x63066CD9,0xFA0F3D63,0x8D080DF5,
    0x3B6E20C8,0x4C69105E,0xD56041E4,0xA2677172,0x3C03E4D1,0x4B04D447,0xD20D85FD,0xA50AB56B,
    0x35B5A8FA,0x42B2986C,0xDBBBC9D6,0xACBCF940,0x32D86CE3,0x45DF5C75,0xDCD60DCF,0xABD13D59,
    0x26D930AC,0x51DE003A,0xC8D75180,0xBFD06116,0x21B4F4B5,0x56B3C423,0xCFBA9599,0xB8BDA50F,
    0x2802B89E,0x5F058808,0xC60CD9B2,0xB10BE924,0x2F6F7C87,0x58684C11,0xC1611DAB,0xB6662D3D,
    0x76DC4190,0x01DB7106,0x98D220BC,0xEFD5102A,0x71B18589,0x06B6B51F,0x9FBFE4A5,0xE8B8D433,
    0x7807C9A2,0x0F00F934,0x9609A88E,0xE10E9818,0x7F6A0DBB,0x086D3D2D,0x91646C97,0xE6635C01,
    0x6B6B51F4,0x1C6C6162,0x856530D8,0xF262004E,0x6C0695ED,0x1B01A57B,0x8208F4C1,0xF50FC457,
    0x65B0D9C6,0x12B7E950,0x8BBEB8EA,0xFCB9887C,0x62DD1DDF,0x15DA2D49,0x8CD37CF3,0xFBD44C65,
    0x4DB26158,0x3AB551CE,0xA3BC0074,0xD4BB30E2,0x4ADFA541,0x3DD895D7,0xA4D1C46D,0xD3D6F4FB,
    0x4369E96A,0x346ED9FC,0xAD678846,0xDA60B8D0,0x44042D73,0x33031DE5,0xAA0A4C5F,0xDD0D7CC9,
    0x5005713C,0x270241AA,0xBE0B1010,0xC90C2086,0x5768B525,0x206F85B3,0xB966D409,0xCE61E49F,
    0x5EDEF90E,0x29D9C998,0xB0D09822,0xC7D7A8B4,0x59B33D17,0x2EB40D81,0xB7BD5C3B,0xC0BA6CAD,
    0xEDB88320,0x9ABFB3B6,0x03B6E20C,0x74B1D29A,0xEAD54739,0x9DD277AF,0x04DB2615,0x73DC1683,
    0xE3630B12,0x94643B84,0x0D6D6A3E,0x7A6A5AA8,0xE40ECF0B,0x9309FF9D,0x0A00AE27,0x7D079EB1,
    0xF00F9344,0x8708A3D2,0x1E01F268,0x6906C2FE,0xF762575D,0x806567CB,0x196C3671,0x6E6B06E7,
    0xFED41B76,0x89D32BE0,0x10DA7A5A,0x67DD4ACC,0xF9B9DF6F,0x8EBEEFF9,0x17B7BE43,0x60B08ED5,
    0xD6D6A3E8,0xA1D1937E,0x38D8C2C4,0x4FDFF252,0xD1BB67F1,0xA6BC5767,0x3FB506DD,0x48B2364B,
    0xD80D2BDA,0xAF0A1B4C,0x36034AF6,0x41047A60,0xDF60EFC3,0xA867DF55,0x316E8EEF,0x4669BE79,
    0xCB61B38C,0xBC66831A,0x256FD2A0,0x5268E236,0xCC0C7795,0xBB0B4703,0x220216B9,0x5505262F,
    0xC5BA3BBE,0xB2BD0B28,0x2BB45A92,0x5CB36A04,0xC2D7FFA7,0xB5D0CF31,0x2CD99E8B,0x5BDEAE1D,
    0x9B64C2B0,0xEC63F226,0x756AA39C,0x026D930A,0x9C0906A9,0xEB0E363F,0x72076785,0x05005713,
    0x95BF4A82,0xE2B87A14,0x7BB12BAE,0x0CB61B38,0x92D28E9B,0xE5D5BE0D,0x7CDCEFB7,0x0BDBDF21,
    0x86D3D2D4,0xF1D4E242,0x68DDB3F8,0x1FDA836E,0x81BE16CD,0xF6B9265B,0x6FB077E1,0x18B74777,
    0x88085AE6,0xFF0F6A70,0x66063BCA,0x11010B5C,0x8F659EFF,0xF862AE69,0x616BFFD3,0x166CCF45,
    0xA00AE278,0xD70DD2EE,0x4E048354,0x3903B3C2,0xA7672661,0xD06016F7,0x4969474D,0x3E6E77DB,
    0xAED16A4A,0xD9D65ADC,0x40DF0B66,0x37D83BF0,0xA9BCAE53,0xDEBB9EC5,0x47B2CF7F,0x30B5FFE9,
    0xBDBDF21C,0xCABAC28A,0x53B39330,0x24B4A3A6,0xBAD03605,0xCDD70693,0x54DE5729,0x23D967BF,
    0xB3667A2E,0xC4614AB8,0x5D681B02,0x2A6F2B94,0xB40BBE37,0xC30C8EA1,0x5A05DF1B,0x2D02EF8D
]


def crc32(data: bytes) -> int:
    crc = 0xFFFFFFFF
    for b in data:
        crc = CRC32_TABLE[(crc ^ b) & 0xFF] ^ (crc >> 8)
    return crc ^ 0xFFFFFFFF


def read_text(path: str) -> str:
    for encoding in ("utf-8", "gbk"):
        try:
            with open(path, encoding=encoding) as f:
                return f.read()
        except UnicodeDecodeError:
            continue
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def read_seed_from_config(cpp_dir: str) -> int:
    """Read VMP_BUILD_SEED from g_vmp_config.h."""
    config_path = os.path.join(cpp_dir, "g_vmp_config.h")
    for line in read_text(config_path).splitlines():
        if "VMP_BUILD_SEED" in line:
            # Parse: #define VMP_BUILD_SEED  0x55A565ADu
            parts = line.split()
            for p in parts:
                p = p.rstrip('uU')
                if p.startswith("0x") or p.startswith("0X"):
                    return int(p, 16)
    raise ValueError(f"VMP_BUILD_SEED not found in {config_path}")


def patch_so(so_path: str, seed: int) -> bool:
    """Patch IG_EXPECTED_TEXT_CRC32_OBF in a single .so file."""
    with open(so_path, 'rb') as f:
        data = bytearray(f.read())

    elf = ELFFile(open(so_path, 'rb'))

    # 1. Find .text section
    text_section = None
    for sec in elf.iter_sections():
        if sec.name == '.text':
            text_section = sec
            break
    if text_section is None:
        print(f"   ⚠ No .text section — skipping")
        return False

    # Read .text data
    text_offset = text_section['sh_offset']
    text_size = text_section['sh_size']
    text_data = data[text_offset:text_offset + text_size]
    text_crc32 = crc32(text_data)
    obfuscated = text_crc32 ^ seed
    print(f"   .text: offset=0x{text_offset:X} size=0x{text_size:X} CRC32=0x{text_crc32:08X}")
    print(f"   XOR'd with seed 0x{seed:08X} = 0x{obfuscated:08X}")

    # 2. Find IG_EXPECTED_TEXT_CRC32_OBF in symbol table
    symtab = elf.get_section_by_name('.symtab')
    if symtab is None:
        symtab = elf.get_section_by_name('.dynsym')

    target_symbol = None
    if symtab:
        for sym in symtab.iter_symbols():
            if sym.name == 'IG_EXPECTED_TEXT_CRC32_OBF':
                target_symbol = sym
                break

    if target_symbol is None:
        print(f"   ⚠ Symbol IG_EXPECTED_TEXT_CRC32_OBF not found — falling back to string search")
        # Fallback: find sentinel bytes in the data
        sentinel_le = struct.pack('<I', 0x4E4F5045)  # "NOPE" little-endian
        pos = data.find(sentinel_le)
        if pos == -1:
            print(f"   ✗ Sentinel 0x4E4F5045 not found in binary")
            return False
        file_offset = pos
    else:
        # sym['st_value'] is the virtual address, need to map to file offset
        vaddr = target_symbol['st_value']
        # Find which segment contains this vaddr
        file_offset = None
        for seg in elf.iter_segments():
            if seg['p_type'] == 'PT_LOAD':
                seg_start = seg['p_vaddr']
                seg_end = seg_start + seg['p_memsz']
                if seg_start <= vaddr < seg_end:
                    file_offset = seg['p_offset'] + (vaddr - seg_start)
                    break
        if file_offset is None:
            print(f"   ✗ Cannot resolve vaddr 0x{vaddr:X} to file offset")
            return False

    # 3. Patch the value
    patched_bytes = struct.pack('<I', obfuscated)
    print(f"   Patching at file offset 0x{file_offset:X}: 0x{struct.unpack('<I', data[file_offset:file_offset+4])[0]:08X} → 0x{obfuscated:08X}")
    data[file_offset:file_offset + 4] = patched_bytes

    # Write back
    with open(so_path, 'wb') as f:
        f.write(data)

    return True


def main():
    parser = argparse.ArgumentParser(description='Post-build SO CRC32 injection')
    parser.add_argument('--apk', required=True, help='Path to signed APK')
    parser.add_argument('--config-dir', default=None,
                        help='Path to cpp dir containing g_vmp_config.h '
                             '(default: <script_dir>/../core/security/src/main/cpp)')
    args = parser.parse_args()

    # Resolve config dir
    if args.config_dir:
        config_dir = args.config_dir
    else:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        config_dir = os.path.join(script_dir, '..', 'core', 'security', 'src', 'main', 'cpp')

    seed = read_seed_from_config(config_dir)
    print(f"🔑 VMP_BUILD_SEED = 0x{seed:08X}")

    if not os.path.exists(args.apk):
        print(f"✗ APK not found: {args.apk}")
        sys.exit(1)

    abis = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']
    patched = 0

    # Read entire APK, patch SOs in memory, rewrite clean (no duplicates)
    so_data = {}
    with zipfile.ZipFile(args.apk, 'r') as zf:
        all_entries = {name: zf.read(name) for name in zf.namelist()}

    for abi in abis:
        so_entry = f'lib/{abi}/liblianyu_security.so'
        if so_entry not in all_entries:
            continue

        print(f"\n📦 {abi}")

        # Write SO to temp file for pyelftools processing
        with tempfile.NamedTemporaryFile(suffix='.so', delete=False) as tmp:
            tmp.write(all_entries[so_entry])
            tmp_path = tmp.name

        try:
            ok = patch_so(tmp_path, seed)
            if ok:
                so_data[so_entry] = open(tmp_path, 'rb').read()
                patched += 1
            else:
                so_data[so_entry] = all_entries[so_entry]  # keep original
        finally:
            os.unlink(tmp_path)

    if patched > 0:
        # Write clean APK (no duplicate entries)
        with zipfile.ZipFile(args.apk, 'w', compression=zipfile.ZIP_DEFLATED) as zf_out:
            for name, data in all_entries.items():
                if name in so_data:
                    zf_out.writestr(zipfile.ZipInfo(name), so_data[name])
                else:
                    zf_out.writestr(zipfile.ZipInfo(name), data)
        print(f"\n✅ Patched {patched} SO(s) in {args.apk}")
    else:
        print(f"\n⚠ No SOs patched — APK unchanged")


if __name__ == '__main__':
    main()
