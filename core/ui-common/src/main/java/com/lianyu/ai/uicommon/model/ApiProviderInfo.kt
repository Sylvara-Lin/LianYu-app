package com.lianyu.ai.uicommon.model

import androidx.compose.runtime.Stable

import androidx.compose.ui.graphics.Color

@Stable
data class ApiProviderInfo(
    val name: String,
    val displayName: String,
    val color: Color,
    val configId: Long? = null
) {
    companion object {
        val OPENAI = ApiProviderInfo("OPENAI", "OpenAI", Color(0xFF10A37F))
        val ANTHROPIC = ApiProviderInfo("ANTHROPIC", "Claude", Color(0xFFCC785C))
        val GEMINI = ApiProviderInfo("GEMINI", "Gemini", Color(0xFF8E75B7))
        val DEEPSEEK = ApiProviderInfo("DEEPSEEK", "DeepSeek", Color(0xFF4D6BFA))
        val DASHSCOPE = ApiProviderInfo("DASHSCOPE", "通义千问", Color(0xFF624AFF))
        val KIMI = ApiProviderInfo("KIMI", "Kimi", Color(0xFF000000))
        val XIAOMI = ApiProviderInfo("XIAOMI", "小米 MiMo", Color(0xFFFF6900))
        val IFLYTEK = ApiProviderInfo("IFLYTEK", "讯飞星火", Color(0xFF1677FF))
        val ZHIPU = ApiProviderInfo("ZHIPU", "智谱清言", Color(0xFF4169E1))
        val SILICONFLOW = ApiProviderInfo("SILICONFLOW", "硅基流动", Color(0xFF10A37F))
        val OPENROUTER = ApiProviderInfo("OPENROUTER", "OpenRouter", Color(0xFF7B68EE))
        val GROQ = ApiProviderInfo("GROQ", "Groq", Color(0xFFF4845F))
        val PARTNER = ApiProviderInfo("PARTNER", "自定义中继", Color(0xFFFF69B4))
        val CUSTOM = ApiProviderInfo("CUSTOM", "自定义", Color(0xFFFF6B6B))
        val LOCAL = ApiProviderInfo("LOCAL", "本地模型", Color(0xFF00C853))

        fun fromName(name: String): ApiProviderInfo {
            return when (name) {
                "OPENAI" -> OPENAI
                "ANTHROPIC" -> ANTHROPIC
                "GEMINI" -> GEMINI
                "DEEPSEEK" -> DEEPSEEK
                "DASHSCOPE" -> DASHSCOPE
                "KIMI" -> KIMI
                "XIAOMI" -> XIAOMI
                "IFLYTEK" -> IFLYTEK
                "ZHIPU" -> ZHIPU
                "SILICONFLOW" -> SILICONFLOW
                "OPENROUTER" -> OPENROUTER
                "GROQ" -> GROQ
                "PARTNER" -> PARTNER
                "CUSTOM" -> CUSTOM
                "LOCAL" -> LOCAL
                else -> ApiProviderInfo(name, name, Color(0xFF888888))
            }
        }
    }
}
