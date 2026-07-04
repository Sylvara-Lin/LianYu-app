#!/usr/bin/env python3
"""
dex_extract.py — Code Item Extraction Build Tool

Extracts method Code Items from DEX, replaces with NOP instructions,
recomputes checksum + signature, and generates encrypted recovery blob.

Usage:
  python dex_extract.py <input.apk> [--output-dir <dir>]
"""

import struct
import hashlib
import zlib
import sys
import os
import json
from pathlib import Path

# ── DEX Format Constants ──
DEX_MAGIC_PREFIX = b'dex\n0'
DEX_HEADER_SIZE = 112

# Shell encryption key (must match dex-extractor.cpp)
SHELL_KEY = bytes([
    0x4C, 0x69, 0x61, 0x6E, 0x59, 0x75, 0x53, 0x68,
    0x65, 0x6C, 0x6C, 0x4B, 0x65, 0x79, 0x32, 0x35
])


class DexParser:
    """Parse DEX file header and extract Code Items."""

    def __init__(self, data: bytes):
        self.data = data
        self.header = {}
        self.strings = []
        self.types = []
        self.protos = []
        self.methods = []
        self.fields = []
        self.class_defs = []
        self.extracted = []  # List of (class_name, method_name, code_off, code_size, code_bytes)
        self._parse()

    def _parse(self):
        d = self.data
        if d[:6] != b'dex\n03' and d[:6] != b'dex\n03':
            raise ValueError(f"Invalid DEX magic: {d[:8]}")

        # Parse DEX header: magic[8], checksum[4], signature[20], file_size[4], header_size[4], endian_tag[4]
        h1 = struct.unpack_from('<8sI20sIII', d, 0)
        # Parse table offsets: link[12], then sizes/offsets
        h2 = struct.unpack_from('<III III III III III III III III', d, 44)
        keys = ['magic', 'checksum', 'signature', 'file_size', 'header_size', 'endian_tag',
                'link_size', 'link_off', 'map_off',
                'string_ids_size', 'string_ids_off',
                'type_ids_size', 'type_ids_off', 'proto_ids_size', 'proto_ids_off',
                'field_ids_size', 'field_ids_off', 'method_ids_size', 'method_ids_off',
                'class_defs_size', 'class_defs_off', 'data_size', 'data_off']
        h = h1 + h2
        self.header = dict(zip(keys, h))

        # Parse strings
        self.strings = self._parse_strings()
        # Parse types
        self.types = self._parse_types()
        # Parse protos
        self.protos = self._parse_protos()
        # Parse methods
        self.methods = self._parse_methods()
        # Parse class defs
        self.class_defs = self._parse_class_defs()

    def _read_uleb128(self, offset):
        """Read unsigned LEB128 value with bounds check."""
        result = 0
        shift = 0
        max_len = len(self.data)
        while offset < max_len and shift < 35:
            byte = self.data[offset]
            result |= (byte & 0x7F) << shift
            offset += 1
            if (byte & 0x80) == 0:
                return result, offset
            shift += 7
        return 0, max_len  # Bounds exceeded — return safe default

    
    def _parse_strings(self):
        """Fast parse: only reads count, skips detailed string parsing for large DEX."""
        result = []
        size = self.header['string_ids_size']
        if size > 10000:
            # Too many strings — skip detailed parsing for speed
            return ["<str>"] * size
        off = self.header['string_ids_off']
        for i in range(size):
            str_off = struct.unpack_from('<I', self.data, off + i * 4)[0]
            end = self.data.index(0, str_off) if 0 in self.data[str_off:str_off+256] else str_off + 1
            try:
                s = self.data[str_off:end].decode('utf-8', errors='replace')
            except:
                s = f"<str_{i}>"
            result.append(s)
        return result

    def _parse_types(self):
        result = []
        size = self.header['type_ids_size']
        off = self.header['type_ids_off']
        for i in range(size):
            idx = struct.unpack_from('<I', self.data, off + i * 4)[0]
            result.append(self.strings[idx] if idx < len(self.strings) else f"<type_{i}>")
        return result

    def _parse_protos(self):
        result = []
        size = self.header['proto_ids_size']
        off = self.header['proto_ids_off']
        for i in range(size):
            shorty, return_type, params_off = struct.unpack_from('<III', self.data, off + i * 12)
            proto_str = f"({self.strings[shorty] if shorty < len(self.strings) else '?'})"
            result.append(proto_str)
        return result

    def _parse_methods(self):
        result = []
        size = self.header['method_ids_size']
        off = self.header['method_ids_off']
        for i in range(size):
            class_idx, proto_idx, name_idx = struct.unpack_from('<HHI', self.data, off + i * 8)
            class_name = self.types[class_idx] if class_idx < len(self.types) else f"<cls_{class_idx}>"
            method_name = self.strings[name_idx] if name_idx < len(self.strings) else f"<m_{name_idx}>"
            proto = self.protos[proto_idx] if proto_idx < len(self.protos) else "()?"
            result.append(f"{class_name}->{method_name}{proto}")
        return result

    def _parse_class_defs(self):
        result = []
        size = self.header['class_defs_size']
        off = self.header['class_defs_off']
        for i in range(size):
            entry = struct.unpack_from('<IIIIIIII', self.data, off + i * 32)
            class_idx = entry[0]
            class_name = self.types[class_idx] if class_idx < len(self.types) else f"<cls_{class_idx}>"
            result.append({
                'class_idx': class_idx,
                'class_name': class_name,
                'class_data_off': entry[7],
            })
        return result

    def extract_code_items(self):
        """Extract all Code Items from class data."""
        for cls in self.class_defs:
            if cls['class_data_off'] == 0:
                continue
            self._extract_class(cls)

    
    def _extract_class(self, cls):
        """Parse class_data_item with safety limits."""
        off = cls['class_data_off']
        if off >= len(self.data):
            return
        static_fields_size, off = self._read_uleb128(off)
        instance_fields_size, off = self._read_uleb128(off)
        direct_methods_size, off = self._read_uleb128(off)
        virtual_methods_size, off = self._read_uleb128(off)
        # Safety cap
        direct_methods_size = min(direct_methods_size, 200)
        virtual_methods_size = min(virtual_methods_size, 200)
        static_fields_size = min(static_fields_size, 100)
        instance_fields_size = min(instance_fields_size, 100)
        for _ in range(static_fields_size + instance_fields_size):
            off = self._skip_encoded_field(off)
        for _ in range(direct_methods_size):
            off = self._extract_method_entry(off, cls)
        for _ in range(virtual_methods_size):
            off = self._extract_method_entry(off, cls)

    def _skip_encoded_field(self, off):
        """Skip one encoded_field entry."""
        _, off = self._read_uleb128(off)  # field_idx_diff
        _, off = self._read_uleb128(off)  # access_flags
        return off

    def _extract_method_entry(self, off, cls):
        """Extract one encoded_method entry."""
        method_idx_diff, off = self._read_uleb128(off)
        access_flags, off = self._read_uleb128(off)
        code_off_val, off = self._read_uleb128(off)

        if code_off_val == 0:
            return off  # No code (abstract/native)

        # Read code_item with bounds check
        code_item_start = code_off_val
        if code_item_start + 16 > len(self.data):
            return off  # Invalid offset
        registers_size = struct.unpack_from('<H', self.data, code_item_start)[0]
        ins_size = struct.unpack_from('<H', self.data, code_item_start + 2)[0]
        outs_size = struct.unpack_from('<H', self.data, code_item_start + 4)[0]
        tries_size = struct.unpack_from('<H', self.data, code_item_start + 6)[0]
        debug_info_off = struct.unpack_from('<I', self.data, code_item_start + 8)[0]
        insns_size = struct.unpack_from('<I', self.data, code_item_start + 12)[0]

        # Total code_item size: 16 header + instructions (insns_size*2) + tries + handlers
        insns_bytes = insns_size * 2
        base_size = 16 + insns_bytes

        # Handle try/catch (with bounds clamping)
        if tries_size > 0:
            base_size += tries_size * 8
            tmp = code_item_start + 16 + insns_bytes + tries_size * 8
            if tmp < len(self.data):
                handlers_size, _ = self._read_uleb128(tmp)
                for _ in range(min(handlers_size, 100)):  # Sanity cap
                    catch_count, tmp = self._read_uleb128(tmp)
                    tmp += catch_count * 8
                    if catch_count <= 0:
                        tmp += 8
                base_size = min(tmp - code_item_start, len(self.data) - code_item_start)

        code_bytes = self.data[code_item_start:code_item_start + base_size]
        class_name = cls['class_name']
        method_ref = f"{class_name}.method_{len(self.extracted)}"

        self.extracted.append({
            'class_name': class_name,
            'method_ref': method_ref,
            'code_off': code_item_start,
            'code_size': base_size,
            'code_bytes': code_bytes,
        })
        return off

    def nop_fill(self):
        """Replace extracted Code Items with NOP instructions (0x0000 for Dalvik)."""
        data = bytearray(self.data)
        for entry in self.extracted:
            off = entry['code_off']
            size = entry['code_size']
            for i in range(size):
                data[off + i] = 0x00
        self.data = bytes(data)

    def recompute_checksum(self):
        """Recompute Adler32 checksum and SHA-1 signature."""
        data = bytearray(self.data)
        # Leave checksum (offset 8) and signature (offset 12) as zero
        for i in range(8, 32):
            data[i] = 0

        # Adler32 checksum
        adler = zlib.adler32(bytes(data)) & 0xFFFFFFFF
        struct.pack_into('<I', data, 8, adler)

        # SHA-1 signature (20 bytes)
        sha = hashlib.sha1(bytes(data)).digest()
        for i in range(20):
            data[12 + i] = sha[i]

        self.data = bytes(data)


def encrypt_blob(data: bytes, key: bytes) -> bytes:
    """XOR-encrypt with rotating key (matches dex-extractor.cpp)."""
    result = bytearray(data)
    for i in range(len(result)):
        result[i] ^= key[i % 16] ^ (i * 0x9D & 0xFF)
    return bytes(result)


def main():
    import argparse
    ap = argparse.ArgumentParser(description="DEX Code Item Extractor")
    ap.add_argument("apk", help="Input APK file")
    ap.add_argument("--output-dir", default="shell_output", help="Output dir")
    args = ap.parse_args()

    # Read APK
    import zipfile
    with zipfile.ZipFile(args.apk, 'r') as zf:
        dex_files = sorted([n for n in zf.namelist() if n.endswith('.dex')])
        if not dex_files:
            print("ERROR: No .dex files in APK")
            sys.exit(1)

        os.makedirs(args.output_dir, exist_ok=True)
        all_extracted = []
        total_methods = 0

        for dex_name in dex_files:
            dex_data = zf.read(dex_name)
            parser = DexParser(dex_data)
            parser.extract_code_items()

            extracted_count = len(parser.extracted)
            total_methods += extracted_count
            print(f"[{dex_name}] Extracted {extracted_count} Code Items ({len(dex_data):,} bytes → {parser.header['file_size']:,} bytes)")

            # Combine all code bytes
            for entry in parser.extracted:
                all_extracted.append({
                    'dex': dex_name,
                    'class_name': entry['class_name'],
                    'code_off': entry['code_off'],
                    'code_size': entry['code_size'],
                    'code_bytes': entry['code_bytes'].hex(),
                })

            # NOP fill + recompute
            parser.nop_fill()
            parser.recompute_checksum()

            # Write modified DEX
            out_path = os.path.join(args.output_dir, dex_name)
            with open(out_path, 'wb') as f:
                f.write(parser.data)

        # Write recovery blob (encrypted code items)
        code_blob = bytearray()
        index = []
        offset_in_blob = 0
        for entry in all_extracted:
            cb = bytes.fromhex(entry['code_bytes'])
            code_blob.extend(cb)
            index.append({
                'class_name': entry['class_name'],
                'code_off': entry['code_off'],
                'code_size': entry['code_size'],
                'offset_in_blob': offset_in_blob,
            })
            offset_in_blob += len(cb)

        encrypted = encrypt_blob(bytes(code_blob), SHELL_KEY)
        blob_path = os.path.join(args.output_dir, 'code_items.bin')
        with open(blob_path, 'wb') as f:
            f.write(encrypted)

        # Write binary method index (for native dex-extractor.cpp)
        bin_path = os.path.join(args.output_dir, 'method_index.bin')
        with open(bin_path, 'wb') as f:
            f.write(struct.pack('<I', len(index)))  # method_count
            for entry in index:
                f.write(struct.pack('<I', entry['code_off']))   # code_off
                f.write(struct.pack('<I', entry['code_size']))  # code_size
                f.write(struct.pack('<I', entry['offset_in_blob']))  # offset_in_blob

        # Write JSON index (for human inspection)
        index_path = os.path.join(args.output_dir, 'method_index.json')
        with open(index_path, 'w') as f:
            json.dump(index, f, indent=2)

        print(f"\nTotal: {total_methods} methods extracted")
        print(f"Recovery blob: {blob_path} ({len(encrypted):,} bytes)")
        print(f"Binary index: {bin_path} ({len(index)} entries)")
        print(f"JSON index: {index_path}")
        print(f"Modified DEX → {args.output_dir}/")


if __name__ == '__main__':
    main()
