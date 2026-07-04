package com.lianyu.ai.network.tts

/**
 * TTS 专用文本清洗器（纯函数，无状态）。
 *
 * 参考反编译代码 [H:\aiyu\chat_tts] 的 `CleanerPatterns.java` + `MainActivityKt.cleanMessageForDisplay`，
 * 精简为 TTS 朗读前的清洗规则。**不负责分段**——分段由 [com.lianyu.ai.common.text.MessageSegmenter] 统一处理，
 * 此处仅移除不应当被朗读出来的杂质（贴纸标签、Markdown 标记、指令标签、括号内心戏）。
 *
 * 清洗顺序（与参考一致）：
 * 1. 移除 `<voice:> <note:> <web_search:> <start_voice_call>` 等内部指令标签
 * 2. （可选）移除括号内容 `<...> (...) （...）` ——跳过 AI 的"内心戏"
 * 3. 移除方括号贴纸 `[微笑]`
 * 4. 移除 Markdown 标记 `*_~`
 */
object TtsTextCleaner {

    /** 移除 `<voice:url>` `<note:...>` `<web_search:...>` `<start_voice_call>` 等指令标签 */
    private val TAG_REMOVE = Regex("<[a-z_]+:?[^>]*>")

    /**
     * 移除括号内容（内心戏）：
     * - `<...>` 尖括号（指令标签的兜底，与 [TAG_REMOVE] 叠加）
     * - `(...)` 英文括号
     * - `（...）` 中文括号
     *
     * 注意：`[\s\S]` 跨行匹配，避免多行内心戏遗漏。
     */
    private val PARENTHESES_REMOVE = Regex("(<[\\s\\S]*?>|\\([^\\)]*?\\)|（[^）]*?）)")

    /** 移除 `[微笑]` 这类方括号贴纸标签 */
    private val TTS_BRACKET_REMOVE = Regex("\\[[^\\]]+\\]")

    /** 移除 Markdown 加粗/斜体/删除线标记 `* _ ~` */
    private val TTS_MARKDOWN_REMOVE = Regex("[*_~]")

    /**
     * 清洗文本以供 TTS 朗读。
     *
     * @param text 原始 AI 回复文本（可能是分段后的子串）
     * @param skipParentheses 是否跳过括号内容（用户可配置 `key_skip_parentheses`）
     * @return 清洗后的可朗读文本；若全被移除则返回空串（调用方应跳过空串）
     */
    fun clean(text: String, skipParentheses: Boolean): String {
        var s = text
        // 1. 指令标签（与参考 cleanMessageForDisplay 思路一致，但精简为 TTS 专用）
        s = TAG_REMOVE.replace(s, "")
        // 2. 括号内心戏（可选）
        if (skipParentheses) {
            s = PARENTHESES_REMOVE.replace(s, "")
        }
        // 3. 方括号贴纸
        s = TTS_BRACKET_REMOVE.replace(s, "")
        // 4. Markdown 标记
        s = TTS_MARKDOWN_REMOVE.replace(s, "")
        return s.trim()
    }
}
