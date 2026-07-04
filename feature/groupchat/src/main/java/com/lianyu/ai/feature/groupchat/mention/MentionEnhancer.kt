package com.lianyu.ai.feature.groupchat.mention

import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.domain.AiServiceProvider
import kotlin.text.MatchResult

object MentionEnhancer {

    private val highConfidencePatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""(\S{2,})(觉得呢|怎么看|你呢|呢\?|认为呢|的想法|怎么说)""") to { matchResult: MatchResult ->
            matchResult.groupValues[1]
        },
        Regex("""(你觉得呢|你怎么看|你来回答|你说说|你的看法)""") to { _: MatchResult -> "你" }
    )

    suspend fun enhanceMentionsForAssistantReply(
        content: String,
        speakerName: String,
        members: List<CompanionEntity>,
        aiService: AiServiceProvider? = null,
        judgeEnabled: Boolean = false,
        judgeThreshold: Float = 0.8f,
        recentContext: List<MentionMessageSnapshot> = emptyList()
    ): String {
        if (MentionParser.hasAnyMention(content)) return content

        for ((pattern, nameExtractor) in highConfidencePatterns) {
            val match = pattern.find(content)
            if (match != null) {
                val targetName = nameExtractor(match)
                if (targetName == "你") return "@你 $content"
                val targetMember = members.find { it.name == targetName || it.name.contains(targetName) }
                if (targetMember != null && targetMember.name != speakerName) {
                    return "@${targetMember.name} $content"
                }
            }
        }

        if (judgeEnabled && aiService != null && looksLikeMention(content)) {
            return tryJudgeAndEnhance(content, speakerName, members, aiService, judgeThreshold, recentContext)
        }

        return content
    }

    private fun looksLikeMention(content: String): Boolean {
        return content.contains("？") || content.contains("?") ||
                content.contains("觉得呢") || content.contains("怎么看") ||
                content.contains("你呢") || content.contains("怎么说")
    }

    private suspend fun tryJudgeAndEnhance(
        content: String,
        speakerName: String,
        members: List<CompanionEntity>,
        aiService: AiServiceProvider,
        threshold: Float,
        recentContext: List<MentionMessageSnapshot>
    ): String {
        val candidateList = members.map { it.name } + "你"
        val contextSummary = recentContext.takeLast(6).joinToString("\n") { msg ->
            "${msg.speakerName}: ${msg.content.take(80)}"
        }

        val judgePrompt = """判断以下AI回复是否需要@某人。只返回JSON，不要其他内容。
格式：{"shouldMention":true/false,"target":"名或NONE","confidence":0~1}

说话者：$speakerName
候选列表：${candidateList.joinToString(",")}
当前回复：$content
最近上下文：
$contextSummary"""

        try {
            val rawResponse = aiService.callJudge(judgePrompt)
            val jsonStr = rawResponse.trim().removePrefix("```json").removeSuffix("```").trim()
            val shouldMention = Regex(""""shouldMention"\s*:\s*(true|false)""").find(jsonStr)?.groupValues?.get(1)?.toBoolean() ?: false
            val target = Regex(""""target"\s*:\s*"([^"]+)""").find(jsonStr)?.groupValues?.get(1) ?: "NONE"
            val confidence = Regex(""""confidence"\s*:\s*([\d.]+)""").find(jsonStr)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

            if (shouldMention && confidence >= threshold && target != "NONE") {
                if (target == "你" || members.any { it.name == target }) {
                    return "@$target $content"
                }
            }
        } catch (_: Exception) {}

        return content
    }
}
