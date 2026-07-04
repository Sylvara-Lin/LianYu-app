#!/usr/bin/env python3
"""
Per-build VMP opcode mapping randomization.

Generates a random permutation of VM opcode values and applies it to:
  1. g_vmp_config.h — #define VMP_OP_* macros for the C++ compiler
  2. vm-bytecode.cpp — remap bytecode arrays to use new opcodes
  3. vm-engine.h — auto-adapts via g_vmp_config.h include

Usage:
  python tools/randomize_vmp_ops.py [--seed HEX]

Must run BEFORE ndk-build. Works with obfuscate_bytecode.py.
"""
import os, sys, struct, random, argparse, re
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
CONFIG_H = PROJECT / "core/security/src/main/cpp/g_vmp_config.h"
BYTECODE_CPP = PROJECT / "core/security/src/main/cpp/vm-bytecode.cpp"
VM_ENGINE_H = PROJECT / "core/security/src/main/cpp/vm-engine.h"

def read_text(path: Path) -> str:
    for encoding in ("utf-8", "gbk"):
        try:
            return path.read_text(encoding=encoding)
        except UnicodeDecodeError:
            continue
    return path.read_text(encoding="utf-8", errors="replace")

# Opcode names and their default values (current enum)
OPCODES = {
    "OP_NOP":       0x00,
    "OP_LOAD_IMM":  0x01,
    "OP_LOAD_REG":  0x02,
    "OP_STORE_REG": 0x03,
    "OP_LOAD_MEM":  0x04,
    "OP_STORE_MEM": 0x05,
    "OP_ADD":       0x10,
    "OP_SUB":       0x11,
    "OP_XOR":       0x12,
    "OP_AND":       0x13,
    "OP_OR":        0x14,
    "OP_SHL":       0x15,
    "OP_SHR":       0x16,
    "OP_ADD_IMM":   0x17,
    "OP_SBOX":      0x18,
    "OP_GFMUL":     0x19,
    "OP_XTIME":     0x1A,
    "OP_MUL":       0x1B,
    "OP_MUL_IMM":   0x1C,
    "OP_CMP":       0x20,
    "OP_JMP":       0x21,
    "OP_JE":        0x22,
    "OP_JNE":       0x23,
    "OP_JG":        0x24,
    "OP_JL":        0x25,
    "OP_CMP_IMM":   0x26,
    "OP_JGE":       0x27,
    "OP_CALL":      0x30,
    "OP_RET":       0x31,
    "OP_HYPERCALL": 0x32,
    "OP_HALT":      0xFF,
}

# OP_HALT stays at 0xFF (universal stop sentinel)
# OP_NOP can move but should stay simple


def generate_mapping(seed: int) -> dict:
    """Generate random opcode → new_value mapping."""
    rng = random.Random(seed ^ 0xDEADBEEF)
    
    # Get all opcodes except HALT (must stay 0xFF)
    movable = [k for k in OPCODES if k != "OP_HALT"]
    
    # Generate random values in range 0x01-0xFE (leave 0x00 and 0xFF)
    used = set()
    mapping = {}
    for name in movable:
        while True:
            val = rng.randint(0x01, 0xFE)
            if val not in used:
                used.add(val)
                mapping[name] = val
                break
    
    # HALT stays at 0xFF
    mapping["OP_HALT"] = 0xFF
    
    return mapping


def update_config_header(mapping: dict, seed: int):
    """Add VMP_OP_* defines to g_vmp_config.h."""
    content = read_text(CONFIG_H)
    
    # Remove any existing VMP_OP_* defines
    lines = content.split('\n')
    new_lines = []
    in_vmp_ops = False
    for line in lines:
        if line.strip().startswith('// VMP_OP_') or line.strip().startswith('#define VMP_OP_'):
            if not in_vmp_ops:
                in_vmp_ops = True
            continue
        if in_vmp_ops and line.strip() == '':
            continue
        in_vmp_ops = False
        new_lines.append(line)
    
    content = '\n'.join(new_lines).rstrip()
    
    # Append VMP_OP_* defines before #endif
    vmp_ops_block = "\n// ── Per-build VMP opcode mapping (randomized per APK) ──\n"
    vmp_ops_block += f"// Seed: 0x{seed:08X}\n"
    for name in sorted(mapping.keys()):
        vmp_ops_block += f"#define VMP_{name} 0x{mapping[name]:02X}\n"
    
    content = content.replace('\n#endif', f'\n{vmp_ops_block}\n#endif')
    
    CONFIG_H.write_text(content, encoding='utf-8')
    
    print(f"  Updated g_vmp_config.h with {len(mapping)} opcode mappings")


def update_vm_engine_h(mapping: dict):
    """Patch vm-engine.h to use VMP_OP_* defines instead of hardcoded enum."""
    content = read_text(VM_ENGINE_H)
    
    # Add include for g_vmp_config.h if not present
    if '#include "g_vmp_config.h"' not in content:
        content = content.replace(
            '#include <cstddef>',
            '#include <cstddef>\n#include "g_vmp_config.h"'
        )
    
    # Replace enum with conditional defines
    # The enum becomes: #ifdef VMP_OP_NOP → use defines, else → use defaults
    old_enum = content.find('enum VMOpcode')
    old_end = content.find('};', old_enum) + 2
    
    new_enum = """/* Opcodes — per-build randomized via VMP_OP_* defines in g_vmp_config.h */
#ifdef VMP_OP_NOP
  // Randomized opcodes from g_vmp_config.h
  #define OP_NOP       VMP_OP_NOP
  #define OP_LOAD_IMM  VMP_OP_LOAD_IMM
  #define OP_LOAD_REG  VMP_OP_LOAD_REG
  #define OP_STORE_REG VMP_OP_STORE_REG
  #define OP_LOAD_MEM  VMP_OP_LOAD_MEM
  #define OP_STORE_MEM VMP_OP_STORE_MEM
  #define OP_ADD       VMP_OP_ADD
  #define OP_SUB       VMP_OP_SUB
  #define OP_XOR       VMP_OP_XOR
  #define OP_AND       VMP_OP_AND
  #define OP_OR        VMP_OP_OR
  #define OP_SHL       VMP_OP_SHL
  #define OP_SHR       VMP_OP_SHR
  #define OP_ADD_IMM   VMP_OP_ADD_IMM
  #define OP_SBOX      VMP_OP_SBOX
  #define OP_GFMUL     VMP_OP_GFMUL
  #define OP_XTIME     VMP_OP_XTIME
  #define OP_MUL       VMP_OP_MUL
  #define OP_MUL_IMM   VMP_OP_MUL_IMM
  #define OP_CMP       VMP_OP_CMP
  #define OP_JMP       VMP_OP_JMP
  #define OP_JE        VMP_OP_JE
  #define OP_JNE       VMP_OP_JNE
  #define OP_JG        VMP_OP_JG
  #define OP_JL        VMP_OP_JL
  #define OP_CMP_IMM   VMP_OP_CMP_IMM
  #define OP_JGE       VMP_OP_JGE
  #define OP_CALL      VMP_OP_CALL
  #define OP_RET       VMP_OP_RET
  #define OP_HYPERCALL VMP_OP_HYPERCALL
  #define OP_HALT      VMP_OP_HALT
#else
  // Fallback: default opcodes
  enum VMOpcode : uint8_t {"""
    
    # Keep the original enum values as defaults
    default_lines = []
    for name in sorted(OPCODES.keys()):
        default_lines.append(f"    {name} = 0x{OPCODES[name]:02X},")
    
    new_enum += '\n'.join(default_lines) + '\n  };\n#endif'
    
    content = content[:old_enum] + new_enum + content[old_end:]
    
    VM_ENGINE_H.write_text(content, encoding='utf-8')
    
    print(f"  Updated vm-engine.h to use VMP_OP_* defines")


def remap_bytecodes(path: Path, mapping: dict):
    """Remap opcode bytes in vm-bytecode.cpp using the new mapping."""
    data = bytearray(path.read_bytes())
    
    # Build reverse map: old_value → new_value
    reverse_map = {}
    for name, new_val in mapping.items():
        old_val = OPCODES[name]
        reverse_map[old_val] = new_val
    
    # Only remap bytecode arrays (inside {...}), not C source code
    # Strategy: find bytecode array declarations and remap between the braces
    # Pattern: static const uint8_t g_vm_...[] = { 0x01, 0x00, ... };
    
    text = data.decode('utf-8', errors='replace')
    
    # Find all hex byte values in array initializers
    # Match: 0xXX where XX is a hex byte
    def remap_hex(m):
        val = int(m.group(1), 16)
        if val in reverse_map:
            return f'0x{reverse_map[val]:02X}'
        return m.group(0)
    
    # Only remap within array braces, not in comments
    # Simple approach: find array initializers by looking for = { ... }
    import re
    pattern = re.compile(r'(static\s+const\s+uint8_t\s+\w+\[\].*?=\s*\{)([^}]+)\}', re.DOTALL)
    
    def remap_array(m):
        prefix = m.group(1)
        body = m.group(2)
        # Remap hex bytes in body
        body = re.sub(r'0x([0-9A-Fa-f]{2})', remap_hex, body)
        return prefix + body + '}'
    
    text = pattern.sub(remap_array, text)
    
    path.write_bytes(text.encode('utf-8'))
    
    # Count changes
    changes = sum(1 for a, b in zip(data, text.encode('utf-8')) if a != b if len(data) == len(text.encode('utf-8')))
    print(f"  Remapped opcodes in vm-bytecode.cpp")


def main():
    parser = argparse.ArgumentParser(description='VMP opcode randomization')
    parser.add_argument('--seed', default=None, help='Override seed (hex)')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    
    if args.seed:
        seed = int(args.seed, 0)
    else:
        # Read from g_vmp_config.h or generate
        seed = random.getrandbits(32)
        if CONFIG_H.exists():
            with open(CONFIG_H, encoding='utf-8') as f:
                for line in f:
                    if 'VMP_BUILD_SEED' in line:
                        parts = line.split()
                        for p in parts:
                            p = p.rstrip('uU')
                            if p.startswith('0x'):
                                seed = int(p, 16)
                                break
    
    print(f"VMP Opcode Randomizer — Seed: 0x{seed:08X}")
    
    mapping = generate_mapping(seed)
    
    print("\nOpcode mapping:")
    for name in sorted(mapping.keys()):
        print(f"  {name:20s} 0x{OPCODES[name]:02X} → 0x{mapping[name]:02X}")
    
    if args.dry_run:
        print("\nDry run — no files modified")
        return
    
    update_config_header(mapping, seed)
    update_vm_engine_h(mapping)
    
    if BYTECODE_CPP.exists():
        remap_bytecodes(BYTECODE_CPP, mapping)
    
    print(f"\nDone. Rebuild ndk to apply randomized opcodes.")


if __name__ == '__main__':
    main()
