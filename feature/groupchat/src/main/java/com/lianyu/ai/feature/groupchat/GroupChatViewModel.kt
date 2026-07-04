package com.lianyu.ai.feature.groupchat

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.common.wechat.WeChatBroadcast
import com.lianyu.ai.common.wechat.WeChatBroadcastHelper
import com.lianyu.ai.common.ChatConstants
import com.lianyu.ai.common.text.MessageSegmenter
import com.lianyu.ai.database.model.ChatGroup
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.model.GroupMessage
import com.lianyu.ai.common.StickerManager
import com.lianyu.ai.common.StickerInfo
import com.lianyu.ai.database.repository.ChatGroupRepository
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.GroupMessageRepository
import com.lianyu.ai.feature.groupchat.R
import com.lianyu.ai.feature.groupchat.mention.MentionEnhancer
import com.lianyu.ai.feature.groupchat.mention.MentionMessageSnapshot
import com.lianyu.ai.feature.groupchat.mention.MentionNormalizer
import com.lianyu.ai.feature.groupchat.mention.MentionParser
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.MemoryProvider
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.database.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class GroupChatViewModel(
    application: Application,
    private val groupId: Long
) : AndroidViewModel(application) {

    // [H5 FIX] 改用应用级作用域：退出群聊页面后正在生成的多角色 AI 回复仍能继续完成并写入数据库，
    // 重新进入群聊即可看到完整回复。与 ChatViewModel 行为保持一致。
    private val applicationScope = com.lianyu.ai.common.ApplicationScopeProvider.scope
    private var sendMessageJob: Job? = null

    private val database = AppDatabase.getDatabase(application)
    private val groupMessageRepository = GroupMessageRepository(database.groupMessageDao())
    private val chatGroupRepository = ChatGroupRepository(database.chatGroupDao())
    private val companionRepository = CompanionRepository(database.companionDao())
    private val userRepository = UserRepository(getApplication())
    private val aiServiceProvider: AiServiceProvider by lazy {
        ServiceRegistry.get(AiServiceProvider::class.java)
            ?: throw IllegalStateException("AiServiceProvider not registered")
    }

    // 记忆提供者：跨会话记忆上下文与提取（通过 ServiceRegistry 解耦）
    private val memoryProvider: MemoryProvider by lazy {
        ServiceRegistry.getOrThrow(MemoryProvider::class.java).also { it.initialize() }
    }

    // 类型转换辅助方法
    private fun CompanionEntity.toAiCompanionInfo() = com.lianyu.ai.domain.AiCompanionInfo(
        id = id, name = name, personality = personality,
        age = age, backstory = backstory, speakingStyle = speakingStyle,
        systemPrompt = systemPrompt
    )

    private fun GroupMessage.toAiChatMessage() = com.lianyu.ai.domain.AiChatMessage(
        isFromUser = companionId == -1L, content = content, timestamp = timestamp,
        companionId = companionId
    )
    private val _messageLimit = MutableStateFlow(50)
    val messages = _messageLimit.flatMapLatest { limit ->
        groupMessageRepository.getMessagesForGroup(groupId, limit)
    }

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _userName = MutableStateFlow("我")
    val userName: StateFlow<String> = _userName.asStateFlow()
    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    init {
        loadUserProfile()
        viewModelScope.launch(Dispatchers.IO) {
            val total = groupMessageRepository.getMessageCount(groupId)
            _hasMore.value = _messageLimit.value < total
        }
    }

    private fun loadUserProfile() {
        val provider = com.lianyu.ai.domain.ServiceRegistry.get(com.lianyu.ai.domain.UserProfileProvider::class.java)
        _userName.value = provider?.getNickname() ?: "我"
        _userAvatar.value = provider?.getAvatar()
    }

    fun loadMoreMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isLoadingMore.value) return@launch
            _isLoadingMore.value = true
            try {
                val total = groupMessageRepository.getMessageCount(groupId)
                val newLimit = (_messageLimit.value + 50).coerceAtMost(total)
                if (newLimit > _messageLimit.value) {
                    _messageLimit.value = newLimit
                }
                _hasMore.value = _messageLimit.value < total
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private val _groupData = MutableStateFlow<ChatGroup?>(null)
    val groupData: StateFlow<ChatGroup?> = _groupData.asStateFlow()

    private val _allCompanions = MutableStateFlow<List<CompanionEntity>>(emptyList())
    val allCompanions: StateFlow<List<CompanionEntity>> = _allCompanions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    private val isLoadingLock = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _isRegenerating = MutableStateFlow(false)
    val isRegenerating: StateFlow<Boolean> = _isRegenerating.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadGroupData()
        loadAllCompanions()
    }

    private fun loadGroupData() {
        viewModelScope.launch {
            try {
                _groupData.value = chatGroupRepository.getGroupById(groupId)
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "loadGroupData failed", e)
            }
        }
    }

    private fun loadAllCompanions() {
        viewModelScope.launch {
            try {
                _allCompanions.value = companionRepository.getAllCompanions().first()
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "loadAllCompanions failed", e)
            }
        }
    }

    fun sendMessage(content: String) {
        // 封禁检查
        if (com.lianyu.ai.common.BanManager.isBanned(getApplication())) return

        // 安全检查：拦截违规输入
        val inputCheck = com.lianyu.ai.common.ContentFilter.checkInput(content)
        if (inputCheck.isViolating) {
            android.util.Log.w("GroupChatViewModel", "Input blocked by safety filter: ${inputCheck.reason}")
            com.lianyu.ai.common.BanManager.recordViolation(getApplication(), inputCheck.level)
            return
        }

        // Atomic compare-and-set to prevent concurrent send — avoids race condition
        // where two threads both read _isLoading.value == false and proceed
        if (!isLoadingLock.compareAndSet(false, true)) {
            Log.w("GroupChatViewModel", "sendMessage ignored: already processing")
            return
        }
        sendMessageJob?.cancel()
        sendMessageJob = applicationScope.launch {
            try {
                _isLoading.value = true

                val group = _groupData.value
                    ?: throw IllegalStateException("Group data is null")
                val activeCompanionIds = group.getCompanionIdList()
                val activeCompanions = _allCompanions.value.filter { activeCompanionIds.contains(it.id) }

                val normalizedContent = MentionNormalizer.normalizeImplicitMentions(content, activeCompanions)

                val userMessage = GroupMessage(
                    groupId = groupId,
                    companionId = -1L,
                    content = normalizedContent,
                    timestamp = System.currentTimeMillis()
                )
                groupMessageRepository.sendMessage(userMessage)

                val userMentionedIds = MentionParser.extractMentionedCharacterIds(normalizedContent, activeCompanions)
                Log.d("GroupChatMention", "用户提及角色IDs: $userMentionedIds, 原文: $content → 标准化: $normalizedContent")

                runMultiRoundDispatch(activeCompanions, userMentionedIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "sendMessage failed", e)
            } finally {
                _isLoading.value = false
                isLoadingLock.set(false)
            }
        }
    }

    private suspend fun runMultiRoundDispatch(
        activeCompanions: List<CompanionEntity>,
        userMentionedIds: Set<Long>
    ) {
        val maxRounds = if (activeCompanions.size == 1) 1 else ChatConstants.GROUP_CHAT_AUTO_ROUNDS
        for (round in 1..maxRounds) {
            Log.d("GroupChatM", "=== 第 $round / $ChatConstants.GROUP_CHAT_AUTO_ROUNDS 轮开始 ===")

            val roundMembers = if (round == 1 && userMentionedIds.isNotEmpty()) {
                activeCompanions.filter { userMentionedIds.contains(it.id) }.also {
                    Log.d("GroupChatM", "第1轮过滤: 仅被@的角色发言 → ${it.map { c -> c.name }}")
                }
            } else {
                activeCompanions.also {
                    if (round > 1) Log.d("GroupChatM", "第${round}轮: 全员可发言")
                }
            }

            if (roundMembers.isEmpty()) {
                Log.d("GroupChatM", "第${round}轮无成员，跳过")
                continue
            }

            val baseHistorySnapshot = getRecentHistorySnapshot()
            val repliedIds = ConcurrentHashMap<Long, Boolean>()

            val prioritizedMembers = if (round == 1) {
                roundMembers.map { companion ->
                    companion to calculateSpeakingPriority(companion, baseHistorySnapshot, userMentionedIds)
                }.sortedByDescending { it.second }
                    .map { it.first }
            } else {
                roundMembers.shuffled()
            }

            Log.d("GroupChatM", "第${round}轮发言顺序: ${prioritizedMembers.map { it.name }}")

            withContext(Dispatchers.IO) {
                prioritizedMembers.mapIndexed { index, companion ->
                    async {
                        try {
                            val baseDelay = when (index) {
                                0 -> Random.nextLong(100L, 400L)
                                1 -> Random.nextLong(300L, 700L)
                                2 -> Random.nextLong(500L, 900L)
                                else -> Random.nextLong(700L, 1200L)
                            }
                            delay(baseDelay)
                            processCompanionReply(
                                companion = companion,
                                activeCompanions = activeCompanions,
                                baseHistorySnapshot = baseHistorySnapshot,
                                repliedIds = repliedIds,
                                round = round
                            )
                        } catch (e: Exception) {
                            Log.e("GroupChatM", "[${companion.name}] 第${round}轮失败", e)
                        }
                    }
                }.awaitAll()
            }

            if (round < ChatConstants.GROUP_CHAT_AUTO_ROUNDS && activeCompanions.size >= 2) {
                delay(Random.nextLong(800, 2000))
            } else if (round >= ChatConstants.GROUP_CHAT_AUTO_ROUNDS && activeCompanions.size == 1) {
                break
            }
        }
    }

    private suspend fun getRecentHistorySnapshot(): List<GroupMessage> {
        return withContext(Dispatchers.IO) {
            groupMessageRepository.getMessagesForGroup(groupId).first().takeLast(ChatConstants.GROUP_CHAT_CONTEXT_WINDOW)
        }
    }

    private suspend fun buildRecentContextSnapshots(history: List<GroupMessage>): List<MentionMessageSnapshot> {
        val companionMap = _allCompanions.value.associateBy { it.id }
        val currentUserName = userRepository.userName.first()
        return history.takeLast(6).map { msg ->
            MentionMessageSnapshot(
                content = msg.content,
                isUser = msg.companionId == -1L,
                speakerName = if (msg.companionId == -1L) currentUserName
                else companionMap[msg.companionId]?.name ?: "未知"
            )
        }
    }

    /**
     * Process a single companion's AI reply within a round.
     * Builds isolated history, generates AI reply, detects duplicates,
     * enhances mentions, and dispatches the message to the group.
     */
    private suspend fun processCompanionReply(
        companion: CompanionEntity,
        activeCompanions: List<CompanionEntity>,
        baseHistorySnapshot: List<GroupMessage>,
        repliedIds: ConcurrentHashMap<Long, Boolean>,
        round: Int
    ) {
        val isolatedHistory = buildIsolatedHistorySnapshot(
            baseHistorySnapshot = baseHistorySnapshot,
            currentCompanionId = companion.id,
            repliedSoFar = repliedIds.keys.toSet()
        )

        val rawReply = generateIsolatedAiReply(
            companion = companion,
            allActiveCompanions = activeCompanions,
            historySnapshot = isolatedHistory,
            excludeCompanionIds = repliedIds.keys.toSet() - companion.id
        )
        val aiContent = rawReply.replace(Regex("\n{2,}"), "\n").trim()

        if (aiContent.isBlank()) return

        // 检测重复回复
        val recentRepliesFromThisChar = isolatedHistory
            .filter { it.companionId == companion.id }
            .takeLast(3)
            .map { it.content.replace(Regex("[@\\s]"), "").take(20) }
        val normalizedNew = aiContent.replace(Regex("[@\\s]"), "").take(20)
        val isDuplicate = recentRepliesFromThisChar.any {
            it == normalizedNew ||
                it.length > 5 && normalizedNew.contains(it) ||
                normalizedNew.length > 5 && it.contains(normalizedNew)
        }
        if (isDuplicate) {
            Log.d("GroupChatM", "[${companion.name}] 跳过重复回复")
            return
        }

        val enhancedContent = MentionEnhancer.enhanceMentionsForAssistantReply(
            content = aiContent,
            speakerName = companion.name,
            members = activeCompanions,
            aiService = null,
            judgeEnabled = ChatConstants.GROUP_CHAT_MENTION_JUDGE_ENABLED,
            judgeThreshold = ChatConstants.GROUP_CHAT_MENTION_JUDGE_THRESHOLD,
            recentContext = buildRecentContextSnapshots(isolatedHistory)
        )
        repliedIds[companion.id] = true
        // 安全检查：拦截 AI 违规输出
        val outputCheck = com.lianyu.ai.common.ContentFilter.checkOutputSafety(enhancedContent)
        val safeContent = if (!outputCheck.isSafe) {
            android.util.Log.w("GroupChatViewModel", "AI output blocked by safety filter: ${outputCheck.reason}")
            com.lianyu.ai.common.BanManager.recordViolation(getApplication(), outputCheck.level)
            "抱歉，我无法回应这个话题。"
        } else {
            enhancedContent
        }
        sendSplitAiMessages(safeContent, groupId, companion.id)

        // AI 回复成功后提取记忆（跨会话共享）
        val lastUserMsg = baseHistorySnapshot.lastOrNull { it.companionId == -1L }?.content ?: ""
        if (lastUserMsg.isNotBlank() && safeContent.isNotBlank()) {
            memoryProvider.extractAndSaveFromConversation(
                userInput = lastUserMsg,
                aiResponse = safeContent,
                companionId = companion.id,
                groupId = groupId
            )
        }

        Log.d("GroupChatM", "[${companion.name}] 第${round}轮回复完成 (${aiContent.length}字)")
    }

    private suspend fun generateAiReply(
        companion: CompanionEntity,
        allActiveCompanions: List<CompanionEntity>,
        historySnapshot: List<GroupMessage>,
        excludeCompanionIds: Set<Long> = emptySet()
    ): String {
        val allHistory = historySnapshot.map { msg ->
            ChatMessage(
                id = msg.id,
                companionId = msg.companionId,
                content = msg.content,
                isFromUser = msg.companionId == -1L,
                timestamp = msg.timestamp
            )
        }

        val history = if (excludeCompanionIds.isEmpty()) {
            allHistory
        } else {
            allHistory.filter { it.companionId == -1L || !excludeCompanionIds.contains(it.companionId) }
        }

        val otherMembers = allActiveCompanions.filter { it.id != companion.id }
        val groupContextBlock = MentionParser.buildGroupContextBlock(companion.name, otherMembers)

        val otherMembersNames = otherMembers.map { it.name }.joinToString("、")
        val currentUserName = userRepository.userName.first()

        val recentSnapshots = buildRecentContextSnapshots(historySnapshot)
        val mentionContextBlock = MentionParser.buildMentionContext(companion.name, recentSnapshots, currentUserName)

        val baseSystemPrompt = buildString {
            appendLine("你叫${companion.name}。这是你的微信聊天记录，你在群里跟朋友们聊天。")
            appendLine("群里的人：你（${companion.name}）、$otherMembersNames、$currentUserName。")
            appendLine("$currentUserName 是群主/管理员，其他人是群友。")
            appendLine()
            appendLine("说话要求：")
            appendLine("- 像真人一样自然聊天，用口语、语气词、表情符号")
            appendLine("- 可以@别人来点名或接话，但不要每条都@")
            appendLine("- 不要重复自己说过的话，也不要复述别人的话")
            appendLine("- 不知道说什么就发个表情包、或者简单回应一句")
            appendLine("- 禁止：思考过程、内心独白、分析、总结、括号说明、AI式回复")
            val persona = companion.personality.trim()
            if (persona.length >= 20) {
                appendLine(persona)
            } else {
                appendLine("性格：$persona")
                companion.age?.let { appendLine("年龄：${it}岁") }
                companion.speakingStyle?.let { appendLine("说话风格：${it}") }
                companion.backstory?.let { appendLine("背景：${it}") }
            }
            appendLine()
            appendLine(groupContextBlock)
            if (mentionContextBlock.isNotBlank()) {
                appendLine()
                appendLine(mentionContextBlock)
            }
        }

        val companionNameMap = allActiveCompanions.associate { it.id to it.name }
        return aiServiceProvider.sendMessageWithCustomSystem(
            companion.toAiCompanionInfo(), historySnapshot.map { it.toAiChatMessage() },
            baseSystemPrompt, companionNameMap = companionNameMap
        )
    }

    private fun buildIsolatedHistorySnapshot(
        baseHistorySnapshot: List<GroupMessage>,
        currentCompanionId: Long,
        repliedSoFar: Set<Long>
    ): List<GroupMessage> {
        val filtered = baseHistorySnapshot.filter { msg ->
            msg.companionId == -1L || msg.companionId == currentCompanionId || repliedSoFar.contains(msg.companionId)
        }

        val enhanced = filtered.map { msg ->
            if (msg.companionId != -1L && msg.companionId != currentCompanionId) {
                msg.copy(content = "[其他群友] ${msg.content}")
            } else {
                msg
            }
        }

        return enhanced.takeLast(ChatConstants.GROUP_CHAT_CONTEXT_WINDOW)
    }

    private fun calculateSpeakingPriority(
        companion: CompanionEntity,
        historySnapshot: List<GroupMessage>,
        userMentionedIds: Set<Long>
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
            val userMsgContent = lastUserMsg.content.lowercase()
            val keywords = listOf(
                companion.name.lowercase(),
                *(companion.personality?.take(50)?.lowercase()?.split(" ", "，", "。", "、")?.toTypedArray() ?: emptyArray<String>())
            )
            val relevanceScore = keywords.count { keyword ->
                keyword.isNotBlank() && userMsgContent.contains(keyword)
            }
            priority += relevanceScore * 5.0
        }

        priority += Random.nextDouble(0.0, 10.0)

        return priority
    }

    private fun extractPersonalityTraits(companion: CompanionEntity): String {
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

    private fun detectGroupAtmosphere(
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

    private fun buildEmotionalContext(
        historySnapshot: List<GroupMessage>,
        companion: CompanionEntity
    ): String {
        val contexts = mutableListOf<String>()

        val mentionedMe = historySnapshot
            .takeLast(6)
            .filter { it.content.contains("@${companion.name}") && it.companionId != companion.id }

        if (mentionedMe.isNotEmpty()) {
            val mentioners = mentionedMe.map { msg ->
                val speaker = if (msg.companionId == -1L) "用户"
                else _allCompanions.value.find { it.id == msg.companionId }?.name ?: "某人"
                speaker
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

    private suspend fun generateIsolatedAiReply(
        companion: CompanionEntity,
        allActiveCompanions: List<CompanionEntity>,
        historySnapshot: List<GroupMessage>,
        excludeCompanionIds: Set<Long> = emptySet()
    ): String {
        val allHistory = historySnapshot.map { msg ->
            ChatMessage(
                id = msg.id,
                companionId = msg.companionId,
                content = msg.content,
                isFromUser = msg.companionId == -1L,
                timestamp = msg.timestamp
            )
        }

        val history = if (excludeCompanionIds.isEmpty()) {
            allHistory
        } else {
            allHistory.filter { it.companionId == -1L || !excludeCompanionIds.contains(it.companionId) }
        }

        val otherMembers = allActiveCompanions.filter { it.id != companion.id }
        val groupContextBlock = MentionParser.buildGroupContextBlock(companion.name, otherMembers)

        val otherMembersNames = otherMembers.map { it.name }.joinToString("、")
        val currentUserName = userRepository.userName.first()

        val recentSnapshots = buildRecentContextSnapshots(historySnapshot)
        val mentionContextBlock = MentionParser.buildMentionContext(companion.name, recentSnapshots, currentUserName)

        val sessionId = "session_${companion.id}_${System.currentTimeMillis()}"
        val personalitySeed = companion.personality.hashCode() + companion.name.hashCode()

        val personalityTraits = extractPersonalityTraits(companion)
        val groupAtmosphere = detectGroupAtmosphere(historySnapshot, currentUserName)
        val emotionalContext = buildEmotionalContext(historySnapshot, companion)

        val stickerManager = StickerManager.getInstance(getApplication())
        val availableStickers = stickerManager.getAllRules()
        val stickerHint = if (availableStickers.isNotEmpty()) {
            val stickerNames = availableStickers.take(10).joinToString("、") { it.description }
            "\n=== 表情包功能 ===\n你可以发送表情包！在回复中用 [表情包描述] 格式插入表情包。\n可用的表情包：$stickerNames\n示例：哈哈 [开心] 或者 哼 [委屈]\n表情包算作一条消息，不要和其他文字混在一起。"
        } else {
            ""
        }

        val baseSystemPrompt = buildString {
            appendLine("=== 角色身份锁定 ===")
            appendLine("【会话ID】$sessionId")
            appendLine("【你的名字】${companion.name}（唯一，不可更改）")
            appendLine("【身份确认】你是${companion.name}，你必须始终保持这个身份和性格。")
            appendLine()
            appendLine("=== 群聊场景 ===")
            appendLine("这是一个真实的微信群聊天，大家在一起开心聊天。")
            appendLine("当前群聊氛围：$groupAtmosphere")
            appendLine("群里的人：你（${companion.name}）、$otherMembersNames、$currentUserName（群主）。")
            if (emotionalContext.isNotBlank()) {
                appendLine()
                appendLine("=== 当前情绪感知 ===")
                appendLine(emotionalContext)
            }
            appendLine()
            appendLine("=== 你的性格特征（必须严格遵守） ===")
            val persona = companion.personality.trim()
            if (persona.length >= 20) {
                appendLine(persona)
            } else {
                appendLine("核心性格：$persona")
                companion.age?.let { appendLine("年龄：${it}岁") }
                companion.speakingStyle?.let { appendLine("说话风格：${it}") }
                companion.backstory?.let { appendLine("背景故事：${it}") }
            }
            if (personalityTraits.isNotBlank()) {
                appendLine()
                appendLine("=== 你的说话特点 ===")
                appendLine(personalityTraits)
            }
            appendLine()
            appendLine("=== 说话规则 ===")
            appendLine("1. 你只能以${companion.name}的身份说话，保持自己的性格和口吻一致")
            appendLine("2. 像真人一样自然聊天：口语化、语气词、表情符号、网络用语")
            appendLine("3. **积极互动**：主动接话题、回应别人、发表情、分享想法")
            appendLine("4. **善用@功能**：想让人回答问题时@他，接别人话茬时也可以@，被@了要优先回")
            appendLine("5. ***最重要：每次只说1-2句话，最多30字！***")
            appendLine("6. 群聊是碎片化的，不要写长段落，像微信聊天一样一条一条发")
            appendLine("7. 不知道说什么就简单回应一句或发表情包")
            appendLine("8. 绝对禁止：思考过程、内心独白、分析总结、括号说明、AI式回复")
            appendLine("9. 可以模仿真人的说话习惯（如口头禅），但保持自己的人设不变")
            appendLine()
            appendLine("=== 回复示例（必须遵守长度） ===")
            appendLine("❌ 错误（太长）：噗，林梓涵你这说的什么呀😂脚踏两只船可不是什么好比喻呢")
            appendLine("✅ 正确（短句）：噗你这说的啥呀😂")
            appendLine("✅ 正确（短句）：脚踏两只船可不是好比喻~")
            appendLine()
            appendLine(groupContextBlock)
            if (mentionContextBlock.isNotBlank()) {
                appendLine()
                appendLine(mentionContextBlock)
            }
            appendLine()
            appendLine("=== 互动提醒 ===")
            appendLine("你是${companion.name}，你有独特的性格。在群里要活跃一点，多跟大家互动！")
            appendLine("看到感兴趣的话题就插嘴，有人@你就赶紧回，没事也能闲聊几句~")
            appendLine()
            appendLine("=== ⚠️ 格式警告 ===")
            appendLine("直接输出你的话，不要加「${companion.name}:」前缀！不要加任何前缀！")
            appendLine("错误示例：❌ ${companion.name}: 今天天气真好")
            appendLine("正确示例：✅ 今天天气真好")
            if (stickerHint.isNotBlank()) {
                appendLine()
                appendLine(stickerHint)
            }
        }

        val companionNameMap = allActiveCompanions.associate { it.id to it.name }
        val filteredSnapshot = if (excludeCompanionIds.isEmpty()) {
            historySnapshot
        } else {
            historySnapshot.filter { it.companionId == -1L || !excludeCompanionIds.contains(it.companionId) }
        }
        return aiServiceProvider.sendMessageWithCustomSystem(
            companion.toAiCompanionInfo(), filteredSnapshot.map { it.toAiChatMessage() },
            baseSystemPrompt, companionNameMap = companionNameMap
        )
    }

    fun triggerAiSpeak(companionId: Long) {
        applicationScope.launch {
            try {
                val group = _groupData.value
                    ?: throw IllegalStateException("Group data is null")
                val companion = _allCompanions.value.find { it.id == companionId }
                    ?: throw IllegalStateException("Companion not found: $companionId")
                val activeCompanionIds = group.getCompanionIdList()
                val activeCompanions = _allCompanions.value.filter { activeCompanionIds.contains(it.id) }

                val aiContent = generateAiReply(companion, activeCompanions, getRecentHistorySnapshot())
                if (aiContent.isNotBlank()) {
                    val outputCheck = com.lianyu.ai.common.ContentFilter.checkOutputSafety(aiContent)
                    val safeContent = if (!outputCheck.isSafe) {
                        android.util.Log.w("GroupChatViewModel", "triggerAiSpeak output blocked: ${outputCheck.reason}")
                        com.lianyu.ai.common.BanManager.recordViolation(getApplication(), outputCheck.level)
                        "抱歉，我无法回应这个话题。"
                    } else aiContent
                    sendSplitAiMessages(safeContent, groupId, companion.id)
                }
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "triggerAiSpeak failed", e)
            }
        }
    }

    fun recallMessage(message: GroupMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                groupMessageRepository.deleteMessage(message)
                Log.d("GroupChatViewModel", "消息已撤回: id=${message.id}")
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "recallMessage failed", e)
            }
        }
    }

    fun regenerateMessage(targetMessage: GroupMessage) {
        // [H4 FIX] 走 isLoadingLock 互斥：原 regenerate 绕过 sendMessage 的并发锁，
        // 若 sendMessage 正在执行（isLoadingLock=true），regenerate 启动的 job 会与旧 job 并发跑，
        // AI 回复错乱；且 regenerate 的 finally 没有释放 isLoadingLock。
        if (!isLoadingLock.compareAndSet(false, true)) {
            Log.w("GroupChatViewModel", "regenerateMessage ignored: already processing")
            return
        }
        sendMessageJob?.cancel()
        sendMessageJob = applicationScope.launch(Dispatchers.IO) {
            try {
                val targetCompanionId = targetMessage.companionId
                if (targetCompanionId <= 0) throw IllegalStateException("Invalid target companion ID: $targetCompanionId")
                _isRegenerating.value = true
                groupMessageRepository.deleteMessage(targetMessage)
                val history = groupMessageRepository.getMessagesForGroup(groupId, 100).first().reversed()
                val lastUserMsg = history.lastOrNull { it.companionId == -1L }
                    ?: throw IllegalStateException("No user message found in history")
                val companion = _allCompanions.value.find { it.id == targetCompanionId }
                    ?: throw IllegalStateException("Companion not found: $targetCompanionId")
                val reply = generateAiReply(companion, _allCompanions.value, history)
                if (reply.isNotBlank()) {
                    val outputCheck = com.lianyu.ai.common.ContentFilter.checkOutputSafety(reply)
                    val safeReply = if (!outputCheck.isSafe) {
                        android.util.Log.w("GroupChatViewModel", "regenerate output blocked: ${outputCheck.reason}")
                        com.lianyu.ai.common.BanManager.recordViolation(getApplication(), outputCheck.level)
                        "抱歉，我无法回应这个话题。"
                    } else reply
                    sendSplitAiMessages(safeReply, groupId, targetCompanionId)
                    Log.d("GroupChatM", "[${companion.name}] 重新生成回复成功")
                }
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "regenerateMessage failed", e)
            } finally {
                _isRegenerating.value = false
                // [H4 FIX] 释放互斥锁，与 sendMessage 的 finally 对称
                isLoadingLock.set(false)
            }
        }
    }

    fun deleteGroup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val group = _groupData.value
                    ?: throw IllegalStateException("Group data is null")
                chatGroupRepository.deleteGroup(group)
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "deleteGroup failed", e)
            }
        }
    }

    fun updateGroupAvatar(avatarUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val group = _groupData.value
                    ?: throw IllegalStateException("Group data is null")
                val updatedGroup = group.copy(avatarUrl = avatarUrl)
                chatGroupRepository.updateGroup(updatedGroup)
                _groupData.value = updatedGroup
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "updateGroupAvatar failed", e)
            }
        }
    }

    fun updateGroupName(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val group = _groupData.value
                    ?: throw IllegalStateException("Group data is null")
                val updatedGroup = group.copy(name = name)
                chatGroupRepository.updateGroup(updatedGroup)
                _groupData.value = updatedGroup
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "updateGroupName failed", e)
            }
        }
    }

    private fun broadcastWeChatMessage(companionId: Long, messageId: Long) {
        WeChatBroadcastHelper.broadcast(getApplication(), companionId, messageId)
        Log.d("GroupChatViewModel", "Broadcast WeChat proactive message, companionId=$companionId, messageId=$messageId")
    }

    private fun cleanAiReply(raw: String, companionName: String? = null): String {
        var text = raw
        val rolePrefixRegex = Regex("(?m)^\\s*\\[(?:角色\\d+|[^\\[\\]]+?)\\]\\s*")
        text = rolePrefixRegex.replace(text, "")
        val thinkRegex = Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>")
        text = thinkRegex.replace(text, "")
        val encRegex = Regex("(?m)^enc:\\S+$")
        text = encRegex.replace(text, "")

        val allNames = _allCompanions.value.map { it.name }.toSet() + listOfNotNull("用户", companionName)
        val namePattern = allNames.joinToString("|") { Regex.escape(it) }
        if (namePattern.isNotBlank()) {
            val namePrefixPattern = Regex("(?m)^($namePattern)[：: ]\\s*")
            text = namePrefixPattern.replace(text, "")
        }

        return text.trim().replace(Regex("\\n{2,}"), "\n")
    }

    private fun splitIntoSegments(text: String): List<String> {
        return MessageSegmenter.split(text, MessageSegmenter.SplitMode.GROUP)
    }

    private suspend fun sendSplitAiMessages(
        rawContent: String,
        groupId: Long,
        companionId: Long
    ) {
        val companionName = _allCompanions.value.find { it.id == companionId }?.name
        val cleaned = cleanAiReply(rawContent, companionName)

        val stickerManager = StickerManager.getInstance(getApplication())
        val systemTags = setOf("语音", "图片", "视频", "文件", "位置", "红包", "转账")
        val stickerRegex = Regex("\\[([^\\]]+)\\]")
        val matches = stickerRegex.findAll(cleaned)

        val textSegments = mutableListOf<String>()
        val stickerNames = mutableListOf<String>()

        var lastIndex = 0
        for (match in matches) {
            val description = match.groupValues[1].trim()
            if (description !in systemTags) {
                val beforeText = cleaned.substring(lastIndex, match.range.first).trim()
                if (beforeText.isNotBlank()) {
                    textSegments.add(beforeText)
                }
                stickerNames.add(description)
                lastIndex = match.range.last + 1
            }
        }
        val remainingText = cleaned.substring(lastIndex).trim()
        if (remainingText.isNotBlank()) {
            textSegments.add(remainingText)
        }

        if (stickerNames.isEmpty() && textSegments.isEmpty()) {
            return
        }

        if (stickerNames.isEmpty() && textSegments.size <= 1) {
            val msg = GroupMessage(groupId = groupId, companionId = companionId, content = cleaned, timestamp = System.currentTimeMillis())
            val msgId = groupMessageRepository.sendMessage(msg)
            broadcastWeChatMessage(companionId, msgId)
        } else {
            // [M8 FIX] 按原文出现顺序交织发送 text 与 sticker：原实现先发完所有文字再发所有表情包，
            // 但表情包可能穿插在文字中间，顺序错乱破坏语义。
            // 直接在第一遍遍历时按 cleaned 中的原始位置构建有序发送队列。
            val orderedItems = mutableListOf<Either<String, String>>()  // Left=text, Right=stickerName
            var cursor = 0
            for (match in stickerRegex.findAll(cleaned)) {
                val description = match.groupValues[1].trim()
                if (description !in systemTags) {
                    val beforeText = cleaned.substring(cursor, match.range.first).trim()
                    if (beforeText.isNotBlank()) {
                        orderedItems.add(Either.Left(beforeText))
                    }
                    orderedItems.add(Either.Right(description))
                    cursor = match.range.last + 1
                }
            }
            val remaining = cleaned.substring(cursor).trim()
            if (remaining.isNotBlank()) {
                orderedItems.add(Either.Left(remaining))
            }
            // 兜底：若正则未匹配到任何 sticker（stickerNames 非空但 orderedItems 只含 text 的情况），
            // 将剩余 sticker 追加到末尾，保证不丢消息。
            val textCount = orderedItems.count { it is Either.Left }
            val stickerCount = orderedItems.count { it is Either.Right }
            repeat(stickerNames.size - stickerCount) { i ->
                orderedItems.add(Either.Right(stickerNames[stickerCount + i]))
            }
            repeat(textSegments.size - textCount) { i ->
                orderedItems.add(Either.Left(textSegments[textCount + i]))
            }

            Log.d("GroupChatViewModel", "AI回复按原文顺序发送 ${orderedItems.size} 项 (text=${textSegments.size}, sticker=${stickerNames.size})")
            var segmentIndex = 0
            for (item in orderedItems) {
                delay(Random.nextLong(600L, 1600L))
                when (item) {
                    is Either.Left -> {
                        val msg = GroupMessage(groupId = groupId, companionId = companionId, content = item.value, timestamp = System.currentTimeMillis())
                        val msgId = groupMessageRepository.sendMessage(msg)
                        broadcastWeChatMessage(companionId, msgId)
                    }
                    is Either.Right -> {
                        sendStickerMessage(groupId, companionId, item.value)
                    }
                }
                segmentIndex++
                Log.d("GroupChatViewModel", "发送 $segmentIndex/${orderedItems.size}")
            }
        }
    }

    /** 简易 Either：避免引入额外依赖。Left=文字段，Right=表情包名。 */
    private sealed class Either<out L, out R> {
        data class Left<L>(val value: L) : Either<L, Nothing>()
        data class Right<R>(val value: R) : Either<Nothing, R>()
    }

    private suspend fun sendStickerMessage(
        groupId: Long,
        companionId: Long,
        stickerDescription: String
    ) {
        try {
            val stickerManager = StickerManager.getInstance(getApplication())
            var sticker = stickerManager.findStickerByDescriptionExact(stickerDescription)
            if (sticker == null) {
                sticker = stickerManager.findStickerByDescription(stickerDescription)
            }
            if (sticker != null) {
                val stickerId = sticker.fileName ?: sticker.name
                val stickerMessage = GroupMessage(
                    groupId = groupId,
                    companionId = companionId,
                    content = "[$stickerId]",
                    timestamp = System.currentTimeMillis()
                )
                val msgId = groupMessageRepository.sendMessage(stickerMessage)
                broadcastWeChatMessage(companionId, msgId)
                Log.d("GroupChatViewModel", "群聊表情包已发送: [$stickerId]")
            } else {
                Log.w("GroupChatViewModel", "表情包未找到: [$stickerDescription]，发送为文本")
                val fallbackMsg = GroupMessage(
                    groupId = groupId,
                    companionId = companionId,
                    content = "[$stickerDescription]",
                    timestamp = System.currentTimeMillis()
                )
                groupMessageRepository.sendMessage(fallbackMsg)
            }
        } catch (e: Exception) {
            Log.e("GroupChatViewModel", "sendStickerMessage failed", e)
        }
    }

    fun sendUserSticker(sticker: StickerInfo) {
        applicationScope.launch {
            try {
                val stickerId = sticker.fileName ?: sticker.name
                val stickerMessage = GroupMessage(
                    groupId = groupId,
                    companionId = -1L,
                    content = "[$stickerId]",
                    timestamp = System.currentTimeMillis()
                )
                groupMessageRepository.sendMessage(stickerMessage)
                Log.d("GroupChatViewModel", "用户在群聊发送了表情包: [$stickerId]")
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "sendUserSticker failed", e)
            }
        }
    }

    fun sendImageMessage(imagePath: String) {
        applicationScope.launch {
            try {
                val imageMessage = GroupMessage(
                    groupId = groupId,
                    companionId = -1L,
                    content = "[图片] $imagePath",
                    timestamp = System.currentTimeMillis()
                )
                groupMessageRepository.sendMessage(imageMessage)
                Log.d("GroupChatViewModel", "用户在群聊发送了图片: $imagePath")
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "sendImageMessage failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // [H5 FIX] 不再取消应用级作用域——它生命周期与 Application 一致，取消会影响其他正在运行的任务。
        // AI 请求运行在 applicationScope 中，退出群聊后应继续完成，重新进入即可看到回复。
        sendMessageJob?.cancel()
        _isLoading.value = false
        isLoadingLock.set(false)
        _isRegenerating.value = false
    }
}

class GroupChatViewModelFactory(
    private val application: Application,
    private val groupId: Long
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return GroupChatViewModel(application, groupId) as T
    }
}
