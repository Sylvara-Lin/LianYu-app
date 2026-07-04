package com.lianyu.ai.feature.wechat.data

import android.content.Context
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.common.StickerInfo
import com.lianyu.ai.common.StickerManager
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.repository.ChatRepository
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.MemoryRepository
import com.lianyu.ai.database.repository.filterDecrypted
import com.lianyu.ai.feature.wechat.data.model.M0
import com.lianyu.ai.feature.wechat.data.model.M1
import com.lianyu.ai.feature.wechat.data.model.M1Type
import com.lianyu.ai.feature.wechat.data.model.M2
import com.lianyu.ai.feature.wechat.service.WeChatServiceLocator
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiMessageType
import com.lianyu.ai.domain.AiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class WeChatChatBridge(
    private val context: Context,
    private val weChatRepository: WeChatMessageRepository
) {
    private val database = AppDatabase.getDatabase(context)
    private val deviceId = DeviceIdProvider.getDeviceId(context)
    private val chatRepository = ChatRepository(database.chatMessageDao())
    private val companionRepository = CompanionRepository(database.companionDao())
    private val memoryRepository = MemoryRepository(database.memoryDao(), deviceId)
    private val tokenStore = WeChatTokenStore(context)
    private val aiServiceProvider: AiServiceProvider by lazy {
        ServiceRegistry.get(AiServiceProvider::class.java)
            ?: throw IllegalStateException("AiServiceProvider not registered in ServiceRegistry")
    }
    private val mappingManager = WeChatUserMappingManager(tokenStore, companionRepository)
    private val bridgeJob = SupervisorJob()
    private val bridgeScope = CoroutineScope(bridgeJob + Dispatchers.IO)

    suspend fun handleIncomingMessage(message: M0): String? = withContext(Dispatchers.IO) {
        try {
            val wechatUserId = message.fromUserId ?: return@withContext null
            val text = extractText(message) ?: return@withContext null
            if (text.isBlank()) return@withContext null

            // 封禁检查
            if (com.lianyu.ai.common.BanManager.isBanned(context)) return@withContext null

            val companionId = mappingManager.getOrCreateMapping(wechatUserId)
                ?: return@withContext null

            val companion = companionRepository.getCompanionById(companionId)
                ?: return@withContext null

            // 安全检查：先检查后入库，避免违规原文持久化
            val filterResult = com.lianyu.ai.common.ContentFilter.checkInput(text)
            if (filterResult.isViolating) {
                android.util.Log.w("WeChatBridge", "Input blocked by safety filter: ${filterResult.reason}")
                com.lianyu.ai.common.BanManager.recordViolation(context, filterResult.level)
                val blockedResponse = "抱歉，我无法处理这个话题。"
                val blockedMsg = ChatMessage(
                    companionId = companionId,
                    content = blockedResponse,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                chatRepository.sendMessage(blockedMsg)
                return@withContext blockedResponse
            }

            val userMessage = ChatMessage(
                companionId = companionId,
                content = text,
                isFromUser = true,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.sendMessage(userMessage)
            companionRepository.updateTimestamp(companionId)

            val history = chatRepository.getRecentMessagesSync(companionId, limit = 30)
                .filterDecrypted()
            // 非流式 — AI 输出不允许流式
            val aiResponse = try {
                aiServiceProvider.sendMessage(companion.toAiCompanionInfo(), history.toAiChatMessages(), 0)
            } catch (e: Exception) {
                android.util.Log.e("WeChatBridge", "sendMessage failed", e)
                AiResponse(content = e.message ?: "API 错误")
            }
            val aiResponseText = aiResponse.content
            val hasReceivedContent = aiResponseText.isNotBlank()

            // 安全检查：拦截 AI 违规输出
            if (aiResponseText.isNotBlank()) {
                val outputSafetyResult = com.lianyu.ai.common.ContentFilter.checkOutputSafety(aiResponseText)
                if (!outputSafetyResult.isSafe) {
                    android.util.Log.w("WeChatBridge", "AI output blocked by safety filter: ${outputSafetyResult.level} - ${outputSafetyResult.reason}")
                    // [R2 FIX] AI 生成内容不应累加用户封禁——模型输出不是用户的责任（与 AiService/ChatViewModel 策略对齐）
                    val blockedResponse = "抱歉，我无法回应这个话题。"
                    val blockedMsg = ChatMessage(
                        companionId = companionId,
                        content = blockedResponse,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    chatRepository.sendMessage(blockedMsg)
                    return@withContext blockedResponse
                }
            }

            val msg = ChatMessage(
                companionId = companionId,
                content = aiResponseText.ifBlank { "API返回空内容" },
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
            val aiMessageId = chatRepository.sendMessageAndGetId(msg)

            if (aiMessageId > 0) {
                val processed = runCatching { extractStickerTags(aiResponseText) }
                    .getOrDefault(Pair(aiResponseText, emptyList<StickerInfo>()))
                if (processed.first.isNotEmpty() && processed.first != aiResponseText) {
                    chatRepository.updateMessageContent(aiMessageId, processed.first)
                }
            }

            if (aiMessageId > 0) {
                companionRepository.updateTimestamp(companionId)
                companionRepository.increaseIntimacy(companionId, 2)
                bridgeScope.launch {
                    runCatching { memoryRepository.extractAndSaveMemories(companionId, text, aiResponseText) }
                }
            }

            if (tokenStore.getForwardEnabled() && aiResponseText.isNotBlank()) {
                val (cleanText, stickers) = runCatching { extractStickerTags(aiResponseText) }
                    .getOrDefault(Pair(aiResponseText, emptyList<StickerInfo>()))

                android.util.Log.d("WeChatBridge", "Forward: cleanText length=${cleanText.length}, stickers count=${stickers.size}")

                val isTextMeaningful = cleanText.isNotBlank() &&
                        cleanText.length > 1 &&
                        !cleanText.all { it.isWhitespace() } &&
                        cleanText != "\u200B"

                if (stickers.isNotEmpty() && !isTextMeaningful) {
                    android.util.Log.d("WeChatBridge", "Only stickers, no meaningful text to send")
                } else if (isTextMeaningful) {
                    val finalText = cleanText.trim()
                                .replace(Regex("\\s+"), " ")
                                .replace(Regex("^[\\[\\]\\s，。！？、]+"), "")
                                .replace(Regex("[\\[\\]\\s，。！？、]+$"), "")
                                .trim()
                    if (finalText.length >= 1) {
                        val sendResult = weChatRepository.sendTextMessage(wechatUserId, finalText)
                        android.util.Log.d("WeChatBridge", "Text sent result: ${if (sendResult.isSuccess) "OK" else sendResult.exceptionOrNull()?.message}")
                    }
                }

                stickers.forEachIndexed { index, sticker ->
                    bridgeScope.launch {
                        runCatching {
                            android.util.Log.d("WeChatBridge", "Sending sticker[$index]: name=${sticker.name}, path=${sticker.path}")
                            val bytes = loadStickerBytes(sticker)
                            if (bytes != null) {
                                android.util.Log.d("WeChatBridge", "Sticker bytes loaded: ${bytes.size}, sending...")
                                val imgResult = weChatRepository.sendImageMessage(
                                    toUserId = wechatUserId,
                                    imageBytes = bytes,
                                    fileName = sticker.fileName ?: "sticker.png",
                                    description = ""
                                )
                                android.util.Log.d("WeChatBridge", "Sticker[$index] sent: ${if (imgResult.isSuccess) "OK" else imgResult.exceptionOrNull()?.message}")
                            } else {
                                android.util.Log.w("WeChatBridge", "Sticker[$index] bytes is NULL!")
                            }
                        }.onFailure { e ->
                            android.util.Log.e("WeChatBridge", "Error sending sticker[$index]", e)
                        }
                    }
                }
            }

            aiResponseText.ifBlank { null }
        } catch (e: Exception) {
            android.util.Log.e("WeChatBridge", "Error handling incoming message", e)
            null
        }
    }

    suspend fun handleTextMessage(wechatUserId: String, text: String): String? = withContext(Dispatchers.IO) {
        val message = M0(
            fromUserId = wechatUserId,
            toUserId = "",
            itemList = listOf(
                M1(type = 1, textItem = M2(text = text))
            )
        )
        handleIncomingMessage(message)
    }

    suspend fun handleImageMessage(wechatUserId: String, message: M0): String? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("WeChatBridge", "handleImageMessage called for $wechatUserId")

            val companionId = mappingManager.getOrCreateMapping(wechatUserId)
                ?: run {
                    android.util.Log.e("WeChatBridge", "Failed to get/create mapping for $wechatUserId")
                    return@withContext null
                }

            val companion = companionRepository.getCompanionById(companionId)
                ?: run {
                    android.util.Log.e("WeChatBridge", "Companion not found for id=$companionId")
                    return@withContext null
                }

            val imageItem = message.itemList?.firstOrNull { it.type == M1Type.IMAGE.value }?.imageItem
            if (imageItem == null) {
                android.util.Log.w("WeChatBridge", "No image item found in message")
                return@withContext null
            }

            android.util.Log.d("WeChatBridge", "Image item found, cdnInfo present=${imageItem.cdnImg != null}")

            val imagePath = downloadImageFromCdn(message, imageItem)

            if (imagePath == null) {
                android.util.Log.e("WeChatBridge", "Failed to download image, sending fallback response")
                val fallbackResponse = "收到您的图片了！不过暂时无法识别图片内容，可能是因为SDK版本限制。您可以描述一下图片内容，我会尽力帮助您~"
                weChatRepository.sendTextMessage(wechatUserId, fallbackResponse)
                return@withContext fallbackResponse
            }

            android.util.Log.d("WeChatBridge", "Image downloaded successfully: $imagePath")

            val userMessage = ChatMessage(
                companionId = companionId,
                content = imagePath,
                isFromUser = true,
                timestamp = System.currentTimeMillis(),
                type = com.lianyu.ai.database.model.MessageType.IMAGE,
                linkString = imagePath
            )
            chatRepository.sendMessage(userMessage)
            companionRepository.updateTimestamp(companionId)

            // 安全检查：图片消息暂无法做内容审核，跳过输入安全检查
            // TODO: 接入 OCR/视觉模型后对图片描述文本进行安全过滤

            val history = chatRepository.getRecentMessagesSync(companionId, limit = 30)
                .filterDecrypted()

            val aiResponse = aiServiceProvider.sendMessageWithImage(
                companion.toAiCompanionInfo(), history.toAiChatMessages(), imagePath
            )
            val responseText = aiResponse.content

            // 安全检查：拦截 AI 违规输出
            if (responseText.isNotBlank()) {
                val outputSafetyResult = com.lianyu.ai.common.ContentFilter.checkOutputSafety(responseText)
                if (!outputSafetyResult.isSafe) {
                    android.util.Log.w("WeChatBridge", "Vision AI output blocked by safety filter: ${outputSafetyResult.level} - ${outputSafetyResult.reason}")
                    // [R2 FIX] AI 生成内容不应累加用户封禁
                    val blockedResponse = "抱歉，我无法回应这个话题。"
                    weChatRepository.sendTextMessage(wechatUserId, blockedResponse)
                    val blockedMsg = ChatMessage(
                        companionId = companionId,
                        content = blockedResponse,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    chatRepository.sendMessage(blockedMsg)
                    return@withContext blockedResponse
                }
            }

            if (responseText.isNotBlank()) {
                val (cleanText, stickers) = runCatching { extractStickerTags(responseText) }.getOrDefault(Pair(responseText, emptyList()))

                val sendResult = weChatRepository.sendTextMessage(wechatUserId, cleanText)
                if (sendResult.isSuccess) {
                    android.util.Log.d("WeChatBridge", "Vision AI response sent to $wechatUserId")
                } else {
                    android.util.Log.e("WeChatBridge", "Failed to send vision response: ${sendResult.exceptionOrNull()?.message}")
                }

                if (stickers.isNotEmpty()) {
                    stickers.forEachIndexed { index, sticker ->
                        bridgeScope.launch {
                            runCatching {
                                android.util.Log.d("WeChatBridge", "Sending vision sticker[$index]: name=${sticker.name}, path=${sticker.path}")
                                val bytes = loadStickerBytes(sticker)
                                if (bytes != null) {
                                    android.util.Log.d("WeChatBridge", "Vision sticker bytes loaded: ${bytes.size}, sending...")
                                    val imgResult = weChatRepository.sendImageMessage(
                                        toUserId = wechatUserId,
                                        imageBytes = bytes,
                                        fileName = sticker.fileName ?: "sticker.png",
                                        description = sticker.description ?: sticker.name
                                    )
                                    android.util.Log.d("WeChatBridge", "Vision sticker[$index] sent: ${if (imgResult.isSuccess) "OK" else imgResult.exceptionOrNull()?.message}")
                                } else {
                                    android.util.Log.w("WeChatBridge", "Vision sticker[$index] bytes is NULL!")
                                }
                            }.onFailure { e ->
                                android.util.Log.e("WeChatBridge", "Error sending vision sticker[$index]", e)
                            }
                        }
                    }
                }
            }

            val aiMessage = ChatMessage(
                companionId = companionId,
                content = responseText,
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.sendMessage(aiMessage)

            runCatching {
                memoryRepository.extractAndSaveMemories(companionId, "[图片]", responseText)
            }.onFailure {
                android.util.Log.e("WeChatBridge", "Memory save failed for vision: ${it.message}")
            }

            responseText.ifBlank { null }
        } catch (e: Exception) {
            android.util.Log.e("WeChatBridge", "Error in handleImageMessage", e)
            val errorResponse = "图片识别过程中出现错误: ${e.message}. 请稍后重试或发送文字描述。"
            runCatching {
                weChatRepository.sendTextMessage(wechatUserId, errorResponse)
            }
            errorResponse
        }
    }

    private suspend fun downloadImageFromCdn(message: M0, imageItem: com.lianyu.ai.feature.wechat.data.model.M3): String? {
        return try {
            val sdkClient = WeChatServiceLocator.sdkClientManager(context)

            val tempFile = File(context.cacheDir, "wechat_img_${System.currentTimeMillis()}.jpg")

            when {
                imageItem.cdnImg != null -> {
                    android.util.Log.d("WeChatBridge", "Attempting to download image via SDK CDN")

                    runCatching {
                        val downloadedBytes = sdkClient.downloadMedia(
                            imageItem.cdnImg
                        )

                        if (downloadedBytes != null && downloadedBytes.size > 0) {
                            tempFile.writeBytes(downloadedBytes)
                            tempFile.absolutePath
                        } else {
                            android.util.Log.w("WeChatBridge", "SDK returned empty bytes for image")
                            null
                        }
                    }.getOrNull()
                }
                else -> {
                    android.util.Log.w("WeChatBridge", "No CDN info available for image")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeChatBridge", "Failed to download image from CDN", e)
            null
        }
    }

    fun close() {
        bridgeJob.cancel()
    }

    private fun extractText(message: M0): String? {
        return message.itemList?.firstNotNullOfOrNull { item ->
            item.textItem?.text
        }
    }

    private fun extractStickerTags(text: String): Pair<String, List<StickerInfo>> {
        val stickerManager = StickerManager.getInstance(context)
        val systemTags = setOf("语音", "图片", "视频", "文件", "位置", "红包", "转账")
        val stickerRegex = Regex("\\[([^\\[\\]]+?)\\]")
        val fileNamePattern = Regex("^[a-zA-Z0-9_\\-]+\\.(png|jpg|jpeg|gif|webp)$", RegexOption.IGNORE_CASE)
        val matches = stickerRegex.findAll(text).toList()

        val stickers = mutableListOf<StickerInfo>()
        val sentStickerDescs = mutableSetOf<String>()
        var cleanText = text

        val rolePrefixRegex = Regex("(?m)^\\s*\\[(?:角色\\d+|[^\\[\\]]+?)\\]\\s*")
        cleanText = rolePrefixRegex.replace(cleanText, "")

        val thinkRegex = Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>")
        cleanText = thinkRegex.replace(cleanText, "")

        val encRegex = Regex("(?m)^enc:\\S+$")
        cleanText = encRegex.replace(cleanText, "")

        for (match in matches) {
            val description = match.groupValues[1].trim()
            if (description in systemTags) continue

            var found = false
            val sticker = stickerManager.findStickerByDescriptionExact(description)
                ?: stickerManager.findStickerByDescription(description)
            if (sticker != null) {
                // 防止同一回合重复添加相同的表情包
                if (stickers.none { it.name == sticker.name }) {
                    stickers.add(sticker)
                    found = true
                } else {
                    android.util.Log.d("WeChatBridge", "Duplicate sticker skipped: ${sticker.name}")
                }
            }
            // 无论是否成功匹配，都记录描述用于后续清理
            sentStickerDescs.add(description)
            sticker?.description?.let { sentStickerDescs.add(it) }
            cleanText = cleanText.replace(match.value, "")
            if (!found && fileNamePattern.matches(description)) {
                android.util.Log.d("WeChatBridge", "Removed unmatched sticker file tag: [$description]")
            }
            if (!found && !fileNamePattern.matches(description)) {
                android.util.Log.w("WeChatBridge", "Removed unmatched sticker tag: [$description]")
            }
        }

        // 同时清理 cleanText 中可能残留的已发送表情包描述文字（如AI同时输出 [爱你] 和 爱你）
        for (desc in sentStickerDescs) {
            if (desc.length >= 2 && cleanText.contains(desc)) {
                cleanText = cleanText.replace(desc, "")
                android.util.Log.d("WeChatBridge", "Removed residual sticker desc from text: $desc")
            }
        }

        // 清理孤立的 ] 和 [（防止格式异常的残留）
        cleanText = cleanText.replace("]", "").replace("[", "")
        // 清理残留的 sticker 文件名
        cleanText = Regex("\\bsticker_\\w+\\.png\\b", RegexOption.IGNORE_CASE).replace(cleanText, "")

        // 额外清理：如果已发送的表情包的 description/name 还残留在 cleanText 中，也移除
        for (sticker in stickers) {
            val desc = sticker.description
            if (!desc.isNullOrBlank() && desc.length >= 2 && cleanText.contains(desc)) {
                cleanText = cleanText.replace(desc, "")
                android.util.Log.d("WeChatBridge", "Removed sticker desc from text (extra): $desc")
            }
            val name = sticker.name
            if (name.length >= 2 && cleanText.contains(name)) {
                cleanText = cleanText.replace(name, "")
                android.util.Log.d("WeChatBridge", "Removed sticker name from text (extra): $name")
            }
        }

        var result = cleanText.trim()
            .replace(Regex("\\r\\n|\\r|\\n+"), "，")
            .replace(Regex("，{2,}"), "，")
            .trim()
            .trimStart('，', ',', '.', '。', ' ')

        // 模式2：AI文字中提到表情包但没有输出标记 → 智能匹配（与App端保持一致）
        if (stickers.isEmpty()) {
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
                    // 防止同一回合重复添加相同的表情包
                    if (stickers.none { it.name == picked.name }) {
                        stickers.add(picked)
                        android.util.Log.d("WeChatBridge", "Matched sticker from text: ${picked.name}")
                    } else {
                        android.util.Log.d("WeChatBridge", "Duplicate sticker skipped: ${picked.name}")
                    }
                    // 从 result 中移除描述文字：优先用AI文字中实际匹配到的desc，其次用sticker.description/name
                    val descToRemove = if (result.contains(matchedDesc)) matchedDesc else (picked.description ?: picked.name)
                    if (descToRemove.length >= 2) {
                        result = result.replace(descToRemove, "")
                        android.util.Log.d("WeChatBridge", "Removed sticker desc from text: $descToRemove (matched: $matchedDesc)")
                    }
                    result = removeLocalRepetition(result)
                }
            }
        }

        // 去除局部重复子串（与App端保持一致）
        result = removeLocalRepetition(result)

        return result to stickers
    }

    /**
     * 去除文本中的局部重复子串（与ChatViewModel保持一致）
     */
    private fun removeLocalRepetition(text: String): String {
        if (text.length < 4) return text
        var result = text

        // 模式1：X + Y + Y（Y是X的后缀）
        for (len in result.length / 2 downTo 2) {
            val suffix = result.takeLast(len)
            val beforeSuffix = result.dropLast(len)
            if (beforeSuffix.endsWith(suffix)) {
                result = beforeSuffix
                return removeLocalRepetition(result)
            }
        }

        // 模式2：检测子串重复（非后缀）
        for (len in result.length / 2 downTo 4) {
            val suffix = result.takeLast(len)
            val beforeSuffix = result.dropLast(len)
            if (beforeSuffix.contains(suffix)) {
                result = beforeSuffix
                return removeLocalRepetition(result)
            }
            val suffixCleaned = suffix.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
            val beforeSuffixCleaned = beforeSuffix.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
            if (suffixCleaned.length >= 4 && beforeSuffixCleaned.endsWith(suffixCleaned)) {
                result = beforeSuffix
                return removeLocalRepetition(result)
            }
        }

        // 模式3：句子级重复检测
        val sentenceDelimiters = Regex("(?<=[。！？.!?])")
        val sentences = result.split(sentenceDelimiters)
        if (sentences.size >= 2) {
            val deduped = mutableListOf<String>()
            for (sentence in sentences) {
                val trimmed = sentence.trim()
                if (trimmed.isEmpty()) continue
                val currentClean = trimmed.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
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

    private fun loadStickerBytes(sticker: StickerInfo): ByteArray? {
        return try {
            when {
                sticker.path.startsWith("asset://") -> {
                    val assetPath = sticker.path.removePrefix("asset://")
                    context.assets.open(assetPath).use { it.readBytes() }
                }
                else -> File(sticker.path).takeIf { it.exists() }?.readBytes()
            }
        } catch (e: Exception) {
            android.util.Log.e("WeChatBridge", "Failed to load sticker bytes: ${sticker.path}", e)
            null
        }
    }

    private fun com.lianyu.ai.database.model.CompanionEntity.toAiCompanionInfo() = AiCompanionInfo(
        id = id, name = name, personality = personality,
        age = age, backstory = backstory, speakingStyle = speakingStyle,
        systemPrompt = systemPrompt
    )

    private fun com.lianyu.ai.database.model.ChatMessage.toAiChatMessage() = AiChatMessage(
        isFromUser = isFromUser, content = content, timestamp = timestamp,
        type = when (type) {
            com.lianyu.ai.database.model.MessageType.IMAGE -> AiMessageType.IMAGE
            else -> AiMessageType.TEXT
        },
        companionId = companionId
    )

    private fun List<com.lianyu.ai.database.model.ChatMessage>.toAiChatMessages() = map { it.toAiChatMessage() }
}
