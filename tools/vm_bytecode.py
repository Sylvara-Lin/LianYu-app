#!/usr/bin/env python3
"""
LianYu VM Bytecode Compiler — Assembly → Bytecode.

Compiles VM assembly (.vmasm) into bytecode (.bin) and C header (.inc).

Usage:
    python3 tools/vm_bytecode.py compile input.vmasm -o output.inc
    python3 tools/vm_bytecode.py example        # print example
"""

import sys
import struct
import argparse

OPCODES = {
    'nop':      0x00,
    'load_imm': 0x01,  'li': 0x01,
    'load_reg': 0x02,  'lr': 0x02,
    'store_reg':0x03,  'sr': 0x03,
    'add':      0x10,  'sub': 0x11,  'xor': 0x12,
    'and':      0x13,  'or':  0x14,
    'shl':      0x15,  'shr': 0x16,
    'sbox':     0x18,  'gfmul': 0x19,  'xtime': 0x1A,
    'cmp':      0x20,  'jmp': 0x21,  'je': 0x22,
    'jne':      0x23,  'jg': 0x24,  'jl': 0x25,
    'call':     0x30,  'ret': 0x31,
    'halt':     0xFF,
}

REG_NAMES = {f'r{i}': i for i in range(16)}

def parse_reg(s):
    s = s.strip().lower()
    if s in REG_NAMES:
        return REG_NAMES[s]
    raise ValueError(f"Unknown register: {s}")

def parse_int(s):
    s = s.strip()
    if s.startswith('0x'):
        return int(s, 16)
    return int(s)

def assemble_line(line, labels, offset):
    """Assemble one line, return (bytes, new_offset) or raise."""
    line = line.split('#')[0].strip()
    if not line:
        return b'', offset

    parts = line.replace(',', ' ').split()
    mnemonic = parts[0].lower()

    if mnemonic.endswith(':'):
        return b'', offset  # label, already handled

    if mnemonic not in OPCODES:
        raise ValueError(f"Unknown opcode: {mnemonic}")

    op = OPCODES[mnemonic]
    args = parts[1:]

    if op == 0x00:  # nop
        return struct.pack('B', op), offset + 1

    elif op == 0x01:  # load_imm rd, imm
        rd = parse_reg(args[0])
        imm = parse_int(args[1])
        return struct.pack('BBBI', op, rd, 0, imm), offset + 7

    elif op in (0x02, 0x03):  # load_reg, store_reg: rd, rs
        rd = parse_reg(args[0])
        rs = parse_reg(args[1])
        return struct.pack('BBB', op, rd, rs), offset + 3

    elif op in (0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16):  # ALU rd, rs1, rs2
        rd = parse_reg(args[0])
        rs1 = parse_reg(args[1])
        rs2 = parse_reg(args[2])
        return struct.pack('BBBB', op, rd, rs1, rs2), offset + 4

    elif op in (0x18, 0x19, 0x1A):  # sbox/gfmul/xtime rd, rs
        rd = parse_reg(args[0])
        rs = parse_reg(args[1])
        return struct.pack('BBB', op, rd, rs), offset + 3

    elif op == 0x20:  # cmp rs1, rs2
        rs1 = parse_reg(args[0])
        rs2 = parse_reg(args[1])
        return struct.pack('BBB', op, rs1, rs2), offset + 3

    elif op in (0x21, 0x22, 0x23, 0x24, 0x25, 0x30):  # jump/call addr
        addr_str = args[0]
        if addr_str in labels:
            addr = labels[addr_str]
        else:
            addr = parse_int(addr_str)
        return struct.pack('BBI', op, 0, addr), offset + 6

    elif op == 0x31:  # ret
        return struct.pack('B', op), offset + 1

    elif op == 0xFF:  # halt
        return struct.pack('B', op), offset + 1

    else:
        raise ValueError(f"Cannot assemble opcode {op:02X}")


def assemble(source):
    """Assemble VM assembly source to bytecode."""
    lines = source.split('\n')

    # First pass: collect labels
    labels = {}
    offset = 0
    for line in lines:
        stripped = line.split('#')[0].strip()
        if not stripped:
            continue
        if stripped.endswith(':'):
            label = stripped[:-1].strip()
            labels[label] = offset
            continue
        try:
            _, offset = assemble_line(line, labels, offset)
        except Exception as e:
            raise ValueError(f"Error at offset {offset}: {e}")

    # Second pass: generate bytecode
    result = bytearray()
    offset = 0
    for line in lines:
        stripped = line.split('#')[0].strip()
        if not stripped:
            continue
        if stripped.endswith(':'):
            continue
        try:
            bc, offset = assemble_line(line, labels, offset)
            result.extend(bc)
        except Exception as e:
            raise ValueError(f"Error at offset {offset}: {e}")

    return bytes(result)


def format_c_array(data, name="g_vm_program"):
    """Format bytecode as C array declaration."""
    lines = [f'const uint8_t {name}[] = {{']
    for i in range(0, len(data), 16):
        chunk = data[i:i+16]
        hexs = ', '.join(f'0x{b:02X}' for b in chunk)
        lines.append(f'    {hexs},')
    lines.append('};')
    lines.append(f'const uint32_t {name}_size = sizeof({name});')
    return '\n'.join(lines) + '\n'


def generate_example():
    """Generate a VM assembly example: AES MixColumns on one column."""
    return """# LianYu VM Assembly Example
# AES MixColumns: one column
# Input: r1 = b0, r2 = b1, r3 = b2, r4 = b3
# Output: r5-r8 = mixed column

    load_imm r5, 0      # result[0] = 0
    load_imm r6, 0      # result[1] = 0
    load_imm r7, 0      # result[2] = 0
    load_imm r8, 0      # result[3] = 0

    # out[0] = xtime(b0) ^ gfmul(b1,3) ^ b2 ^ b3
    xtime   r9, r1
    gfmul   r10, r2, 3
    xor     r9, r9, r10
    xor     r9, r9, r3
    xor     r9, r9, r4
    store_reg r5, r9

    # out[1] = b0 ^ xtime(b1) ^ gfmul(b2,3) ^ b3
    xtime   r9, r2
    gfmul   r10, r3, 3
    xor     r9, r1, r9
    xor     r9, r9, r10
    xor     r9, r9, r4
    store_reg r6, r9

    halt
"""


def main():
    parser = argparse.ArgumentParser(description='VM Bytecode Compiler')
    parser.add_argument('action', choices=['compile', 'example', 'aes_demo'],
                       help='Action to perform')
    parser.add_argument('input', nargs='?', help='Input .vmasm file')
    parser.add_argument('-o', '--output', help='Output file (.inc or .bin)')

    args = parser.parse_args()

    if args.action == 'example':
        print(generate_example())
        return

    if args.action == 'aes_demo':
        # Generate AES S-Box lookup test
        asm = """# AES S-Box bytecode test
        load_imm r0, 0x42   # test input = 0x42
        sbox    r1, r0       # r1 = SBOX[0x42] = 0x2C
        halt
"""
        if args.output:
            bc = assemble(asm)
            c_code = format_c_array(bc, "g_vm_aes_demo")
            with open(args.output, 'w') as f:
                f.write(c_code)
            print(f"Generated: {args.output}")
        else:
            print(asm)
        return

    if args.action == 'compile':
        if not args.input:
            print("ERROR: input file required", file=sys.stderr)
            sys.exit(1)

        with open(args.input) as f:
            source = f.read()

        bc = assemble(source)

        if args.output:
            if args.output.endswith('.inc') or args.output.endswith('.h'):
                import os
                name = os.path.splitext(os.path.basename(args.input))[0]
                if not name.startswith('g_vm_'):
                    name = 'g_vm_' + name
                c_code = format_c_array(bc, name)
                with open(args.output, 'w') as f:
                    f.write(c_code)
                print(f"Generated C header: {args.output} ({len(bc)} bytes)")
            else:
                with open(args.output, 'wb') as f:
                    f.write(bc)
                print(f"Generated binary: {args.output} ({len(bc)} bytes)")
        else:
            # stdout
            sys.stdout.buffer.write(bc)


if __name__ == '__main__':
    main()
