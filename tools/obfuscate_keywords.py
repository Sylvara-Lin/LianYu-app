#!/usr/bin/env python3
"""
obfuscate_keywords.py — XOR-obfuscate all banned keywords with SM3-derived key.

Reads keywords from buildKeywords() in ContentFilter.kt and all generateXxxKeywords()
in SecurityDataSeeder.kt, XOR-encrypts every string (regex patterns + keywords) with
a 32-byte SM3-derived key, and rewrites both files with encrypted hex literals.

Usage:
    python tools/obfuscate_keywords.py

Output:
    - ContentFilter.kt     — buildKeywords() replaced with encrypted hex + deobfuscator
    - SecurityDataSeeder.kt — all generateXxxKeywords() replaced
"""

import hashlib
import os
import re
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CF_PATH = os.path.join(PROJECT_ROOT, "core", "common", "src", "main", "java", "com", "lianyu", "ai", "common", "ContentFilter.kt")
SD_PATH = os.path.join(PROJECT_ROOT, "core", "database", "src", "main", "java", "com", "lianyu", "ai", "database", "SecurityDataSeeder.kt")
TXT_PATH = os.path.join(PROJECT_ROOT, "tools", "keywords_extended.txt")

# Derive 32-byte key from SM3 of seed
_KDF_SEED = b"lianyu_banned_kw_kdf_v2_salt_0x9D4A"
_KEY = hashlib.new("sm3", _KDF_SEED).digest()


def xor_encrypt(plain: str) -> str:
    """XOR plaintext with cyclic 32-byte key, return uppercase hex."""
    b = plain.encode("utf-8")
    enc = bytes(b[i] ^ _KEY[i % 32] for i in range(len(b)))
    return enc.hex().upper()


def xor_decrypt_fn(indent: int = 4) -> str:
    """Generate the Kotlin deobfuscator code with given indentation (spaces)."""
    prefix = " " * indent
    key_hex = _KEY.hex().upper()
    lines = [
        f"private val OBF_KEY = \"{key_hex}\".chunked(2).map {{ it.toInt(16).toByte() }}.toByteArray()",
        "",
        f"private fun d(enc: String): String {{",
        f"    val b = enc.chunked(2).map {{ it.toInt(16).toByte() }}.toByteArray()",
        f"    val k = OBF_KEY",
        f"    for (i in b.indices) b[i] = (b[i].toInt() xor (k[i % 32].toInt() and 0xFF)).toByte()",
        f"    return String(b, Charsets.UTF_8)",
        f"}}",
    ]
    return "\n".join(prefix + l for l in lines)


def xor_encrypt_str_list(strings: list[str]) -> str:
    """Encrypt a list of strings, return Kotlin code."""
    items = []
    for s in sorted(set(strings)):
        items.append(f'd("{xor_encrypt(s)}")')
    return ",\n                ".join(items)


def xor_encrypt_arr_list(strings: list[str]) -> str:
    """Encrypt an array of strings, return Kotlin code."""
    items = []
    for s in strings:
        items.append(f'd("{xor_encrypt(s)}")')
    return ",\n                    ".join(items)


def extract_kotlin_strings(text: str) -> set[str]:
    """Extract all Kotlin string literals (double-quoted, with escapes)."""
    results = set()
    # Match double-quoted strings, handling escapes
    for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', text):
        raw = m.group(1)
        try:
            decoded = raw.encode().decode("unicode_escape")
        except (UnicodeDecodeError, UnicodeEncodeError):
            decoded = raw
        results.add(decoded)
    return results


def build_encrypted_buildKeywords(include_deobfuscator: bool = True) -> str:
    """Read ContentFilter.kt, extract buildKeywords() strings, encrypt them, return replacement function."""
    with open(CF_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # Extract the old buildKeywords() via brace counting
    func_marker = "private fun buildKeywords():"
    marker_idx = content.find(func_marker)
    if marker_idx == -1:
        print("ERROR: Cannot find buildKeywords() in ContentFilter.kt")
        sys.exit(1)
    brace_start = content.find("{", marker_idx)
    brace_end = find_matching_brace(content, brace_start)
    if brace_end == -1:
        print("ERROR: Cannot parse buildKeywords() braces")
        sys.exit(1)
    old_body = content[brace_start + 1:brace_end]

    # Parse each level block
    levels_data = {}
    level_blocks = re.findall(
        r"ViolationLevel\.(\w+)\s*to\s*Pair\s*\(\s*arrayOf\((.*?)\)\s*,\s*setOf\((.*?)\)\s*\)",
        old_body, re.DOTALL,
    )

    for level_name, arr_str, set_str in level_blocks:
        patterns = extract_kotlin_strings(arr_str)
        keywords = extract_kotlin_strings(set_str)
        levels_data[level_name] = {"patterns": patterns, "keywords": keywords}

    if not levels_data:
        print("ERROR: Cannot parse level blocks from buildKeywords()")
        sys.exit(1)

    lines = []
    if include_deobfuscator:
        lines.append(xor_decrypt_fn(4))
        lines.append("")

    lines.append("    private fun buildKeywords(): Map<ViolationLevel, Pair<Array<String>, Set<String>>> {")
    lines.append("        return mapOf(")

    for level_name in ["EXTREME", "CRITICAL", "SEVERE", "HIGH", "MEDIUM", "LOW"]:
        if level_name not in levels_data:
            continue
        ld = levels_data[level_name]
        pat_list = sorted(set(ld["patterns"]))
        kw_list = sorted(set(ld["keywords"]))

        pat_lines = [f'd("{xor_encrypt(p)}"),' for p in pat_list]
        kw_lines = [f'd("{xor_encrypt(k)}"),' for k in kw_list]

        # Build the block with proper 12/16/20-space indentation
        block = [f"            ViolationLevel.{level_name} to Pair(",
                 "                arrayOf("]
        for pl in pat_lines:
            block.append(f"                    {pl}")
        block.append("                ),")
        block.append("                setOf(")
        for kl in kw_lines:
            block.append(f"                    {kl}")
        if kw_lines:
            block[-1] = block[-1].rstrip(",")  # remove trailing comma from last keyword
        block.append("                )")
        block.append("            ),")

        lines.extend(block)

    # Remove trailing comma from last block
    for i in range(len(lines) - 1, -1, -1):
        if lines[i].rstrip().endswith("),"):
            lines[i] = lines[i].rstrip().rstrip(",")
            break

    lines.append("        )")
    lines.append("    }")

    return "\n".join(lines)


def find_matching_brace(s: str, start: int) -> int:
    """Find the matching closing brace for the opening brace at position `start`."""
    if start >= len(s) or s[start] != '{':
        return -1
    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(s)):
        c = s[i]
        if escape:
            escape = False
            continue
        if c == '\\':
            escape = True
            continue
        if c == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return i
    return -1


def process_contentfilter():
    print("[*] Processing ContentFilter.kt ...")
    with open(CF_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    already_done = "private val OBF_KEY" in content

    # Find buildKeywords() using brace counting to handle nested parens
    func_marker = "private fun buildKeywords():"
    marker_idx = content.find(func_marker)
    if marker_idx == -1:
        print("ERROR: Cannot find buildKeywords() in ContentFilter.kt")
        return False

    # Find the opening brace of buildKeywords()
    brace_start = content.find("{", marker_idx)
    if brace_start == -1:
        print("ERROR: Cannot find opening brace of buildKeywords()")
        return False

    # Find the matching closing brace
    brace_end = find_matching_brace(content, brace_start)
    if brace_end == -1:
        print("ERROR: Cannot find closing brace of buildKeywords()")
        return False

    new_fn = build_encrypted_buildKeywords(include_deobfuscator=not already_done)
    new_content = content[:marker_idx] + new_fn + content[brace_end + 1:]

    with open(CF_PATH, "w", encoding="utf-8") as f:
        f.write(new_content)
    print("  ✅ ContentFilter.kt updated — all keywords XOR-encrypted")
    return True


def find_matching_paren(s: str, start: int) -> int:
    """Find the matching closing paren for the opening paren at position `start`."""
    if start >= len(s) or s[start] != '(':
        return -1
    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(s)):
        c = s[i]
        if escape:
            escape = False
            continue
        if c == '\\':
            escape = True
            continue
        if c == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return i
    return -1


def process_securitydataseeder():
    print("[*] Processing SecurityDataSeeder.kt ...")
    with open(SD_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # Add deobfuscation helper to companion object (only if not already present)
    if "private val OBF_KEY" not in content:
        dec_fn = xor_decrypt_fn(8)
        comp_m = re.search(r"(companion object\s*\{)", content)
        if comp_m:
            insert_pos = comp_m.end()
            dec_insert = "\n" + dec_fn + "\n"
            content = content[:insert_pos] + dec_insert + content[insert_pos:]

    # Replace all generateXxxKeywords() functions
    ban_days_map = {
        "ExtremeKeywords": 365, "CriticalKeywords": 31, "SevereKeywords": 10,
        "HighKeywords": 7, "MediumKeywords": 3, "LowKeywords": 1,
    }
    level_map = {
        "ExtremeKeywords": "EXTREME", "CriticalKeywords": "CRITICAL", "SevereKeywords": "SEVERE",
        "HighKeywords": "HIGH", "MediumKeywords": "MEDIUM", "LowKeywords": "LOW",
    }

    func_pattern = re.compile(
        r"private fun generate(\w+Keywords)\(\): List<KeywordEntity>\s*\{",
    )

    # Process from last to first so replacements don't shift positions
    for func_match in reversed(list(func_pattern.finditer(content))):
        func_name = func_match.group(1)
        func_start = func_match.end()
        bd = ban_days_map.get(func_name, 365)
        lv = level_map.get(func_name, func_name.upper().replace("KEYWORDS", ""))

        # Find the matching closing brace of the function
        depth = 1
        func_end = func_start
        for i in range(func_start, len(content)):
            if content[i] == '{':
                depth += 1
            elif content[i] == '}':
                depth -= 1
                if depth == 0:
                    func_end = i
                    break

        func_body = content[func_start:func_end]

        # Extract patterns from arrayOf(...)
        pat_match = re.search(r"val patterns\s*=\s*arrayOf\s*\(", func_body)
        pat_strs = []
        if pat_match:
            pat_paren_start = func_start + pat_match.end() - 1  # position of '('
            pat_paren_end = find_matching_paren(content, pat_paren_start)
            if pat_paren_end >= 0:
                pat_content = content[pat_paren_start + 1:pat_paren_end]
                pat_strs = list(extract_kotlin_strings(pat_content))

        # Extract keywords from setOf(...)
        kw_match = re.search(r"val kwds\s*=\s*setOf\s*\(", func_body)
        kw_strs = []
        if kw_match:
            kw_paren_start = func_start + kw_match.end() - 1  # position of '('
            kw_paren_end = find_matching_paren(content, kw_paren_start)
            if kw_paren_end >= 0:
                kw_content = content[kw_paren_start + 1:kw_paren_end]
                kw_strs = list(extract_kotlin_strings(kw_content))

        # Build encrypted replacement
        pat_lines = []
        for p in sorted(set(pat_strs)):
            pat_lines.append(f'd("{xor_encrypt(p)}"),')
        kw_lines = []
        for k in sorted(set(kw_strs)):
            kw_lines.append(f'd("{xor_encrypt(k)}"),')

        pat_block = ""
        for pl in pat_lines:
            pat_block += f"\n                    {pl}"
        if pat_block:
            pat_block += "\n                "

        kw_block = ""
        for kl in kw_lines:
            kw_block += f"\n                {kl}"
        if kw_block:
            kw_block += "\n            "

        new_body = f"""\
    private fun generate{func_name}(): List<KeywordEntity> {{
        val patterns = arrayOf({pat_block})
        
        val kwds = setOf({kw_block})

        return patterns.map {{ KeywordEntity(keyword = it, pattern = it, level = "{lv}", type = "PATTERN", banDays = {bd}) }} +
               kwds.map {{ KeywordEntity(keyword = it, pattern = null, level = "{lv}", type = "KEYWORD", banDays = {bd}) }}
    }}"""

        content = content[:func_match.start()] + new_body + content[func_end + 1:]

    with open(SD_PATH, "w", encoding="utf-8") as f:
        f.write(content)
    print("  ✅ SecurityDataSeeder.kt updated — all keywords XOR-encrypted")
    return True


def encrypt_txt_file():
    """Encrypt keywords_extended.txt and save as .enc"""
    print("[*] Encrypting keywords_extended.txt ...")
    enc_path = TXT_PATH + ".enc"

    with open(TXT_PATH, "r", encoding="utf-8") as f:
        plain = f.read()

    encrypted = xor_encrypt(plain)

    with open(enc_path, "w", encoding="utf-8") as f:
        f.write(encrypted)

    print(f"  ✅ keywords_extended.txt → {enc_path} ({len(encrypted)} hex chars)")
    return True


def main():
    print("=" * 60)
    print("LianYu Keyword Obfuscator")
    print(f"Key (SM3): {_KEY.hex()[:16]}...")
    print("=" * 60)

    ok1 = process_contentfilter()
    ok2 = process_securitydataseeder()
    ok3 = encrypt_txt_file()

    if ok1 and ok2 and ok3:
        print("\n✅ All keywords obfuscated.")
        print("   - ContentFilter.kt: buildKeywords() now uses encrypted hex")
        print("   - SecurityDataSeeder.kt: all generateXxxKeywords() use encrypted hex")
        print("   - keywords_extended.txt.enc: encrypted keywords backup")
        print("\n   Rebuild with: ./gradlew assembleRelease")
    else:
        print("\n❌ Some files failed. Check errors above.")
        sys.exit(1)


if __name__ == "__main__":
    main()
