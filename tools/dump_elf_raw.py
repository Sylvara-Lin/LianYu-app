#!/usr/bin/env python3
"""Dump raw ELF program headers and dynamic section from packed and unpacked SOs."""
import struct, sys

def read_u64(data, off):
    return struct.unpack_from('<Q', data, off)[0]

def read_u32(data, off):
    return struct.unpack_from('<I', data, off)[0]

def read_u16(data, off):
    return struct.unpack_from('<H', data, off)[0]

def analyze_phdr(path, label):
    with open(path, 'rb') as f:
        data = f.read()


    print(f'\n=== {label} ===')
    print(f'File size: {len(data)}')


    ei_class = data[4]
    is_64 = (ei_class == 2)


    if is_64:
        e_phoff = read_u64(data, 32)   # e_phoff at offset 32 for 64-bit
        e_phentsize = read_u16(data, 54)
        e_phnum = read_u16(data, 56)
        e_shoff = read_u64(data, 40)
        e_shnum = read_u16(data, 60)
        e_shentsize = read_u16(data, 58)
    else:
        e_phoff = read_u32(data, 28)
        e_phentsize = read_u16(data, 42)
        e_phnum = read_u16(data, 44)
        e_shoff = read_u32(data, 32)
        e_shnum = read_u16(data, 48)
        e_shentsize = read_u16(data, 46)


    print(f'e_phoff=0x{e_phoff:X} e_phnum={e_phnum} e_phentsize={e_phentsize}')
    print(f'e_shoff=0x{e_shoff:X} e_shnum={e_shnum} e_shentsize={e_shentsize}')


    PT_TYPES = {0: 'NULL', 1: 'LOAD', 2: 'DYNAMIC', 3: 'INTERP', 4: 'NOTE',
                5: 'SHLIB', 6: 'PHDR', 7: 'TLS', 0x6474e550: 'GNU_EH_FRAME',
                0x6474e551: 'GNU_STACK', 0x6474e552: 'GNU_RELRO', 0x6474e553: 'GNU_PROPERTY'}


    print('\n--- Program Headers (raw) ---')
    dyn_offset = None
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        if is_64:
            p_type = read_u32(data, off)
            p_flags = read_u32(data, off + 4)
            p_offset = read_u64(data, off + 8)
            p_vaddr = read_u64(data, off + 16)
            p_paddr = read_u64(data, off + 24)
            p_filesz = read_u64(data, off + 32)
            p_memsz = read_u64(data, off + 40)
            p_align = read_u64(data, off + 48)
        else:
            p_type = read_u32(data, off)
            p_offset = read_u32(data, off + 4)
            p_vaddr = read_u32(data, off + 8)
            p_paddr = read_u32(data, off + 12)
            p_filesz = read_u32(data, off + 16)
            p_memsz = read_u32(data, off + 20)
            p_flags = read_u32(data, off + 24)
            p_align = read_u32(data, off + 28)


        type_name = PT_TYPES.get(p_type, f'0x{p_type:X}')
        print(f'  [{i}] {type_name:20s} offset=0x{p_offset:08X} vaddr=0x{p_vaddr:08X} '
              f'filesz=0x{p_filesz:08X} memsz=0x{p_memsz:08X} flags={p_flags}')


        if p_type == 2:  # PT_DYNAMIC
            dyn_offset = p_offset


    # Parse .dynamic section
    if dyn_offset is not None:
        print(f'\n--- .dynamic section (at file offset 0x{dyn_offset:X}) ---')
        DT_TAGS = {0: 'NULL', 1: 'NEEDED', 2: 'PLTRELSZ', 3: 'PLTGOT', 4: 'HASH',
                   5: 'STRTAB', 6: 'SYMTAB', 7: 'RELA', 8: 'RELASZ', 9: 'RELAENT',
                   10: 'STRSZ', 11: 'SYMENT', 12: 'INIT', 13: 'FINI', 14: 'SONAME',
                   15: 'RPATH', 16: 'SYMBOLIC', 17: 'REL', 20: 'PLTREL',
                   21: 'DEBUG', 23: 'JMPREL', 25: 'INIT_ARRAY', 26: 'FINI_ARRAY',
                   27: 'INIT_ARRAYSZ', 28: 'FINI_ARRAYSZ', 29: 'RUNPATH',
                   30: 'FLAGS', 0x6ffffb00: 'VERSYM', 0x6ffffffc: 'VERDEF',
                   0x6ffffffd: 'VERDEFNUM', 0x6ffffffe: 'VERNEED', 0x6fffffff: 'VERNEEDNUM'}


        pos = dyn_offset
        while pos < len(data) - (16 if is_64 else 8):
            if is_64:
                d_tag = struct.unpack_from('<q', data, pos)[0]  # signed
                d_val = read_u64(data, pos + 8)
                pos += 16
            else:
                d_tag = struct.unpack_from('<i', data, pos)[0]
                d_val = read_u32(data, pos + 4)
                pos += 8


            tag_name = DT_TAGS.get(d_tag, f'0x{d_tag:X}')
            print(f'  {tag_name:20s} val=0x{d_val:016X}' if is_64 else f'  {tag_name:20s} val=0x{d_val:08X}')


            if d_tag == 0:  # DT_NULL
                break


    # Check .text section
    # Find .text by scanning section headers
    if e_shoff + e_shnum * e_shentsize <= len(data):
        shstrtab_off = e_shoff + (e_shnum - 2) * e_shentsize if e_shnum >= 2 else 0
        if is_64:
            shstrtab_sh_offset = read_u64(data, shstrtab_off + 24)
            shstrtab_sh_size = read_u64(data, shstrtab_off + 32)
        else:
            shstrtab_sh_offset = read_u32(data, shstrtab_off + 16)
            shstrtab_sh_size = read_u32(data, shstrtab_off + 20)


        print(f'\n--- .text section ---')
        for i in range(e_shnum):
            off = e_shoff + i * e_shentsize
            if is_64:
                sh_name = read_u32(data, off)
                sh_type = read_u32(data, off + 4)
                sh_offset = read_u64(data, off + 24)
                sh_size = read_u64(data, off + 32)
                sh_addr = read_u64(data, off + 16)
            else:
                sh_name = read_u32(data, off)
                sh_type = read_u32(data, off + 4)
                sh_offset = read_u32(data, off + 16)
                sh_size = read_u32(data, off + 20)
                sh_addr = read_u32(data, off + 12)


            # Get section name from shstrtab
            if shstrtab_sh_offset + sh_name < len(data):
                end = data.index(0, shstrtab_sh_offset + sh_name)
                name = data[shstrtab_sh_offset + sh_name:end].decode('ascii', errors='replace')
            else:
                name = f'<invalid:{sh_name}>'


            if name == '.text':
                print(f'  .text: offset=0x{sh_offset:X} size=0x{sh_size:X} addr=0x{sh_addr:X}')
                # Check first 16 bytes of .text
                if sh_offset + 16 <= len(data):
                    first_bytes = data[sh_offset:sh_offset+16]
                    print(f'  First 16 bytes: {first_bytes.hex()}')
                break
    else:
        print(f'\n--- Section headers out of bounds (e_shoff=0x{e_shoff:X}, need 0x{e_shoff + e_shnum * e_shentsize:X}, file size={len(data)}) ---')

if __name__ == '__main__':
    base = r'c:\Users\27194\Desktop\LianYu'


    # Shell SO
    analyze_phdr(base + r'\core\security\build\intermediates\cxx\Release\1d71203t\obj\local\arm64-v8a\liblianyu_shell.so',
                 'ORIGINAL liblianyu_shell.so')
    analyze_phdr(base + r'\app\build\tmp\ultimate_shell\packed_so\arm64-v8a\liblianyu_shell.so',
                 'PACKED liblianyu_shell.so')


    # Security SO
    analyze_phdr(base + r'\core\security\build\intermediates\cxx\Release\1d71203t\obj\local\arm64-v8a\liblianyu_security.so',
                 'ORIGINAL liblianyu_security.so')
    analyze_phdr(base + r'\app\build\tmp\ultimate_shell\packed_so\arm64-v8a\liblianyu_security.so',
                 'PACKED liblianyu_security.so')
