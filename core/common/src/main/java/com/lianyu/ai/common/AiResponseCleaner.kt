package com.lianyu.ai.common

/**
 * AI 回复文本清洗工具。
 *
 * 某些模型会偶发输出英文元前缀（如 "response 怎么啦"）。
 * 一旦该前缀进入历史上下文，模型会持续模仿输出，因此必须在源头统一清理。
 */
object AiResponseCleaner {

    /**
     * 匹配回复开头的 "response" 元前缀（不区分大小写），可跟随空格、中英文冒号，
     * 或紧跟中文字符（某些模型输出 "response怎么啦" 时不加空格）。
     *
     * 使用负向前瞻避免误删 "responseable"、"responses" 等正常英文单词。
     * 例如：
     *   "response 怎么啦" → "怎么啦"
     *   "Response: 你好" → "你好"
     *   "RESPONSE：测试" → "测试"
     *   "response怎么啦" → "怎么啦"
     *   "responses 呢" → "responses 呢"（保留）
     */
    private val RESPONSE_PREFIX_REGEX = Regex("(?i)^\\s*response(?![a-z0-9_])[\\s:：]*")

    /**
     * 移除 [text] 开头的 response 元前缀。
     * 如果移除后内容为空，则保留原始文本，避免把合法短回复误删成空字符串。
     */
    fun stripResponsePrefix(text: String): String {
        if (text.isBlank()) return text
        val stripped = text.replace(RESPONSE_PREFIX_REGEX, "").trim()
        return stripped.ifEmpty { text.trim() }
    }
}
