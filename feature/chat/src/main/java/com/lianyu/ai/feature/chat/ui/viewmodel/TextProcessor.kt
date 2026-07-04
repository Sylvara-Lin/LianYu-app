package com.lianyu.ai.feature.chat.ui.viewmodel

import android.util.Log
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.StickerInfo
import com.lianyu.ai.common.StickerManager

object TextProcessor {

    private val ROLE_PREFIX_REGEX = Regex("(?m)^\\s*\\[(?:角色\\d+|[^\\[\\]]+?)\\]\\s*")
    private val THINK_REGEX = Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>")
    private val ENC_REGEX = Regex("(?m)^enc:\\S+$")
    private val STICKER_REGEX = Regex("\\[([^\\[\\]]+?)\\]")
    private val STICKER_FILE_REGEX = Regex("\\bsticker_\\w+\\.png\\b", RegexOption.IGNORE_CASE)
    private val MULTI_NEWLINE_REGEX = Regex("\\n{2,}")
    private val SYSTEM_TAGS = setOf("语音", "图片", "视频", "文件", "位置", "红包", "转账")

    /**
     * 去除文本中的局部重复子串
     * 支持多种重复模式：
     * 1. X + Y + Y（Y是X的后缀）: "说话了不～话了不～" → "说话了不～"
     * 2. X + Y + Y（Y是X的子串）: "能正常看到了嘛～现在好了吧？正常看到了嘛～现在好了吧？" → "能正常看到了嘛～现在好了吧？"
     * 3. 句子级重复: "老觉得我重复发呀。老觉得我重复发呀。" → "老觉得我重复发呀。"
     * 4. 字符级重复: "好好吃饭饭" → "好好吃饭"
     */
    fun removeLocalRepetition(text: String): String {
        if (text.length < 4) return text
        var result = text

        // 模式1：X + Y + Y（Y是X的后缀）
        // 例如 "说话了不～话了不～" 中 "说话了不～" 的后缀 "话了不～" 重复了
        for (len in result.length / 2 downTo 2) {
            val suffix = result.takeLast(len)
            val beforeSuffix = result.dropLast(len)
            if (beforeSuffix.endsWith(suffix)) {
                result = beforeSuffix
                return removeLocalRepetition(result)
            }
        }

        // 模式2：检测子串重复（非后缀）
        // 例如 "能正常看到了嘛～现在好了吧？正常看到了嘛～现在好了吧？"
        // 其中 "正常看到了嘛～现在好了吧？" 是前面 "能正常看到了嘛～现在好了吧？" 的子串（去掉前缀"能"）
        for (len in result.length / 2 downTo 4) {
            val suffix = result.takeLast(len)
            val beforeSuffix = result.dropLast(len)
            // 检查 suffix 是否是 beforeSuffix 的某个子串
            if (beforeSuffix.contains(suffix)) {
                result = beforeSuffix
                return removeLocalRepetition(result)
            }
            // 也检查去掉标点后的匹配
            val suffixCleaned = suffix.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
            val beforeSuffixCleaned = beforeSuffix.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
            if (suffixCleaned.length >= 4 && beforeSuffixCleaned.endsWith(suffixCleaned)) {
                result = beforeSuffix
                return removeLocalRepetition(result)
            }
        }

        // 模式3：句子级重复检测
        // 按标点分割句子，检测相邻句子是否有重复
        val sentenceDelimiters = Regex("(?<=[。！？.!?])")
        val sentences = result.split(sentenceDelimiters)
        if (sentences.size >= 2) {
            val deduped = mutableListOf<String>()
            for (sentence in sentences) {
                val trimmed = sentence.trim()
                if (trimmed.isEmpty()) continue
                val currentClean = trimmed.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
                // 检查当前句子是否与之前任何句子重复
                var isDuplicate = false
                for (prev in deduped) {
                    val prevClean = prev.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
                    if (currentClean == prevClean ||
                        (currentClean.length >= 4 && prevClean.endsWith(currentClean)) ||
                        (prevClean.length >= 4 && currentClean.endsWith(prevClean))
                    ) {
                        isDuplicate = true
                        break
                    }
                }
                if (!isDuplicate) {
                    deduped.add(trimmed)
                }
            }
            val joined = deduped.joinToString("")
            if (joined.length < result.length) {
                result = joined
                return removeLocalRepetition(result)
            }
        }

        return result
    }

    /**
     * 初始清洗：移除 AI 输出中的各类元标记，避免泄露到聊天界面。
     * 注意：sticker 标记的识别必须在调用本函数之前的原始文本上进行。
     *
     * [P0 FIX] 改进清洗逻辑：
     * - 保留方括号外的正常文本内容（之前会误删括号附近的文本）
     * - 添加清洗前后长度校验，防止短回复被完全清空
     * - 记录清洗日志便于调试
     */
    private fun cleanAiResponseText(text: String): String {
        val originalLength = text.length
        var cleaned = ROLE_PREFIX_REGEX.replace(text, "")
        cleaned = THINK_REGEX.replace(cleaned, "")
        cleaned = ENC_REGEX.replace(cleaned, "").trim()

        // [P0 FIX] 改进的括号处理：只移除匹配的括号对及其内容，保留孤立字符和外部文本
        val sb = StringBuilder(cleaned.length)
        var i = 0
        while (i < cleaned.length) {
            when (cleaned[i]) {
                '[', '【', '{', '<' -> {
                    val closeChar = when (cleaned[i]) {
                        '[' -> ']'
                        '【' -> '】'
                        '{' -> '}'
                        '<' -> '>'
                        else -> null
                    }
                    if (closeChar != null) {
                        val closeIdx = cleaned.indexOf(closeChar, i)
                        if (closeIdx >= 0 && closeIdx - i <= 50) {  // [FIX] 只跳过合理长度的内容（≤50字符）
                            i = closeIdx + 1
                            continue
                        }
                        // 超长或未闭合的括号，保留原字符
                    }
                    sb.append(cleaned[i])
                    i++
                    continue
                }
                ']', '】', '}', '>' -> {
                    // 孤立的右半括号，保留（可能是表情符号或特殊用法）
                    sb.append(cleaned[i])
                    i++
                    continue
                }
                else -> {
                    sb.append(cleaned[i])
                    i++
                }
            }
        }

        var result = sb.toString().trim()
        result = STICKER_FILE_REGEX.replace(result, "")
        result = MULTI_NEWLINE_REGEX.replace(result, "\n")

        // [P0 FIX] 清洗保护：如果清洗后内容过短（<原始20%且<2字符），返回保守结果
        val cleanedLength = result.length
        if (cleanedLength < 2 && originalLength > 5) {
            SecureLog.w("TextProcessor", "Aggressive cleaning detected: $originalLength → $cleanedLength chars, applying conservative cleanup")
            // 保守清洗：只移除think标签和明显的AI标记
            result = ROLE_PREFIX_REGEX.replace(text, "")
            result = THINK_REGEX.replace(result, "")
            result = ENC_REGEX.replace(result, "").trim()
            // 二次尝试：如果仍然太短，只做最小清理
            if (result.length < 2) {
                result = text.trim()
                    .replace(Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>"), "")
                    .replace(Regex("(?is)<thinking[^>]*>[\\s\\S]*?</thinking\\s*>"), "")
                    .trim()
            }
        }

        return result
    }

    /**
     * 最终兜底：如果本轮已发送表情包，且剩余文本恰好是某个表情包的描述/名称，
     * 说明 AI 把表情包描述当正文输出了，需要清空避免重复显示。
     */
    private suspend fun clearLeakedStickerDescription(
        cleanText: String,
        sentStickers: List<StickerInfo>,
        stickerManager: StickerManager
    ): String {
        if (sentStickers.isEmpty() || cleanText.isBlank()) return cleanText
        if (cleanText.length > 20) return cleanText

        val allStickers = stickerManager.getAllStickers()
        val leaked = allStickers.any { s ->
            val desc = s.description
            val name = s.name
            (desc != null && desc.equals(cleanText, ignoreCase = true)) ||
                    name.equals(cleanText, ignoreCase = true)
        }
        return if (leaked) {
            SecureLog.d("TextProcessor", "Cleared leaked sticker description: $cleanText")
            ""
        } else cleanText
    }

    /**
     * 处理AI回复中的表情包（分段/分流模式），支持三种模式：
     * 1. 解析AI输出的 [描述] 标记
     * 2. AI文字中提到表情包但没有标记 → 不在此模式处理
     * 3. 根据概率主动随机发送表情包（当AI没有输出标记时）
     * @return 去除表情包标记后的纯文本
     */
    suspend fun processStickerTagsForSplit(
        text: String,
        stickerManager: StickerManager,
        stickerProbability: Int,
        sendStickerMessage: suspend (StickerInfo) -> Long
    ): String {
        val sentStickers = mutableListOf<StickerInfo>()

        // 模式1：解析AI输出的 [描述] 标记（在原始 text 上匹配）
        val matches = STICKER_REGEX.findAll(text).toList()
        for (match in matches) {
            val desc = match.groupValues[1].trim()
            if (desc in SYSTEM_TAGS) continue
            val sticker = stickerManager.findStickerByDescriptionExact(desc)
            if (sticker != null) {
                sendStickerMessage(sticker)
                sentStickers.add(sticker)
            }
        }

        // 初始清洗：移除所有元标记（包括未匹配的 [xxx]）
        var cleanText = cleanAiResponseText(text)

        // 清理已发送表情的 description/name 残留
        cleanText = removeSentStickerResiduals(cleanText, sentStickers)

        // 模式2：AI没有输出标记，根据概率随机发送
        // [P0 FIX] 只有当清洗后的文本仍有实质内容时才触发概率表情包
        // 避免文本被清空后只剩表情包的"只回复表情包"问题
        if (sentStickers.isEmpty() && stickerProbability > 0 && cleanText.isNotBlank()) {
            val allRules = stickerManager.getAllRules()
            if (allRules.isNotEmpty()) {
                val random = kotlin.random.Random.nextInt(1, 101)
                if (random <= stickerProbability) {
                    val randomRule = allRules.random()
                    val sticker = stickerManager.findStickerByDescription(randomRule.description)
                    if (sticker != null) {
                        sendStickerMessage(sticker)
                        sentStickers.add(sticker)
                        val descToRemove = if (cleanText.contains(randomRule.description)) {
                            randomRule.description
                        } else {
                            sticker.description ?: sticker.name
                        }
                        // [FIX] 只移除描述文字，且移除后检查剩余内容是否仍然有意义
                        if (descToRemove.length >= 2 && cleanText.length > descToRemove.length + 1) {
                            cleanText = cleanText.replace(descToRemove, "")
                            SecureLog.d("TextProcessor", "Removed sticker desc from split text (prob mode): $descToRemove")
                        } else {
                            SecureLog.d("TextProcessor", "Skipping desc removal: would leave text too short (${cleanText.length} - ${descToRemove.length})")
                        }
                        SecureLog.d("TextProcessor", "Auto-sent sticker by probability in split mode: ${sticker.name} ($stickerProbability%)")
                    }
                }
            }
        }

        cleanText = cleanText.trim()
        cleanText = removeLocalRepetition(cleanText)
        cleanText = clearLeakedStickerDescription(cleanText, sentStickers, stickerManager)

        // [P0 FIX] 最终保护：如果处理后的文本为空但原始输入不为空，记录警告
        if (cleanText.isBlank() && text.trim().isNotBlank()) {
            SecureLog.w("TextProcessor", "WARNING: Text processing resulted in blank output. Original: '${text.take(50)}', stickers sent: ${sentStickers.size}")
        }

        return cleanText
    }

    /**
     * 处理AI回复中的表情包，支持三种模式：
     * 1. 解析AI输出的 [描述] 标记
     * 2. AI文字中提到表情包但没有标记 → 智能匹配文字中的关键词发送对应表情包
     * 3. 根据概率主动随机发送表情包（当AI没有输出标记也没有提到表情包时）
     * @return 去除表情包标记后的纯文本
     */
    suspend fun processStickerTags(
        text: String,
        stickerManager: StickerManager,
        stickerProbability: Int,
        stickerSentThisTurn: Boolean,
        sendStickerMessage: suspend (StickerInfo) -> Long
    ): String {
        val sentStickers = mutableListOf<StickerInfo>()

        // 模式1：解析AI输出的 [描述] 标记（在原始 text 上匹配）
        val matches = STICKER_REGEX.findAll(text).toList()

        Log.i("TextProcessor", "=== STICKER PROCESSING ===")
        Log.i("TextProcessor", "Input text: $text")
        Log.i("TextProcessor", "Found ${matches.size} bracket matches: ${matches.map { it.value }}")

        val stickerMatches = matches.filter { match ->
            val desc = match.groupValues[1].trim()
            desc !in SYSTEM_TAGS
        }

        Log.i("TextProcessor", "After filtering system tags: ${stickerMatches.map { it.value }}")

        for (match in stickerMatches) {
            val description = match.groupValues[1].trim()
            Log.i("TextProcessor", "Looking for sticker: [$description]")
            val sticker = stickerManager.findStickerByDescriptionExact(description)
            if (sticker != null) {
                Log.i("TextProcessor", "Found sticker: ${sticker.name} at ${sticker.path}")
                sendStickerMessage(sticker)
                sentStickers.add(sticker)
            } else {
                Log.w("TextProcessor", "Sticker not found: [$description], removing tag")
            }
        }
        Log.i("TextProcessor", "=== END STICKER PROCESSING ===")

        // 初始清洗：移除所有元标记（包括未匹配的 [xxx]）
        var cleanText = cleanAiResponseText(text)

        // 清理已发送表情的 description/name 残留
        cleanText = removeSentStickerResiduals(cleanText, sentStickers)

        // 模式2：AI文字中提到表情包但没有输出标记 → 智能匹配（仅在未通过模式1发送时）
        // [P0 FIX] 增加文本内容保护，避免清空后只发表情包
        if (sentStickers.isEmpty() && !stickerSentThisTurn && stickerProbability > 0 && cleanText.isNotBlank()) {
            val allRules = stickerManager.getAllRules()
            if (allRules.isNotEmpty()) {
                val matchedStickers = mutableListOf<Pair<StickerInfo, String>>()
                for (rule in allRules.shuffled()) {
                    val desc = rule.description
                    if (desc.length >= 2 && text.contains(desc)) {
                        val sticker = stickerManager.findStickerByDescription(desc)
                        if (sticker != null) matchedStickers.add(sticker to desc)
                    }
                }
                if (matchedStickers.isNotEmpty()) {
                    val (picked, matchedDesc) = matchedStickers.random()
                    sendStickerMessage(picked)
                    sentStickers.add(picked)
                    val descToRemove = if (cleanText.contains(matchedDesc)) matchedDesc else (picked.description ?: picked.name)
                    if (descToRemove.length >= 2) {
                        cleanText = cleanText.replace(descToRemove, "")
                        SecureLog.d("TextProcessor", "Removed sticker desc from text: $descToRemove (matched: $matchedDesc)")
                    }
                    cleanText = removeLocalRepetition(cleanText)
                    SecureLog.d("TextProcessor", "Matched sticker from text: ${picked.name}")
                } else {
                    val random = kotlin.random.Random.nextInt(1, 101)
                    if (random <= stickerProbability) {
                        val allStickers = stickerManager.getAllStickers()
                        if (allStickers.isNotEmpty()) {
                            val randomSticker = allStickers.random()
                            sendStickerMessage(randomSticker)
                            sentStickers.add(randomSticker)
                            val descToRemove = randomSticker.description ?: randomSticker.name
                            if (descToRemove.length >= 2) {
                                cleanText = cleanText.replace(descToRemove, "")
                                SecureLog.d("TextProcessor", "Removed random sticker desc from text: $descToRemove")
                            }
                        }
                    }
                }
            }
        }

        cleanText = cleanText.trim().replace(Regex("\\n{2,}"), "\n")
        cleanText = removeLocalRepetition(cleanText)
        cleanText = clearLeakedStickerDescription(cleanText, sentStickers, stickerManager)
        return cleanText
    }

    /**
     * 清理 cleanText 中可能残留的已发送表情包描述/名称。
     */
    private fun removeSentStickerResiduals(
        text: String,
        sentStickers: List<StickerInfo>
    ): String {
        if (sentStickers.isEmpty()) return text
        var result = text
        val descsToRemove = mutableSetOf<String>()
        for (sticker in sentStickers) {
            sticker.description?.takeIf { it.length >= 2 }?.let { descsToRemove.add(it) }
            sticker.name.takeIf { it.length >= 2 }?.let { descsToRemove.add(it) }
        }
        for (desc in descsToRemove) {
            if (result.contains(desc)) {
                result = result.replace(desc, "")
                SecureLog.d("TextProcessor", "Removed residual sticker text: $desc")
            }
        }
        return result
    }
}
