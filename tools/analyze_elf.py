#!/usr/bin/env python3
"""Analyze ELF structure of liblianyu_shell.so to diagnose pack_so.py issues."""
import sys
import struct
from elftools.elf.elffile import ELFFile

def analyze(path, label):
    with open(path, 'rb') as f:
        data = f.read()
    elf = ELFFile(open(path, 'rb'))

    print(f'\n=== {label} ===')
    print(f'File size: {len(data)} bytes')
    print(f'ELF class: {elf.elfclass}')
    print(f'e_shoff: 0x{elf.header["e_shoff"]:X}')
    print(f'e_shnum: {elf.header["e_shnum"]}')
    print(f'e_shentsize: {elf.header["e_shentsize"]}')
    print(f'e_phoff: 0x{elf.header["e_phoff"]:X}')
    print(f'e_phnum: {elf.header["e_phnum"]}')
    print(f'e_phentsize: {elf.header["e_phentsize"]}')

    text_sec = None
    dynamic_sec = None
    dynstr_sec = None
    dynsym_sec = None

    print('\n--- Section Headers ---')
    for i, sec in enumerate(elf.iter_sections()):
        print(f'  [{i:2d}] {sec.name:30s} offset=0x{sec["sh_offset"]:06X} size=0x{sec["sh_size"]:06X} '
              f'addr=0x{sec["sh_addr"]:06X} type={sec["sh_type"]} flags=0x{sec["sh_flags"]:X}')
        if sec.name == '.text':
            text_sec = sec
        elif sec.name == '.dynamic':
            dynamic_sec = sec
        elif sec.name == '.dynstr':
            dynstr_sec = sec
        elif sec.name == '.dynsym':
            dynsym_sec = sec

    print('\n--- Program Headers ---')
    for i, seg in enumerate(elf.iter_segments()):
        print(f'  [{i}] type={str(seg["p_type"]):20s} offset=0x{seg["p_offset"]:06X} '
              f'vaddr=0x{seg["p_vaddr"]:06X} filesz=0x{seg["p_filesz"]:06X} '
              f'memsz=0x{seg["p_memsz"]:06X} flags={seg["p_flags"]}')

    if text_sec and dynamic_sec:
        print(f'\n.text:    offset=0x{text_sec["sh_offset"]:X} size=0x{text_sec["sh_size"]:X} vaddr=0x{text_sec["sh_addr"]:X}')
        print(f'.dynamic: offset=0x{dynamic_sec["sh_offset"]:X} size=0x{dynamic_sec["sh_size"]:X} vaddr=0x{dynamic_sec["sh_addr"]:X}')
        if dynamic_sec['sh_offset'] > text_sec['sh_offset']:
            print(f'  .dynamic is AFTER .text (offset diff: 0x{dynamic_sec["sh_offset"] - text_sec["sh_offset"]:X})')
        else:
            print(f'  .dynamic is BEFORE .text')

    # Check section header table position relative to .text
    e_shoff = elf.header['e_shoff']
    if text_sec:
        if e_shoff > text_sec['sh_offset']:
            print(f'  Section header table is AFTER .text (e_shoff=0x{e_shoff:X} > .text offset=0x{text_sec["sh_offset"]:X})')
        else:
            print(f'  Section header table is BEFORE .text')

if __name__ == '__main__':
    base = r'c:\Users\27194\Desktop\LianYu'

    # Original unpacked SO
    orig = base + r'\core\security\build\intermediates\cxx\Release\1d71203t\obj\local\arm64-v8a\liblianyu_shell.so'
    analyze(orig, 'ORIGINAL liblianyu_shell.so')

    # Packed SO
    packed = base + r'\app\build\tmp\ultimate_shell\packed_so\arm64-v8a\liblianyu_shell.so'
    try:
        analyze(packed, 'PACKED liblianyu_shell.so')
    except Exception as e:
        print(f'\nPACKED analysis failed: {e}')

    # Also check liblianyu_security.so for comparison
    orig_sec = base + r'\core\security\build\intermediates\cxx\Release\1d71203t\obj\local\arm64-v8a\liblianyu_security.so'
    try:
        analyze(orig_sec, 'ORIGINAL liblianyu_security.so (for comparison)')
    except Exception as e:
        print(f'\nSecurity SO analysis failed: {e}')

    packed_sec = base + r'\app\build\tmp\ultimate_shell\packed_so\arm64-v8a\liblianyu_security.so'
    try:
        analyze(packed_sec, 'PACKED liblianyu_security.so (for comparison)')
    except Exception as e:
        print(f'\nPacked security SO analysis failed: {e}')
