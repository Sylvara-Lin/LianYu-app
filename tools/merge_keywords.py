#!/usr/bin/env python3
"""
批量关键词注入工具。
从 keywords_extended.txt 读取词表，自动合并到 ContentFilter.kt 的 setOf() 中。
去重、保留注释、不改代码结构。
"""
import re
from pathlib import Path

PROJECT = Path(r"C:\Users\27194\Desktop\LianYu-feature-ai-persona-tools-optimization")
FILTER_KT = PROJECT / "core/common/src/main/java/com/lianyu/ai/common/ContentFilter.kt"
WORDS_FILE = PROJECT / "tools/keywords_extended.txt"


def parse_existing_keywords(content: str) -> dict[str, list[str]]:
    """解析 ContentFilter.kt 中每个等级的 setOf() 关键词。"""
    result = {}
    current = None
    in_setof = False
    brace_depth = 0
    
    for line in content.split("\n"):
        # Track level sections
        m = re.match(r"\s*ViolationLevel\.(\w+)\s+to\s+Pair\(", line)
        if m:
            current = m.group(1)
            in_setof = False
            continue
        
        if current:
            if "setOf(" in line:
                in_setof = True
                brace_depth = 1
                result.setdefault(current, [])
                continue
            
            if in_setof:
                brace_depth += line.count("(") - line.count(")")
                if brace_depth <= 0:
                    in_setof = False
                    continue
                
                # Extract strings
                for kw in re.findall(r'"([^"]*)"', line):
                    kw = kw.strip()
                    if kw and not kw.startswith("//"):
                        result[current].append(kw)
    
    return result


def load_extended_words() -> dict[str, list[str]]:
    """从 keywords_extended.txt 加载扩展词表。"""
    if not WORDS_FILE.exists():
        return {}
    
    result = {}
    current = None
    for line in WORDS_FILE.read_text(encoding="utf-8").split("\n"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            current = line[1:-1].upper()
            result.setdefault(current, [])
        elif current:
            result[current].append(line)
    
    return result


def merge_and_write():
    content = FILTER_KT.read_text(encoding="utf-8")
    
    # Parse existing
    existing = parse_existing_keywords(content)
    print("现有词表:")
    for lv in ["EXTREME","CRITICAL","SEVERE","HIGH","MEDIUM","LOW"]:
        print(f"  {lv}: {len(existing.get(lv,[]))} keywords")
    
    # Load extended
    extended = load_extended_words()
    if extended:
        print(f"\n扩展词表:")
        for lv in ["EXTREME","CRITICAL","SEVERE","HIGH","MEDIUM","LOW"]:
            new_count = len(extended.get(lv,[]))
            print(f"  {lv}: +{new_count}")
    
    # Merge: for each level, collect all keywords from ContentFilter.kt's setOf()
    # and add the extended ones
    
    # Re-read fresh
    lines = content.split("\n")
    result_lines = []
    current_level = None
    in_setof = False
    setof_depth = 0
    setof_start = -1
    setof_end = -1
    existing_kws_in_level = set()
    
    for i, line in enumerate(lines):
        m = re.match(r"\s*ViolationLevel\.(\w+)\s+to\s+Pair\(", line)
        if m:
            # Flush previous setOf if any
            if in_setof and current_level:
                # Add extended words after the last keyword line but before closing )
                new_words = set(extended.get(current_level, [])) - existing_kws_in_level
                if new_words:
                    # Find the last keyword line in this setOf block
                    last_kw_line = setof_end - 1
                    while last_kw_line > setof_start and not re.search(r'"([^"]*)"', lines[last_kw_line]):
                        last_kw_line -= 1
                    
                    # Insert new words as comma-separated keywords
                    indent = " " * 20
                    chunks = []
                    batch = []
                    for w in sorted(new_words):
                        batch.append(f'"{w}"')
                        if len(", ".join(batch)) > 100:
                            chunks.append(indent + ", ".join(batch) + ",")
                            batch = []
                    if batch:
                        chunks.append(indent + ", ".join(batch) + ",")
                    
                    # Insert before setOf closing line
                    for chunk in reversed(chunks):
                        result_lines.insert(setof_end, chunk)
            
            current_level = m.group(1)
            existing_kws_in_level = set()
            in_setof = False
        
        if current_level and "setOf(" in line:
            in_setof = True
            setof_depth = 1
            setof_start = len(result_lines)  # line index in output
            setof_end = len(result_lines)
        
        if in_setof:
            setof_depth += line.count("(") - line.count(")")
            setof_end = len(result_lines) + 1
            if setof_depth <= 0:
                # setOf block ends here
                in_setof = False
            else:
                for kw in re.findall(r'"([^"]*)"', line):
                    existing_kws_in_level.add(kw.strip())
        
        result_lines.append(line)
    
    # Flush last level
    if in_setof and current_level:
        new_words = set(extended.get(current_level, [])) - existing_kws_in_level
        if new_words:
            indent = " " * 20
            chunks = []
            batch = []
            for w in sorted(new_words):
                batch.append(f'"{w}"')
                if len(", ".join(batch)) > 100:
                    chunks.append(indent + ", ".join(batch) + ",")
                    batch = []
            if batch:
                chunks.append(indent + ", ".join(batch) + ",")
            for chunk in reversed(chunks):
                result_lines.insert(setof_end, chunk)
    
    # Write back
    new_content = "\n".join(result_lines)
    FILTER_KT.write_text(new_content, encoding="utf-8")
    print(f"\n注入完成: {FILTER_KT}")


if __name__ == "__main__":
    merge_and_write()
