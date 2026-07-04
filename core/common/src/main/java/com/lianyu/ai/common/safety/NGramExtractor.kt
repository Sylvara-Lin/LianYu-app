package com.lianyu.ai.common.safety

/**
 * n-gram 特征提取器 + 文本归一化（抗绕过）。
 *
 * 归一化策略（在提取前执行）：
 * - 全角→半角（字母/数字/符号）
 * - 去除零宽字符
 * - 去除不可见控制字符
 *
 * 中文：字符级 1-3 gram（中文无天然词边界）
 * 英文/ASCII：词级 1-2 gram（按空白分词）
 * 混合文本：两套并行，合并去重
 */
object NGramExtractor {

    /** 提取 1-3 gram 特征列表（含归一化） */
    fun extract(text: String): List<String> {
        val normalized = normalize(text)
        val cjkGrams = extractCjkCharGrams(normalized)
        val asciiGrams = extractAsciiWordGrams(normalized)
        return (cjkGrams + asciiGrams).distinct()
    }

    // ---- 文本归一化 ----

    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\uFEFF' -> {
                    // 零宽字符：跳过
                }
                ch.isWhitespace() && ch != ' ' && ch != '\n' -> {
                    // 控制字符（除空格和换行）：跳过
                }
                ch in '\uFF01'..'\uFF5E' -> {
                    // 全角 ASCII → 半角
                    sb.append((ch.code - 0xFEE0).toChar())
                }
                ch == '\u3000' -> {
                    // 全角空格 → 半角空格
                    sb.append(' ')
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // ---- CJK 字符级 n-gram ----

    private fun extractCjkCharGrams(text: String): List<String> {
        val chars = text.filter { it.isCjk() }
        if (chars.length < 2) return chars.map { it.toString() }

        // 懒计算：长度 < 30 时全算，否则采样短 gram
        val maxN = if (chars.length > 100) 2 else 3

        val result = mutableListOf<String>()
        for (n in 1..maxN) {
            for (i in 0..chars.length - n) {
                result.add(chars.substring(i, i + n))
            }
        }
        return result
    }

    // ---- ASCII 词级 n-gram ----

    private fun extractAsciiWordGrams(text: String): List<String> {
        val words = text.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 2 }
            .map { it.lowercase() }
        if (words.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        for (n in 1..2) {
            for (i in 0..words.size - n) {
                result.add(words.subList(i, i + n).joinToString(" "))
            }
        }
        return result
    }

    private fun Char.isCjk(): Boolean {
        val cp = this.code
        return cp in 0x4E00..0x9FFF
            || cp in 0x3400..0x4DBF
            || cp in 0x20000..0x2A6DF
            || cp in 0xF900..0xFAFF
            || cp in 0x3040..0x309F
            || cp in 0x30A0..0x30FF
            || cp in 0xAC00..0xD7AF
    }
}
