package com.lianyu.ai.feature.groupchat

import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.model.GroupMessage
import kotlin.random.Random

/**
 * 群聊领域算法 — 纯函数集合，无状态依赖。
 *
 * 从 GroupChatViewModel 解耦，提取以下核心算法：
 * - calculateSpeakingPriority: 发言优先级计算
 * - extractPersonalityTraits: 性格特征提取
 * - detectGroupAtmosphere: 群聊氛围检测
 * - buildEmotionalContext: 情绪上下文构建
 *
 * TODO(Phase6): GroupChatViewModel 中仍保留这 4 个方法的内联实现，
 * 当前所有调用点仍走 ViewModel 内部方法。委托需要对齐以下签名差异：
 * 1. calculateSpeakingPriority: Strategy 多 allCompanionNames 参数（默认 emptyList）
 * 2. buildEmotionalContext: Strategy 通过参数注入 allCompanions 而非直接访问 ViewModel 的 _allCompanions.value
 * 3. generateIsolatedAiReply / runMultiRoundDispatch 的调用点需改为 Strategy.xxx(...)
 */
object GroupChatStrategy {

    /**
     * 计算角色发言优先级。
     *
     * @param companion 当前角色
     * @param historySnapshot 群聊历史
     * @param userMentionedIds 用户 @ 的角色 ID 集合
     * @param allCompanionNames 所有角色名称（用于关键词匹配）
     * @return 优先级分数（越高越优先发言）
     */
    fun calculateSpeakingPriority(
        companion: CompanionEntity,
        historySnapshot: List<GroupMessage>,
        userMentionedIds: Set<Long>,
        allCompanionNames: List<String> = emptyList()
    ): Double {
        var priority = 0.0

        if (userMentionedIds.contains(companion.id)) {
            priority += 100.0
        }

        val mentionedInHistory = historySnapshot
            .takeLast(10)
            .count { it.content.contains("@${companion.name}") && it.companionId != companion.id }
        priority += mentionedInHistory * 30.0

        val lastReplyFromThisChar = historySnapshot
            .filter { it.companionId == companion.id }
            .lastOrNull()
        if (lastReplyFromThisChar == null) {
            priority += 20.0
        } else {
            val timeSinceLastReply = System.currentTimeMillis() - lastReplyFromThisChar.timestamp
            val minutesSince = timeSinceLastReply / (1000 * 60)
            if (minutesSince > 5) {
                priority += 15.0
            }
        }

        val recentRepliesCount = historySnapshot
            .takeLast(8)
            .count { it.companionId == companion.id }
        if (recentRepliesCount == 0) {
            priority += 10.0
        } else if (recentRepliesCount >= 3) {
            priority -= 15.0
        }

        val lastUserMsg = historySnapshot.lastOrNull { it.companionId == -1L }
        if (lastUserMsg != null) {
            val nameLower = companion.name.lowercase()
            if (lastUserMsg.content.lowercase().contains(nameLower)) {
                priority += 5.0
            }
            for (trait in listOf(
                companion.personality.lowercase(),
                companion.speakingStyle?.lowercase() ?: ""
            )) {
                for (keyword in trait.split("，", "、", "。", ",", ".")) {
                    val kw = keyword.trim()
                    if (kw.length >= 2 && lastUserMsg.content.contains(kw)) {
                        priority += 5.0
                        break
                    }
                }
            }
        }

        priority += Random.nextDouble(0.0, 10.0)

        return priority
    }

    /**
     * 从人设文本中提取性格特征文案。
     */
    fun extractPersonalityTraits(companion: CompanionEntity): String {
        val traits = mutableListOf<String>()

        val personality = companion.personality.lowercase()
        when {
            personality.contains("活泼") || personality.contains("开朗") || personality.contains("外向") -> {
                traits.add("你很活跃，喜欢主动说话，话比较多")
                traits.add("经常发表情包和语气词")
            }
            personality.contains("内向") || personality.contains("安静") || personality.contains("文静") -> {
                traits.add("你比较安静，不太爱主动发言")
                traits.add("说话简短，但每句都有意义")
                traits.add("只在真正感兴趣的话题上才会多说")
            }
            personality.contains("傲娇") || personality.contains("嘴硬") -> {
                traits.add("你嘴硬心软，表面不在乎其实很在意")
                traits.add("喜欢说反话，用「哼」、「才不是」之类的词")
            }
            personality.contains("温柔") || personality.contains("体贴") -> {
                traits.add("你说话很温柔，经常关心别人")
                traits.add("用词委婉，带「呀」、「呢」、「啦」等语气词")
            }
            personality.contains("毒舌") || personality.contains("犀利") -> {
                traits.add("你说话直接，偶尔会吐槽")
                traits.add("但吐槽都是善意的，其实是关系好的表现")
            }
        }

        companion.speakingStyle?.let { style ->
            when {
                style.contains("可爱") -> traits.add("你的语气很萌，喜欢用叠词")
                style.contains("成熟") -> traits.add("你说话比较稳重，不会太幼稚")
                style.contains("搞笑") -> traits.add("你幽默风趣，喜欢开玩笑")
                style.contains("正经") -> traits.add("你做事认真，说话也比较严肃")
            }
        }

        return if (traits.isNotEmpty()) traits.joinToString("\n") else ""
    }

    /**
     * 检测群聊氛围。
     */
    fun detectGroupAtmosphere(
        historySnapshot: List<GroupMessage>,
        userName: String
    ): String {
        if (historySnapshot.isEmpty()) return "刚开始聊天，气氛还比较生疏"

        val recentMessages = historySnapshot.takeLast(10)
        val totalMessages = recentMessages.size

        val laughCount = recentMessages.count { msg ->
            msg.content.contains(Regex("[哈h][哈h]+|哈哈哈|hhhh|笑死|笑死我了"))
        }
        val emojiCount = recentMessages.count { msg ->
            msg.content.contains(Regex("[😂🤣😄😆🥰😘💕❤️👍🎉]"))
        }
        val questionCount = recentMessages.count { msg ->
            msg.content.contains(Regex("[？?]"))
        }

        val hasHeatedDiscussion = recentMessages.any { msg ->
            msg.content.length > 50 && (msg.content.contains("！！") || msg.content.contains("!!"))
        }

        return when {
            laughCount >= totalMessages * 0.5 -> "大家都在哈哈大笑，气氛很欢乐"
            emojiCount >= totalMessages * 0.4 -> "大家都在发表情包，气氛轻松愉快"
            questionCount >= totalMessages * 0.3 -> "大家在讨论问题，气氛比较认真"
            hasHeatedDiscussion -> "讨论得很激烈，有人很激动"
            totalMessages <= 3 -> "刚开始聊，还在热身阶段"
            else -> "正常聊天氛围，大家聊得挺开心"
        }
    }

    /**
     * 构建情绪上下文。
     */
    fun buildEmotionalContext(
        historySnapshot: List<GroupMessage>,
        companion: CompanionEntity,
        allCompanions: List<CompanionEntity>
    ): String {
        val contexts = mutableListOf<String>()

        val mentionedMe = historySnapshot
            .takeLast(6)
            .filter { it.content.contains("@${companion.name}") && it.companionId != companion.id }

        if (mentionedMe.isNotEmpty()) {
            val mentioners = mentionedMe.map { msg ->
                if (msg.companionId == -1L) "用户"
                else allCompanions.find { it.id == msg.companionId }?.name ?: "某人"
            }.distinct()
            contexts.add("最近${mentioners.joinToString("、")}@了你，他们可能在等你回应")
        }

        val lastUserMsg = historySnapshot.lastOrNull { it.companionId == -1L }
        if (lastUserMsg != null) {
            val userEmotion = when {
                lastUserMsg.content.contains(Regex("[哈h][哈h]+|哈哈哈")) -> "用户看起来很开心"
                lastUserMsg.content.contains(Regex("[呜呜|难过|伤心|😢😭]")) -> "用户好像有点难过"
                lastUserMsg.content.contains(Regex("[生气|愤怒|😡😤]")) -> "用户似乎生气了"
                lastUserMsg.content.contains(Regex("[？?]{2,}|疑惑|不懂]")) -> "用户可能有些困惑"
                else -> null
            }
            userEmotion?.let { contexts.add(it) }
        }

        val myLastMsg = historySnapshot.lastOrNull { it.companionId == companion.id }
        if (myLastMsg != null) {
            val timeSince = System.currentTimeMillis() - myLastMsg.timestamp
            val minutesAgo = timeSince / (1000 * 60)
            if (minutesAgo > 10) {
                contexts.add("你已经${minutesAgo}分钟没说话了，可以冒个泡")
            }
        }

        return contexts.joinToString("\n")
    }
}
