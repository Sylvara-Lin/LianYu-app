#!/usr/bin/env python3
"""
disguise_as_png.py — 360-style DEX fragment disguiser.

将加密的 DEX 碎片包装成合法的 PNG 文件，嵌入 APK 的 res/drawable*/ 目录。
攻击者看到的是一组"损坏的 PNG 图片"，而非加密 DEX。

用法:
  python tools/disguise_as_png.py \\
    --input-dir fragments/ \\
    --output-dir res/drawable/ \\
    --fragment-size 1048576   # 1MB 每片
"""

import struct
import zlib
import os
import sys
import random
import argparse


def make_fake_png(payload: bytes, width: int = 256, height: int = 256,
                  idat_min: int = 4096, idat_max: int = 16384) -> bytes:
    """
    将任意数据包装成合法的 PNG 文件。

    结构:
      - PNG 签名 (8 bytes)
      - IHDR 块 (13 + 12 = 25 bytes)
      - 多个 IDAT 块 (payload, 每块 4KB-16KB)
      - IEND 块 (12 bytes)
    """
    png = bytearray()
    png += b'\x89PNG\r\n\x1a\n'  # PNG 签名

    # --- IHDR 块 ---
    # 随机尺寸增加真实性
    w = random.randint(128, 512)
    h = random.randint(128, 512)
    bit_depth = 8
    color_type = 2  # RGB
    ihdr = struct.pack('>IIBBBBB', w, h, bit_depth, color_type, 0, 0, 0)
    png += struct.pack('>I', len(ihdr))
    png += b'IHDR'
    png += ihdr
    png += struct.pack('>I', zlib.crc32(b'IHDR' + ihdr) & 0xFFFFFFFF)

    # --- IDAT 块 (payload) ---
    chunk_size = random.randint(idat_min, idat_max)
    for i in range(0, len(payload), chunk_size):
        chunk = payload[i:i + chunk_size]
        # zlib 压缩（加密数据本身已是高熵，压缩效果有限但不影响）
        compressed = zlib.compress(chunk, level=1)  # level=1 最小开销
        png += struct.pack('>I', len(compressed))
        png += b'IDAT'
        png += compressed
        png += struct.pack('>I', zlib.crc32(b'IDAT' + compressed) & 0xFFFFFFFF)

    # --- IEND 块 ---
    png += struct.pack('>I', 0)
    png += b'IEND'
    png += struct.pack('>I', zlib.crc32(b'IEND') & 0xFFFFFFFF)

    return bytes(png)


def extract_payload_from_png(png_data: bytes) -> bytes:
    """
    从伪装 PNG 中提取原始 payload（运行时 SO 侧使用）。
    解析 PNG IDAT 块，拼接并解压。
    """
    if png_data[:8] != b'\x89PNG\r\n\x1a\n':
        return png_data  # 不是 PNG，直接返回

    payload = bytearray()
    pos = 8  # 跳过签名

    while pos < len(png_data) - 12:
        chunk_len = struct.unpack('>I', png_data[pos:pos + 4])[0]
        chunk_type = png_data[pos + 4:pos + 8]
        chunk_data = png_data[pos + 8:pos + 8 + chunk_len]

        if chunk_type == b'IDAT':
            try:
                decompressed = zlib.decompress(chunk_data)
                payload += decompressed
            except zlib.error:
                # 损坏的 IDAT — 跳过
                pass
        elif chunk_type == b'IEND':
            break

        pos += 12 + chunk_len

    return bytes(payload)


def main():
    parser = argparse.ArgumentParser(description="DEX 碎片伪装成 PNG")
    parser.add_argument("--input", required=True, help="加密 DEX 输入文件")
    parser.add_argument("--output-dir", required=True, help="PNG 输出目录")
    parser.add_argument("--fragment-size", type=int, default=1024 * 1024,
                        help="每个 PNG 的 payload 大小 (默认 1MB)")
    parser.add_argument("--extract", action="store_true",
                        help="提取模式: PNG → 原始 payload")
    parser.add_argument("--output", help="提取模式输出文件")
    args = parser.parse_args()

    if args.extract:
        # 提取模式
        with open(args.input, "rb") as f:
            png_data = f.read()
        payload = extract_payload_from_png(png_data)
        out = args.output or args.input + ".bin"
        with open(out, "wb") as f:
            f.write(payload)
        print(f"Extracted {len(payload)} bytes to {out}")
        return

    # 伪装模式
    with open(args.input, "rb") as f:
        dex_data = f.read()

    total = len(dex_data)
    chunk = args.fragment_size
    count = (total + chunk - 1) // chunk

    print(f"Input: {total} bytes → {count} PNG fragments ({chunk} bytes each)")

    for i in range(count):
        start = i * chunk
        end = min(start + chunk, total)
        fragment = dex_data[start:end]
        png = make_fake_png(fragment)

        name = f"p{i:02d}.png"
        path = os.path.join(args.output_dir, name)
        with open(path, "wb") as f:
            f.write(png)
        print(f"  [{i + 1}/{count}] {name}: {len(png)}B PNG (payload {len(fragment)}B)")


if __name__ == "__main__":
    main()
