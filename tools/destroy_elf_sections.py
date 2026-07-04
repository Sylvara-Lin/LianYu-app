#!/usr/bin/env python3
"""
destroy_elf_sections.py — 360-style ELF section header table destruction.

爆破 SO 的节头表，使 objdump/readelf/IDA 自动分析失效。
Android API 26+ 的 dynamic linker 只用 program headers，节头表破坏不影响加载。

用法:
  python tools/destroy_elf_sections.py liblianyu_security.so [--preserve-first 64]
"""

import struct
import os
import sys
import argparse
import random


def destroy_section_headers(so_path: str, preserve_bytes: int = 64):
    """
    破坏 ELF section header table。
    
    策略:
      1. 保留 ELF header 中的 e_shoff 字段（不影响 program headers 解析）
      2. 用随机数据覆盖节头表内容
      3. 可选：将 e_shoff 指向 0xFFFFFFFF（让工具彻底放弃解析）
    """
    with open(so_path, 'r+b') as f:
        # 验证 ELF
        f.seek(0)
        magic = f.read(4)
        if magic != b'\x7fELF':
            print(f"ERROR: {so_path} is not a valid ELF file")
            return False

        # 读取 ELF class
        f.seek(4)
        elf_class = f.read(1)[0]
        if elf_class == 1:
            # ELF32
            f.seek(0x20)
            e_shoff = struct.unpack('<I', f.read(4))[0]
        elif elf_class == 2:
            # ELF64
            f.seek(0x28)
            e_shoff = struct.unpack('<Q', f.read(8))[0]
        else:
            print(f"ERROR: Unknown ELF class {elf_class}")
            return False

        if e_shoff == 0:
            print("WARNING: No section headers (already destroyed?)")
            return True

        # 覆盖节头表为随机数据
        f.seek(e_shoff + preserve_bytes)
        file_size = os.path.getsize(so_path)
        remaining = file_size - (e_shoff + preserve_bytes)
        if remaining > 0:
            # 用随机字节覆盖（模拟爆破后的样子）
            random_bytes = bytearray(random.randint(0, 255) for _ in range(min(remaining, 8192)))
            f.write(random_bytes)
            print(f"  Overwritten {min(remaining, 8192)} bytes at offset 0x{e_shoff + preserve_bytes:x}")

        # 可选：破坏 e_shoff 指向非法地址
        f.seek(0x28 if elf_class == 2 else 0x20)
        if elf_class == 2:
            f.write(struct.pack('<Q', 0xFFFFFFFFFFFFFFFF))
        else:
            f.write(struct.pack('<I', 0xFFFFFFFF))

        print(f"  e_shoff → 0xFFFFFFFF (invalid)")
        return True


def main():
    parser = argparse.ArgumentParser(description="Destroy ELF section headers")
    parser.add_argument("so_path", help="Path to .so file")
    parser.add_argument("--preserve-first", type=int, default=64,
                        help="Bytes to preserve at section header offset (default: 64)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show what would be done without modifying")
    args = parser.parse_args()

    if not os.path.exists(args.so_path):
        print(f"ERROR: {args.so_path} not found")
        sys.exit(1)

    size_before = os.path.getsize(args.so_path)
    print(f"Input: {args.so_path} ({size_before} bytes)")

    if args.dry_run:
        print("DRY RUN — no changes made")
        return

    if destroy_section_headers(args.so_path, args.preserve_first):
        size_after = os.path.getsize(args.so_path)
        print(f"Done. Size: {size_before} → {size_after} bytes")
        print()
        print("Verify:")
        print(f"  readelf -S {args.so_path}   # 期望: 无输出或错误")
        print(f"  objdump -d {args.so_path}    # 期望: 拒绝反汇编")
    else:
        sys.exit(1)


if __name__ == "__main__":
    main()
