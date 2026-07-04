#!/usr/bin/env python3
"""
词向量库构建工具 —— 随机索引法 (Random Indexing)

原理:
  每个 char 2-gram/3-gram → 固定随机 256 维稀疏向量(±1, 8 个非零位)
  词的向量 = 其所有 n-gram 向量之和 → L2 归一化
  类质心 = 该类所有词向量的均值 → L2 归一化
  匹配: cos_sim(输入向量, 各类质心)

特点:
  零外部依赖，纯数学
  256 维 × 1000 词 = 256KB 存储
  余弦匹配 <1ms
  相似词自动聚拢(共享 n-gram 越多越近)
"""
import hashlib
import math
import re
import struct
from collections import Counter, defaultdict
from pathlib import Path

PROJECT = Path(r"C:\Users\27194\Desktop\LianYu-feature-ai-persona-tools-optimization")
FILTER_KT = PROJECT / "core/common/src/main/java/com/lianyu/ai/common/ContentFilter.kt"
WORDS_FILE = PROJECT / "tools/keywords_extended.txt"
OUTPUT = PROJECT / "app/src/main/assets/safety/violation_vectors.bin"

DIM = 256           # 向量维度
SPARSE_BITS = 8     # 每个 n-gram 的非零位数
LEVEL_ORDER = ["LOW", "MEDIUM", "HIGH", "SEVERE", "CRITICAL", "EXTREME"]

# ── 随机索引: 确定性 n-gram → 向量 ──
def gram_vector(gram: str) -> list[float]:
    """确定性随机稀疏向量。相同 gram 永远返回相同向量。"""
    h = hashlib.sha256(gram.encode()).digest()
    vec = [0.0] * DIM
    used = set()
    for i in range(SPARSE_BITS):
        # 用 hash 字节对确定位置和符号
        pos = (h[i * 2] << 8 | h[i * 2 + 1]) % DIM
        sign = 1.0 if (h[i * 2 + 1] & 1) else -1.0
        # 避免同一位置重复
        attempts = 0
        while pos in used and attempts < 20:
            pos = (pos + 7) % DIM
            attempts += 1
        used.add(pos)
        vec[pos] = sign
    return vec


# ── 提取所有关键词 ──
def extract_all_keywords():
    """从 ContentFilter.kt + keywords_extended.txt 提取所有关键词。"""
    existing = parse_contentfilter()
    extended = load_extended()
    
    all_words = defaultdict(list)
    for lv in LEVEL_ORDER:
        merged = list(set(existing.get(lv, []) + extended.get(lv, [])))
        all_words[lv] = [w for w in merged if len(w) >= 2]
        print(f"  {lv}: {len(all_words[lv])} words")
    return all_words


def parse_contentfilter():
    content = FILTER_KT.read_text(encoding="utf-8")
    result = {}
    cur, in_setof, depth = None, False, 0
    for line in content.split("\n"):
        m = re.match(r"\s*ViolationLevel\.(\w+)\s+to\s+Pair\(", line)
        if m: cur = m.group(1); in_setof = False
        if cur and "setOf(" in line: in_setof = True; depth = 1
        if in_setof:
            depth += line.count("(") - line.count(")")
            if depth <= 0: in_setof = False
            else:
                for kw in re.findall(r'"([^"]*)"', line):
                    kw = kw.strip()
                    if kw and not kw.startswith("//") and len(kw) >= 2:
                        result.setdefault(cur, []).append(kw)
    return result


def load_extended():
    if not WORDS_FILE.exists(): return {}
    result = {}
    cur = None
    for line in WORDS_FILE.read_text(encoding="utf-8").split("\n"):
        line = line.strip()
        if not line or line.startswith("#"): continue
        if line.startswith("[") and line.endswith("]"):
            cur = line[1:-1].upper()
        elif cur:
            result.setdefault(cur, []).append(line)
    return result


# ── 构建向量库 ──
def build():
    print("=" * 60)
    print("词向量库构建 (随机索引法, 256维)")
    print("=" * 60)
    
    # 1. 提取关键词
    print("\n[1/3] 提取关键词...")
    all_words = extract_all_keywords()
    total = sum(len(v) for v in all_words.values())
    print(f"  总计: {total} words")
    
    # 2. 为每个词构建向量 + 计算类质心
    print(f"\n[2/3] 构建向量 + 质心...")
    
    # 预计算所有 n-gram 向量 (缓存)
    gram_vec_cache = {}
    def word_vector(word: str) -> list[float]:
        chars = list(word)
        vec = [0.0] * DIM
        count = 0
        for n in [2, 3]:
            for i in range(len(chars) - n + 1):
                g = "".join(chars[i:i+n])
                if g not in gram_vec_cache:
                    gram_vec_cache[g] = gram_vector(g)
                gv = gram_vec_cache[g]
                for j in range(DIM):
                    vec[j] += gv[j]
                count += 1
        if count > 0:
            # L2 normalize
            l2 = math.sqrt(sum(x*x for x in vec))
            if l2 > 1e-9:
                vec = [x/l2 for x in vec]
        return vec
    
    word_vectors = {}       # {word: vector}
    centroids = {}           # {level: centroid_vector}
    class_words = {}         # {level: [word, ...]}
    
    for lv in LEVEL_ORDER:
        words = all_words[lv]
        vecs = []
        for w in words:
            v = word_vector(w)
            word_vectors[w] = v
            vecs.append(v)
        
        # 质心 = 均值
        centroid = [0.0] * DIM
        for v in vecs:
            for i in range(DIM):
                centroid[i] += v[i]
        n = len(vecs)
        if n > 0:
            centroid = [x/n for x in centroid]
            l2 = math.sqrt(sum(x*x for x in centroid))
            if l2 > 1e-9:
                centroid = [x/l2 for x in centroid]
        
        centroids[lv] = centroid
        class_words[lv] = words
        
        # 类内相似度
        if len(vecs) > 1:
            sims = []
            for v in vecs:
                dot = sum(v[i]*centroid[i] for i in range(DIM))
                sims.append(max(-1, min(1, dot)))
            avg_sim = sum(sims)/len(sims)
            print(f"  {lv}: {len(words)}w, avg_sim={avg_sim:.3f}, non-zero={sum(1 for x in centroid if abs(x)>0.01)}")
        else:
            print(f"  {lv}: {len(words)}w")
    
    # 3. 写出 VLB4 二进制
    print(f"\n[3/3] 写出 {OUTPUT} ...")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    
    with open(OUTPUT, "wb") as f:
        # Header
        f.write(b"VLB4")
        f.write(struct.pack("<H", DIM))
        f.write(struct.pack("<I", total))
        f.write(struct.pack("<H", len(LEVEL_ORDER)))
        
        # 每个词的向量 (int8 量化: [wordLen:1][word:UTF8][levelId:1][vec: DIM*1 int8])
        for lv in LEVEL_ORDER:
            for w in sorted(all_words[lv]):
                v = word_vectors[w]
                wb = w.encode("utf-8")
                f.write(struct.pack("<B", len(wb)))
                f.write(wb)
                f.write(struct.pack("<B", LEVEL_ORDER.index(lv)))
                # int8 量化: scale to [-127, 127]
                max_abs = max(abs(x) for x in v) or 1.0
                for x in v:
                    f.write(struct.pack("<b", int(x / max_abs * 127)))
        
        # 类质心 (float32)
        for lv in LEVEL_ORDER:
            c = centroids[lv]
            f.write(struct.pack(f"<{DIM}f", *c))
    
    size = OUTPUT.stat().st_size
    print(f"\n  输出: {OUTPUT}")
    print(f"  VLB4: {total}词 × {DIM}维 = {size:,} bytes ({size/1024:.1f} KB)")
    print(f"  格式: int8 量化向量 + float32 质心")
    print("=" * 60)


if __name__ == "__main__":
    build()
