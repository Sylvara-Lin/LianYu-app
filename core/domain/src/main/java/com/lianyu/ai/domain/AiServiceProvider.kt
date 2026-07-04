package com.lianyu.ai.domain

/**
 * AI 对话服务提供者接口。
 * 由 core:network 实现，通过 ServiceRegistry 注入到 feature 模块。
 *
 * 注意：此接口属于 core:domain 零依赖模块，
 * 使用领域层数据类而非 core:database 实体，避免模块间耦合。
 */

/** AI 对话响应 */
data class AiResponse(
    val content: String,
    val reasoningContent: String? = null,
    /** AI 要求调用的工具列表（finishReason == "tool_calls" 时非空） */
    val toolCalls: List<AiToolCall>? = null,
    /** 结束原因：stop / tool_calls / length */
    val finishReason: String? = null
)

/** AI 发起的单次工具调用 */
data class AiToolCall(
    val id: String,
    val name: String,
    /** 参数 JSON 字符串，由执行端解析 */
    val arguments: String
)

/** 伴侣角色摘要信息，供 AI 对话使用 */
data class AiCompanionInfo(
    val id: Long,
    val name: String,
    val personality: String,
    val age: Int? = null,
    val backstory: String? = null,
    val speakingStyle: String? = null,
    val systemPrompt: String? = null
)

/** 消息类型 */
enum class AiMessageType {
    TEXT, IMAGE
}

/** 聊天消息摘要，供 AI 对话使用 */
data class AiChatMessage(
    val isFromUser: Boolean,
    val content: String,
    val timestamp: Long,
    val type: AiMessageType = AiMessageType.TEXT,
    val companionId: Long = 0
)

interface AiServiceProvider {
    /**
     * 发送文本消息并获取 AI 响应。
     *
     * @param companion 伴侣角色信息
     * @param history 聊天历史消息
     * @param stickerProbability 表情包发送概率 (0-100)
     * @return AI 响应
     */
    suspend fun sendMessage(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        stickerProbability: Int = 0,
        ntpTimeEnabled: Boolean = false
    ): AiResponse

    /**
     * 发送文本消息并获取 AI 响应（带工具调用能力）。
     *
     * 当 [tools] 非空时，请求体会包含 tools 定义，AI 可返回 tool_calls。
     * 调用方负责执行 tool_calls 并重新调用本方法（传入追加了 tool 结果的 history）。
     *
     * @param tools 可被 AI 调用的工具列表，null 或空表示不支持工具调用
     */
    suspend fun sendMessage(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        stickerProbability: Int,
        ntpTimeEnabled: Boolean,
        tools: List<AiTool>?
    ): AiResponse

    /**
     * 发送图片消息并调用视觉 AI 模型进行识别。
     *
     * @param companion 伴侣角色信息
     * @param history 聊天历史消息
     * @param imagePath 本地图片文件路径
     * @param stickerProbability 表情包发送概率 (0-100)
     * @return AI 响应
     */
    suspend fun sendMessageWithImage(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        imagePath: String,
        stickerProbability: Int = 0,
        ntpTimeEnabled: Boolean = false
    ): AiResponse

    /**
     * 判断是否需要发送主动消息。
     *
     * @param companion 伴侣角色信息
     * @param recentMessages 最近聊天消息
     * @return 是否应发送主动消息
     */
    fun shouldProactivelyMessage(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>
    ): Boolean

    /**
     * 判断是否需要发送主动消息（带自定义设置）。
     *
     * @param settings 主动消息自定义设置，null 走默认行为
     */
    fun shouldProactivelyMessage(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>,
        settings: ProactiveMessageSettings?
    ): Boolean {
        // 默认转发到无设置版本，实现侧可覆盖以读取开关
        return shouldProactivelyMessage(companion, recentMessages)
    }

    /**
     * 生成主动消息内容。
     *
     * @param companion 伴侣角色信息
     * @param recentMessages 最近聊天消息
     * @return 主动消息内容，null 表示不发送
     */
    suspend fun generateProactiveMessage(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>
    ): String?

    /**
     * 生成主动消息内容（带自定义设置）。
     *
     * 设置影响：allowNewTopic=false 时强制承接上一话题；
     * allowFollowUpMessage 控制是否生成追问。
     *
     * @param settings 主动消息自定义设置，null 走默认行为
     */
    suspend fun generateProactiveMessage(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>,
        settings: ProactiveMessageSettings?
    ): String? {
        // 默认转发到无设置版本，实现侧可覆盖以读取开关
        return generateProactiveMessage(companion, recentMessages)
    }

    /**
     * 使用自定义系统提示词发送消息（群聊场景）。
     *
     * @param companion 伴侣角色信息
     * @param history 聊天历史消息
     * @param customSystemPrompt 自定义系统提示词
     * @param stickerProbability 表情包发送概率 (0-100)
     * @param companionNameMap 角色 ID→名称 映射
     * @return AI 响应文本
     */
    suspend fun sendMessageWithCustomSystem(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        customSystemPrompt: String,
        stickerProbability: Int = 30,
        companionNameMap: Map<Long, String> = emptyMap()
    ): String

    /**
     * 生成追问问题。AI回复后按概率触发，让对话继续下去。
     *
     * @param companion 伴侣角色信息
     * @param recentMessages 最近聊天消息
     * @param lastAiContent AI最后一条回复内容
     * @return 追问内容，null 表示不生成
     */
    suspend fun generateFollowUpQuestion(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>,
        lastAiContent: String
    ): String?

    /**
     * 轻量 AI 调用：用于 @ 提及判断。
     * 使用当前活跃 API 配置，不依赖特定 provider。
     */
    suspend fun callJudge(prompt: String): String

    /**
     * 轻量 AI 调用：用于人设/角色自动生成。
     * 使用当前活跃 API 配置，不依赖特定 provider。
     */
    suspend fun callGeneration(prompt: String): String
}
