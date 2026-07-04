package com.lianyu.ai.feature.chat.ui.viewmodel

/**
 * Splits AI response text into multiple segments for simulated human-like typing.
 *
 * Extracted from ChatViewModel as a pure function with no dependencies.
 */
internal object ChatSegmentSplitter {

    private val SPLIT_PARAGRAPH_REGEX = Regex("\\n{2,}")
    private val SPLIT_SENTENCE_REGEX = Regex("(?<=[。！？～…!?~])\\s*")

    /**
     * Split text into segments: first by double-newline (paragraphs),
     * then by sentence-ending punctuation, or return as single segment.
     */
    fun splitIntoSegments(text: String): List<String> {
        val trimmed = text.trim()
        // 先按双换行拆分段落
        val paragraphs = trimmed.split(SPLIT_PARAGRAPH_REGEX).filter { it.isNotBlank() }
        if (paragraphs.size >= 2) return paragraphs.map { it.trim() }
        // 再按句号/感叹号/问号/波浪线/省略号拆分句子
        val sentences = trimmed.split(SPLIT_SENTENCE_REGEX).filter { it.isNotBlank() }
        if (sentences.size >= 2) return sentences.map { it.trim() }
        return listOf(trimmed)
    }
}
