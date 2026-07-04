package com.lianyu.ai.domain

/**
 * 跨会话记忆提供者接口。
 *
 * 消费方：
 * - core:network/AiService（注入记忆上下文）
 * - feature:groupchat/GroupChatViewModel（提取并保存对话记忆）
 *
 * 实现方：feature:memory（MemoryManager）
 *
 * 通过 ServiceRegistry 绑定，消除 core:network → feature:memory 的非法依赖。
 */
interface MemoryProvider {
    /**
     * 初始化：加载持久化记忆到内存。幂等，可多次调用。
     */
    fun initialize()

    /**
     * 获取记忆上下文（注入 AI 对话）。
     *
     * @param companionId 角色ID（单聊时传入，群聊时为 null）
     * @param groupId 群聊ID（群聊时传入，单聊时为 null）
     * @param query 查询文本（当前用户消息）
     * @param limit 返回记忆数量限制
     * @return 格式化的记忆上下文字符串；无匹配时返回空串
     */
    suspend fun getMemoryContext(
        companionId: Long?,
        groupId: Long?,
        query: String,
        limit: Int = 5
    ): String

    /**
     * 从对话中提取并保存记忆。
     *
     * @param userInput 用户输入
     * @param aiResponse AI 回复
     * @param companionId 角色 ID
     * @param groupId 群聊 ID（群聊时传入，单聊时为 null）
     */
    suspend fun extractAndSaveFromConversation(
        userInput: String,
        aiResponse: String,
        companionId: Long,
        groupId: Long? = null
    )
}
