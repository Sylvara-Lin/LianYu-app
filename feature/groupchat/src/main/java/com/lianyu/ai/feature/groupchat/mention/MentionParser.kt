package com.lianyu.ai.feature.groupchat.mention

import com.lianyu.ai.database.model.CompanionEntity

object MentionParser {

    fun extractMentionedCharacterIds(
        content: String,
        members: List<CompanionEntity>
    ): Set<Long> {
        val mentionedIds = mutableSetOf<Long>()
        for (member in members) {
            if (content.contains("@${member.name}")) {
                mentionedIds.add(member.id)
            }
        }
        return mentionedIds
    }

    fun extractAllMentions(content: String): List<String> {
        val pattern = Regex("@(\\S+?)(?=\\s|$|@|[！？。,，.!?])")
        return pattern.findAll(content).map { it.groupValues[1] }.toList()
    }

    fun hasAnyMention(content: String): Boolean {
        return content.contains("@")
    }

    fun buildGroupContextBlock(currentCharacterName: String, otherMembers: List<CompanionEntity>): String {
        val otherNames = otherMembers.joinToString("、") { it.name }
        return """
=== 群聊信息 ===
你在群聊中。群里还有: $otherNames。可自然互动。
可连发 1~2 条短消息，多条用空行分隔。

=== @机制（非常重要）===
1) 你拥有 @别人的权力，使用「@角色名」点名；
2) 被@的对象在后续轮次更容易优先回应你；
3) 希望某角色回答或接话题时，应优先 @；
4) 若有人 @ 了你，请优先回应对方，再补充自己观点；
5) @ 要克制：每条最多 @1 人，确实需要点名时才用。

表达约束：只能以你自己（$currentCharacterName）身份发言，禁止代替他人、禁止「角色名:台词」剧本格式。
""".trimIndent()
    }

    fun buildMentionContext(
        currentCharacterName: String,
        recentMessages: List<MentionMessageSnapshot>,
        userName: String = "用户",
        maxContextMessages: Int = 12
    ): String {
        val mentions = recentMessages
            .takeLast(maxContextMessages)
            .filter { it.content.contains("@$currentCharacterName") }
            .takeLast(3)

        if (mentions.isEmpty()) return ""

        val contextLines = mentions.joinToString("\n") { msg ->
            val speaker = if (msg.isUser) userName else msg.speakerName
            val summary = msg.content.take(120)
            "- $speaker 提及你: $summary"
        }

        return """
=== 被 @ 提及上下文 ===
最近有人明确 @你，请优先回应：
$contextLines
""".trimIndent()
    }

    fun formatHistoryForGroup(
        messages: List<GroupHistoryItem>,
        currentCompanionId: Long,
        contextWindow: Int = 20
    ): List<GroupHistoryItem> {
        return messages.takeLast(contextWindow).map { msg ->
            if (!msg.isUser && msg.companionId != currentCompanionId) {
                msg.copy(prefix = "${msg.speakerName}: ")
            } else {
                msg
            }
        }
    }
}

data class MentionMessageSnapshot(
    val content: String,
    val isUser: Boolean,
    val speakerName: String
)

data class GroupHistoryItem(
    val role: String,
    val content: String,
    val isUser: Boolean,
    val companionId: Long,
    val speakerName: String = "",
    val prefix: String = ""
)
