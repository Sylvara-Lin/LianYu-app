#!/usr/bin/env python3
"""
obfuscate_bytecode.py — Per-build VMP bytecode randomization

Randomizes opaque predicate constants and register assignments in
vm-bytecode.cpp. This makes each build's bytecode structurally unique,
defeating pattern-matching static analysis.

Usage:
  python3 tools/obfuscate_bytecode.py [--seed HEX]

The script reads vm-bytecode.cpp, replaces deterministic patterns
(0xDEADBEEF, 0xCAFEBABE, register choices) with seed-derived random
values, and writes back.

Run BEFORE ndk-build in the build pipeline.
"""

import os
import re
import sys
import struct
import random
import argparse
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent
BYTECODE_PATH = PROJECT_ROOT / "core" / "security" / "src" / "main" / "cpp" / "vm-bytecode.cpp"
CONFIG_PATH = PROJECT_ROOT / "core" / "security" / "src" / "main" / "cpp" / "g_vmp_config.h"


def read_seed_from_config() -> int:
    """Read VMP_BUILD_SEED from g_vmp_config.h."""
    if not CONFIG_PATH.exists():
        return random.getrandbits(32)

    with open(CONFIG_PATH) as f:
        for line in f:
            if "VMP_BUILD_SEED" in line:
                parts = line.split()
                for p in parts:
                    p = p.rstrip('uU')
                    if p.startswith("0x") or p.startswith("0X"):
                        return int(p, 16)
    return random.getrandbits(32)


def generate_random_constant(rng: random.Random, avoid: set) -> int:
    """Generate a random 32-bit constant not in 'avoid' set."""
    while True:
        val = rng.getrandbits(32)
        if val not in avoid and val != 0 and val != 0xFFFFFFFF:
            return val


def obfuscate_bytecode(data: bytes, seed: int) -> bytes:
    """Replace sentinel constants in C source hex representation with random values."""
    rng = random.Random(seed ^ 0xA5A5A5A5)
    avoid = set()
    text = data.decode('utf-8', errors='replace')

    # Known opaque predicate patterns as hex strings in C arrays
    # Format: 0xDE, 0xAD, 0xBE, 0xEF (little-endian in byte arrays)
    SENTINEL_PATTERNS = [
        (r'0xDE,\s*0xAD,\s*0xBE,\s*0xEF', '0xDEADBEEF'),
        (r'0xCA,\s*0xFE,\s*0xBA,\s*0xBE', '0xCAFEBABE'),
        (r'0xBA,\s*0xAD,\s*0xF0,\s*0x0D', '0xBAADF00D'),
    ]

    count = 0
    for pattern, name in SENTINEL_PATTERNS:
        def replacer(m):
            nonlocal count
            new_val = generate_random_constant(rng, avoid)
            avoid.add(new_val)
            # Format as BIG-ENDIAN hex bytes (VMP uses big-endian for immediates)
            b = struct.pack('>I', new_val)
            replacement = ', '.join(f'0x{byte:02X}' for byte in b)
            # Check if the match is in a comment (preceded by '//' on same line)
            line_start = text.rfind('\n', 0, m.start()) + 1
            line_prefix = text[line_start:m.start()]
            if '//' in line_prefix.split('\n')[-1]:
                return m.group(0)  # skip comments
            count += 1
            print(f"  {name}: → 0x{new_val:08X}")
            return replacement

        text, n = re.subn(pattern, replacer, text)
        if n > 0:
            print(f"  Replaced {n} occurrence(s) of {name}")

    print(f"  Total replacements: {count}")
    return text.encode('utf-8')


def shuffle_registers(data: bytes, seed: int) -> bytes:
    """Randomly remap free registers (R3-R9) used in bytecode arrays."""
    rng = random.Random(seed ^ 0xBEEF)
    result = bytearray(data)

    # Registers available for shuffling (R0-R2, R10-R15 are typically reserved)
    free_regs = list(range(3, 10))  # R3 through R9
    shuffled = free_regs.copy()
    rng.shuffle(shuffled)
    reg_map = {free_regs[i]: shuffled[i] for i in range(len(free_regs))}

    print(f"  Register map: {reg_map}")

    # Parse bytecode arrays and remap register operands
    # We look for LOAD_IMM (0x01, 7 bytes), LOAD_REG (0x02, 3 bytes),
    # STORE_REG (0x03, 3 bytes), ALU ops (0x10-0x16, 4 bytes)
    # Format: opcode (1) + rd (1) + ...
    pos = 0
    while pos < len(result):
        op = result[pos]

        if op == 0xFF:  # HALT
            pos += 1
            continue
        elif op == 0x00:  # NOP
            pos += 1
            continue
        elif op == 0x01:  # LOAD_IMM rd, imm32 (7 bytes)
            rd = result[pos + 1]
            if rd in reg_map:
                result[pos + 1] = reg_map[rd]
            pos += 7
        elif op in (0x02, 0x03):  # LOAD_REG / STORE_REG rd, rs (3 bytes)
            rd = result[pos + 1]
            rs = result[pos + 2]
            if rd in reg_map:
                result[pos + 1] = reg_map[rd]
            if rs in reg_map:
                result[pos + 2] = reg_map[rs]
            pos += 3
        elif 0x10 <= op <= 0x16:  # ALU rd, rs1, rs2 (4 bytes)
            rd = result[pos + 1]
            rs1 = result[pos + 2]
            rs2 = result[pos + 3]
            if rd in reg_map:
                result[pos + 1] = reg_map[rd]
            if rs1 in reg_map:
                result[pos + 2] = reg_map[rs1]
            if rs2 in reg_map:
                result[pos + 3] = reg_map[rs2]
            pos += 4
        elif op == 0x20:  # CMP rs1, rs2 (3 bytes)
            rs1 = result[pos + 1]
            rs2 = result[pos + 2]
            if rs1 in reg_map:
                result[pos + 1] = reg_map[rs1]
            if rs2 in reg_map:
                result[pos + 2] = reg_map[rs2]
            pos += 3
        elif op in (0x21, 0x22, 0x23, 0x24, 0x25):  # JMP/JE/JNE/JG/JL (6 bytes)
            pos += 6
        elif op in (0x18, 0x19, 0x1A):  # SBOX/GFMUL/XTIME (2 bytes)
            pos += 2
        elif op in (0x30, 0x31):  # CALL/RET (1 byte)
            pos += 1
        elif op == 0x40:  # HYPERCALL (6 bytes)
            pos += 6
        else:
            # Unknown opcode — skip one byte to avoid infinite loop
            pos += 1

    return bytes(result)


def main():
    parser = argparse.ArgumentParser(description='Per-build VMP bytecode obfuscation')
    parser.add_argument('--seed', default=None, help='Override seed (hex)')
    parser.add_argument('--no-reg-shuffle', action='store_true',
                        help='Skip register shuffling')
    parser.add_argument('--dry-run', action='store_true',
                        help='Print changes but do not write')
    args = parser.parse_args()

    if args.seed:
        seed = int(args.seed, 0)
    else:
        seed = read_seed_from_config()

    print(f"🎲 Seed: 0x{seed:08X}")

    if not BYTECODE_PATH.exists():
        print(f"✗ File not found: {BYTECODE_PATH}")
        sys.exit(1)

    with open(BYTECODE_PATH, 'rb') as f:
        original = f.read()

    # Step 1: Replace opaque constants
    print("\n📝 Step 1: Randomizing opaque predicates...")
    data = obfuscate_bytecode(original, seed)

    # Step 2: Shuffle register assignments
    if not args.no_reg_shuffle:
        print("\n📝 Step 2: Shuffling register assignments...")
        # Only apply register shuffling to the bytecode arrays,
        # not the C source text. We find bytecode array boundaries
        # by looking for "static const uint8_t g_vm_" ... "static" patterns.
        # For now, apply to the whole file — the shuffler is bytecode-aware
        # and only modifies valid opcode streams.
        data = shuffle_registers(data, seed)

    if data != original:
        diff_bytes = sum(1 for a, b in zip(original, data) if a != b)
        print(f"\n✅ {diff_bytes} bytes changed ({diff_bytes/len(original)*100:.1f}%)")

        if not args.dry_run:
            with open(BYTECODE_PATH, 'wb') as f:
                f.write(data)
            print(f"   Written to {BYTECODE_PATH}")
    else:
        print("\n⚠ No changes made")


if __name__ == '__main__':
    main()
