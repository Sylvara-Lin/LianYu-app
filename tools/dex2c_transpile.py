#!/usr/bin/env python3
"""
LianYu Dex2C Transpiler — DEX bytecode → C++ native (Phase 2).

Reads a whitelisted subset of methods from classes.dex and translates
them into JNI C++ native functions. The generated code is compiled into
liblianyu_dex2c.so and loaded at runtime for maximum security.

Usage:
    python3 tools/dex2c_transpile.py <path/to/classes.dex> [--whitelist whitelist.txt]
                                         [--out-cpp output.cpp] [--out-h registry.h]

DEX Format Reference:
    https://source.android.com/docs/core/runtime/dex-format

Architecture:
    Phase 2: DEX → C++ transpiler (this file)
    Phase 3: VmpDex2cDispatcher.kt routes calls
    Phase 4: dex-packer.cpp verifies .so integrity
"""

import sys
import os
import struct
import argparse
import hashlib
import zlib
from typing import List, Dict, Optional, Tuple, Set


# ═══════════════════════════════════════════════════════════════════
# DEX Format Constants
# ═══════════════════════════════════════════════════════════════════

ENDIAN_CONSTANT = 0x12345678
REVERSE_ENDIAN_CONSTANT = 0x78563412
NO_INDEX = 0xFFFFFFFF

# DEX header offsets (ULEB128-encoded leb128_size fields skipped)
DEX_HEADER_SIZE = 0x70  # Standard DEX header size

# Access flags
ACC_PUBLIC = 0x1
ACC_PRIVATE = 0x2
ACC_PROTECTED = 0x4
ACC_STATIC = 0x8
ACC_FINAL = 0x10
ACC_NATIVE = 0x100

# Dalvik opcode constants (subset — extend as needed)
OP_NOP = 0x00
OP_MOVE = 0x01
OP_MOVE_WIDE = 0x04
OP_MOVE_OBJECT = 0x07
OP_MOVE_RESULT = 0x0A
OP_RETURN_VOID = 0x0E
OP_RETURN = 0x0F
OP_RETURN_WIDE = 0x10
OP_RETURN_OBJECT = 0x11
OP_CONST_4 = 0x12
OP_CONST_16 = 0x13
OP_CONST = 0x14
OP_CONST_HIGH16 = 0x15
OP_CONST_WIDE_16 = 0x16
OP_CONST_STRING = 0x1A
OP_CONST_STRING_JUMBO = 0x1B
OP_CONST_CLASS = 0x1C
OP_MONITOR_ENTER = 0x1D
OP_MONITOR_EXIT = 0x1E
OP_CHECK_CAST = 0x1F
OP_INSTANCE_OF = 0x20
OP_NEW_INSTANCE = 0x22
OP_NEW_ARRAY = 0x23
OP_FILLED_NEW_ARRAY = 0x24
OP_FILL_ARRAY_DATA = 0x26
OP_THROW = 0x27
OP_GOTO = 0x28
OP_GOTO_16 = 0x29
OP_GOTO_32 = 0x2A
OP_IF_EQ = 0x32
OP_IF_NE = 0x33
OP_IF_LT = 0x34
OP_IF_GE = 0x35
OP_IF_GT = 0x36
OP_IF_LE = 0x37
OP_IF_EQZ = 0x38
OP_IF_NEZ = 0x39
OP_IF_LTZ = 0x3A
OP_IF_GEZ = 0x3B
OP_IF_GTZ = 0x3C
OP_IF_LEZ = 0x3D
OP_AGET = 0x44
OP_AGET_WIDE = 0x45
OP_AGET_OBJECT = 0x46
OP_APUT = 0x4B
OP_APUT_WIDE = 0x4C
OP_APUT_OBJECT = 0x4D
OP_IGET = 0x52
OP_IGET_WIDE = 0x53
OP_IGET_OBJECT = 0x54
OP_IPUT = 0x59
OP_IPUT_WIDE = 0x5A
OP_IPUT_OBJECT = 0x5B
OP_SGET = 0x60
OP_SGET_WIDE = 0x61
OP_SGET_OBJECT = 0x62
OP_SPUT = 0x67
OP_SPUT_WIDE = 0x68
OP_SPUT_OBJECT = 0x69
OP_INVOKE_VIRTUAL = 0x6E
OP_INVOKE_DIRECT = 0x70
OP_INVOKE_STATIC = 0x71
OP_INVOKE_INTERFACE = 0x72
OP_INVOKE_VIRTUAL_RANGE = 0x74
OP_INVOKE_DIRECT_RANGE = 0x76
OP_INVOKE_STATIC_RANGE = 0x77
OP_INVOKE_INTERFACE_RANGE = 0x78
OP_NEG_INT = 0x7B
OP_NOT_INT = 0x7C
OP_NEG_LONG = 0x7D
OP_NOT_LONG = 0x7E
OP_INT_TO_LONG = 0x81
OP_INT_TO_FLOAT = 0x82
OP_LONG_TO_INT = 0x84
OP_ADD_INT = 0x90
OP_SUB_INT = 0x91
OP_MUL_INT = 0x92
OP_DIV_INT = 0x93
OP_REM_INT = 0x94
OP_AND_INT = 0x95
OP_OR_INT = 0x96
OP_XOR_INT = 0x97
OP_SHL_INT = 0x98
OP_SHR_INT = 0x99
OP_USHR_INT = 0x9A
OP_ADD_LONG = 0x9B
OP_SUB_LONG = 0x9C
OP_MUL_LONG = 0x9D
OP_DIV_LONG = 0x9E
OP_CMP_LONG = 0x9F
OP_ARRAY_LENGTH = 0x21


# ═══════════════════════════════════════════════════════════════════
# DEX Parser
# ═══════════════════════════════════════════════════════════════════

class DexParser:
    """Parse a DEX file and extract method bytecodes."""

    def __init__(self, path: str):
        with open(path, 'rb') as f:
            self.data = f.read()
        self._parse_header()
        self._parse_strings()
        self._parse_types()
        self._parse_protos()
        self._parse_methods()
        self._build_method_code_index()

    def _read_uleb128(self, offset: int) -> Tuple[int, int]:
        """Read unsigned LEB128 value. Returns (value, bytes_consumed)."""
        result = 0
        shift = 0
        consumed = 0
        while True:
            byte = self.data[offset + consumed]
            consumed += 1
            result |= (byte & 0x7F) << shift
            if (byte & 0x80) == 0:
                break
            shift += 7
        return result, consumed

    def _read_sleb128(self, offset: int) -> Tuple[int, int]:
        """Read signed LEB128 value. Returns (value, bytes_consumed)."""
        result = 0
        shift = 0
        consumed = 0
        while True:
            byte = self.data[offset + consumed]
            consumed += 1
            result |= (byte & 0x7F) << shift
            shift += 7
            if (byte & 0x80) == 0:
                if (byte & 0x40) and (shift < 64):
                    result |= -(1 << shift)
                break
        return result, consumed

    def _parse_header(self):
        """Parse DEX file header (0x70 bytes standard)."""
        d = self.data
        # DEX header: magic(8) + checksum(4) + signature(20) + file_size(4) +
        # header_size(4) + endian_tag(4) + link_size(4) + link_off(4) +
        # map_off(4) + string_ids_size(4) + string_ids_off(4) +
        # type_ids_size(4) + type_ids_off(4) + proto_ids_size(4) +
        # proto_ids_off(4) + field_ids_size(4) + field_ids_off(4) +
        # method_ids_size(4) + method_ids_off(4) + class_defs_size(4) +
        # class_defs_off(4) + data_size(4) + data_off(4)
        header = struct.unpack_from('<8s I 20s I I I I I I I I I I I I I I I I I I I I', d, 0)
        self.magic = header[0][:4]  # First 4 bytes: 'dex\n'
        self.checksum = header[1]
        # header[2] = signature (20 bytes, unused)
        # header[3] = file_size
        # header[4] = header_size
        # header[5] = endian_tag
        # header[6] = link_size
        # header[7] = link_off
        # header[8] = map_off
        self.string_ids_size = header[9]
        self.string_ids_off = header[10]
        self.type_ids_size = header[11]
        self.type_ids_off = header[12]
        self.proto_ids_size = header[13]
        self.proto_ids_off = header[14]
        self.field_ids_size = header[15]
        self.field_ids_off = header[16]
        self.method_ids_size = header[17]
        self.method_ids_off = header[18]
        self.class_defs_size = header[19]
        self.class_defs_off = header[20]
        # self.data_size = header[21] — unused
        # self.data_off = header[22]  — unused

        print(f"[dex2c] DEX: magic={self.magic}, checksum=0x{self.checksum:08X}")
        print(f"[dex2c]   strings={self.string_ids_size}, types={self.type_ids_size}")
        print(f"[dex2c]   protos={self.proto_ids_size}, fields={self.field_ids_size}")
        print(f"[dex2c]   methods={self.method_ids_size}, classes={self.class_defs_size}")

    def _parse_strings(self):
        """Parse string_id list → list of strings."""
        self.strings = []
        for i in range(self.string_ids_size):
            offset = struct.unpack_from('<I', self.data,
                self.string_ids_off + i * 4)[0]
            # String data: uleb128 size + modified UTF-8 + null
            strlen, consumed = self._read_uleb128(offset)
            # TODO: proper MUTF-8 decoding
            raw = self.data[offset + consumed : offset + consumed + strlen]
            self.strings.append(raw.decode('utf-8', errors='replace'))

    def _parse_types(self):
        """Parse type_id list → list of type descriptor strings."""
        self.types = []
        off = self.type_ids_off
        for _ in range(self.type_ids_size):
            string_idx = struct.unpack_from('<I', self.data, off)[0]
            off += 4
            self.types.append(self.strings[string_idx])

    def _parse_protos(self):
        """Parse proto_id list."""
        self.protos = []
        off = self.proto_ids_off
        for _ in range(self.proto_ids_size):
            shorty_idx, return_type_idx, param_off = struct.unpack_from('<I I I', self.data, off)
            off += 12
            self.protos.append({
                'shorty': self.strings[shorty_idx],
                'return_type': return_type_idx,
                'param_off': param_off
            })

    def _parse_methods(self):
        """Parse method_id list."""
        self.methods = []
        off = self.method_ids_off
        for _ in range(self.method_ids_size):
            class_idx, proto_idx, name_idx = struct.unpack_from('<H H I', self.data, off)
            off += 8
            self.methods.append({
                'class': self.types[class_idx],
                'proto': proto_idx,
                'name': self.strings[name_idx]
            })

    def get_method_key(self, method_idx: int) -> str:
        """Get canonical method key: com.package.ClassName.methodName"""
        m = self.methods[method_idx]
        class_name = m['class'][1:-1].replace('/', '.')  # Lfoo/Bar; → foo.Bar
        return f"{class_name}.{m['name']}"

    def find_method(self, key: str) -> Optional[int]:
        """Find method index by canonical key. Returns index or None. O(1) lookup."""
        if not hasattr(self, '_method_key_index'):
            self._method_key_index = {}
            for i in range(self.method_ids_size):
                self._method_key_index[self.get_method_key(i)] = i
            print(f"[dex2c] Indexed {len(self._method_key_index)} method keys")
        return self._method_key_index.get(key)

    def get_method_info(self, method_idx: int) -> dict:
        """Get full method info: name, class, proto, return type."""
        m = self.methods[method_idx]
        proto = self.protos[m['proto']]
        return {
            'name': m['name'],
            'class': m['class'],
            'return_type': self.types[proto['return_type']],
            'shorty': proto['shorty'],
            'proto': proto,
            'index': method_idx
        }

    def _build_method_code_index(self):
        """Pre-build method_idx → code_offset mapping for O(1) lookup.

        R8-compacted DEX can produce abnormal class_data: delta-encoded
        method_idx_diff values may be sentinel-large (~0x0FFFFFFF), and
        code_off may point to garbage. We validate at every step to avoid
        reading implausible code_items.
        """
        self._method_code_map = {}  # method_idx → code_offset
        off = self.class_defs_off
        max_off = len(self.data)
        total_classes_parsed = 0
        total_methods_with_code = 0
        total_skipped_uleb128 = 0  # skipped due to overflow/out-of-range

        for ci in range(self.class_defs_size):
            if off + 32 > max_off:
                break
            cd = struct.unpack_from('<I I I I I I I I', self.data, off)
            class_data_off = cd[6]
            off += 32

            if class_data_off == 0 or class_data_off >= max_off - 16:
                continue

            total_classes_parsed += 1

            pos = class_data_off
            # Read sizes with overflow guard
            if pos >= max_off: continue
            static_fields_size, consumed = self._read_uleb128(pos)
            if static_fields_size > 10000: continue  # R8 sentinel / garbage
            pos += consumed

            if pos >= max_off: continue
            instance_fields_size, consumed = self._read_uleb128(pos)
            if instance_fields_size > 10000: continue
            pos += consumed

            if pos >= max_off: continue
            direct_methods_size, consumed = self._read_uleb128(pos)
            if direct_methods_size > 10000: continue
            pos += consumed

            if pos >= max_off: continue
            virtual_methods_size, consumed = self._read_uleb128(pos)
            if virtual_methods_size > 10000: continue
            pos += consumed

            last_idx = 0
            total = direct_methods_size + virtual_methods_size
            for _ in range(min(total, 5000)):  # hard cap per class
                if pos + 3 > max_off:  # need at least 3 uleb128 values
                    break
                method_idx_diff, consumed = self._read_uleb128(pos)
                if method_idx_diff > 0xFFFFFF:  # sentinel / garbage delta
                    total_skipped_uleb128 += 1
                    break  # remaining data for this class is corrupt
                pos += consumed

                if pos >= max_off: break
                access_flags, consumed = self._read_uleb128(pos); pos += consumed

                if pos >= max_off: break
                code_off, consumed = self._read_uleb128(pos); pos += consumed

                actual_idx = last_idx + method_idx_diff
                last_idx = actual_idx

                # Validate method_idx is within bounds
                if actual_idx >= self.method_ids_size:
                    continue

                # Validate code_off: must be in-bounds AND point to
                # readable code_item header (>= 16 bytes remaining)
                if code_off == 0 or code_off + 16 > max_off:
                    continue

                # Quick pre-validation: peek at code_item header
                # registers_size(2) + ins_size(2) + outs_size(2) +
                # tries_size(2) + debug_info_off(4) + insns_size(4)
                peek = struct.unpack_from('<H H H H I I', self.data, code_off)
                regs, ins_count, outs, tries, _, insns = peek
                # Reject obviously garbage code_items
                if regs > 1000 or insns > 100000 or tries > 500:
                    continue
                # Reject zero-insns code_items (abstract methods, etc.)
                if insns == 0:
                    continue

                self._method_code_map[actual_idx] = code_off
                total_methods_with_code += 1

        print(f"[dex2c] Indexed {len(self._method_code_map)} methods with code "
              f"(classes_parsed={total_classes_parsed}, "
              f"uleb128_overflows={total_skipped_uleb128})")

    def read_method_code(self, method_idx: int, return_type: str = 'V') -> Optional[dict]:
        """Read code_item for a method. Returns parsed code or None. O(1) lookup."""
        code_off = self._method_code_map.get(method_idx)
        if code_off is None:
            return None
        return self._parse_code_item(code_off, method_idx, return_type)

    def _parse_code_item(self, offset: int, method_idx: int, return_type: str = 'V') -> dict:
        """Parse code_item structure."""
        # registers_size, ins_size, outs_size, tries_size, debug_info_off, insns_size
        header = struct.unpack_from('<H H H H I I', self.data, offset)
        registers_size, ins_size, outs_size, tries_size, debug_info_off, insns_size = header

        # Validate: reject garbage data that R8 sometimes produces
        if registers_size > 1000 or insns_size > 100000:
            print(f"  [skip] Method {method_idx}: implausible code_item (regs={registers_size}, insns={insns_size})")
            return None

        code_start = offset + 16  # code_item header = 16 bytes
        code_bytes = self.data[code_start : code_start + insns_size * 2]

        # Parse instructions (pass return_type for return-void suppression)
        instructions = self._decode_instructions(code_bytes, insns_size, return_type)

        return {
            'method_idx': method_idx,
            'registers_size': registers_size,
            'ins_size': ins_size,
            'outs_size': outs_size,
            'tries_size': tries_size,
            'code': code_bytes,
            'instructions': instructions,
            'insns_count': insns_size,
            'return_type': return_type
        }

    def _decode_instructions(self, code: bytes, count: int, return_type: str = 'V') -> List[dict]:
        """Decode DEX bytecode instructions.

        return_type: the DEX type descriptor of the method's return type (e.g., 'V'=void, 'L...;'=object).
        Used to suppress return; emission for non-void methods where the bytecode ends with return-void
        (R8 optimization: inlines a void body into a non-void method).
        """
        result = []
        i = 0
        while i < len(code):
            opcode = code[i] & 0xFF
            inst = {
                'offset': i // 2,
                'opcode': opcode,
                'name': OPCODE_NAMES.get(opcode, f'unknown_{opcode:02X}'),
                'args': code[i:i+10]  # max 5 ushorts
            }

            # Decode common formats
            if opcode == OP_NOP:
                i += 2
            elif opcode in (OP_MOVE, OP_MOVE_WIDE, OP_MOVE_OBJECT,
                            OP_RETURN_VOID, OP_RETURN, OP_RETURN_WIDE, OP_RETURN_OBJECT,
                            OP_NEG_INT, OP_NOT_INT, OP_NEG_LONG, OP_NOT_LONG,
                            OP_INT_TO_LONG, OP_INT_TO_FLOAT, OP_LONG_TO_INT,
                            OP_ARRAY_LENGTH,
                            OP_ADD_INT, OP_SUB_INT, OP_MUL_INT, OP_DIV_INT, OP_REM_INT,
                            OP_AND_INT, OP_OR_INT, OP_XOR_INT, OP_SHL_INT, OP_SHR_INT, OP_USHR_INT,
                            OP_ADD_LONG, OP_SUB_LONG, OP_MUL_LONG, OP_DIV_LONG, OP_CMP_LONG):
                if i + 2 <= len(code):
                    inst['format'] = '12x'
                    v = struct.unpack_from('<B', code, i + 1)[0]
                    inst['vA'] = v & 0xF
                    inst['vB'] = (v >> 4) & 0xF
                i += 2
            elif opcode == OP_CONST_4:
                if i + 2 <= len(code):
                    v = struct.unpack_from('<B', code, i + 1)[0]
                    inst['vA'] = v & 0xF
                    inst['vB'] = (v >> 4) & 0xF  # 4-bit literal
            elif opcode == OP_CONST_16:
                if i + 2 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
                    inst['lit'] = struct.unpack_from('<h', code, i + 2)[0] if i + 4 <= len(code) else 0
            elif opcode == OP_CONST:
                # TODO: proper 31t format
                pass
            elif opcode == OP_CONST_STRING:
                if i + 2 <= len(code):
                    inst['string_idx'] = struct.unpack_from('<H', code, i + 1)[0] if i + 3 <= len(code) else 0
            elif opcode in (OP_IF_EQZ, OP_IF_NEZ, OP_IF_LTZ, OP_IF_GEZ, OP_IF_GTZ, OP_IF_LEZ):
                if i + 4 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
                    inst['target'] = struct.unpack_from('<h', code, i + 2)[0]
            elif opcode in (OP_IF_EQ, OP_IF_NE, OP_IF_LT, OP_IF_GE, OP_IF_GT, OP_IF_LE):
                if i + 4 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
                    inst['vB'] = (v >> 4) & 0xF
                    inst['target'] = struct.unpack_from('<h', code, i + 2)[0]
            elif opcode == OP_GOTO:
                inst['target'] = struct.unpack_from('<b', code, i + 1)[0]
            elif opcode == OP_GOTO_16:
                inst['target'] = struct.unpack_from('<h', code, i + 2)[0]
            elif opcode == OP_GOTO_32:
                inst['target'] = struct.unpack_from('<i', code, i + 2)[0]
            elif opcode in (OP_IGET, OP_IGET_WIDE, OP_IGET_OBJECT,
                            OP_IPUT, OP_IPUT_WIDE, OP_IPUT_OBJECT):
                if i + 4 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
                    inst['vB'] = (v >> 4) & 0xF
                    inst['field_idx'] = struct.unpack_from('<H', code, i + 2)[0]
            elif opcode in (OP_SGET, OP_SGET_WIDE, OP_SGET_OBJECT,
                            OP_SPUT, OP_SPUT_WIDE, OP_SPUT_OBJECT):
                if i + 4 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
                    inst['field_idx'] = struct.unpack_from('<H', code, i + 2)[0]
            elif opcode in (OP_INVOKE_VIRTUAL, OP_INVOKE_DIRECT, OP_INVOKE_STATIC,
                            OP_INVOKE_INTERFACE):
                if i + 6 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF  # arg count
                    inst['method_idx'] = struct.unpack_from('<H', code, i + 2)[0]
                    # args in vC..vG
                    v2 = struct.unpack_from('<H', code, i + 4)[0]
                    inst['regs'] = [v2 & 0xF, (v2 >> 4) & 0xF,
                                    (v2 >> 8) & 0xF, (v2 >> 12) & 0xF]
            elif opcode in (OP_NEW_INSTANCE, OP_NEW_ARRAY, OP_CHECK_CAST, OP_INSTANCE_OF):
                if i + 4 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
                    inst['type_idx'] = struct.unpack_from('<H', code, i + 2)[0]
            elif opcode in (OP_AGET, OP_AGET_WIDE, OP_AGET_OBJECT,
                            OP_APUT, OP_APUT_WIDE, OP_APUT_OBJECT):
                if i + 4 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
                    inst['vB'] = (v >> 4) & 0xF
            elif opcode == OP_FILL_ARRAY_DATA:
                if i + 6 <= len(code):
                    v = code[i + 1]
                    inst['vA'] = v & 0xF
            elif opcode == OP_THROW:
                inst['vA'] = code[i + 1] & 0xF
            elif opcode == OP_MONITOR_ENTER or opcode == OP_MONITOR_EXIT:
                inst['vA'] = code[i + 1] & 0xF
            elif opcode == OP_CONST_WIDE_16:
                inst['vA'] = code[i + 1] & 0xF

            # Calculate instruction size (most common: 2 bytes per insn unit)
            i += 2
            result.append(inst)

        return result


# Dalvik opcode name map
OPCODE_NAMES = {
    OP_NOP: 'nop',
    OP_MOVE: 'move', OP_MOVE_WIDE: 'move-wide', OP_MOVE_OBJECT: 'move-object',
    OP_MOVE_RESULT: 'move-result',
    OP_RETURN_VOID: 'return-void', OP_RETURN: 'return',
    OP_RETURN_WIDE: 'return-wide', OP_RETURN_OBJECT: 'return-object',
    OP_CONST_4: 'const/4', OP_CONST_16: 'const/16', OP_CONST: 'const',
    OP_CONST_HIGH16: 'const/high16',
    OP_CONST_STRING: 'const-string', OP_CONST_CLASS: 'const-class',
    OP_MONITOR_ENTER: 'monitor-enter', OP_MONITOR_EXIT: 'monitor-exit',
    OP_CHECK_CAST: 'check-cast', OP_INSTANCE_OF: 'instance-of',
    OP_NEW_INSTANCE: 'new-instance', OP_NEW_ARRAY: 'new-array',
    OP_FILLED_NEW_ARRAY: 'filled-new-array',
    OP_FILL_ARRAY_DATA: 'fill-array-data',
    OP_THROW: 'throw',
    OP_GOTO: 'goto', OP_GOTO_16: 'goto/16', OP_GOTO_32: 'goto/32',
    OP_IF_EQ: 'if-eq', OP_IF_NE: 'if-ne', OP_IF_LT: 'if-lt',
    OP_IF_GE: 'if-ge', OP_IF_GT: 'if-gt', OP_IF_LE: 'if-le',
    OP_IF_EQZ: 'if-eqz', OP_IF_NEZ: 'if-nez', OP_IF_LTZ: 'if-ltz',
    OP_IF_GEZ: 'if-gez', OP_IF_GTZ: 'if-gtz', OP_IF_LEZ: 'if-lez',
    OP_AGET: 'aget', OP_APUT: 'aput',
    OP_IGET: 'iget', OP_IPUT: 'iput',
    OP_SGET: 'sget', OP_SPUT: 'sput',
    OP_INVOKE_VIRTUAL: 'invoke-virtual', OP_INVOKE_DIRECT: 'invoke-direct',
    OP_INVOKE_STATIC: 'invoke-static', OP_INVOKE_INTERFACE: 'invoke-interface',
    OP_NEG_INT: 'neg-int', OP_NOT_INT: 'not-int',
    OP_NEG_LONG: 'neg-long', OP_NOT_LONG: 'not-long',
    OP_INT_TO_LONG: 'int-to-long', OP_LONG_TO_INT: 'long-to-int',
    OP_ADD_INT: 'add-int', OP_SUB_INT: 'sub-int', OP_MUL_INT: 'mul-int',
    OP_DIV_INT: 'div-int', OP_REM_INT: 'rem-int',
    OP_AND_INT: 'and-int', OP_OR_INT: 'or-int', OP_XOR_INT: 'xor-int',
    OP_SHL_INT: 'shl-int', OP_SHR_INT: 'shr-int', OP_USHR_INT: 'ushr-int',
    OP_ADD_LONG: 'add-long', OP_SUB_LONG: 'sub-long',
    OP_MUL_LONG: 'mul-long', OP_DIV_LONG: 'div-long',
    OP_CMP_LONG: 'cmp-long',
    OP_ARRAY_LENGTH: 'array-length',
}


# ═══════════════════════════════════════════════════════════════════
# C++ Code Generator
# ═══════════════════════════════════════════════════════════════════

class Dex2CCodeGen:
    """Generate C++ JNI native functions from DEX bytecode."""

    def __init__(self, dex: DexParser):
        self.dex = dex
        self.method_entries: List[str] = []  # JNINativeMethod entries
        self.functions: List[str] = []       # C++ function bodies
        self.whitelisted: Set[int] = set()   # method indices to transpile

    def java_to_jni_name(self, class_name: str, method_name: str) -> str:
        """Convert Java class/method to JNI function name.
        com.lianyu.ai.security.NativeBridge.verifySignature →
        Java_com_lianyu_ai_security_VmpDex2cDispatcher_nativeVerifySignature
        """
        jni_class = class_name.replace('.', '_').replace('$', '_00024')
        # Always use VmpDex2cDispatcher as the holding class
        jni_name = f"Java_com_lianyu_ai_security_VmpDex2cDispatcher_native{method_name}"
        return jni_name

    def jni_signature(self, return_type: str, proto: dict) -> str:
        """Generate JNI type signature from DEX type descriptor."""
        # Simplified: map common types
        sig_map = {
            'V': 'V', 'Z': 'Z', 'B': 'B', 'S': 'S', 'C': 'C',
            'I': 'I', 'J': 'J', 'F': 'F', 'D': 'D',
            'Ljava/lang/String;': 'Ljava/lang/String;',
            'Ljava/lang/Object;': 'Ljava/lang/Object;',
        }
        shorty = proto['shorty']
        # Build JNI signature from shorty descriptor
        sig = "("
        for c in shorty[1:]:  # skip return type
            sig += c
        sig += ")" + shorty[0]
        return sig

    def generate_function_signature(self, jni_name: str, return_type: str,
                                     proto: dict, method_name: str) -> str:
        """Generate C++ function signature with JNI types."""
        cpp_return = 'void'
        return_prefix = ''

        if return_type == 'Z':
            cpp_return = 'jboolean'
            return_prefix = 'return JNI_FALSE;'
        elif return_type == 'I':
            cpp_return = 'jint'
            return_prefix = 'return 0;'
        elif return_type == 'J':
            cpp_return = 'jlong'
            return_prefix = 'return 0;'
        elif return_type == 'V':
            cpp_return = 'void'
        elif return_type.startswith('L') or return_type.startswith('['):
            cpp_return = 'jobject'
            return_prefix = 'return nullptr;'
        else:
            cpp_return = 'jobject'
            return_prefix = 'return nullptr;'

        # Build parameter list
        params = ["JNIEnv* env", "jclass cls"]

        shorty = proto['shorty']
        # Add parameters for each arg in shorty[1:]
        arg_idx = 0
        arg_names = ['env', 'cls']
        for c in shorty[1:]:
            arg_name = f"arg{arg_idx}"
            arg_names.append(arg_name)
            if c == 'Z':
                params.append(f"jboolean {arg_name}")
            elif c == 'I':
                params.append(f"jint {arg_name}")
            elif c == 'J':
                params.append(f"jlong {arg_name}")
            elif c == 'F':
                params.append(f"jfloat {arg_name}")
            elif c == 'D':
                params.append(f"jdouble {arg_name}")
            elif c == 'L':
                params.append(f"jobject {arg_name}")
            elif c == '[':
                params.append(f"jarray {arg_name}")
            else:
                params.append(f"jobject {arg_name}")
            arg_idx += 1

        param_str = ", ".join(params)

        return f"""JNIEXPORT {cpp_return} JNICALL
{jni_name}({param_str}) {{
    (void)env; (void)cls;
    // TODO: DEX bytecode translation for {method_name}
    // Registers: {arg_idx} params
    // Return type: {return_type}
    {return_prefix}
}}"""

    def generate_method_entry(self, java_name: str, jni_sig: str, jni_name: str) -> str:
        """Generate JNINativeMethod table entry."""
        return f'    {{"{java_name}", "{jni_sig}", (void*){jni_name}}}'

    def transpile_method(self, method_idx: int) -> Optional[str]:
        """Transpile a single method from DEX bytecode to C++.

        Returns the C++ function body as a string, or None if no code.
        """
        # P2-15: get method info FIRST to obtain return_type, then pass it to read_method_code
        info = self.dex.get_method_info(method_idx)
        return_type = info['return_type']
        code = self.dex.read_method_code(method_idx, return_type)
        if not code:
            print(f"  [skip] No code for method {method_idx}")
            return None

        class_name = info['class'][1:-1].replace('/', '.')
        jni_name = self.java_to_jni_name(class_name, info['name'])
        proto = info['proto']

        instructions = code.get('instructions', [])

        # Determine default return value for non-void methods
        default_return = ''
        if return_type == 'Z':   default_return = 'return JNI_FALSE;'
        elif return_type == 'I': default_return = 'return 0;'
        elif return_type == 'J': default_return = 'return 0;'
        elif return_type == 'F': default_return = 'return 0.0f;'
        elif return_type == 'D': default_return = 'return 0.0;'
        elif return_type == 'V': default_return = ''
        elif return_type.startswith('L') or return_type.startswith('['):
            default_return = 'return nullptr;'
        else: default_return = 'return nullptr;'

        # Generate function
        lines = []
        lines.append(f"/* ── Dex2C: {class_name}.{info['name']} ── */")
        lines.append(f"/*     Original DEX: {len(instructions)} instructions, "
                     f"{code['registers_size']} registers */")

        # Build function manually instead of string-replacing the template
        cpp_return = 'void'
        if return_type == 'Z': cpp_return = 'jboolean'
        elif return_type == 'I': cpp_return = 'jint'
        elif return_type == 'J': cpp_return = 'jlong'
        elif return_type == 'V': cpp_return = 'void'
        elif return_type.startswith('L') or return_type.startswith('['): cpp_return = 'jobject'
        else: cpp_return = 'jobject'

        # Build params
        params = ["JNIEnv* env", "jclass cls"]
        shorty = proto['shorty']
        for idx, c in enumerate(shorty[1:]):
            arg_name = f"arg{idx}"
            if c == 'Z': params.append(f"jboolean {arg_name}")
            elif c == 'I': params.append(f"jint {arg_name}")
            elif c == 'J': params.append(f"jlong {arg_name}")
            elif c == 'F': params.append(f"jfloat {arg_name}")
            elif c == 'D': params.append(f"jdouble {arg_name}")
            else: params.append(f"jobject {arg_name}")
        param_str = ", ".join(params)

        # Build function
        final = f"JNIEXPORT {cpp_return} JNICALL\n"
        final += f"{jni_name}({param_str}) {{\n"
        final += f"    // Dex2C transpiled from {class_name}.{info['name']}\n"
        final += f"    // DEX: {len(instructions)} instructions, {code['registers_size']} registers\n"

        # Insert translated instructions (pass return_type for return-void suppression)
        body_lines = self._translate_instructions(instructions, info, code, return_type)
        for bl in body_lines:
            final += f"    {bl}\n"

        # P2-15: always inject default return for non-void methods
        # R8 may inline bytecode that ends with return-void into a non-void method,
        # leaving no explicit return. The default return ensures the C++ compiles.
        if default_return:
            final += f"    // Dex2C: default return (method may not have explicit return after R8 inlining)\n"
            final += f"    {default_return}\n"

        final += "}\n"
        return final

    def _translate_instructions(self, instructions: List[dict],
                                 info: dict, code: dict, return_type: str = 'V') -> List[str]:
        """Translate DEX instructions to C++ statements.

        return_type: the DEX type descriptor of the method return type ('V'=void, 'L...;'=object, etc.)
        When return_type != 'V' and the bytecode ends with return-void (R8 optimization),
        the 'return;' statement is suppressed and the function falls through to a default
        return injected by transpile_method.

        This is a FRAMEWORK implementation — full opcode coverage requires
        extending the dispatch table below. TODO markers indicate where
        specific opcodes need implementation.
        """
        lines = []
        # Register tracking: v{N} → C++ local variable
        reg_count = code['registers_size']
        regs = [f"r{i}" for i in range(reg_count)]
        local_lines = []

        # Declare all registers upfront to avoid undeclared variable errors
        local_lines.append(f"// Registers: {', '.join(regs)}")
        for i in range(reg_count):
            local_lines.append(f"jint {regs[i]} = 0;  // v{i}")

        # Track register types for type-aware codegen
        reg_types = ['jint'] * reg_count

        # Label counter for branch targets
        label_counter = [0]
        labels = {}

        def get_label(offset):
            if offset not in labels:
                labels[offset] = f"label_{label_counter[0]}"
                label_counter[0] += 1
            return labels[offset]

        for inst in instructions:
            op = inst['opcode']
            off = inst['offset']
            comment = f"// 0x{off:04X}: {inst['name']}"

            if off in labels:
                lines.append(f"{labels[off]}:")

            if op == OP_NOP:
                pass

            elif op in (OP_RETURN_VOID,):
                # P2-15: suppress return; for non-void methods (R8 inlined bytecode)
                if return_type == 'V':
                    lines.append(f"{comment} → return;")
                    lines.append("return;")
                else:
                    lines.append(f"{comment} → return-void suppressed (method returns {return_type})")
                    lines.append(f"// return-void suppressed: non-void method, fall through to default return")

            elif op in (OP_RETURN,):
                a = inst.get('vA', 0)
                lines.append(f"{comment} → return {regs[a]};")
                lines.append(f"return (jint){regs[a]};")

            elif op in (OP_RETURN_OBJECT,):
                a = inst.get('vA', 0)
                lines.append(f"{comment} → return {regs[a]};")
                lines.append(f"return (jobject){regs[a]};")

            elif op == OP_CONST_4:
                a = inst.get('vA', 0)
                lit = inst.get('vB', 0)
                lines.append(f"{comment} → {regs[a]} = {lit};")
                lines.append(f"{regs[a]} = {lit};")
                reg_types[a] = 'jint'

            elif op == OP_CONST_16:
                a = inst.get('vA', 0)
                lit = inst.get('lit', 0)
                lines.append(f"{comment} → {regs[a]} = {lit};")
                lines.append(f"{regs[a]} = {lit};")
                reg_types[a] = 'jint'

            elif op == OP_CONST_STRING:
                # TODO: JNI NewStringUTF for const-string
                string_idx = inst.get('string_idx', 0)
                if string_idx < len(self.dex.strings):
                    s = self.dex.strings[string_idx]
                    lines.append(f"{comment} → {regs[inst.get('vA', 0)]} = env->NewStringUTF(\"{s}\");")
                    lines.append(f"{regs[inst.get('vA', 0)]} = env->NewStringUTF(\"{s}\");")
                else:
                    lines.append(f"{comment} → TODO: const-string idx={string_idx}")

            elif op in (OP_INVOKE_STATIC, OP_INVOKE_VIRTUAL, OP_INVOKE_DIRECT):
                midx = inst.get('method_idx', 0)
                if midx < len(self.dex.methods):
                    m = self.dex.methods[midx]
                    target_cls = m['class'][1:-1].replace('/', '.')
                    target = f"{target_cls}.{m['name']}"
                    lines.append(f"{comment} → TODO: call {target}")
                    lines.append(f"// TODO: invoke {target}")
                else:
                    lines.append(f"{comment} → TODO: invoke method_idx={midx}")

            elif op in (OP_IGET, OP_IPUT, OP_SGET, OP_SPUT):
                field_idx = inst.get('field_idx', 0)
                lines.append(f"{comment} → TODO: field access idx={field_idx}")

            elif op == OP_NEW_INSTANCE:
                type_idx = inst.get('type_idx', 0)
                if type_idx < len(self.dex.types):
                    t = self.dex.types[type_idx]
                    lines.append(f"{comment} → TODO: new {t}")
                else:
                    lines.append(f"{comment} → TODO: new-instance idx={type_idx}")

            elif op == OP_NEW_ARRAY:
                type_idx = inst.get('type_idx', 0)
                lines.append(f"{comment} → TODO: new-array type={type_idx}")

            elif op in (OP_AGET, OP_APUT):
                lines.append(f"{comment} → TODO: array access")

            elif op == OP_ARRAY_LENGTH:
                a = inst.get('vA', 0)
                b = inst.get('vB', 0)
                lines.append(f"{comment} → {regs[a]} = env->GetArrayLength({regs[b]});")
                lines.append(f"{regs[a]} = env->GetArrayLength((jarray){regs[b]});")

            elif op == OP_ADD_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{comment} → {regs[a]} += {regs[b]};")
                lines.append(f"{regs[a]} += {regs[b]};")

            elif op == OP_SUB_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{comment} → {regs[a]} -= {regs[b]};")
                lines.append(f"{regs[a]} -= {regs[b]};")

            elif op == OP_MUL_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{comment} → {regs[a]} *= {regs[b]};")
                lines.append(f"{regs[a]} *= {regs[b]};")

            elif op == OP_DIV_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"// Guard against div-by-zero")
                lines.append(f"if ({regs[b]} != 0) {regs[a]} /= {regs[b]};")
                lines.append(f"{comment}")

            elif op == OP_REM_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{regs[a]} %= {regs[b]};")
                lines.append(f"{comment}")

            elif op in (OP_AND_INT, OP_OR_INT, OP_XOR_INT):
                op_char = {'and': '&', 'or': '|', 'xor': '^'}[inst['name'].split('-')[0]]
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{regs[a]} {op_char}= {regs[b]};")
                lines.append(f"{comment}")

            elif op == OP_SHL_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{regs[a]} <<= ({regs[b]} & 0x1F);")
                lines.append(f"{comment}")

            elif op == OP_SHR_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{regs[a]} >>= ({regs[b]} & 0x1F);")
                lines.append(f"{comment}")

            elif op == OP_NEG_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{regs[a]} = -{regs[b]};")
                lines.append(f"{comment}")

            elif op == OP_NOT_INT:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{regs[a]} = ~{regs[b]};")
                lines.append(f"{comment}")

            elif op in (OP_ADD_LONG, OP_SUB_LONG, OP_MUL_LONG, OP_DIV_LONG):
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                op_char = {'add': '+', 'sub': '-', 'mul': '*', 'div': '/'}[inst['name'].split('-')[0]]
                lines.append(f"{comment} → TODO: long arithmetic ({op_char})")
                lines.append(f"// TODO: long {op_char}")

            elif op == OP_MOVE:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{regs[a]} = {regs[b]};")
                lines.append(f"{comment}")

            elif op == OP_MOVE_RESULT:
                a = inst.get('vA', 0)
                lines.append(f"{comment} → TODO: move-result")

            elif op == OP_CMP_LONG:
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                lines.append(f"{comment} → TODO: cmp-long")

            elif op == OP_GOTO:
                target = inst.get('target', 0)
                target_off = off + target
                label = get_label(target_off)
                lines.append(f"{comment} → goto {label};")
                lines.append(f"goto {label};")

            elif op == OP_GOTO_16:
                target = inst.get('target', 0)
                target_off = off + target
                label = get_label(target_off)
                lines.append(f"{comment} → goto {label};")
                lines.append(f"goto {label};")

            elif op in (OP_IF_EQZ, OP_IF_NEZ, OP_IF_LTZ, OP_IF_GEZ, OP_IF_GTZ, OP_IF_LEZ):
                a = inst.get('vA', 0)
                target = inst.get('target', 0)
                target_off = off + target
                label = get_label(target_off)
                cond = {'eqz': '== 0', 'nez': '!= 0', 'ltz': '< 0',
                        'gez': '>= 0', 'gtz': '> 0', 'lez': '<= 0'}[inst['name'].split('-')[1]]
                op_name = inst['name'].split('-')[1]
                cond_map = {'eqz': '== 0', 'nez': '!= 0', 'ltz': '< 0',
                           'gez': '>= 0', 'gtz': '> 0', 'lez': '<= 0'}
                cond_str = cond_map.get(op_name, '!= 0')
                lines.append(f"{comment} → if ({regs[a]} {cond_str}) goto {label};")
                lines.append(f"if ((int32_t){regs[a]} {cond_str}) {{ goto {label}; }}")

            elif op in (OP_IF_EQ, OP_IF_NE, OP_IF_LT, OP_IF_GE, OP_IF_GT, OP_IF_LE):
                a, b = inst.get('vA', 0), inst.get('vB', 0)
                target = inst.get('target', 0)
                target_off = off + target
                label = get_label(target_off)
                cond_map = {'eq': '==', 'ne': '!=', 'lt': '<',
                           'ge': '>=', 'gt': '>', 'le': '<='}
                op_name = inst['name'].split('-')[1]
                cond_str = cond_map.get(op_name, '!=')
                lines.append(f"{comment} → if ({regs[a]} {cond_str} {regs[b]}) goto {label};")
                lines.append(f"if ((int32_t){regs[a]} {cond_str} (int32_t){regs[b]}) {{ goto {label}; }}")

            elif op == OP_MONITOR_ENTER:
                a = inst.get('vA', 0)
                lines.append(f"{comment} → TODO: monitor-enter {regs[a]}")

            elif op == OP_MONITOR_EXIT:
                a = inst.get('vA', 0)
                lines.append(f"{comment} → TODO: monitor-exit {regs[a]}")

            elif op == OP_THROW:
                a = inst.get('vA', 0)
                lines.append(f"{comment} → TODO: throw {regs[a]}")

            elif op == OP_FILL_ARRAY_DATA:
                lines.append(f"{comment} → TODO: fill-array-data")

            else:
                lines.append(f"{comment} → TODO: unsupported opcode 0x{op:02X}")

        # Define any labels that were referenced but not placed inline
        for offset, lbl in sorted(labels.items()):
            if not any(lbl + ':' in l for l in lines):
                lines.append(f"{lbl}:  // {hex(offset)}")
                lines.append("    ;  // label placeholder")
        return local_lines + lines

    def generate_output(self, whitelist: List[str], output_cpp: str, output_h: str):
        """Generate the complete output files."""
        methods_found = []
        methods_generated = []

        for key in whitelist:
            key = key.strip()
            if not key or key.startswith('#'):
                continue
            midx = self.dex.find_method(key)
            if midx is None:
                print(f"[dex2c] WARNING: Method not found in DEX: {key}")
                continue
            methods_found.append((key, midx))
            print(f"[dex2c] Found: {key} (method_idx={midx})")

        # Generate C++ for each method
        functions = []
        entries = []
        for key, midx in methods_found:
            func = self.transpile_method(midx)
            if func:
                functions.append(func)
                # Generate JNI entry
                info = self.dex.get_method_info(midx)
                class_name = info['class'][1:-1].replace('/', '.')
                java_name = f"native{info['name']}"
                jni_name = self.java_to_jni_name(class_name, info['name'])
                jni_sig = self.jni_signature(info['return_type'], info['proto'])
                entry = self.generate_method_entry(java_name, jni_sig, jni_name)
                entries.append(entry)
                methods_generated.append((key, jni_name))
                print(f"[dex2c]   → {jni_name} ({len(functions[-1].split(chr(10)))} lines)")

        # Write CPP output
        cpp_lines = []
        cpp_lines.append("/*")
        cpp_lines.append(" * LianYu Dex2C Methods — AUTO-GENERATED by tools/dex2c_transpile.py")
        cpp_lines.append(" * DO NOT EDIT MANUALLY")
        cpp_lines.append(" */")
        cpp_lines.append("")
        cpp_lines.append('#include <jni.h>')
        cpp_lines.append('#include <cstring>')
        cpp_lines.append('#include <android/log.h>')
        cpp_lines.append("")
        cpp_lines.append('#define D2C_TAG "LianYu-Dex2C"')
        cpp_lines.append('#define D2C_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, D2C_TAG, __VA_ARGS__)')
        cpp_lines.append('#define D2C_LOGI(...) __android_log_print(ANDROID_LOG_INFO, D2C_TAG, __VA_ARGS__)')
        cpp_lines.append("")
        cpp_lines.append('#include "dex2c_registry.h"')
        cpp_lines.append("")
        cpp_lines.append("/* ═══════════════════════════════════════════════════════════")
        cpp_lines.append(f" * Generated: {len(methods_generated)} methods transpiled")
        cpp_lines.append(" * ═══════════════════════════════════════════════════════════ */")
        cpp_lines.append("")
        cpp_lines.append("// Dex2C .text CRC32 — set to 0xFFFFFFFF for runtime integrity placeholder")
        cpp_lines.append("// Patched by build pipeline after ndk-build completes")
        cpp_lines.append("const uint32_t gDex2cTextCrc32 = 0xFFFFFFFF;")
        cpp_lines.append("")

        # JNI_OnLoad
        cpp_lines.append("JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {")
        cpp_lines.append("    (void)reserved;")
        cpp_lines.append("")
        cpp_lines.append("    JNIEnv* env = nullptr;")
        cpp_lines.append("    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {")
        cpp_lines.append("        D2C_LOGE(\"JNI_OnLoad: GetEnv failed\");")
        cpp_lines.append("        return JNI_ERR;")
        cpp_lines.append("    }")
        cpp_lines.append("")
        cpp_lines.append("    jclass dispatcherClass = env->FindClass(\"com/lianyu/ai/security/VmpDex2cDispatcher\");")
        cpp_lines.append("    if (!dispatcherClass) {")
        cpp_lines.append("        D2C_LOGE(\"JNI_OnLoad: VmpDex2cDispatcher not found\");")
        cpp_lines.append("        env->ExceptionClear();")
        cpp_lines.append("        return JNI_VERSION_1_6;")
        cpp_lines.append("    }")
        cpp_lines.append("")
        cpp_lines.append("    if (env->RegisterNatives(dispatcherClass, gDex2cMethods, gDex2cMethodCount) != JNI_OK) {")
        cpp_lines.append("        D2C_LOGE(\"JNI_OnLoad: RegisterNatives failed\");")
        cpp_lines.append("        return JNI_ERR;")
        cpp_lines.append("    }")
        cpp_lines.append("")
        cpp_lines.append("    D2C_LOGI(\"JNI_OnLoad: registered %d Dex2C methods\", (int)gDex2cMethodCount);")
        cpp_lines.append("    return JNI_VERSION_1_6;")
        cpp_lines.append("}")
        cpp_lines.append("")

        # Function bodies
        for func in functions:
            cpp_lines.append(func)
            cpp_lines.append("")

        # Method table
        cpp_lines.append("/* ═══════════════════════════════════════════════════════════")
        cpp_lines.append(" * JNINativeMethod Registration Table")
        cpp_lines.append(" * ═══════════════════════════════════════════════════════════ */")
        cpp_lines.append("JNINativeMethod gDex2cMethods[] = {")
        for entry in entries:
            cpp_lines.append(entry + ",")
        cpp_lines.append("};")
        cpp_lines.append("")
        cpp_lines.append(f"const size_t gDex2cMethodCount = sizeof(gDex2cMethods) / sizeof(gDex2cMethods[0]);")

        with open(output_cpp, 'w') as f:
            f.write('\n'.join(cpp_lines))
        print(f"[dex2c] Wrote {output_cpp} ({len(cpp_lines)} lines)")

        # Write registry header
        h_lines = []
        h_lines.append("/*")
        h_lines.append(" * LianYu Dex2C Registry — AUTO-GENERATED")
        h_lines.append(f" * {len(methods_generated)} methods transpiled")
        h_lines.append(" */")
        h_lines.append("")
        h_lines.append("#ifndef DEX2C_REGISTRY_H")
        h_lines.append("#define DEX2C_REGISTRY_H")
        h_lines.append("")
        h_lines.append("#include <cstdint>")
        h_lines.append("#include <cstddef>")
        h_lines.append("")
        h_lines.append("extern JNINativeMethod gDex2cMethods[];")
        h_lines.append("extern const size_t gDex2cMethodCount;")
        h_lines.append("")
        h_lines.append("/* CRC32 of liblianyu_dex2c.so .text — patched by build pipeline */")
        h_lines.append("extern const uint32_t gDex2cTextCrc32;")
        h_lines.append("")
        # Compute a CRC32 of the generated CPP for build-time integrity
        cpp_hash = zlib.crc32('\n'.join(cpp_lines).encode('utf-8')) & 0xFFFFFFFF
        h_lines.append(f"/* Build-time CRC32 of dex2c_methods.cpp: 0x{cpp_hash:08X} */")
        h_lines.append(f"static const uint32_t gDex2cMethodCountExpected = {len(methods_generated)};")
        h_lines.append("")
        h_lines.append("#endif /* DEX2C_REGISTRY_H */")

        with open(output_h, 'w') as f:
            f.write('\n'.join(h_lines))
        print(f"[dex2c] Wrote {output_h} ({len(h_lines)} lines)")

        # Summary
        print(f"\n[dex2c] ═══ Summary ═══")
        print(f"[dex2c]   Whitelisted: {len(whitelist)} methods")
        print(f"[dex2c]   Found in DEX: {len(methods_found)} methods")
        print(f"[dex2c]   Transpiled: {len(methods_generated)} methods")
        for key, jni_name in methods_generated:
            print(f"[dex2c]     {key} → {jni_name}")


# ═══════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description='LianYu Dex2C Transpiler — DEX bytecode → C++ JNI native')
    parser.add_argument('dex_path', help='Path to classes.dex')
    parser.add_argument('--whitelist', '-w', default='tools/dex2c_whitelist.txt',
                        help='Path to whitelist file')
    parser.add_argument('--out-cpp', '-c',
                        default='core/security/src/main/cpp/generated/dex2c_methods.cpp',
                        help='Output C++ file')
    parser.add_argument('--out-h', '-H',
                        default='core/security/src/main/cpp/generated/dex2c_registry.h',
                        help='Output registry header')
    args = parser.parse_args()

    if not os.path.exists(args.dex_path):
        print(f"ERROR: DEX file not found: {args.dex_path}", file=sys.stderr)
        sys.exit(1)

    if not os.path.exists(args.whitelist):
        print(f"ERROR: Whitelist not found: {args.whitelist}", file=sys.stderr)
        print("Create it with one method per line:", file=sys.stderr)
        print("  com.lianyu.ai.security.NativeBridge.verifySignature", file=sys.stderr)
        sys.exit(1)

    # Parse DEX
    print(f"[dex2c] Loading DEX: {args.dex_path}")
    dex = DexParser(args.dex_path)

    # Load whitelist
    with open(args.whitelist, 'r') as f:
        whitelist = [l.strip() for l in f if l.strip() and not l.strip().startswith('#')]
    print(f"[dex2c] Whitelist: {len(whitelist)} methods from {args.whitelist}")

    # Generate output
    os.makedirs(os.path.dirname(args.out_cpp), exist_ok=True)
    gen = Dex2CCodeGen(dex)
    gen.generate_output(whitelist, args.out_cpp, args.out_h)

    print("\n[dex2c] ✅ Transpilation complete.")


if __name__ == '__main__':
    main()
