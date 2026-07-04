package com.lianyu.ai.network

import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity as CompanionModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * AiContextTools — 时间格式化 + 上下文压缩 + 记忆提取。
 * 从 AiService 解耦的纯函数集合, 无状态依赖。
 */
object AiContextTools {

    data class CompressedContext(
        val summary: String,
        val keptMessages: List<ChatMessage>,
        val compressedCount: Int
    )

    // === private fun formatTimeAgo(nowMs: Long, timestampMs: Long): String { ===
    internal fun formatTimeAgo(nowMs: Long, timestampMs: Long): String {
        val diffSeconds = (nowMs - timestampMs) / 1000L
        return when {
            diffSeconds < 5 -> "刚刚"
            diffSeconds < 60 -> "${diffSeconds}秒"
            diffSeconds < 3600 -> "${diffSeconds / 60}分"
            else -> {
                val hours = diffSeconds / 3600
                val mins = (diffSeconds % 3600) / 60
                if (hours >= 24) {
                    val days = hours / 24
                    "${days}天${hours % 24}小时"
                } else "${hours}小时${mins}分"
            }
        }
    }

    // === private fun formatCurrentTime(): String { ===
    internal fun formatCurrentTime(): String {
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val min = cal.get(java.util.Calendar.MINUTE)
        val sec = cal.get(java.util.Calendar.SECOND)
        val weekdayNames = mapOf(
            java.util.Calendar.MONDAY to "周一", java.util.Calendar.TUESDAY to "周二",
            java.util.Calendar.WEDNESDAY to "周三", java.util.Calendar.THURSDAY to "周四",
            java.util.Calendar.FRIDAY to "周五", java.util.Calendar.SATURDAY to "周六",
            java.util.Calendar.SUNDAY to "周日"
        )
        val weekdayName = weekdayNames[cal.get(java.util.Calendar.DAY_OF_WEEK)] ?: ""
        return "$weekdayName ${String.format("%02d", hour)}:${String.format("%02d", min)}:${String.format("%02d", sec)}"
    }

    // === private fun formatGapDuration(ms: Long): String { ===
    internal fun formatGapDuration(ms: Long): String {
        val totalSeconds = ms / 1000L
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return when {
            days > 0 -> "${days}天${hours}时${mins}分${secs}秒"
            hours > 0 -> "${hours}时${mins}分${secs}秒"
            mins > 0 -> "${mins}分${secs}秒"
            else -> "${secs}秒"
        }
    }

    // === private fun buildCurrentTimeContext(): String { ===
    fun buildCurrentTimeContext(ntpTimeEnabled: Boolean = false): String {
        val zone = TimeZone.getDefault()
        val formatter = SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm:ss", Locale.CHINA).apply {
            timeZone = zone
        }
        val timeMs = if (ntpTimeEnabled) NtpTimeProvider.getCurrentTimeMs() else System.currentTimeMillis()
        val now = formatter.format(Date(timeMs))
        val source = if (ntpTimeEnabled && NtpTimeProvider.isNtpSynced()) "NTP网络校时" else "设备本地时钟"
        return "当前精确时间：$now（${zone.id}，$source）。如果用户问今天、现在、几点几分几秒、星期几、多久、刚才、明天等时间相关问题，必须以这个精确时间为准，不要猜测或编造。"
    }

    // === private fun compressContext( ===
    internal fun compressContext(
        history: List<ChatMessage>,
        contextLimit: Int,
        companionNameMap: Map<Long, String> = emptyMap(),
        memoryContext: String = "",
        keepRatio: Float = 0.5f,
        minKeep: Int = 6
    ): CompressedContext {
        if (history.size <= contextLimit) {
            return CompressedContext("", history, 0)
        }

        val keepRecent = maxOf(minKeep, (contextLimit * keepRatio).toInt().coerceAtLeast(minKeep))
        val oldMessages = history.dropLast(keepRecent)
        val recentMessages = history.takeLast(keepRecent)

        val summary = buildLocalSummary(oldMessages, companionNameMap, memoryContext)

        return CompressedContext(summary, recentMessages, oldMessages.size)
    }

    // === private fun extractMemoryKeywords(memoryContext: String): Set<String> { ===
    internal fun extractMemoryKeywords(memoryContext: String): Set<String> {
        if (memoryContext.isBlank()) return emptySet()
        val keywords = mutableSetOf<String>()
        val coreSection = Regex("【核心记忆[^】]*】([\\s\\S]*?)(?=【|$)").find(memoryContext)?.groupValues?.get(1) ?: ""
        val relatedSection = Regex("【相关记忆[^】]*】([\\s\\S]*?)(?=【|$)").find(memoryContext)?.groupValues?.get(1) ?: ""

        listOf(coreSection, relatedSection).forEach { section ->
            section.lines().forEach { line ->
                val clean = line.trimStart('-', '[', ']', '【', '】', ' ').trim()
                if (clean.length in 2..30) {
                    keywords.add(clean.lowercase())
                    clean.split(Regex("[，。、；：！？\\s]")).filter { it.length >= 2 }.forEach { kw ->
                        keywords.add(kw.lowercase())
                    }
                }
            }
        }
        return keywords.filter { it.length >= 2 }.take(50).toSet()
    }

    // === private fun buildLocalSummary( ===
    internal fun buildLocalSummary(
        messages: List<ChatMessage>,
        companionNameMap: Map<Long, String> = emptyMap(),
        memoryContext: String = ""
    ): String {
        if (messages.isEmpty()) return ""

        val memoryKeywords = extractMemoryKeywords(memoryContext)

        val highPriority = mutableListOf<Pair<Int, String>>()
        val emotionalMoments = mutableListOf<String>()
        val userMentions = mutableListOf<String>()
        val keyFacts = mutableListOf<String>()
        val otherTopics = mutableSetOf<String>()

        messages.forEach { msg ->
            val role = if (msg.isFromUser) "用户" else (companionNameMap[msg.companionId] ?: "AI")
            val content = msg.content.trim()
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("（.*?）"), "")
                .trim()

            if (content.isBlank() || content.length < 3) return@forEach

            val contentLower = content.lowercase()

            val memoryRelevanceScore = memoryKeywords.count { keyword ->
                contentLower.contains(keyword) || keyword.contains(contentLower.take(4))
            }

            when {
                memoryRelevanceScore >= 2 -> {
                    highPriority.add(Pair(memoryRelevanceScore, "$role: ${content.take(50)}"))
                }
                content.contains(Regex("(喜欢|爱|想|念|开心|难过|生气|害羞|感动|委屈|撒娇|哄|哭|笑|亲|抱|牵手|约会|见面)")) ||
                content.contains(Regex("(呜呜|嘿嘿|嘤|哼|呀|呢|啦|嘛|好想你|宝贝|宝宝|亲爱的)")) -> {
                    emotionalMoments.add("$role: ${content.take(40)}")
                }
                content.contains(Regex("(叫|名字|年龄|生日|地址|电话|工作|学校|专业|记住|别忘了|以后|约定|答应|重要|一定|永远|承诺)")) -> {
                    keyFacts.add(content.take(50))
                }
                else -> {
                    otherTopics.add(content.take(25))
                }
            }

            if (msg.isFromUser && userMentions.size < 5) {
                userMentions.add(content.take(25))
            }
        }

        val sb = StringBuilder()
        sb.appendLine("=== 早期对话摘要（已压缩${messages.size}条消息） ===")

        if (highPriority.isNotEmpty()) {
            sb.appendLine("与记忆相关的关键内容（已存入长期记忆，此处为上下文补充）：")
            highPriority.sortedByDescending { it.first }.take(6).forEach { (_, text) ->
                sb.appendLine("  ★ $text")
            }
            sb.appendLine()
        }

        if (emotionalMoments.isNotEmpty()) {
            sb.appendLine("情感时刻：")
            emotionalMoments.take(4).forEach { sb.appendLine("  - $it") }
        }

        if (keyFacts.isNotEmpty()) {
            sb.appendLine("关键事实/约定：")
            keyFacts.take(3).forEach { sb.appendLine("  - $it") }
        }

        if (otherTopics.size > emotionalMoments.size + keyFacts.size + highPriority.size) {
            val remainingTopics = otherTopics.filter { t ->
                !highPriority.any { it.second.contains(t) } &&
                !emotionalMoments.any { it.contains(t) } &&
                !keyFacts.any { it.contains(t) }
            }.take(5)
            if (remainingTopics.isNotEmpty()) {
                sb.appendLine("讨论过的其他话题：")
                remainingTopics.forEach { sb.append("  - $it") }
            }
        }

        if (memoryContext.isNotBlank() && memoryKeywords.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("（注：以上摘要基于已有${memoryKeywords.size}条记忆关键词动态筛选，与核心/相关记忆重叠的内容已标记★优先保留）")
        }

        return sb.toString().trim()
    }

}
