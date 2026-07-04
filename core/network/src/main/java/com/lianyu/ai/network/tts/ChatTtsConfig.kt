package com.lianyu.ai.network.tts

import android.content.Context
import com.lianyu.ai.common.SecureLog

/**
 * 聊天页 TTS 朗读模式。
 *
 * 参考反编译代码 [H:\aiyu\chat_tts] 的 `SettingsActivity.KEY_VOICE_MODE`：
 * - [SILENT]   完全静音，不生成任何语音
 * - [VOICE_BAR] 语音条：合成音频 → 显示可点击波形气泡（复用 VoiceMessageBubble）
 * - [READ_ALOUD] 语音朗读：合成音频 → 加入队列 → 自动顺序播放
 *
 * 放在 core:network 以便 feature:chat 和 feature:settings 共用（feature 模块间不能互相依赖）。
 */
enum class ChatTtsMode(val displayName: String, val description: String) {
    SILENT("静音", "不生成任何语音"),
    VOICE_BAR("语音条", "合成语音气泡，点击播放"),
    READ_ALOUD("语音朗读", "自动顺序朗读 AI 回复");

    companion object {
        fun fromOrdinalSafe(value: Int): ChatTtsMode =
            entries.elementAtOrNull(value) ?: SILENT
    }
}

/**
 * 聊天页 TTS 配置（独立于 [TtsConfig]，避免污染其 data class）。
 *
 * 持久化到 SharedPreferences `"tts_settings"`（与 TtsConfig 同名但独立 keys），
 * 不触发 Room schema 变更。
 *
 * @property mode 朗读模式
 * @property skipParentheses 跳过括号内容（AI 内心戏），对应参考的 `key_skip_parentheses`
 * @property autoDedup 自动去重，不重复朗读已读内容（参考的 `KEY_VOICE_AUTO_DEDUP`，默认 true）
 * @property beautify 启用音频美化（Android Equalizer 预设，参考的 `KEY_VOICE_BEAUTIFY`，默认 true）
 */
data class ChatTtsConfig(
    val mode: ChatTtsMode = ChatTtsMode.SILENT,
    val skipParentheses: Boolean = false,
    val autoDedup: Boolean = true,
    val beautify: Boolean = true
) {
    companion object {
        private const val PREFS_NAME = "tts_settings"
        private const val KEY_MODE = "chat_tts_mode"
        private const val KEY_SKIP_PARENTHESES = "key_skip_parentheses"
        private const val KEY_AUTO_DEDUP = "chat_tts_auto_dedup"
        private const val KEY_BEAUTIFY = "chat_tts_beautify"

        fun fromSharedPreferences(context: Context): ChatTtsConfig {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ChatTtsConfig(
                    mode = ChatTtsMode.fromOrdinalSafe(prefs.getInt(KEY_MODE, 0)),
                    skipParentheses = prefs.getBoolean(KEY_SKIP_PARENTHESES, false),
                    autoDedup = prefs.getBoolean(KEY_AUTO_DEDUP, true),
                    beautify = prefs.getBoolean(KEY_BEAUTIFY, true)
                )
            } catch (e: Exception) {
                SecureLog.e("ChatTtsConfig", "Failed to read prefs, using defaults", e)
                ChatTtsConfig()
            }
        }

        fun saveToSharedPreferences(context: Context, config: ChatTtsConfig) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putInt(KEY_MODE, config.mode.ordinal)
                    putBoolean(KEY_SKIP_PARENTHESES, config.skipParentheses)
                    putBoolean(KEY_AUTO_DEDUP, config.autoDedup)
                    putBoolean(KEY_BEAUTIFY, config.beautify)
                    apply()
                }
            } catch (e: Exception) {
                SecureLog.e("ChatTtsConfig", "Failed to save prefs", e)
            }
        }
    }
}
