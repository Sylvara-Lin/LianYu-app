package com.lianyu.ai.feature.wechat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lianyu.ai.common.StickerManager
import com.lianyu.ai.common.wechat.WeChatBroadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**【已脱敏】接收来自 feature:notification 的广播，将 App 主动生成的消息同步到微信。**/
class WeChatProactiveMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        handleReceive(context, intent, pendingResult)
    }

    companion object {
        private const val TAG = "WeChatProactiveReceiver"
        private val STICKER_PATTERN = Regex("^\\[([^\\[\\]]+)\\]$")
        private val ROLE_PREFIX_REGEX = Regex("(?m)^\\s*\\[(?:角色\\d+|[^\\[\\]]+?)\\]\\s*")
        private val ENC_LEAK_REGEX = Regex("(?m)^enc:\\S+$")
        private val THINK_REGEX = Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>")
        private val FILE_NAME_PATTERN = Regex("^[a-zA-Z0-9_\\-]+\\.(png|jpg|jpeg|gif|webp)$", RegexOption.IGNORE_CASE)

        fun isStickerContent(content: String): Boolean {
            return STICKER_PATTERN.matches(content.trim())
        }

        fun cleanAiContentForWechat(raw: String): String {
            var text = raw
            text = ROLE_PREFIX_REGEX.replace(text, "")
            text = THINK_REGEX.replace(text, "")
            text = ENC_LEAK_REGEX.replace(text, "")
            val bracketRegex = Regex("\\[([^\\[\\]]+?)\\]")
            val systemTags = setOf("语音", "图片", "视频", "文件", "位置", "红包", "转账")
            val cleanedStickerDescs = mutableSetOf<String>()
            text = bracketRegex.replace(text) { match: MatchResult ->
                val inner = match.groupValues[1].trim()
                if (inner in systemTags) {
                    match.value
                } else {
                    cleanedStickerDescs.add(inner)
                    Log.d(TAG, "Cleaned sticker/non-system tag: [$inner]")
                    ""
                }
            }
            // 清理孤立的 ] 和 [（防止格式异常的残留）
            text = text.replace("]", "").replace("[", "")
            // 清理残留的 sticker 文件名
            text = Regex("\\bsticker_\\w+\\.png\\b", RegexOption.IGNORE_CASE).replace(text, "")
            // 清理已知的表情包描述文字残留
            for (desc in cleanedStickerDescs) {
                if (desc.length >= 2 && text.contains(desc)) {
                    text = text.replace(desc, "")
                    Log.d(TAG, "Removed residual sticker desc: $desc")
                }
            }
            // 换行处理：前面是标点的直接删除，否则替换成逗号
            text = text.replace(Regex("(?<=[。！？])\\s*\\n+\\s*"), "")
            text = text.replace(Regex("(?<![。！？])\\s*\\n+\\s*"), "，")
            text = text.replace(Regex("，{2,}"), "，")
                .trim()
                .trimStart('，', ',', '.', '。', ' ')

            // 去除连续重复的短句（如"打算晚上吃什么呀。打算晚上吃什么呀。"）
            val sentences = text.split(Regex("(?<=[。！？])"))
            val deduped = mutableListOf<String>()
            for (sentence in sentences) {
                val trimmed = sentence.trim()
                if (trimmed.isEmpty()) continue
                val currentClean = trimmed.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
                // 检查当前句子是否与之前任何句子重复（支持子串匹配）
                var isDuplicate = false
                for (prev in deduped) {
                    val prevClean = prev.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
                    if (currentClean == prevClean ||
                        (currentClean.length >= 4 && prevClean.endsWith(currentClean)) ||
                        (prevClean.length >= 4 && currentClean.endsWith(prevClean))
                    ) {
                        isDuplicate = true
                        Log.d(TAG, "Removed duplicate sentence: $trimmed (matches previous: $prevClean)")
                        break
                    }
                }
                if (!isDuplicate) {
                    deduped.add(sentence)
                }
            }
            var result = deduped.joinToString("").trim()

            // 额外检测：子串级重复（如"能正常看到了嘛～现在好了吧？正常看到了嘛～现在好了吧？"）
            for (len in result.length / 2 downTo 4) {
                val suffix = result.takeLast(len)
                val beforeSuffix = result.dropLast(len)
                if (beforeSuffix.contains(suffix)) {
                    result = beforeSuffix
                    break
                }
                val suffixCleaned = suffix.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
                val beforeSuffixCleaned = beforeSuffix.trimEnd('。', '！', '？', '，', '.', '!', '?', ',', ' ')
                if (suffixCleaned.length >= 4 && beforeSuffixCleaned.endsWith(suffixCleaned)) {
                    result = beforeSuffix
                    break
                }
            }

            return result
        }

        private fun extractStickerName(content: String): String? {
            return STICKER_PATTERN.find(content.trim())?.groupValues?.get(1)
        }

        fun handleReceive(
            context: Context,
            intent: Intent,
            pendingResult: BroadcastReceiver.PendingResult
        ) {
            val companionId = intent.getLongExtra(WeChatBroadcast.EXTRA_COMPANION_ID, -1L)
            if (companionId == -1L) {
                pendingResult.finish()
                return
            }

            val messageId = intent.getLongExtra(WeChatBroadcast.EXTRA_MESSAGE_ID, -1L)
            val directContent = intent.getStringExtra(WeChatBroadcast.EXTRA_CONTENT)
            val finalContent = intent.getStringExtra(WeChatBroadcast.EXTRA_FINAL_CONTENT)

            // 使用标志位防止 finish() 重复调用：
            // goAsync() 有 10 秒超时，系统可能自动 finish()，
            // 协程 finally 中需检查是否已完成，避免 "Broadcast already finished" 闪退。
            var finishedByCoroutine = false

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    // goAsync() 限制 10 秒，预留 500ms 余量避免系统先 finish
                    withTimeoutOrNull(9500L) {
                        val content: String = if (!finalContent.isNullOrBlank()) {
                            Log.d(TAG, "Using final content from App, skipping DB lookup")
                            finalContent
                        } else if (messageId != -1L) {
                            val db = com.lianyu.ai.database.AppDatabase.getDatabase(context)
                            val msg = db.chatMessageDao().getMessageById(messageId)
                            msg?.content ?: return@withTimeoutOrNull
                        } else {
                            directContent ?: return@withTimeoutOrNull
                        }

                        val tokenStore = WeChatServiceLocator.tokenStore(context)
                        if (!tokenStore.isLoggedIn()) {
                            Log.d(TAG, "Not logged in, skip proactive sync")
                            return@withTimeoutOrNull
                        }
                        if (!tokenStore.getForwardEnabled()) {
                            Log.d(TAG, "Forwarding disabled, skip proactive sync")
                            return@withTimeoutOrNull
                        }

                        val repository = WeChatServiceLocator.messageRepository(context)
                        val wechatUserIds = tokenStore.getWechatUserIdsForCompanionId(companionId)

                        if (wechatUserIds.isEmpty()) {
                            Log.d(TAG, "No WeChat user mapped to companion $companionId")
                            return@withTimeoutOrNull
                        }

                        val account = tokenStore.getAccount()
                        val isSticker = isStickerContent(content)

                        for (wechatUserId in wechatUserIds) {
                            val contextToken = account?.let { tokenStore.getContextToken(it.accountId, wechatUserId) }
                            if (isSticker) {
                                sendStickerToWechat(context, repository, wechatUserId, content, contextToken)
                            } else {
                                val cleanedContent = cleanAiContentForWechat(content)
                                if (cleanedContent.isBlank()) {
                                    Log.d(TAG, "Skipping empty message after cleaning")
                                    continue
                                }
                                val result = repository.sendTextMessage(wechatUserId, cleanedContent, contextToken)
                                result.onSuccess {
                                    Log.d(TAG, "Proactive text sent to $wechatUserId")
                                }.onFailure { error ->
                                    Log.w(TAG, "Failed to send proactive text to $wechatUserId: ${error.message}")
                                }
                            }
                        }
                    } ?: run {
                        Log.w(TAG, "Proactive message sync timed out (9.5s)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending proactive message", e)
                } finally {
                    // 先取消协程 scope，再 finish 广播
                    cancel()
                    // 仅在协程未超时（系统未自动 finish）时调用 finish()
                    if (!finishedByCoroutine) {
                        finishedByCoroutine = true
                        pendingResult.finish()
                    }
                }
            }
        }

        private suspend fun sendStickerToWechat(
            context: Context,
            repository: com.lianyu.ai.feature.wechat.data.WeChatMessageRepository,
            wechatUserId: String,
            content: String,
            contextToken: String?
        ) {
            val stickerName = extractStickerName(content) ?: run {
                Log.w(TAG, "Invalid sticker format: $content")
                return
            }
            val stickerManager = StickerManager.getInstance(context)
            var sticker = stickerManager.findStickerByDescriptionExact(stickerName)
            if (sticker == null) {
                sticker = stickerManager.findStickerByDescription(stickerName)
            }
            if (sticker == null && !stickerName.endsWith(".png")) {
                sticker = stickerManager.findStickerByDescription("$stickerName.png")
            }
            if (sticker == null) {
                val allStickers = stickerManager.getAllStickers()
                sticker = allStickers.find { it.fileName == stickerName || it.name == stickerName }
            }
            if (sticker == null) {
                Log.w(TAG, "Sticker not found: $stickerName, skipping (total stickers: ${StickerManager.getInstance(context).getAllStickers().size})")
                return
            }
            val bytes = try {
                when {
                    sticker.path.startsWith("asset://") -> {
                        val assetPath = sticker.path.removePrefix("asset://")
                        context.assets.open(assetPath).use { it.readBytes() }
                    }
                    else -> File(sticker.path).takeIf { it.exists() }?.readBytes()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load sticker bytes: ${sticker.path}", e)
                null
            }
            if (bytes == null) {
                Log.w(TAG, "Sticker bytes null for: $stickerName")
                return
            }
            val result = repository.sendImageMessage(
                toUserId = wechatUserId,
                imageBytes = bytes,
                fileName = sticker.fileName ?: "sticker.png",
                description = sticker.description ?: sticker.name,
                contextToken = contextToken
            )
            result.onSuccess {
                Log.d(TAG, "Proactive sticker image sent to $wechatUserId: $stickerName")
            }.onFailure { error ->
                Log.w(TAG, "Failed to send proactive sticker to $wechatUserId: ${error.message}")
            }
        }
    }
}
