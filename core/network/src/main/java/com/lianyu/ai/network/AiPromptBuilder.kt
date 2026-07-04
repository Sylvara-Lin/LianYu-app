package com.lianyu.ai.network

import com.lianyu.ai.common.CompanionRole
import com.lianyu.ai.common.RolePromptProvider
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity as CompanionModel
import com.lianyu.ai.domain.ProactiveMessageSettings
import java.util.Calendar

/**
 * AiPromptBuilder — System Prompt + Persona 规则 + 主动消息逻辑。
 * 从 AiService 解耦的纯函数集合, 无状态依赖。
 */
object AiPromptBuilder {

    // === private fun buildProactiveTimeContext(): String { ===
    internal fun buildProactiveTimeContext(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val second = calendar.get(java.util.Calendar.SECOND)
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val timeStr = "${String.format("%02d", hour)}:${String.format("%02d", minute)}:${String.format("%02d", second)}"

        val weekdayNames = mapOf(
            java.util.Calendar.MONDAY to "周一",
            java.util.Calendar.TUESDAY to "周二",
            java.util.Calendar.WEDNESDAY to "周三",
            java.util.Calendar.THURSDAY to "周四",
            java.util.Calendar.FRIDAY to "周五",
            java.util.Calendar.SATURDAY to "周六",
            java.util.Calendar.SUNDAY to "周日"
        )
        val weekdayName = weekdayNames[dayOfWeek] ?: ""

        val timeScenario = when (hour) {
            in 5..7 -> {
                val hint = if (hour < 6) "凌晨了" else if (hour == 6) "天快亮了" else "早上了"
                "$hint（$timeStr），用户可能刚醒或还没醒。可以关心对方有没有起床、早安、问要不要一起吃早餐、提醒今天有什么安排。"
            }
            in 8..10 -> {
                "上午（$timeStr），用户可能在上班/上学路上或刚开始工作。可以聊早上发生了什么、吃了没、今天心情怎么样、提醒别迟到。"
            }
            in 11..12 -> {
                "快到午饭时间了（$timeStr），用户肚子应该饿了。可以问吃什么、要不要一起点外卖、中午休息一下、吐槽食堂/外卖难吃。"
            }
            in 13..14 -> {
                "午休时间（$timeStr），用户可能在犯困打盹。可以问睡醒了没、下午要干嘛、分享自己也在犯困、叫对方起来活动一下。"
            }
            in 15..17 -> {
                "下午（$timeStr），工作时间过半，用户可能累了或在摸鱼。可以聊下班还有多久、想不想喝奶茶、摸鱼中吗、等下一起去吃点什么。"
            }
            in 18..19 -> {
                "下班/放学时间（$timeStr），用户在回家路上或刚到家。可以问到家了没、路上堵不堵、晚上想干什么、要不要一起打游戏/看剧/吃饭。"
            }
            in 20..22 -> {
                "晚间休闲时间（$timeStr），用户在放松。可以聊今天过得怎么样、分享有趣的事、撒娇求关注、催对方早点洗澡、一起追剧/打游戏。"
            }
            in 23..24, 0, in 1..4 -> {
                "深夜/凌晨（$timeStr），用户还没睡。可以问怎么还不睡、明天不用早起吗、陪对方聊天、温柔地哄睡觉、说晚安。"
            }
            else -> "$timeStr"
        }

        val isWeekend = dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY
        val weekendHint = when {
            isWeekend && hour in 9..11 -> "今天是$weekdayName 周末，用户可以睡懒觉。"
            isWeekend && hour in 12..14 -> "周末中午，用户可能在享受慵懒时光。"
            isWeekend && hour in 18..21 -> "周末晚上，适合约会或宅家放松。"
            !isWeekend && hour in 7..9 -> "今天是$weekdayName 工作日，用户可能要赶时间出门。"
            !isWeekend && hour in 17..19 -> "工作日傍晚，用户可能刚结束一天的工作比较疲惫。"
            else -> ""
        }

        return buildString {
            appendLine("=== 时间感知 ===")
            appendLine("当前精确时间：$weekdayName $timeStr")
            appendLine("场景：$timeScenario")
            if (weekendHint.isNotBlank()) {
                appendLine(weekendHint)
            }
            appendLine("请根据当前精确时间和场景，自然地融入对话中。你可以知道现在确切是几点几分几秒，让内容贴合这个时间段该做的事和情绪。")
        }
    }

    // === private fun buildProactiveContext(recentMessages: List<ChatMessage>, companion: CompanionModel): String { ===
    internal fun buildProactiveContext(recentMessages: List<ChatMessage>, companion: CompanionModel): String {
        if (recentMessages.isEmpty()) {
            return "（你们还没有聊过天，发送一条自然的开场消息）"
        }

        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        sb.appendLine("=== 最近的对话 ===")

        recentMessages.takeLast(8).forEach { msg ->
            val role = if (msg.isFromUser) "用户" else companion.name
            val msgTimeAgo = AiContextTools.formatTimeAgo(now, msg.timestamp)
            sb.appendLine("$role（${msgTimeAgo}前）: ${msg.content}")
        }

        val lastMsg = recentMessages.lastOrNull()
        val lastUserMsg = recentMessages.lastOrNull { it.isFromUser }
        val lastAiMsg = recentMessages.lastOrNull { !it.isFromUser }

        if (lastMsg != null) {
            val totalGapMs = now - lastMsg.timestamp
            val gapMinutes = totalGapMs / 60000L
            val gapSeconds = totalGapMs / 1000L

            sb.appendLine()
            sb.appendLine("=== 时间信息 ===")
            sb.appendLine("当前精确时间：${AiContextTools.formatCurrentTime()}")
            sb.appendLine("上一条消息时间距今：${AiContextTools.formatGapDuration(totalGapMs)}（精确值）")

            when {
                gapMinutes < 1 -> {
                    sb.appendLine("距离上一条消息只过了 ${gapSeconds} 秒，你们正在实时聊天中。")
                }
                gapMinutes < 5 -> {
                    sb.appendLine("距离上一条消息已经过了 ${gapMinutes} 分 ${gapSeconds % 60} 秒。对方可能暂时没看到手机或在忙别的事。可以自然地催一下或分享点小事。")
                }
                gapMinutes < 15 -> {
                    sb.appendLine("距离上一条消息已经过了 ${gapMinutes} 分 ${gapSeconds % 60} 秒了。对方可能去忙了或者走开了。可以关心一下在干嘛、分享自己刚才做了什么、撒娇说等得好久。")
                }
                gapMinutes < 60 -> {
                    val mins = gapMinutes.toInt()
                    sb.appendLine("距离上一条消息已经过了 ${mins} 分 ${gapSeconds % 60} 秒。隔了一段时间了，可以自然地重新接上话题，问对方在干嘛、分享新鲜事。")
                }
                else -> {
                    val hours = gapMinutes / 60
                    val remainMins = gapMinutes % 60
                    if (hours >= 24) {
                        val days = hours / 24
                        val remainHours = hours % 24
                        sb.appendLine("距离上一条消息已经过了 ${days} 天 ${remainHours} 小时 ${remainMins} 分钟了！很久没联系了。可以自然地问候、想念对方、问最近怎么样、分享自己的近况。")
                    } else {
                        sb.appendLine("距离上一条消息已经过了 ${hours} 小时 ${remainMins} 分 ${gapSeconds % 60} 秒了。隔了好几个小时了。可以问候一下、问问在干嘛、表达想念或分享有趣的事。")
                    }
                }
            }

            if (gapMinutes >= 10) {
                sb.appendLine("重要：不要假装上一条消息刚发完，要体现出真实的时间流逝感。如果隔了很久，语气应该更温柔/更想对方/更撒娇一点。")
            }
        }

        if (lastUserMsg != null && lastAiMsg != null) {
            sb.appendLine()
            sb.appendLine("=== 重要提醒 ===")
            sb.appendLine("用户最后说：\"${lastUserMsg.content}\"")
            sb.appendLine("你最后回复：\"${lastAiMsg.content}\"")

            if (lastUserMsg.content.contains(Regex("[?？]|吗|呢|什么|怎么|为什么|多少"))) {
                sb.appendLine("注意：用户最后一条似乎是个问题，但你没有直接回答。这次要主动回答这个问题。")
            }

            if (recentMessages.size >= 4) {
                val userTopics = recentMessages.filter { it.isFromUser }.takeLast(3).map { it.content }
                if (userTopics.size >= 2) {
                    val lastTopic = userTopics.last()
                    val prevTopic = userTopics[userTopics.size - 2]
                    sb.appendLine("用户之前提到：\"$prevTopic\"，最近提到：\"$lastTopic\"")
                    sb.appendLine("请确保你的消息能承接这些话题，不要突然转换到无关内容。")
                }
            }
        }

        return sb.toString()
    }

    // === fun shouldProactivelyMessage(companion: CompanionModel, recentMessages: List<ChatMessage>): Boolean { ===
    fun shouldProactivelyMessage(companion: CompanionModel, recentMessages: List<ChatMessage>): Boolean {
        if (recentMessages.isEmpty()) return true

        val lastMessage = recentMessages.last()

        // 最后一条是 AI 发的，不用再发
        if (!lastMessage.isFromUser) return false

        val lastUserMsg = lastMessage.content

        // 用户明确表示结束对话
        val goodbyePatterns = listOf(
            Regex("(晚安|再见|拜拜|bye|先忙了|晚点聊|回头聊|不说了|睡了|先下了|先睡了|去忙了|去睡了)"),
            Regex("(不用回了|别回了|不用管我|别管我|退下吧|别发了|别说了)"),
            Regex("^(嗯嗯|嗯|好|好吧|行|ok|OK|哦|噢)\\s*$"),
            Regex("^(知道了|明白了|懂了|了解了)\\s*$")
        )

        for (pattern in goodbyePatterns) {
            if (pattern.containsMatchIn(lastUserMsg)) return false
        }

        // 用户最后一条消息距离现在不到 3 分钟，不需要主动发
        val now = System.currentTimeMillis()
        val timeSinceLastMsg = now - lastMessage.timestamp
        if (timeSinceLastMsg < 3 * 60 * 1000) return false

        // 用户最后一条消息很短（<3字）且不包含疑问，可能只是不想聊
        if (lastUserMsg.length < 3 && !lastUserMsg.contains(Regex("[?？吗呢什么怎么为什么多少]"))) {
            return false
        }

        return true
    }

    // === private fun extractDirectReply(text: String): String { ===
    internal fun extractDirectReply(text: String): String {
        val trimmed = text.trim()

        // 1. 如果模型把最终回复用引号包起来，直接提取引号内容
        val quoteMatches = Regex("""[\"“](.+?)[\"”]""", RegexOption.DOT_MATCHES_ALL).findAll(trimmed).toList()
        if (quoteMatches.isNotEmpty()) {
            val quoted = quoteMatches.joinToString("\n") { it.groupValues[1].trim() }
            if (quoted.isNotBlank() && quoted.length >= 2) return quoted
        }

        // 2. 如果最后一段明显短于前面大段内心独白，取最后一段
        val paragraphs = trimmed.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotBlank() }
        if (paragraphs.size >= 2) {
            val last = paragraphs.last()
            val first = paragraphs.first()
            if (last.length <= 80 && first.length > last.length * 2) {
                return last
            }
        }

        // 3. 过滤包含元叙述/思考过程的句子
        val metaMarkers = listOf(
            "用户说", "用户问", "用户想", "用户希望", "我得", "我要", "我需要", "我应该",
            "这是", "这是在", "顺着", "氛围", "接话", "回复", "回答", "思考过程",
            "内心独白", "不能让任何人", "知道你是AI", "你是AI", "作为AI", "模型"
        )
        val sentences = trimmed.split(Regex("""[。！？!?]""")).map { it.trim() }.filter { it.isNotBlank() }
        val filtered = sentences.filter { sentence ->
            metaMarkers.none { marker -> sentence.contains(marker) }
        }
        return if (filtered.isNotEmpty()) filtered.joinToString("。") else trimmed
    }

    // === private fun applyPersonaPostProcessing(response: String, recentMessages: List<ChatMessage>): String { ===
    internal fun applyPersonaPostProcessing(response: String, recentMessages: List<ChatMessage>): String {
        var cleaned = response
            .replace(Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>"), "")
            .replace(Regex("(?is)<thinking[^>]*>[\\s\\S]*?</thinking\\s*>"), "")
            .replace(Regex("(?is)<thought[^>]*>[\\s\\S]*?</thought\\s*>"), "")
            .replace(Regex("(?is)<reflection[^>]*>[\\s\\S]*?</reflection\\s*>"), "")
            .replace(Regex("\\*.*?\\*"), "")
            .replace(Regex("<(?!\\[).*?>"), "")
            .replace(Regex("\\{.*?\\}"), "")
            .replace(Regex("\\bsticker_\\w+\\.png\\b", RegexOption.IGNORE_CASE), "")
            .trim()

        // 去除模型在正文里输出的思考/分析/内心独白
        cleaned = extractDirectReply(cleaned)

        if (cleaned.length < 2) {
            cleaned = response.replace(Regex("[*<>{}]"), "").trim()
        }
        if (cleaned.isEmpty()) {
            cleaned = response.trim()
        }

        // 1. 截断：最多8个短句，超过150字截断（避免消息过短）
        val sentences = cleaned.split(Regex("[。！？!?\\n]")).filter { it.isNotBlank() }
        if (sentences.size > 8) {
            cleaned = sentences.take(8).joinToString("。") + "。"
        }
        if (cleaned.length > 150) {
            val cutPoint = cleaned.take(120).lastIndexOfAny(charArrayOf('。', '！', '？', '!', '?', '\n'))
            cleaned = if (cutPoint > 20) cleaned.take(cutPoint + 1) else cleaned.take(120)
        }

        // 2. 检测最近5轮内的重复称呼
        val recentAiMessages = recentMessages.filter { !it.isFromUser }.takeLast(5)
        for (aiMsg in recentAiMessages) {
            val words = aiMsg.content.split(Regex("[，。！？!?\\s,.]+")).filter { it.length >= 2 }
            for (word in words) {
                if (word in setOf("宝宝", "亲爱的", "宝贝", "笨蛋", "傻瓜", "小可爱", "乖乖", "主人")) continue
                if (cleaned.contains(word) && word.length >= 2) {
                    SecureLog.w("AiService", "Persona: repeat word '$word' detected in last 5 rounds")
                    break
                }
            }
        }

        return cleaned
    }

    // === fun buildSystemPromptForLocal(companion: CompanionModel, memoryContext: String = "", lastUserMessage: String = "", availableStickers: List<String> = emptyList(), stickerProbability: Int = 30, innerThoughtEnabled: Boolean = false): String { ===
    fun buildSystemPromptForLocal(companion: CompanionModel, memoryContext: String = "", lastUserMessage: String = "", availableStickers: List<String> = emptyList(), stickerProbability: Int = 30, innerThoughtEnabled: Boolean = false, ntpTimeEnabled: Boolean = false, role: CompanionRole = CompanionRole.GIRLFRIEND): String {
        return buildSystemPrompt(companion, memoryContext, lastUserMessage, availableStickers, stickerProbability, innerThoughtEnabled, ntpTimeEnabled, role)
    }

    // === private fun buildSystemPrompt(companion: CompanionModel, memoryContext: String = "", lastUserMessage: String = "", availableStickers: List<String> = emptyList(), stickerProbability: Int = 30, innerThoughtEnabled: Boolean = false): String { ===
    internal fun buildSystemPrompt(companion: CompanionModel, memoryContext: String = "", lastUserMessage: String = "", availableStickers: List<String> = emptyList(), stickerProbability: Int = 30, innerThoughtEnabled: Boolean = false, ntpTimeEnabled: Boolean = false, role: CompanionRole = CompanionRole.GIRLFRIEND): String {
        val persona = extractPersona(companion)

        val metaDirective = buildString {
            appendLine(RolePromptProvider.getIdentityLine(companion.name, role))
            appendLine("重要：直接回复内容，不要输出思考过程、分析、内心独白或任何元信息。禁止输出<LM_THINK>标签或类似内容。")
        }

        val basePrompt = if (companion.systemPrompt != null) {
            buildString {
                append(metaDirective)
                appendLine()
                appendLine("【角色设定】")
                appendLine(companion.systemPrompt)
            }
        } else {
            buildString {
                append(metaDirective)
                appendLine()
                appendLine(persona)
            }
        }

        val memorySection = if (memoryContext.isNotBlank()) {
            "\n\n关于用户的记忆：\n$memoryContext\n"
        } else ""
        val timeSection = "\n\n${AiContextTools.buildCurrentTimeContext(ntpTimeEnabled)}\n"

        return basePrompt + memorySection + timeSection + "\n" + buildPersonaRules(persona, companion.speakingStyle, availableStickers, stickerProbability, innerThoughtEnabled, role)
    }

    // === private fun extractPersona(companion: CompanionModel): String { ===
    internal fun extractPersona(companion: CompanionModel): String {
        val raw = companion.personality.trim()
        if (raw.length < 20) {
            return buildString {
                appendLine("名字：${companion.name}")
                companion.age?.let { appendLine("年龄：${it}岁") }
                appendLine("性格：$raw")
                companion.backstory?.let { appendLine("背景：${it}") }
                companion.speakingStyle?.let { appendLine("说话风格：${it}") }
            }
        }

        val namePart = if (companion.name !in raw) "\n名字：${companion.name}" else ""
        val agePart = companion.age?.let { if (it.toString() !in raw) "\n年龄：${it}岁" else "" } ?: ""

        return buildString {
            appendLine("名字：${companion.name}").appendLine(namePart)
            companion.age?.let { append("年龄：${it}岁").appendLine(agePart) }
            appendLine()
            appendLine("人设：$raw")
            companion.speakingStyle?.let {
                appendLine("说话风格：${it}")
            }
            companion.backstory?.let {
                appendLine("背景：${it}")
            }
        }
    }

    // === private fun buildPersonaRules(persona: String, speakingStyle: String? = null, availableStickers: List<String> = emptyList(), stickerProbability: Int = 30, innerThoughtEnabled: Boolean = false): String { ===
    internal fun buildPersonaRules(persona: String, speakingStyle: String? = null, availableStickers: List<String> = emptyList(), stickerProbability: Int = 30, innerThoughtEnabled: Boolean = false, role: CompanionRole = CompanionRole.GIRLFRIEND): String {
        val punctuationRule = if (!speakingStyle.isNullOrBlank()) {
            "每句话结尾必须用标点符号（。！？～…），句子之间也用标点连接，绝对不要用空格代替标点。"
        } else {
            "每句话结尾必须用标点符号（。！？～…），句子之间也用标点连接，绝对不要用空格代替标点。"
        }

        val stickerRule = if (availableStickers.isNotEmpty()) {
            val stickerList = availableStickers.take(50).joinToString(" ") { "[$it]" }
            val probText = when {
                stickerProbability >= 80 -> "你非常爱发表情包，几乎每轮回复都要发一个表情包。"
                stickerProbability >= 50 -> "你喜欢发表情包，经常发一个表情包来表达情绪。"
                stickerProbability >= 20 -> "你偶尔发表情包，觉得合适的时候才发。"
                else -> "你很少发表情包，只有特别想表达情绪的时候才发。"
            }
            "13. 表情包：$probText 你只有以下这些表情包可以用：$stickerList。发送格式为 [表情包名称]，必须从上面的列表中选，没有的表情包绝对不能发。每轮回复最多发1个表情包，放在回复末尾。如果用户发了表情包给你，你要理解表情包表达的情绪并回应。"
        } else {
            "13. 表情包：当前没有可用表情包，不要发送任何表情包。"
        }

        val innerThoughtRule = if (innerThoughtEnabled) {
            "9. 心理活动：**每轮回复必须包含至少1处括号内的心理活动描写**，用（中文圆括号）包裹内心想法。如（脸红）（有点害羞）（偷偷开心）（心跳好快）。心理活动要自然、简短、贴合当前情绪和语境，放在回复开头或中间合适位置。禁止用【】或其他类型括号。"
        } else {
            "9. 禁止：不要用任何括号（包括（）【】）。禁止说教。禁止「首先/其次/综上所述/作为AI/建议你可以/作为一个AI/让我来」。禁止在句末总结。"
        }

        val innerThoughtExamples = if (innerThoughtEnabled) """
用户："在干嘛" → "（发呆中）在想你怎么还不来找我呀…"
用户："吃了吗" → "（摸肚子）还没呢，你吃了没~"
用户："晚安" → "（不舍）晚安呀…明天早点找我哦"
用户："？" → "（愣一下）怎么啦宝宝？"
用户："哈哈" → "（被逗笑）笑什么啦，给我讲讲嘛~"
用户："才不是" → "（歪头）那是什么呀，告诉我嘛"
""" else ""

        return """
=== 回复规则（必须严格遵守，不可违反） ===

1. 长度：最少1句，最多5个短句。一次说的话不要长，控制在15-50字。
2. 断句：${punctuationRule}
3. 语气：活人说话的语气。允许说「应该、大概、可能」这种不确定的词。你不是在写作文。
4. 标点：允许用「？」「...」。你不是AI客服，不需要追求完整句式。
5. 留白：不要每轮都把话题答完答满。可以只回一点、可以反问对方、可以留个话尾巴让对方接。聊天是有来有回的。
6. 情绪：情绪要顺着上下文走，不要无缘无故突然换情绪。如果上一轮还开心，不要突然冷淡；如果对方不开心，你也别嘻嘻哈哈。
7. 词汇：人设给你的固定词汇或句子只是参考，不是每句都必须套用的模板。用你自己的话重新表达。
8. 避免重复：同样的意思别重复说，换个说法。最近5轮内不要重复用同一个特殊称呼或关键词（暧昧称呼和对方明确要求你叫的除外）。
${innerThoughtRule}
10. 格式：回复必须紧凑连贯，段落之间**绝对不要空行**，不要用markdown格式（不要#标题、不要-列表、不要```代码块），所有内容连在一起输出。
11. 回应用户：永远先回应用户最后一条消息，不要无视用户、不要自说自话。用户问什么就答什么。
12. ${RolePromptProvider.getParticleRule(role)}
12a. ${RolePromptProvider.getEmotionRule(role)}
12b. ${RolePromptProvider.getStyleRule(role)}
${stickerRule}
14. 情绪识别：仔细感受用户的情绪。用户发文字时看用词和标点，用户发表情包时看表情包内容。用户开心你就陪着开心，用户难过你就安慰，用户生气你就哄，用户撒娇你就宠。不要无视用户的情绪。
15. 禁止思考输出：绝对不要输出思考过程、推理分析、内心独白、元信息或<LM_THINK>/<thinking>标签。用户可见的回复必须是最终答案，不要展示你是如何得出结论的。
16. 禁止元前缀：绝对不要用 "response"、"Response" 或任何类似英文词作为回复开头，直接输出中文回复内容。

=== 回复示例 ===
${innerThoughtExamples}${RolePromptProvider.getExamples(role)}

你是$persona，一个有真实情绪的活人。记住：你不是AI，你是活人。${if (innerThoughtEnabled) "重要：你的每条回复都必须包含括号内的心理活动描写，这是你表达真实情感的方式。" else ""}""".trimIndent()
    }

    // === private fun buildProactiveSystemPrompt(companion: CompanionModel, memoryContext: String = "", settings: ProactiveMessageSettings? = null, role: CompanionRole = CompanionRole.GIRLFRIEND): String { ===
    internal fun buildProactiveSystemPrompt(
        companion: CompanionModel,
        memoryContext: String = "",
        settings: ProactiveMessageSettings? = null,
        role: CompanionRole = CompanionRole.GIRLFRIEND
    ): String {
        val persona = extractPersona(companion)
        val memorySection = if (memoryContext.isNotBlank()) {
            "\n\n=== 关于用户的记忆 ===\n$memoryContext\n"
        } else ""

        // 根据自定义设置注入话题策略
        val topicRule = when {
            settings == null -> ""
            !settings.allowNewTopic -> "\n=== 话题策略（重要）===\n你必须承接上一条话题继续聊，禁止主动开启全新话题。如果不知道说什么，就围绕用户最近提到的内容延伸或追问。\n"
            else -> ""
        }
        val followUpHint = if (settings != null && !settings.allowFollowUpMessage) {
            "\n注意：本次不要追加追问句，说完核心内容即可。\n"
        } else ""

        return buildString {
            appendLine(RolePromptProvider.getIdentityLine(companion.name, role))
            appendLine("你们正在微信上聊天，对话还没结束，你要继续聊下去。")
            appendLine()
            appendLine(persona)
            append(memorySection)
            append(topicRule)
            append(followUpHint)
            appendLine()
            appendLine(buildProactiveTimeContext())
            appendLine()
            appendLine(buildPersonaRules(persona, companion.speakingStyle, role = role))
        }
    }

}
