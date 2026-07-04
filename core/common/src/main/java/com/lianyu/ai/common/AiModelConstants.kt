package com.lianyu.ai.common

/**
 * AI 模型相关常量集中管理。
 *
 * 包括：
 * - 设置页测试连接时的默认提示词
 * - 模型名关键词（用于区分 chat / embedding / moderation 等能力）
 *
 * 这些值原先散落在 SettingsViewModel 中，集中后便于统一维护。
 */
object AiModelConstants {

    /** 设置页测试连接的默认 system 消息 */
    const val TEST_SYSTEM_MESSAGE = "You are a helpful assistant."

    /** 设置页测试连接的默认 user 消息 */
    const val TEST_USER_MESSAGE = "Hi"

    /** 判断模型是否具备聊天能力的名称关键词 */
    val CHAT_MODEL_KEYWORDS = listOf(
        "chat", "completion", "instruct", "gpt", "claude", "gemini",
        "deepseek", "qwen", "glm", "moonshot", "kimi", "yi-", "ernie", "hunyuan", "doubao"
    )

    /** 判断模型是否应排除的名称关键词（embedding / moderation 等非对话模型） */
    val EXCLUDED_MODEL_KEYWORDS = listOf("embed", "moderation")
}
