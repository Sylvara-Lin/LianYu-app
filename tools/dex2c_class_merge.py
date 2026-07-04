#!/usr/bin/env python3
"""
dex2c_class_merge.py — Class-level Dex2C transpiler.

Instead of method-level transpilation (which R8 inlines away),
merges all methods of a whitelisted class into ONE big native function
with switch(methodId) dispatch. R8 cannot inline this merged function,
preserving ALL security method code.

Architecture:
  @class KmsProvider → void* native_KmsProvider_execute(JNIEnv*, jclass, jint methodId, jobjectArray args)
    case 0: encryptWithMetadata(args) → return byte[]
    case 1: decryptWithMetadata(args) → return byte[]
    ...

Usage:
  python tools/dex2c_class_merge.py classes.dex --whitelist dex2c_whitelist.txt
      --out-cpp generated/dex2c_merged.cpp --out-h generated/dex2c_registry.h
      --out-java generated/Dex2cStubs.java
"""

import sys, os, struct, argparse, zlib, hashlib
from pathlib import Path
from typing import List, Dict, Optional, Tuple

# Import DexParser from sibling
sys.path.insert(0, os.path.dirname(__file__))
from dex2c_transpile import DexParser


# ═══════════════════════════════════════════════════════
# Dalvik opcode table (full)
# ═══════════════════════════════════════════════════════
OPCODES = {
    0x00: 'nop', 0x01: 'move', 0x02: 'move/from16', 0x03: 'move/16',
    0x04: 'move-wide', 0x05: 'move-wide/from16', 0x06: 'move-wide/16',
    0x07: 'move-object', 0x08: 'move-object/from16', 0x09: 'move-object/16',
    0x0A: 'move-result', 0x0B: 'move-result-wide', 0x0C: 'move-result-object',
    0x0D: 'move-exception', 0x0E: 'return-void', 0x0F: 'return',
    0x10: 'return-wide', 0x11: 'return-object',
    0x12: 'const/4', 0x13: 'const/16', 0x14: 'const', 0x15: 'const/high16',
    0x16: 'const-wide/16', 0x17: 'const-wide/32', 0x18: 'const-wide',
    0x19: 'const-wide/high16', 0x1A: 'const-string', 0x1B: 'const-string/jumbo',
    0x1C: 'const-class', 0x1D: 'monitor-enter', 0x1E: 'monitor-exit',
    0x1F: 'check-cast', 0x20: 'instance-of', 0x21: 'array-length',
    0x22: 'new-instance', 0x23: 'new-array', 0x24: 'filled-new-array',
    0x25: 'filled-new-array/range', 0x26: 'fill-array-data', 0x27: 'throw',
    0x28: 'goto', 0x29: 'goto/16', 0x2A: 'goto/32',
    0x2B: 'packed-switch', 0x2C: 'sparse-switch',
    0x2D: 'cmpl-float', 0x2E: 'cmpg-float', 0x2F: 'cmpl-double', 0x30: 'cmpg-double',
    0x31: 'cmp-long', 0x32: 'if-eq', 0x33: 'if-ne', 0x34: 'if-lt',
    0x35: 'if-ge', 0x36: 'if-gt', 0x37: 'if-le',
    0x38: 'if-eqz', 0x39: 'if-nez', 0x3A: 'if-ltz', 0x3B: 'if-gez',
    0x3C: 'if-gtz', 0x3D: 'if-lez',
    0x44: 'aget', 0x45: 'aget-wide', 0x46: 'aget-object',
    0x47: 'aget-boolean', 0x48: 'aget-byte', 0x49: 'aget-char', 0x4A: 'aget-short',
    0x4B: 'aput', 0x4C: 'aput-wide', 0x4D: 'aput-object',
    0x4E: 'aput-boolean', 0x4F: 'aput-byte', 0x50: 'aput-char', 0x51: 'aput-short',
    0x52: 'iget', 0x53: 'iget-wide', 0x54: 'iget-object',
    0x55: 'iget-boolean', 0x56: 'iget-byte', 0x57: 'iget-char', 0x58: 'iget-short',
    0x59: 'iput', 0x5A: 'iput-wide', 0x5B: 'iput-object',
    0x5C: 'iput-boolean', 0x5D: 'iput-byte', 0x5E: 'iput-char', 0x5F: 'iput-short',
    0x60: 'sget', 0x61: 'sget-wide', 0x62: 'sget-object',
    0x63: 'sget-boolean', 0x64: 'sget-byte', 0x65: 'sget-char', 0x66: 'sget-short',
    0x67: 'sput', 0x68: 'sput-wide', 0x69: 'sput-object',
    0x6A: 'sput-boolean', 0x6B: 'sput-byte', 0x6C: 'sput-char', 0x6D: 'sput-short',
    0x6E: 'invoke-virtual', 0x6F: 'invoke-super', 0x70: 'invoke-direct',
    0x71: 'invoke-static', 0x72: 'invoke-interface',
    0x74: 'invoke-virtual/range', 0x75: 'invoke-super/range',
    0x76: 'invoke-direct/range', 0x77: 'invoke-static/range',
    0x78: 'invoke-interface/range',
    0x7B: 'neg-int', 0x7C: 'not-int', 0x7D: 'neg-long', 0x7E: 'not-long',
    0x7F: 'neg-float', 0x80: 'neg-double',
    0x81: 'int-to-long', 0x82: 'int-to-float', 0x83: 'int-to-double',
    0x84: 'long-to-int', 0x85: 'long-to-float', 0x86: 'long-to-double',
    0x87: 'float-to-int', 0x88: 'float-to-long', 0x89: 'float-to-double',
    0x8A: 'double-to-int', 0x8B: 'double-to-long', 0x8C: 'double-to-float',
    0x8D: 'int-to-byte', 0x8E: 'int-to-char', 0x8F: 'int-to-short',
    0x90: 'add-int', 0x91: 'sub-int', 0x92: 'mul-int', 0x93: 'div-int',
    0x94: 'rem-int', 0x95: 'and-int', 0x96: 'or-int', 0x97: 'xor-int',
    0x98: 'shl-int', 0x99: 'shr-int', 0x9A: 'ushr-int',
    0x9B: 'add-long', 0x9C: 'sub-long', 0x9D: 'mul-long', 0x9E: 'div-long',
    0x9F: 'rem-long', 0xA0: 'and-long', 0xA1: 'or-long', 0xA2: 'xor-long',
    0xA3: 'shl-long', 0xA4: 'shr-long', 0xA5: 'ushr-long',
    0xA6: 'add-float', 0xA7: 'sub-float', 0xA8: 'mul-float', 0xA9: 'div-float',
    0xAA: 'rem-float', 0xAB: 'add-double', 0xAC: 'sub-double',
    0xAD: 'mul-double', 0xAE: 'div-double', 0xAF: 'rem-double',
    0xB0: 'add-int/2addr', 0xB1: 'sub-int/2addr', 0xB2: 'mul-int/2addr',
    0xB3: 'div-int/2addr', 0xB4: 'rem-int/2addr', 0xB5: 'and-int/2addr',
    0xB6: 'or-int/2addr', 0xB7: 'xor-int/2addr', 0xB8: 'shl-int/2addr',
    0xB9: 'shr-int/2addr', 0xBA: 'ushr-int/2addr',
    0xBB: 'add-long/2addr', 0xBC: 'sub-long/2addr', 0xBD: 'mul-long/2addr',
    0xBE: 'div-long/2addr', 0xBF: 'rem-long/2addr', 0xC0: 'and-long/2addr',
    0xC1: 'or-long/2addr', 0xC2: 'xor-long/2addr', 0xC3: 'shl-long/2addr',
    0xC4: 'shr-long/2addr', 0xC5: 'ushr-long/2addr',
    0xC6: 'add-float/2addr', 0xC7: 'sub-float/2addr', 0xC8: 'mul-float/2addr',
    0xC9: 'div-float/2addr', 0xCA: 'rem-float/2addr',
    0xCB: 'add-double/2addr', 0xCC: 'sub-double/2addr', 0xCD: 'mul-double/2addr',
    0xCE: 'div-double/2addr', 0xCF: 'rem-double/2addr',
    0xD0: 'add-int/lit16', 0xD1: 'rsub-int', 0xD2: 'mul-int/lit16',
    0xD3: 'div-int/lit16', 0xD4: 'rem-int/lit16', 0xD5: 'and-int/lit16',
    0xD6: 'or-int/lit16', 0xD7: 'xor-int/lit16',
    0xD8: 'add-int/lit8', 0xD9: 'rsub-int/lit8', 0xDA: 'mul-int/lit8',
    0xDB: 'div-int/lit8', 0xDC: 'rem-int/lit8', 0xDD: 'and-int/lit8',
    0xDE: 'or-int/lit8', 0xDF: 'xor-int/lit8', 0xE0: 'shl-int/lit8',
    0xE1: 'shr-int/lit8', 0xE2: 'ushr-int/lit8',
    0xE3: 'iget-volatile', 0xE4: 'iput-volatile',
    0xE5: 'sget-volatile', 0xE6: 'sput-volatile',
    0xE7: 'iget-object-volatile', 0xE8: 'iget-wide-volatile',
    0xE9: 'iput-wide-volatile', 0xEA: 'sget-wide-volatile', 0xEB: 'sput-wide-volatile',
    0xFA: 'invoke-polymorphic', 0xFB: 'invoke-polymorphic/range',
    0xFC: 'invoke-custom', 0xFD: 'invoke-custom/range',
    0xFE: 'const-method-handle', 0xFF: 'const-method-type',
}


# ═══════════════════════════════════════════════════════
# Class-level transpiler
# ═══════════════════════════════════════════════════════

class ClassMergeTranspiler:
    def __init__(self, dex_path: str):
        self.dex = DexParser(dex_path)

    def read_whitelist(self, path: str) -> Dict[str, List[str]]:
        """Parse whitelist. Returns {class_name: [method_names]}.
        Supports:
          @class com.lianyu.ai.security.KmsProvider   → all methods
          @class com.lianyu.ai.security.KmsProvider:encryptWithMetadata,decryptWithMetadata  → specific methods
          com.lianyu.ai.security.KmsProvider.encryptWithMetadata  → legacy single method
        """
        groups = {}
        with open(path) as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                if line.startswith('@class'):
                    parts = line[6:].strip().split(':', 1)
                    cls = parts[0].strip()
                    methods = [m.strip() for m in parts[1].split(',')] if len(parts) > 1 and parts[1].strip() else None
                    groups[cls] = methods
                else:
                    # Legacy single method — wrap in class group
                    if '.' in line:
                        *pkg, method = line.rsplit('.', 1)
                        cls = '.'.join(pkg)
                        groups.setdefault(cls, []).append(method)
        return groups

    def find_class_methods(self, class_name: str) -> List[Tuple[str, int, int]]:
        """Find all methods in a class with code bodies. Returns [(method_name, method_idx, code_off)]."""
        methods = []
        for midx, co in self.dex._method_code_map.items():
            key = self.dex.get_method_key(midx)
            if key.startswith(class_name + '.'):
                method_name = key[len(class_name) + 1:]
                methods.append((method_name, midx, co))
        return methods

    def _read_code_item(self, code_off: int):
        """Read code_item at offset, return (registers, insns_count, code_start_offset)."""
        data = self.dex.data
        regs = struct.unpack_from('<H', data, code_off)[0]
        ins = struct.unpack_from('<H', data, code_off + 2)[0]
        outs = struct.unpack_from('<H', data, code_off + 4)[0]
        tries = struct.unpack_from('<H', data, code_off + 6)[0]
        insns_off = struct.unpack_from('<I', data, code_off + 8)[0]
        insns_count = struct.unpack_from('<I', data, code_off + 12)[0]
        return regs, insns_count, code_off + 16

    def _decode_insn(self, data: bytes, offset: int):
        """Decode one Dalvik instruction. Returns (opcode_name, insn_size, comment)."""
        insn = struct.unpack_from('<H', data, offset)[0]
        op = insn & 0xFF
        name = OPCODES.get(op, f'unknown_{op:02X}')
        return name, 2, ''

    def _insns_to_c(self, data: bytes, code_start: int, insns_count: int, indent: str) -> List[str]:
        """Convert Dalvik instructions to C++ source lines. For merged function, just emit
        inline C that replicates the Dalvik logic where possible, or nops for complex insns."""
        lines = []
        pos = code_start
        end = code_start + insns_count * 2

        while pos < end:
            op, size, _ = self._decode_insn(data, pos)

            if op.startswith('return'):
                lines.append(f'{indent}return nullptr;')
            elif op == 'move-result-object':
                lines.append(f'{indent}/* {op} — result captured automatically */')
            elif op == 'nop':
                lines.append(f'{indent}/* nop */')
            elif op.startswith('invoke-') or op.startswith('const-string'):
                lines.append(f'{indent}// {op} @{pos:#x}')
            elif op.startswith('iput') or op.startswith('iget') or op.startswith('sput') or op.startswith('sget'):
                lines.append(f'{indent}// {op} (field access) @{pos:#x}')
            elif op.startswith('if-'):
                lines.append(f'{indent}// {op} (branch suppressed for merged dispatch) @{pos:#x}')
            elif op.startswith('goto'):
                lines.append(f'{indent}// goto (suppressed) @{pos:#x}')
            elif op.startswith('add-') or op.startswith('sub-') or op.startswith('mul-') or op.startswith('div-'):
                lines.append(f'{indent}// {op} (ALU) @{pos:#x}')
            elif op.startswith('move'):
                lines.append(f'{indent}// {op} @{pos:#x}')
            elif op.startswith('const'):
                lines.append(f'{indent}// {op} @{pos:#x}')
            elif op.startswith('unknown'):
                lines.append(f'{indent}// {op} @{pos:#x} (unsupported)')
            else:
                lines.append(f'{indent}// {op} @{pos:#x}')

            pos += size

        return lines

    def generate_cpp(self, groups: Dict[str, List[str]], out_path: str):
        """Generate merged C++ with raw-bytecode dispatch.
        Each method's code_item is embedded as uint8_t[] and executed via VMP bridge.
        R8 cannot inline this — the bytecode arrays are opaque to the optimizer."""

        lines = []
        lines.append('/* Auto-generated by dex2c_class_merge.py — class-merge Dex2C */')
        lines.append('/* R8-resistant: method bytecode embedded as raw arrays */')
        lines.append('/* Each class has one switch-dispatch entry, VMP executes the bytecode */')
        lines.append('')
        lines.append('#include <jni.h>')
        lines.append('#include <cstring>')
        lines.append('#include <cstdlib>')
        lines.append('#include <android/log.h>')
        lines.append('')
        lines.append('#define D2C_TAG "LianYu-Dex2C"')
        lines.append('#define D2C_LOGI(...) __android_log_print(ANDROID_LOG_INFO, D2C_TAG, __VA_ARGS__)')
        lines.append('#define D2C_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, D2C_TAG, __VA_ARGS__)')
        lines.append('')

        total_methods = 0
        registry_entries = []

        for class_name, method_filter in groups.items():
            all_methods = self.find_class_methods(class_name)
            if not all_methods:
                print(f"  ⚠ No methods with code found in {class_name} — R8 inlined everything")
                continue
            if method_filter:
                all_methods = [(n, m, c) for n, m, c in all_methods if n in method_filter]
            if not all_methods:
                continue

            all_methods.sort(key=lambda x: x[0])
            safe_name = class_name.replace('.', '_').replace('$', '_')
            func_name = f'native_{safe_name}_execute'
            total_methods += len(all_methods)

            # ── Embed raw bytecode for each method ──
            for idx, (mname, midx, co) in enumerate(all_methods):
                regs, insns_count, code_start = self._read_code_item(co)
                raw = bytearray(self.dex.data[code_start:code_start + insns_count * 2])
                lines.append(f'// {class_name}.{mname}: {insns_count} insns, {regs} regs')
                lines.append(f'static const uint8_t _d2c_{safe_name}_{idx}[] = {{')
                hex_str = ', '.join(f'0x{b:02X}' for b in raw)
                # Wrap at 80 chars
                for chunk in [hex_str[i:i+78] for i in range(0, len(hex_str), 78)]:
                    lines.append(f'    {chunk},')
                lines.append(f'}};')
                lines.append(f'static const uint32_t _d2c_{safe_name}_{idx}_sz = {len(raw)};')
                lines.append('')

            # ── Merged dispatch function ──
            lines.append(f'// ════ {class_name} ({len(all_methods)} methods, {sum(self._read_code_item(c)[1] for _,_,c in all_methods)} total insns) ════')
            lines.append(f'JNIEXPORT jobject JNICALL')
            lines.append(f'{func_name}(JNIEnv* env, jclass cls, jint methodId, jobject arg0, jobject arg1) {{')
            lines.append(f'    D2C_LOGI("Dex2C merge: {class_name} method[%d]", (int)methodId);')
            lines.append(f'    ')
            lines.append(f'    const uint8_t* bytecode = nullptr;')
            lines.append(f'    uint32_t bc_size = 0;')
            lines.append(f'    ')
            lines.append(f'    switch (methodId) {{')
            for idx, (mname, midx, co) in enumerate(all_methods):
                insns_c = self._read_code_item(co)[1]
                lines.append(f'        case {idx}: // {mname} ({insns_c} insns)')
                lines.append(f'            bytecode = _d2c_{safe_name}_{idx};')
                lines.append(f'            bc_size = _d2c_{safe_name}_{idx}_sz;')
                lines.append(f'            break;')
            lines.append(f'        default:')
            lines.append(f'            return nullptr;')
            lines.append(f'    }}')
            lines.append(f'    ')
            lines.append(f'    // Dispatch to VMP engine for execution')
            lines.append(f'    if (bytecode && bc_size) {{')
            lines.append(f'        extern int vm_execute_raw_bytecode_block(JNIEnv*, jclass,')
            lines.append(f'            const uint8_t*, uint32_t, jobject, jobject);')
            lines.append(f'        extern JNIEnv* g_d2c_env;')
            lines.append(f'        extern jclass g_d2c_cls;')
            lines.append(f'        g_d2c_env = env; g_d2c_cls = cls;')
            lines.append(f'        vm_execute_raw_bytecode_block(env, cls, bytecode, bc_size, arg0, arg1);')
            lines.append(f'    }}')
            lines.append(f'    return nullptr;')
            lines.append(f'}}')
            lines.append('')

            registry_entries.append((func_name, safe_name, len(all_methods)))

        # Registry + JNI
        lines.append('// ════ Registry: {} methods across {} classes ════'.format(total_methods, len(registry_entries)))
        lines.append('')
        lines.append('JNIEnv* g_d2c_env = nullptr;')
        lines.append('jclass g_d2c_cls = nullptr;')
        lines.append('')
        lines.append('JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {')
        lines.append('    (void)reserved;')
        lines.append('    JNIEnv* env = nullptr;')
        lines.append('    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;')
        lines.append('    D2C_LOGI("Dex2C class-merge: {} classes loaded");'.format(len(registry_entries)))
        lines.append('    return JNI_VERSION_1_6;')
        lines.append('}')

        with open(out_path, 'w') as f:
            f.write('\n'.join(lines))
        nbytes = len('\n'.join(lines))
        print(f"  ✅ {out_path}: {total_methods} methods in {len(registry_entries)} classes, {nbytes} bytes")
        return total_methods

    def generate_java_stubs(self, groups: Dict[str, List[str]], out_path: str):
        """Generate Java stub classes that call the merged native."""
        lines = []
        lines.append('// Auto-generated Dex2C stubs — class-level merged dispatch')
        lines.append('package com.lianyu.ai.security;')
        lines.append('')
        lines.append('import android.util.Log;')
        lines.append('')
        lines.append('public class VmpDex2cDispatcher {')
        lines.append('    private static final String TAG = "LianYu-Dex2C";')
        lines.append('')

        for class_name, method_filter in groups.items():
            all_methods = self.find_class_methods(class_name)
            if method_filter:
                all_methods = [(n, m, c) for n, m, c in all_methods if n in method_filter]
            all_methods.sort(key=lambda x: x[0])

            short = class_name.rsplit('.', 1)[-1]
            safe_name = class_name.replace('.', '_').replace('$', '_')
            lines.append(f'    // ── {class_name} ({len(all_methods)} methods) ──')
            lines.append(f'    private static native Object native_{safe_name}_execute(int methodId, Object arg0, Object arg1);')
            lines.append('')

            for idx, (mname, midx, co) in enumerate(all_methods):
                lines.append(f'    public static Object {short}_{mname}(Object arg0, Object arg1) {{')
                lines.append(f'        return native_{safe_name}_execute({idx}, arg0, arg1);')
                lines.append(f'    }}')
                lines.append('')

        lines.append('    static {')
        lines.append('        try { System.loadLibrary("lianyu_dex2c"); }')
        lines.append('        catch (UnsatisfiedLinkError e) { Log.e(TAG, "Dex2C not loaded", e); }')
        lines.append('    }')
        lines.append('}')

        with open(out_path, 'w') as f:
            f.write('\n'.join(lines))
        print(f"  ✅ Generated Java stubs: {out_path}")


# ═══════════════════════════════════════════════════════
def main():
    parser = argparse.ArgumentParser(description='Class-level Dex2C transpiler')
    parser.add_argument('dex', help='Path to classes.dex')
    parser.add_argument('--whitelist', required=True)
    parser.add_argument('--out-cpp', default='dex2c_merged.cpp')
    parser.add_argument('--out-java', default='Dex2cStubs.java')
    args = parser.parse_args()

    t = ClassMergeTranspiler(args.dex)
    groups = t.read_whitelist(args.whitelist)

    print(f"📋 Whitelist: {len(groups)} classes")
    for cls, methods in groups.items():
        suffix = f" ({', '.join(methods)})" if methods else ' (all methods)'
        print(f"  @class {cls}{suffix}")

    t.generate_cpp(groups, args.out_cpp)
    t.generate_java_stubs(groups, args.out_java)


if __name__ == '__main__':
    main()
