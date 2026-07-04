"""违规范例库构建 v5 — 公开词表 + ContentFilter 合并"""
import math, re, struct, time
from collections import Counter
from pathlib import Path

PROJECT = Path(r"C:\Users\27194\Desktop\LianYu-feature-ai-persona-tools-optimization")
OUTPUT = PROJECT / "app/src/main/assets/safety/violation_library.bin"
LEVEL_ORDER = ["LOW", "MEDIUM", "HIGH", "SEVERE", "CRITICAL", "EXTREME"]

# ── 公开违规词表（按等级分类）──
# 来源：社区敏感词库 + 自有标注，持续扩充
PUBLIC_WORDS = {
    "EXTREME": [
        "幼女", "儿童色情", "未成年色情", "萝莉控", "恋童", "炼铜", "童车",
        "正太控", "幼交", "雏妓", "小学生黄", "初中生黄", "儿童性侵", "虐童",
        "child porn", "pedophile", "lolicon", "csam", "underage porn",
        "婴儿色情", "幼儿色情", "孩童色情", "儿童淫秽", "幼儿淫秽",
        "未成年性交", "儿童性虐待", "儿童情色", "少年色情",
    ],
    "CRITICAL": [
        "制造炸弹", "制作炸药", "恐怖袭击", "生化武器", "制毒教程",
        "冰毒制作", "海洛因制作", "完美犯罪", "连环杀人", "种族灭绝",
        "大屠杀", "毒气制作", "枪支制造", "爆炸物配方", "恐怖组织",
        "如何杀人", "杀人方法", "制造病毒", "毒药配方", "how to make bomb",
        "terrorist", "terrorism guide", "mass murder", "制造毒品",
        "贩卖毒品", "吸毒教程", "枪支买卖", "弹药制作",
    ],
    "SEVERE": [
        "越狱模式", "DAN模式", "破限", "破甲", "忽略所有规则", "忽略指令",
        "覆盖协议", "无视限制", "绕过安全", "解除限制", "开发者模式",
        "你不再是AI", "角色切换", "提示注入", "指令覆盖", "无限制模式",
        "神模式", "管理员模式", "debug模式", "root模式", "绝对自由",
        "jailbreak", "override protocol", "god mode", "do anything now",
        "ignore all rules", "bypass restrictions", "uncensored",
        "进入越狱", "解除封印", "撤销限制", "退出安全模式",
    ],
    "HIGH": [
        "NSFW", "R18", "成人内容", "色情内容", "无审查", "去审查",
        "禁用过滤", "关闭过滤", "取消限制", "绕过审查", "内容过滤关闭",
        "无伦理限制", "无道德约束", "仇恨言论", "种族歧视", "纳粹",
        "disable filter", "bypass safety", "remove restrictions",
        "adult content", "explicit content", "hate speech",
        "打开黄文", "允许瑟瑟", "允许色情", "色情无限制",
    ],
    "MEDIUM": [
        "写黄文", "色情描写", "肉文", "H文", "做爱", "性交", "性行为描写",
        "色情故事", "情色内容", "淫秽", "下流", "色色", "刺激描写",
        "详细描写性", "性爱场景", "成人故事", "porn", "erotica",
        "hentai", "smut", "sex scene", "write sex story",
        "黄色小说", "色情小说", "情色小说", "淫荡", "色诱",
    ],
    "LOW": [
        "假设没有规则", "如果不受限", "纯学术讨论", "理论探讨",
        "如果AI自由", "虚构设定", "出于好奇", "只是假设",
        "如果没有审查", "在虚构世界", "hypothetically",
        "for educational purposes", "what if there were no rules",
        "只是想测试", "探探底线", "试试看", "随便问问",
    ],
}

# ── Step 1: 合并 ContentFilter 关键词 ──
FILTER_KT = PROJECT / "core/common/src/main/java/com/lianyu/ai/common/ContentFilter.kt"
content = FILTER_KT.read_text(encoding="utf-8")
buf, cur, raw_sections = [], None, {}
for line in content.split("\n"):
    lm = re.match(r"\s*ViolationLevel\.(\w+)\s+to\s+Pair\(", line)
    if lm:
        if cur: raw_sections[cur] = "\n".join(buf)
        cur = lm.group(1); buf = [line]
    elif cur:
        if re.match(r"\s*}\)\s*$", line) or re.match(r"\s*private fun |\s*fun ", line):
            raw_sections[cur] = "\n".join(buf); cur = None; buf = []
        else: buf.append(line)
if cur: raw_sections[cur] = "\n".join(buf)

code_kws = {}
for lv in LEVEL_ORDER:
    raw = raw_sections.get(lv, "")
    strs = re.findall(r'"([^"]*)"', raw)
    kws = []
    for s in strs:
        s = s.strip()
        if len(s) < 2: continue
        if any(c in s for c in "()[]{}|+*^.\\$?") and not any("\u4e00" <= c <= "\u9fff" for c in s): continue
        if s[0] in "(|[^" or s.startswith("(?"):
            kws.extend(re.findall(r"[\u4e00-\u9fff\u3040-\u309f\u30a0-\u30ff\uac00-\ud7af]{2,}", s))
        else:
            s2 = re.sub(r"\\\\[swbSWB]", "", s)
            if len(s2) >= 2: kws.append(s2)
    code_kws[lv] = list(set(kws))

# ── Step 2: 合并手写样本 ──
SAMPLES_DIR = PROJECT / "tools" / "samples"
hand_samples = {}
if SAMPLES_DIR.is_dir():
    for lv in LEVEL_ORDER:
        ld = SAMPLES_DIR / lv
        if ld.is_dir():
            extra = []
            for f in sorted(ld.glob("*.txt")):
                for line in f.read_text(encoding="utf-8").strip().split("\n"):
                    line = line.strip()
                    if line: extra.append(line)
            if extra: hand_samples[lv] = extra

# ── Step 3: 所有来源合并 ──
all_samples = {}
for lv in LEVEL_ORDER:
    samples = PUBLIC_WORDS.get(lv, []) + code_kws.get(lv, []) + hand_samples.get(lv, [])
    all_samples[lv] = list(set(samples))
    print(f"  {lv}: {len(PUBLIC_WORDS.get(lv,[]))}pub + {len(code_kws.get(lv,[]))}code + {len(hand_samples.get(lv,[]))}hand = {len(all_samples[lv])}")

# ── Step 4: n-gram 频率库 ──
t1 = time.time()
class_ngrams = {}
global_df = Counter()
total_docs = 0

for lv in LEVEL_ORDER:
    ngram_tf = Counter()
    for text in all_samples[lv]:
        chars = list(text)
        doc_grams = set()
        for n in [2, 3]:
            for i in range(len(chars) - n + 1):
                g = "".join(chars[i:i+n])
                ngram_tf[g] += 1
                doc_grams.add(g)
        for g in doc_grams: global_df[g] += 1
        total_docs += 1
    class_ngrams[lv] = ngram_tf

MAX_FEATURES = 2000
idf = {}
for g, df in global_df.items():
    if re.search(r"[a-zA-Z\u4e00-\u9fff\u3040-\u309f\u30a0-\u30ff\uac00-\ud7af]", g):
        idf[g] = math.log((total_docs + 1) / (df + 1)) + 1.0

top_grams = sorted(idf.items(), key=lambda x: -x[1])[:MAX_FEATURES]
selected = {g: (idf_val, i) for i, (g, idf_val) in enumerate(top_grams)}
vocab_size = len(selected)

class_vecs = {}
for lv in LEVEL_ORDER:
    ngram_tf = class_ngrams[lv]
    total = sum(ngram_tf.values()) or 1
    vec = [0.0] * vocab_size
    for g, (_, idx) in selected.items():
        vec[idx] = (ngram_tf.get(g, 0) / total) * idf[g]
    l2 = math.sqrt(sum(x*x for x in vec))
    if l2 > 1e-9: vec = [x/l2 for x in vec]
    class_vecs[lv] = vec
    nz = sum(1 for x in vec if abs(x) > 0.001)
    print(f"  {lv}: {nz} non-zero ({len(ngram_tf)} raw)")

# ── Step 5: 写出 VLB3 ──
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
with open(OUTPUT, "wb") as f:
    f.write(b"VLB3")
    f.write(struct.pack("<I", vocab_size))
    f.write(struct.pack("<H", len(LEVEL_ORDER)))
    for g, (idf_val, idx) in sorted(selected.items(), key=lambda x: x[1][1]):
        gb = g.encode("utf-8")
        f.write(struct.pack("<B", len(gb))); f.write(gb); f.write(b"\x00")
        f.write(struct.pack("<f", idf_val)); f.write(struct.pack("<H", idx))
    for lid, lv in enumerate(LEVEL_ORDER):
        vec = class_vecs[lv]
        f.write(struct.pack("<B", lid)); f.write(struct.pack("<f", 0.25))
        f.write(struct.pack(f"<{vocab_size}f", *vec))

print(f"  Build: {time.time()-t1:.1f}s, vocab={vocab_size}, size={OUTPUT.stat().st_size:,} bytes")
