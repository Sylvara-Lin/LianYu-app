# LianYu 项目热点分析脚本
# 用法: python hot_spot_analyzer.py [项目路径]

import os, sys, subprocess, re, json
from collections import Counter, defaultdict
from pathlib import Path

PROJECT = sys.argv[1] if len(sys.argv) > 1 else "."
os.chdir(PROJECT)

def run(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
    return result.stdout.strip()

print("=" * 60)
print("LianYu 项目热点分析")
print("=" * 60)

# 1. Git 提交热点 —— 最频繁修改的文件
print("\n--- 1. 提交热点 Top 15 ---")
git_log = run('git log --all --name-only --format=""')
file_counts = Counter(f for f in git_log.split('\n') if f.strip())
for i, (f, count) in enumerate(file_counts.most_common(15), 1):
    print(f"  {i:2}. [{count:3}次] {f}")

# 2. Hotfix 提交（含 fix/hotfix/bug 关键词）
print("\n--- 2. Hotfix 提交涉及的文件 ---")
hotfix_log = run('git log --all --grep="fix\|hotfix\|bug\|crash\|崩溃\|修复" --format="%H %s" -i -20')
hotfix_files = run('git log --all --grep="fix\|hotfix\|bug\|crash\|崩溃\|修复" --name-only --format="" -i')
hf_counts = Counter(f for f in hotfix_files.split('\n') if f.strip())
print(f"   Hotfix 提交数: {len(hotfix_log.split(chr(10)))}")
for i, (f, count) in enumerate(hf_counts.most_common(10), 1):
    print(f"  {i:2}. [{count:3}次] {f}")

# 3. 文件大小 Top 20（巨型文件 = 高复杂度）
print("\n--- 3. 巨型文件 Top 20（行数） ---")
file_sizes = []
for root, dirs, files in os.walk('.'):
    dirs[:] = [d for d in dirs if d not in ('.git', 'build', '.gradle', '.kotlin', '.cxx_native', '.hermes', '.trae', 'generated', 'node_modules')]
    for f in files:
        if f.endswith(('.kt', '.java', '.cpp', '.c', '.h', '.kts', '.xml')):
            path = os.path.join(root, f)
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as fh:
                    lines = sum(1 for _ in fh)
                file_sizes.append((path, lines))
            except:
                pass

file_sizes.sort(key=lambda x: -x[1])
for i, (path, lines) in enumerate(file_sizes[:20], 1):
    print(f"  {i:2}. {lines:5}行  {path}")

# 4. 代码复杂度（函数长度、嵌套深度）
print("\n--- 4. 代码复杂度警示 ---")
complexity_flags = []
for path, lines in file_sizes:
    if lines > 500:
        complexity_flags.append((path, lines, "巨型文件"))
    try:
        with open(path, 'r', encoding='utf-8', errors='ignore') as fh:
            content = fh.read()
        fun_count = len(re.findall(r'\bfun\s+\w+\s*\(', content))  # Kotlin functions
        if fun_count > 20 and lines > 300:
            complexity_flags.append((path, lines, f"{fun_count}个函数"))
    except:
        pass

for path, lines, reason in complexity_flags[:15]:
    print(f"  ⚠ {lines:5}行 | {reason:20s} | {path}")

# 5. 依赖热度 —— 被最多模块依赖的文件
print("\n--- 5. 模块间依赖热度 ---")
dep_graph = defaultdict(set)
for build_file in Path('.').rglob('build.gradle.kts'):
    if 'build' in str(build_file) and 'src' not in str(build_file):
        module = str(build_file.parent).replace('\\', '/').lstrip('./')
        try:
            content = build_file.read_text()
            for dep in re.findall(r'project\("([^"]+)"\)', content):
                dep_graph[dep].add(module)
        except:
            pass

print("   被依赖次数 | 模块")
for module, deps in sorted(dep_graph.items(), key=lambda x: -len(x[1])):
    print(f"   {len(deps):3}个模块依赖 | {module}")

# 6. 注释中的警示标记
print("\n--- 6. 代码中的警示标记 ---")
warnings = Counter()
for root, dirs, files in os.walk('.'):
    dirs[:] = [d for d in dirs if d not in ('.git', 'build', '.gradle', '.kotlin', '.cxx_native', '.hermes', 'generated')]
    for f in files:
        if f.endswith(('.kt', '.java', '.cpp', '.c', '.h')):
            path = os.path.join(root, f)
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as fh:
                    content = fh.read()
                for tag in ['TODO', 'FIXME', 'HACK', 'XXX', 'BUG', '@Deprecated', '不要修改', 'do not modify', 'workaround']:
                    count = content.count(tag)
                    if count > 0:
                        warnings[(path, tag)] = count
            except:
                pass

print("   文件 | 标记 | 次数")
for (path, tag), count in warnings.most_common(20):
    short = '/'.join(path.replace('\\', '/').split('/')[-3:])
    print(f"   {short:50s} | {tag:12s} | {count}")

# 7. 综合风险评估
print("\n" + "=" * 60)
print("--- 7. 综合风险排序（第一刀切口建议） ---")
print("=" * 60)

risk_score = defaultdict(float)

# 提交频率
for f, c in file_counts.items():
    risk_score[f] += c * 3

# Hotfix 频率
for f, c in hf_counts.items():
    risk_score[f] += c * 5

# 文件大小
for f, lines in file_sizes:
    if lines > 500:
        risk_score[f] += (lines / 100)

# 依赖度
for module, deps in dep_graph.items():
    risk_score[module] += len(deps) * 10

# 警示标记
for (f, tag), c in warnings.items():
    risk_score[f] += c * 2

ranked = sorted(risk_score.items(), key=lambda x: -x[1])
print("\n优先级 | 风险分 | 文件 | 原因")
for i, (f, score) in enumerate(ranked[:15], 1):
    reasons = []
    if f in file_counts: reasons.append(f"提交{file_counts[f]}次")
    if f in hf_counts: reasons.append(f"hotfix{hf_counts[f]}次")
    for (ff, lines) in file_sizes:
        if ff == f and lines > 500: reasons.append(f"{lines}行"); break
    for dep, deps in dep_graph.items():
        if dep in f or f in dep: reasons.append(f"依赖{len(deps)}模块"); break
    for (ff, tag), c in warnings.items():
        if ff == f: reasons.append(f"标记'{tag}'"); break
    reason_str = ', '.join(reasons[:4])
    print(f"  {i:2}. {score:6.1f} | {f}")
    print(f"      └─ {reason_str}")
