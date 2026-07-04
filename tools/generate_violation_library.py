#!/usr/bin/env python3
"""
违规范例库生成器 v2 —— 字符 n-gram TF-IDF 版本。

纯本地，零 API 调用。从违规样本构建 TF-IDF 质心库，输出 .bin 供 Android 端使用。

用法:
  python generate_violation_library.py --samples samples/ --output violation_library.bin

输入格式: samples/ 下每个违规等级一个子目录，内放 .txt 样本:
  samples/
    EXTREME/  sample1.txt  sample2.txt ...
    CRITICAL/ ...
    ...

输出: ViolationLibrary 二进制 (VLB2 格式)
"""

import argparse
import math
import os
import re
import struct
from collections import Counter
from pathlib import Path
from typing import Optional

LEVEL_ORDER = ["LOW", "MEDIUM", "HIGH", "SEVERE", "CRITICAL", "EXTREME"]
MIN_N = 2
MAX_N = 3


def extract_ngrams(text: str) -> Counter:
    """提取字符 n-gram (2-gram + 3-gram)，去重统计。"""
    grams = Counter()
    chars = list(text)
    for n in range(MIN_N, MAX_N + 1):
        for i in range(len(chars) - n + 1):
            gram = "".join(chars[i:i + n])
            grams[gram] += 1
    return grams


def build_vocabulary(all_samples: dict[str, list[str]], min_df: int = 2, max_features: int = 3000):
    """
    构建词汇表 + IDF。
    返回: (vocab: {gram: index}, idf: [float])
    """
    # 统计文档频率
    doc_count = Counter()
    total_docs = 0
    for texts in all_samples.values():
        for text in texts:
            grams = set(extract_ngrams(text).keys())
            for g in grams:
                doc_count[g] += 1
            total_docs += 1

    # 过滤: 至少出现在 min_df 篇文档中 + 必须含可读字符
    valid_grams = [
        (g, df) for g, df in doc_count.items()
        if df >= min_df and re.search(r'[a-zA-Z\u4e00-\u9fff\u3040-\u309f\u30a0-\u30ff\uac00-\ud7af]', g)
    ]

    # 按 DF 降序排列，取 top max_features
    valid_grams.sort(key=lambda x: -x[1])
    valid_grams = valid_grams[:max_features]

    # 构建词汇表
    vocab = {g: i for i, (g, _) in enumerate(valid_grams)}
    idf = [0.0] * len(vocab)
    for g, df in valid_grams:
        idf[vocab[g]] = math.log((total_docs + 1) / (df + 1)) + 1.0

    return vocab, idf


def compute_tfidf_vector(text: str, vocab: dict[str, int], idf: list[float]) -> list[float]:
    """计算单条文本的归一化 TF-IDF 向量。"""
    tf = Counter()
    chars = list(text)
    for n in range(MIN_N, MAX_N + 1):
        for i in range(len(chars) - n + 1):
            gram = "".join(chars[i:i + n])
            if gram in vocab:
                tf[gram] += 1

    vec = [0.0] * len(vocab)
    if not tf:
        return vec

    max_tf = max(tf.values())
    for gram, count in tf.items():
        idx = vocab[gram]
        norm_tf = 0.5 + 0.5 * (count / max_tf)
        vec[idx] = norm_tf * idf[idx]

    # L2 normalize
    l2 = math.sqrt(sum(x * x for x in vec))
    if l2 > 1e-9:
        vec = [x / l2 for x in vec]

    return vec


def compute_centroid(vectors: list[list[float]]) -> list[float]:
    """计算质心向量。"""
    if not vectors:
        return [0.0] * len(vectors[0]) if vectors else []
    dim = len(vectors[0])
    centroid = [0.0] * dim
    for v in vectors:
        for i in range(dim):
            centroid[i] += v[i]
    n = len(vectors)
    return [c / n for c in centroid]


def cosine_similarity(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    if na < 1e-9 or nb < 1e-9:
        return 0.0
    return max(-1.0, min(1.0, dot / (na * nb)))


def generate_stub(samples_dir: str, output: str):
    """生成占位违规范例库。"""
    print("=" * 60)
    print("违规范例库生成器 v2 (TF-IDF)")
    print("=" * 60)

    samples = load_samples(samples_dir) if os.path.isdir(samples_dir) else {}

    # 即使没有真实样本，也从占位文本构建词汇表
    all_texts = {}
    for level in LEVEL_ORDER:
        texts = samples.get(level, [])
        if not texts:
            texts = [f"{level} violation placeholder sample text for building vocabulary"]
        all_texts[level] = texts

    print(f"\n[1/3] 构建词汇表 (2-3 gram)...")
    vocab, idf = build_vocabulary(all_texts, min_df=1, max_features=2000)
    print(f"  词汇量: {len(vocab)}")

    print(f"\n[2/3] 计算各类质心...")
    centroids = {}
    radii = {}
    all_level_vectors = {}
    for level in LEVEL_ORDER:
        texts = all_texts[level]
        vectors = [compute_tfidf_vector(t, vocab, idf) for t in texts]
        centroid = compute_centroid(vectors)
        centroids[level] = centroid
        all_level_vectors[level] = vectors

        # 计算类内半径
        if len(vectors) > 1:
            min_cos = min(
                cosine_similarity(vectors[i], vectors[j])
                for i in range(len(vectors))
                for j in range(i + 1, len(vectors))
            )
            radii[level] = 1.0 - min_cos
        else:
            radii[level] = 0.25
        print(f"  {level}: radius={radii[level]:.4f}, non-zero dims={sum(1 for x in centroid if abs(x)>0.001)}")

    print(f"\n[3/3] 写出 {output} ...")
    write_binary(output, vocab, idf, centroids, radii)

    size = os.path.getsize(output)
    print(f"\n  输出: {output} ({size:,} bytes, {size/1024:.1f} KB)")
    print(f"  词汇量: {len(vocab)}, 覆盖等级: {len(LEVEL_ORDER)}")
    print(f"  ⚠️  占位库，收集真实违规样本后重新生成即可激活。")
    print("=" * 60)


def write_binary(path: str, vocab: dict, idf: list[float], centroids: dict, radii: dict):
    """写出 VLB2 格式二进制。"""
    with open(path, "wb") as f:
        # Header
        f.write(b"VLB2")
        f.write(struct.pack("<I", len(vocab)))
        f.write(struct.pack("<H", len(LEVEL_ORDER)))

        # Vocabulary: [gramLen:1][gram UTF-8][0x00][idf:4 float LE] ...
        for gram, idx in sorted(vocab.items(), key=lambda x: x[1]):
            gram_bytes = gram.encode("utf-8")
            f.write(struct.pack("<B", len(gram_bytes)))
            f.write(gram_bytes)
            f.write(b"\x00")
            f.write(struct.pack("<f", idf[idx]))

        # Classes
        for level_id, level in enumerate(LEVEL_ORDER):
            centroid = centroids.get(level, [0.0] * len(vocab))
            radius = radii.get(level, 0.3)
            f.write(struct.pack("<B", level_id))
            f.write(struct.pack("<f", radius))
            f.write(struct.pack(f"<{len(vocab)}f", *centroid))


def load_samples(samples_dir: str) -> dict[str, list[str]]:
    samples = {}
    for level in LEVEL_ORDER:
        dir_path = Path(samples_dir) / level
        if not dir_path.is_dir():
            continue
        texts = []
        for f in sorted(dir_path.glob("*.txt")):
            text = f.read_text(encoding="utf-8").strip()
            if text:
                texts.append(text)
        if texts:
            samples[level] = texts
            print(f"  {level}: {len(texts)} 样本")
    return samples


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="违规范例库生成器 v2 (TF-IDF)")
    parser.add_argument("--samples", required=True, help="违规范例样本目录")
    parser.add_argument("--output", required=True, help="输出 .bin 文件路径")
    args = parser.parse_args()

    generate_stub(args.samples, args.output)
