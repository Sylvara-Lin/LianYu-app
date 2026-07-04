package com.lianyu.ai.common.text

/**
 * AI 回复分段工具。
 *
 * 集中 ChatViewModel 与 GroupChatViewModel 中同名但实现不同的 splitIntoSegments 逻辑，
 * 通过 [SplitMode] 参数化两种策略。
 */
object MessageSegmenter {

    /** 分段模式 */
    enum class SplitMode {
        /** Chat 模式：按段落正则 + 句子正则简洁拆分 */
        SIMPLE,
        /** Group 模式：按标点逐句 + 长度合并的精细拆分 */
        GROUP
    }

    // ── SIMPLE 模式正则 ──
    private val SPLIT_PARAGRAPH_REGEX = Regex("\\n{2,}")
    private val SPLIT_SENTENCE_REGEX = Regex("(?<=[。！？～…!?~])\\s*")

    /**
     * 分段入口。
     *
     * @param text 原始 AI 回复文本
     * @param mode 拆分模式，默认 [SplitMode.SIMPLE]
     * @return 拆分后的短句列表，至少包含 1 项（原文本）
     */
    fun split(text: String, mode: SplitMode = SplitMode.SIMPLE): List<String> {
        return when (mode) {
            SplitMode.SIMPLE -> splitSimple(text)
            SplitMode.GROUP -> splitGroup(text)
        }
    }

    // ── SIMPLE: 原 ChatViewModel.splitIntoSegments ──
    private fun splitSimple(text: String): List<String> {
        val trimmed = text.trim()
        val paragraphs = trimmed.split(SPLIT_PARAGRAPH_REGEX).filter { it.isNotBlank() }
        if (paragraphs.size >= 2) return paragraphs.map { it.trim() }
        val sentences = trimmed.split(SPLIT_SENTENCE_REGEX).filter { it.isNotBlank() }
        if (sentences.size >= 2) return sentences.map { it.trim() }
        return listOf(trimmed)
    }

    // ── GROUP: 原 GroupChatViewModel.splitIntoSegments + splitLongSegment ──
    private fun splitGroup(text: String): List<String> {
        val cleaned = text.trim().replace(Regex("\\n{2,}"), "\n")
        if (cleaned.length <= 15) return listOf(cleaned)

        val rawSegments = cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        if (rawSegments.size > 1) {
            val result = mutableListOf<String>()
            for (segment in rawSegments) {
                if (segment.length <= 20) {
                    result.add(segment)
                } else {
                    result.addAll(splitLongSegment(segment))
                }
            }
            return result.ifEmpty { listOf(cleaned) }
        }

        return splitLongSegment(cleaned)
    }

    private fun splitLongSegment(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val seg = current.toString().trim()
            if (seg.isNotEmpty()) sentences.add(seg)
            current.clear()
        }

        for (char in text) {
            current.append(char)
            when (char) {
                '。', '！', '？' -> flush()
                '…', '～' -> { if (current.length >= 3) flush() }
                ',', '，' -> {
                    if (current.length >= 10 && current.contains(Regex("[！？。]"))) {
                        flush()
                    }
                }
            }
        }
        flush()

        if (sentences.isEmpty()) return listOf(text)

        val result = mutableListOf<String>()
        var buffer = StringBuilder()

        for (sentence in sentences) {
            val cleanSentence = sentence.trimStart('，', ',', '.', '。', ' ')
            if (cleanSentence.isEmpty()) continue

            if (buffer.length + cleanSentence.length <= 12) {
                if (buffer.isNotEmpty()) buffer.append("，")
                buffer.append(cleanSentence)
            } else {
                if (buffer.isNotEmpty()) {
                    result.add(buffer.toString())
                    buffer = StringBuilder()
                }
                if (cleanSentence.length <= 14) {
                    buffer.append(cleanSentence)
                } else if (cleanSentence.length <= 25) {
                    result.add(cleanSentence)
                } else {
                    val midPoint = cleanSentence.length / 2
                    val startIndex = midPoint.coerceAtLeast(0)
                    val endIndex = (midPoint + 10).coerceAtMost(cleanSentence.length)
                    val searchRange = if (startIndex < endIndex) cleanSentence.substring(startIndex, endIndex) else ""
                    val splitPosInRange = searchRange.indexOfAny(charArrayOf('，', ',', '、'))
                    val splitPos = if (splitPosInRange >= 0) midPoint + splitPosInRange else -1
                    if (splitPos > 0) {
                        result.add(cleanSentence.take(splitPos + 1).trim())
                        buffer.append(cleanSentence.drop(splitPos + 1).trimStart())
                    } else {
                        result.add(cleanSentence.take(14).trimEnd('，', ','))
                        buffer.append(cleanSentence.drop(14).trimStart('，', ','))
                    }
                }
            }
        }
        if (buffer.isNotEmpty()) result.add(buffer.toString())

        return result.ifEmpty { listOf(text) }
    }
}
