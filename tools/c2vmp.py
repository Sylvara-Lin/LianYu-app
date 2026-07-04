#!/usr/bin/env python3
"""
c2vmp.py — VMP Bytecode Compiler for 360-style Virtualization (v3.0)

Full 28-instruction VMP ISA matching the vm-engine.cpp interpreter.
Generates bytecode that can be embedded directly into vm-bytecode.cpp
or exported as a C header for the generated/ directory.

Instruction Encoding (matching vm-engine.cpp):
  LOAD_IMM rd, imm:   6 bytes [0x01, rd, imm[4] BE]
  LOAD_REG rd, rs:    3 bytes [0x02, rd, rs, 0x00]
  STORE_REG rd, rs:   3 bytes [0x03, rd, rs, 0x00]
  LOAD_MEM rd, rs:    3 bytes [0x04, rd, rs, 0x00]
  STORE_MEM rd, rs:   3 bytes [0x05, rd, rs, 0x00]
  ADD rd,rs1,rs2:     3 bytes [0x10, rd, rs1, rs2]
  SUB rd,rs1,rs2:     3 bytes [0x11, rd, rs1, rs2]
  XOR rd,rs1,rs2:     3 bytes [0x12, rd, rs1, rs2]
  AND rd,rs1,rs2:     3 bytes [0x13, rd, rs1, rs2]
  OR  rd,rs1,rs2:     3 bytes [0x14, rd, rs1, rs2]
  SHL rd,rs1,rs2:     3 bytes [0x15, rd, rs1, rs2]
  SHR rd,rs1,rs2:     3 bytes [0x16, rd, rs1, rs2]
  ADD_IMM rd, imm:    6 bytes [0x17, rd, imm[4] BE]
  SBOX rd, rs:        3 bytes [0x18, rd, rs, 0x00]
  GFMUL rd,rs1,rs2:   3 bytes [0x19, rd, rs1, rs2]
  XTIME rd, rs:       3 bytes [0x1A, rd, rs, 0x00]
  MUL rd,rs1,rs2:     3 bytes [0x1B, rd, rs1, rs2]
  MUL_IMM rd, imm:    6 bytes [0x1C, rd, imm[4] BE]
  CMP rs1, rs2:       3 bytes [0x20, rs1, rs2, 0x00]
  CMP_IMM rs, imm:    6 bytes [0x26, rs, imm[4] BE]
  JMP addr:           5 bytes [0x21, addr[4] BE]
  JE  addr:           5 bytes [0x22, addr[4] BE]
  JNE addr:           5 bytes [0x23, addr[4] BE]
  JG  addr:           5 bytes [0x24, addr[4] BE]
  JL  addr:           5 bytes [0x25, addr[4] BE]
  JGE addr:           5 bytes [0x27, addr[4] BE]
  HYPERCALL fid,rd,rs1,rs2: 6 bytes [0x32, fid, rd, rs1, rs2, 0x00]
  HALT:               1 byte  [0xFF]

Usage:
  # Generate bytecode for all built-in programs
  python tools/c2vmp.py --all --output-dir core/security/src/main/cpp/generated/

  # Compile a custom VMP program written in Python DSL
  python tools/c2vmp.py --program my_program.py --output generated/
"""

import struct
import argparse
import os
import sys
import textwrap
from dataclasses import dataclass, field
from typing import List, Dict, Tuple, Optional, Callable


# ============================================================
# VMP Instruction Set (matching vm-engine.h VMOpcode enum)
# ============================================================

class VM:
    """VM instruction encoding matching vm-engine.cpp"""
    
    # Instruction sizes in bytes
    SIZES = {
        'NOP': 1, 'LOAD_IMM': 6, 'LOAD_REG': 3, 'STORE_REG': 3,
        'LOAD_MEM': 3, 'STORE_MEM': 3,
        'ADD': 3, 'SUB': 3, 'XOR': 3, 'AND': 3, 'OR': 3,
        'SHL': 3, 'SHR': 3, 'ADD_IMM': 6,
        'SBOX': 3, 'GFMUL': 3, 'XTIME': 3, 'MUL': 3, 'MUL_IMM': 6,
        'CMP': 3, 'CMP_IMM': 6,
        'JMP': 5, 'JE': 5, 'JNE': 5, 'JG': 5, 'JL': 5, 'JGE': 5,
        'HYPERCALL': 6, 'HALT': 1,
    }
    
    # Opcodes
    OP_NOP       = 0x00
    OP_LOAD_IMM  = 0x01
    OP_LOAD_REG  = 0x02
    OP_STORE_REG = 0x03
    OP_LOAD_MEM  = 0x04
    OP_STORE_MEM = 0x05
    OP_ADD  = 0x10
    OP_SUB  = 0x11
    OP_XOR  = 0x12
    OP_AND  = 0x13
    OP_OR   = 0x14
    OP_SHL  = 0x15
    OP_SHR  = 0x16
    OP_ADD_IMM = 0x17
    OP_SBOX  = 0x18
    OP_GFMUL = 0x19
    OP_XTIME = 0x1A
    OP_MUL   = 0x1B
    OP_MUL_IMM = 0x1C
    OP_CMP     = 0x20
    OP_CMP_IMM = 0x26
    OP_JMP  = 0x21
    OP_JE   = 0x22
    OP_JNE  = 0x23
    OP_JG   = 0x24
    OP_JL   = 0x25
    OP_JGE  = 0x27
    OP_HYPERCALL = 0x32
    OP_HALT = 0xFF

    # Hypercall function IDs (matching vm-engine.h)
    HC_READ_FILE     = 0
    HC_TRACER        = 1
    HC_DELAY         = 2
    HC_FRIDA         = 3
    HC_CRC32         = 4
    HC_ROOT_CHECK    = 5
    HC_KMS_STATUS    = 6
    HC_KMS_INIT      = 7
    HC_KDF_SM3       = 8
    HC_WB_AES_DEC    = 9
    HC_SM3_HASH      = 10
    HC_TEE_ATTEST    = 11
    HC_SIG_VERIFY    = 12
    HC_SECURE_WIPE   = 13
    HC_WB_AES_KEYCHECK = 14
    HC_SM4_KEY_EXPAND  = 15
    HC_SM4_DECRYPT_BLOCK = 16

    @staticmethod
    def _be4(v: int) -> List[int]:
        return [(v >> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF]

    # === Encoding Functions ===
    
    @staticmethod
    def nop() -> List[int]:
        return [VM.OP_NOP]

    @staticmethod
    def load_imm(rd: int, imm: int) -> List[int]:
        return [VM.OP_LOAD_IMM, rd & 0xF] + VM._be4(imm)

    @staticmethod
    def load_reg(rd: int, rs: int) -> List[int]:
        return [VM.OP_LOAD_REG, rd & 0xF, rs & 0xF]

    @staticmethod
    def store_reg(rd: int, rs: int) -> List[int]:
        return [VM.OP_STORE_REG, rd & 0xF, rs & 0xF]

    @staticmethod
    def load_mem(rd: int, rs: int) -> List[int]:
        return [VM.OP_LOAD_MEM, rd & 0xF, rs & 0xF]

    @staticmethod
    def store_mem(rd: int, rs: int) -> List[int]:
        return [VM.OP_STORE_MEM, rd & 0xF, rs & 0xF]

    @staticmethod
    def add(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_ADD, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def sub(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_SUB, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def xor(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_XOR, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def and_(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_AND, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def or_(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_OR, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def shl(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_SHL, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def shr(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_SHR, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def add_imm(rd: int, imm: int) -> List[int]:
        return [VM.OP_ADD_IMM, rd & 0xF] + VM._be4(imm)

    @staticmethod
    def sbox(rd: int, rs: int) -> List[int]:
        return [VM.OP_SBOX, rd & 0xF, rs & 0xF]

    @staticmethod
    def gfmul(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_GFMUL, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def xtime(rd: int, rs: int) -> List[int]:
        return [VM.OP_XTIME, rd & 0xF, rs & 0xF]

    @staticmethod
    def mul(rd: int, rs1: int, rs2: int) -> List[int]:
        return [VM.OP_MUL, rd & 0xF, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def mul_imm(rd: int, imm: int) -> List[int]:
        return [VM.OP_MUL_IMM, rd & 0xF] + VM._be4(imm)

    @staticmethod
    def cmp(rs1: int, rs2: int) -> List[int]:
        return [VM.OP_CMP, rs1 & 0xF, rs2 & 0xF]

    @staticmethod
    def cmp_imm(rs: int, imm: int) -> List[int]:
        return [VM.OP_CMP_IMM, rs & 0xF] + VM._be4(imm)

    @staticmethod
    def jmp(addr: int) -> List[int]:
        return [VM.OP_JMP] + VM._be4(addr)

    @staticmethod
    def je(addr: int) -> List[int]:
        return [VM.OP_JE] + VM._be4(addr)

    @staticmethod
    def jne(addr: int) -> List[int]:
        return [VM.OP_JNE] + VM._be4(addr)

    @staticmethod
    def jg(addr: int) -> List[int]:
        return [VM.OP_JG] + VM._be4(addr)

    @staticmethod
    def jl(addr: int) -> List[int]:
        return [VM.OP_JL] + VM._be4(addr)

    @staticmethod
    def jge(addr: int) -> List[int]:
        return [VM.OP_JGE] + VM._be4(addr)

    @staticmethod
    def hypercall(fid: int, rd: int, rs1: int = 0, rs2: int = 0) -> List[int]:
        return [VM.OP_HYPERCALL, fid & 0xFF, rd & 0xF, rs1 & 0xF, rs2 & 0xF, 0x00]

    @staticmethod
    def halt() -> List[int]:
        return [VM.OP_HALT]


# ============================================================
# VMP Assembler (label-based, 2-pass)
# ============================================================

class VmpAssembler:
    """Label-based VMP assembler with automatic address resolution."""
    
    def __init__(self):
        self.instructions: List[Tuple[str, List[int]]] = []  # (mnemonic, raw bytes or label ref)
        self.labels: Dict[str, int] = {}
        self.pending: List[Tuple[int, str, str]] = []  # (offset, branch_type, label_name)
    
    def _emit(self, mnemonic: str, data: List[int]):
        self.instructions.append((mnemonic, data))
    
    def label(self, name: str):
        self.labels[name] = -1  # placeholder, resolved in pass 1
    
    # === Convenience Methods ===
    def nop(self): self._emit('NOP', VM.nop())
    def load_imm(self, rd, imm): self._emit('LOAD_IMM', VM.load_imm(rd, imm))
    def load_reg(self, rd, rs): self._emit('LOAD_REG', VM.load_reg(rd, rs))
    def store_reg(self, rd, rs): self._emit('STORE_REG', VM.store_reg(rd, rs))
    def load_mem(self, rd, rs): self._emit('LOAD_MEM', VM.load_mem(rd, rs))
    def store_mem(self, rd, rs): self._emit('STORE_MEM', VM.store_mem(rd, rs))
    def add(self, rd, rs1, rs2): self._emit('ADD', VM.add(rd, rs1, rs2))
    def sub(self, rd, rs1, rs2): self._emit('SUB', VM.sub(rd, rs1, rs2))
    def xor(self, rd, rs1, rs2): self._emit('XOR', VM.xor(rd, rs1, rs2))
    def and_(self, rd, rs1, rs2): self._emit('AND', VM.and_(rd, rs1, rs2))
    def or_(self, rd, rs1, rs2): self._emit('OR', VM.or_(rd, rs1, rs2))
    def shl(self, rd, rs1, rs2): self._emit('SHL', VM.shl(rd, rs1, rs2))
    def shr(self, rd, rs1, rs2): self._emit('SHR', VM.shr(rd, rs1, rs2))
    def add_imm(self, rd, imm): self._emit('ADD_IMM', VM.add_imm(rd, imm))
    def sbox(self, rd, rs): self._emit('SBOX', VM.sbox(rd, rs))
    def gfmul(self, rd, rs1, rs2): self._emit('GFMUL', VM.gfmul(rd, rs1, rs2))
    def xtime(self, rd, rs): self._emit('XTIME', VM.xtime(rd, rs))
    def mul(self, rd, rs1, rs2): self._emit('MUL', VM.mul(rd, rs1, rs2))
    def mul_imm(self, rd, imm): self._emit('MUL_IMM', VM.mul_imm(rd, imm))
    def cmp(self, rs1, rs2): self._emit('CMP', VM.cmp(rs1, rs2))
    def cmp_imm(self, rs, imm): self._emit('CMP_IMM', VM.cmp_imm(rs, imm))
    def hypercall(self, fid, rd=0, rs1=0, rs2=0):
        self._emit('HYPERCALL', VM.hypercall(fid, rd, rs1, rs2))
    def halt(self): self._emit('HALT', VM.halt())
    
    # Branch instructions — record as pending for label resolution
    def _branch(self, mnemonic: str, label_name: str):
        offset = sum(VM.SIZES[mn] for mn, _ in self.instructions)
        self.pending.append((offset, mnemonic, label_name))
        self._emit(mnemonic, [0x00] * VM.SIZES[mnemonic])  # placeholder
    
    def jmp(self, target): self._branch('JMP', target)
    def je(self, target): self._branch('JE', target)
    def jne(self, target): self._branch('JNE', target)
    def jg(self, target): self._branch('JG', target)
    def jl(self, target): self._branch('JL', target)
    def jge(self, target): self._branch('JGE', target)
    
    def assemble(self) -> bytes:
        """2-pass assembly: resolve labels, emit bytecode."""
        # Pass 1: compute label addresses
        addr = 0
        for mnemonic, data in self.instructions:
            if mnemonic == 'LABEL':
                pass  # handled by label() method
            addr += VM.SIZES.get(mnemonic, len(data))
        
        # Map labels: need to re-scan for label positions
        # Actually labels are stored in self.labels, we need to compute their addresses
        addr = 0
        for mnemonic, data in self.instructions:
            # Check if this instruction is at a label
            for lbl_name, lbl_addr in list(self.labels.items()):
                if lbl_addr == -1 and addr == self._label_positions.get(lbl_name, -1):
                    self.labels[lbl_name] = addr
            addr += VM.SIZES.get(mnemonic, len(data))
        
        # Pass 2: emit bytecode with resolved branches
        bytecode = bytearray()
        for i, (mnemonic, data) in enumerate(self.instructions):
            if mnemonic in ('JMP', 'JE', 'JNE', 'JG', 'JL', 'JGE'):
                # Replace placeholder with resolved address
                target = self._pending_target(i)
                if target is not None:
                    target_addr = self.labels.get(target)
                    if target_addr is None:
                        raise ValueError(f"Undefined label: {target}")
                    if mnemonic == 'JMP':
                        resolved = VM.jmp(target_addr)
                    elif mnemonic == 'JE':
                        resolved = VM.je(target_addr)
                    elif mnemonic == 'JNE':
                        resolved = VM.jne(target_addr)
                    elif mnemonic == 'JG':
                        resolved = VM.jg(target_addr)
                    elif mnemonic == 'JL':
                        resolved = VM.jl(target_addr)
                    elif mnemonic == 'JGE':
                        resolved = VM.jge(target_addr)
                    else:
                        resolved = data
                    bytecode.extend(resolved)
                else:
                    bytecode.extend(data)
            else:
                bytecode.extend(data)
        
        return bytes(bytecode)
    
    def _pending_target(self, instr_idx: int) -> Optional[str]:
        """Find the label target for a pending branch at instruction index."""
        instr_offset = sum(VM.SIZES[mn] for j, (mn, _) in enumerate(self.instructions) if j < instr_idx)
        for offset, mnemonic, label_name in self.pending:
            if offset == instr_offset:
                return label_name
        return None
    
    def _compute_label_addresses(self):
        """Compute addresses for all labels."""
        self._label_positions = {}
        addr = 0
        lbl_idx = 0
        lbl_names = list(self.labels.keys())
        for mnemonic, data in self.instructions:
            if lbl_idx < len(lbl_names) and self.labels[lbl_names[lbl_idx]] == -1:
                self._label_positions[lbl_names[lbl_idx]] = addr
                lbl_idx += 1
            addr += VM.SIZES.get(mnemonic, len(data))


# ============================================================
# Simplified DSL Assembler (label-based, 2-pass resolution)
# ============================================================

class VmpProgram:
    """Simplified VMP program builder with automatic label resolution.
    
    Usage:
        p = VmpProgram()
        p.load_imm(1, 0xDEADBEEF)
        p.cmp(1, 2)
        p.jne('real')
        p.halt()
        p.label('real')
        p.hypercall(VM.HC_TRACER, 0)
        p.halt()
        bytecode = p.assemble()
    """
    
    def __init__(self, name: str = "unnamed"):
        self.name = name
        self._ops: List[tuple] = []  # (mnemonic, args) or ('LABEL', name)
        self._labels: Dict[str, int] = {}
    
    def label(self, name: str):
        self._ops.append(('LABEL', name))
    
    def _op(self, mnemonic: str, data: List[int]):
        self._ops.append((mnemonic, data))
    
    # Convenience
    def nop(self): self._op('NOP', VM.nop())
    def load_imm(self, rd, imm): self._op('LOAD_IMM', VM.load_imm(rd, imm))
    def load_reg(self, rd, rs): self._op('LOAD_REG', VM.load_reg(rd, rs))
    def store_reg(self, rd, rs): self._op('STORE_REG', VM.store_reg(rd, rs))
    def load_mem(self, rd, rs): self._op('LOAD_MEM', VM.load_mem(rd, rs))
    def store_mem(self, rd, rs): self._op('STORE_MEM', VM.store_mem(rd, rs))
    def add(self, rd, rs1, rs2): self._op('ADD', VM.add(rd, rs1, rs2))
    def sub(self, rd, rs1, rs2): self._op('SUB', VM.sub(rd, rs1, rs2))
    def xor(self, rd, rs1, rs2): self._op('XOR', VM.xor(rd, rs1, rs2))
    def and_(self, rd, rs1, rs2): self._op('AND', VM.and_(rd, rs1, rs2))
    def or_(self, rd, rs1, rs2): self._op('OR', VM.or_(rd, rs1, rs2))
    def shl(self, rd, rs1, rs2): self._op('SHL', VM.shl(rd, rs1, rs2))
    def shr(self, rd, rs1, rs2): self._op('SHR', VM.shr(rd, rs1, rs2))
    def add_imm(self, rd, imm): self._op('ADD_IMM', VM.add_imm(rd, imm))
    def sbox(self, rd, rs): self._op('SBOX', VM.sbox(rd, rs))
    def gfmul(self, rd, rs1, rs2): self._op('GFMUL', VM.gfmul(rd, rs1, rs2))
    def xtime(self, rd, rs): self._op('XTIME', VM.xtime(rd, rs))
    def mul(self, rd, rs1, rs2): self._op('MUL', VM.mul(rd, rs1, rs2))
    def mul_imm(self, rd, imm): self._op('MUL_IMM', VM.mul_imm(rd, imm))
    def cmp(self, rs1, rs2): self._op('CMP', VM.cmp(rs1, rs2))
    def cmp_imm(self, rs, imm): self._op('CMP_IMM', VM.cmp_imm(rs, imm))
    def hypercall(self, fid, rd=0, rs1=0, rs2=0):
        self._op('HYPERCALL', VM.hypercall(fid, rd, rs1, rs2))
    def halt(self): self._op('HALT', VM.halt())
    
    def jmp(self, target): self._op('JMP', target)
    def je(self, target): self._op('JE', target)
    def jne(self, target): self._op('JNE', target)
    def jg(self, target): self._op('JG', target)
    def jl(self, target): self._op('JL', target)
    def jge(self, target): self._op('JGE', target)
    
    def assemble(self) -> bytes:
        """2-pass: compute addresses, then emit resolved bytecode."""
        # Pass 1: compute label addresses
        addr = 0
        for op in self._ops:
            kind = op[0]
            if kind == 'LABEL':
                self._labels[op[1]] = addr
            elif kind in ('JMP', 'JE', 'JNE', 'JG', 'JL', 'JGE'):
                addr += VM.SIZES[kind]
            else:
                addr += len(op[1])  # pre-encoded bytes
        
        # Pass 2: emit
        bc = bytearray()
        for op in self._ops:
            kind = op[0]
            if kind == 'LABEL':
                continue
            elif kind in ('JMP', 'JE', 'JNE', 'JG', 'JL', 'JGE'):
                target = op[1]
                target_addr = self._labels.get(target)
                if target_addr is None:
                    raise ValueError(f"Undefined label: {target}")
                if kind == 'JMP':
                    bc.extend(VM.jmp(target_addr))
                elif kind == 'JE':
                    bc.extend(VM.je(target_addr))
                elif kind == 'JNE':
                    bc.extend(VM.jne(target_addr))
                elif kind == 'JG':
                    bc.extend(VM.jg(target_addr))
                elif kind == 'JL':
                    bc.extend(VM.jl(target_addr))
                elif kind == 'JGE':
                    bc.extend(VM.jge(target_addr))
            else:
                bc.extend(op[1])
        return bytes(bc)
    
    def to_c_array(self) -> str:
        """Generate C const uint8_t array declaration."""
        bc = self.assemble()
        hex_lines = []
        for i in range(0, len(bc), 12):
            chunk = bc[i:i+12]
            hex_lines.append('    ' + ', '.join(f'0x{b:02X}' for b in chunk) + ',')
        
        name = self.name
        return textwrap.dedent(f'''\
        /* {name} ({len(bc)} bytes) — auto-generated by c2vmp.py */
        const uint8_t {name}[] = {{
        {chr(10).join(hex_lines)}
        }};
        const uint32_t {name}_size = sizeof({name});
        ''')


# ============================================================
# Built-in VMP Security Programs
# ============================================================

def build_root_detect() -> VmpProgram:
    """Root + debug detection inside VM."""
    p = VmpProgram("g_vmp_root_detect")
    # Opaque predicate
    p.load_imm(1, 0xDEADBEEF)
    p.load_imm(2, 0xCAFEBABE)
    p.cmp(1, 2)
    p.jne('real')
    p.halt()  # dead
    p.label('real')
    p.load_imm(0, 0)           # R0 = 0 (clean default)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)  # TRACER → R14
    p.cmp_imm(14, 0)
    p.jne('exit')              # traced → exit
    p.hypercall(VM.HC_ROOT_CHECK, 0, 0, 0)  # ROOT_CHECK → R0
    p.label('exit')
    p.halt()
    return p

def build_code_integrity() -> VmpProgram:
    """.text CRC32 self-check inside VM. R12 = expected CRC."""
    p = VmpProgram("g_vmp_code_integrity")
    p.load_imm(1, 0xCAFEBABE)
    p.load_imm(2, 0xDEADBEEF)
    p.cmp(1, 2)
    p.jne('real')
    p.load_imm(0, 0)
    p.halt()  # dead
    p.label('real')
    p.load_imm(0, 0xFF)         # R0 = 0xFF (error marker)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')               # traced → exit
    p.hypercall(VM.HC_CRC32, 3, 0, 0)  # CRC32 → R3
    p.cmp(3, 12)                # R3 == R12 (expected)?
    p.jne('exit')               # mismatch → exit
    p.load_imm(0, 1)            # R0 = 1 (ok)
    p.label('exit')
    p.halt()
    return p

def build_sm3_hash() -> VmpProgram:
    """SM3 hash inside VM. R12=data_ptr, R13=data_len."""
    p = VmpProgram("g_vmp_sm3_hash")
    p.load_imm(0, 0)            # R0 = 0 (error default)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')               # traced → exit
    p.hypercall(VM.HC_SM3_HASH, 0, 12, 13)  # SM3_HASH(R12,R13) → R0
    p.label('exit')
    p.halt()
    return p

def build_frida_heartbeat() -> VmpProgram:
    """Combined Frida+CRC32 heartbeat."""
    p = VmpProgram("g_vmp_frida_heartbeat")
    p.hypercall(VM.HC_FRIDA, 1, 0, 0)  # FRIDA → R1
    p.cmp_imm(1, 0)
    p.jne('frida_hit')
    p.hypercall(VM.HC_CRC32, 0, 0, 0)   # CRC32 → R0
    p.halt()
    p.label('frida_hit')
    p.load_imm(0, 0xFFFFFFFF)
    p.halt()
    return p

def build_wb_aes_keycheck() -> VmpProgram:
    """WB-AES table integrity check."""
    p = VmpProgram("g_vmp_wb_aes_keycheck")
    p.load_imm(1, 0xDEADBEEF)
    p.load_imm(2, 0xCAFEBABE)
    p.cmp(1, 2)
    p.jne('real')
    p.halt()  # dead
    p.label('real')
    p.load_imm(0, 0)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')
    p.hypercall(VM.HC_WB_AES_KEYCHECK, 0, 0, 0)
    p.label('exit')
    p.halt()
    return p

def build_kms_derive_sk() -> VmpProgram:
    """KMS DK→SK derivation inside VM. R12=ctx_ptr, R13=ctx_len."""
    p = VmpProgram("g_vmp_kms_derive_sk")
    p.load_imm(1, 0xA59CB610)
    p.load_imm(2, 0x8F554437)
    p.cmp(1, 2)
    p.jne('real')
    p.halt()  # dead
    p.label('real')
    p.load_imm(0, 0)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')
    p.hypercall(VM.HC_ROOT_CHECK, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')
    p.hypercall(VM.HC_KMS_STATUS, 14, 0, 0)
    p.cmp_imm(14, 1)           # KMS ready?
    p.je('skip_init')
    p.hypercall(VM.HC_KMS_INIT, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.je('exit')               # init failed → exit
    p.label('skip_init')
    p.hypercall(VM.HC_KDF_SM3, 0, 12, 13)  # KDF_SM3(ctx_ptr, ctx_len) → R0
    p.label('exit')
    p.halt()
    return p

def build_tee_attest() -> VmpProgram:
    """TEE attestation inside VM."""
    p = VmpProgram("g_vmp_tee_attest")
    p.load_imm(1, 0xC9EB1687)
    p.load_imm(2, 0x919D0F13)
    p.cmp(1, 2)
    p.jne('real')
    p.halt()  # dead
    p.label('real')
    p.load_imm(0, 0)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')
    p.hypercall(VM.HC_TEE_ATTEST, 0, 0, 0)
    p.label('exit')
    p.halt()
    return p

def build_apk_sig_verify() -> VmpProgram:
    """APK V2/V3 signature verification inside VM."""
    p = VmpProgram("g_vmp_apk_sig_verify")
    p.load_imm(1, 0x03FE6B63)
    p.load_imm(2, 0xBFCB470B)
    p.cmp(1, 2)
    p.jne('real')
    p.halt()  # dead
    p.label('real')
    p.load_imm(0, 0)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')
    p.hypercall(VM.HC_ROOT_CHECK, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')
    p.hypercall(VM.HC_SIG_VERIFY, 0, 0, 0)
    p.label('exit')
    p.halt()
    return p

def build_secure_wipe() -> VmpProgram:
    """Secure memory wipe inside VM. R12=ptr, R13=len."""
    p = VmpProgram("g_vmp_secure_wipe")
    p.load_imm(0, 0)
    p.hypercall(VM.HC_TRACER, 14, 0, 0)
    p.cmp_imm(14, 0)
    p.jne('exit')
    p.hypercall(VM.HC_SECURE_WIPE, 0, 12, 13)
    p.label('exit')
    p.halt()
    return p


# ============================================================
# Program Registry
# ============================================================

PROGRAMS: Dict[str, Callable[[], VmpProgram]] = {
    "g_vmp_root_detect": build_root_detect,
    "g_vmp_code_integrity": build_code_integrity,
    "g_vmp_sm3_hash": build_sm3_hash,
    "g_vmp_frida_heartbeat": build_frida_heartbeat,
    "g_vmp_wb_aes_keycheck": build_wb_aes_keycheck,
    "g_vmp_kms_derive_sk": build_kms_derive_sk,
    "g_vmp_tee_attest": build_tee_attest,
    "g_vmp_apk_sig_verify": build_apk_sig_verify,
    "g_vmp_secure_wipe": build_secure_wipe,
}


def generate_c_header(bytecode: bytes, name: str) -> str:
    """Generate embeddable C array snippet."""
    hex_lines = []
    for i in range(0, len(bytecode), 12):
        chunk = bytecode[i:i+12]
        hex_lines.append('    ' + ', '.join(f'0x{b:02X}' for b in chunk) + ',')
    return textwrap.dedent(f'''\
    /* {name} ({len(bytecode)} bytes) — auto-generated by c2vmp.py */
    const uint8_t {name}[] = {{
    {chr(10).join(hex_lines)}
    }};
    const uint32_t {name}_size = sizeof({name});
    ''')


def main():
    parser = argparse.ArgumentParser(
        description="VMP Bytecode Compiler v3.0 — 28-instruction ISA")
    parser.add_argument("--output-dir", default="core/security/src/main/cpp/generated",
                        help="Output directory for generated headers")
    parser.add_argument("--list", action="store_true",
                        help="List available VMP programs")
    parser.add_argument("--all", action="store_true",
                        help="Compile all built-in programs")
    parser.add_argument("--program", help="Compile specific program by name")
    parser.add_argument("--cpp", action="store_true",
                        help="Output as C++ const array (for vm-bytecode.cpp)")
    args = parser.parse_args()

    if args.list:
        print("Available VMP programs:")
        for name, builder in PROGRAMS.items():
            p = builder()
            print(f"  {name}: {len(p.assemble())} bytes")
        return

    os.makedirs(args.output_dir, exist_ok=True)

    if args.all:
        programs = list(PROGRAMS.keys())
    elif args.program:
        if args.program not in PROGRAMS:
            print(f"ERROR: Unknown program '{args.program}'")
            print(f"Available: {', '.join(PROGRAMS.keys())}")
            sys.exit(1)
        programs = [args.program]
    else:
        parser.print_help()
        return

    for name in programs:
        p = PROGRAMS[name]()
        bc = p.assemble()
        
        if args.cpp:
            header = generate_c_header(bc, name)
        else:
            header = p.to_c_array()
        
        path = os.path.join(args.output_dir, f"{name}.h")
        with open(path, "w") as f:
            f.write(header)
        print(f"  {name}: {len(bc)} bytes → {path}")

    # Also generate a combined header
    if args.all and len(programs) > 1:
        combined_path = os.path.join(args.output_dir, "vmp_bytecode_all.h")
        with open(combined_path, "w") as f:
            f.write("// Auto-generated by c2vmp.py — ALL VMP bytecode programs\n")
            f.write("// DO NOT EDIT\n\n")
            f.write("#ifndef VMP_BYTECODE_ALL_H\n")
            f.write("#define VMP_BYTECODE_ALL_H\n\n")
            f.write('#include <stdint.h>\n\n')
            for name in programs:
                p = PROGRAMS[name]()
                bc = p.assemble()
                f.write(generate_c_header(bc, name))
                f.write('\n')
            f.write("#endif /* VMP_BYTECODE_ALL_H */\n")
        print(f"  Combined header → {combined_path}")


if __name__ == "__main__":
    main()
