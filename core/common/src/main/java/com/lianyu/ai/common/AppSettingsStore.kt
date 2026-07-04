package com.lianyu.ai.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsStore(context: Context) {

    private val dataStore = context.applicationContext.appSettingsDataStore

    private companion object {
        private val CONTEXT_LIMIT_KEY = intPreferencesKey("context_message_limit")
        private const val DEFAULT_CONTEXT_LIMIT = 50
        private const val MIN_CONTEXT_LIMIT = 1
        private const val MAX_CONTEXT_LIMIT = 10000

        private val SHOW_REASONING_KEY = booleanPreferencesKey("show_reasoning")
        private const val DEFAULT_SHOW_REASONING = false

        private val REASONING_RESPONSE_FIELD_KEY = stringPreferencesKey("reasoning_response_field")
        private const val DEFAULT_REASONING_RESPONSE_FIELD = "reasoning_content"

        private val REASONING_REQUEST_FIELD_KEY = stringPreferencesKey("reasoning_request_field")
        private const val DEFAULT_REASONING_REQUEST_FIELD = "reasoning_content"

        private val SEND_REASONING_KEY = booleanPreferencesKey("send_reasoning")
        private const val DEFAULT_SEND_REASONING = false

        private val AUTO_COLLAPSE_REASONING_KEY = booleanPreferencesKey("auto_collapse_reasoning")
        private const val DEFAULT_AUTO_COLLAPSE_REASONING = true

        private val VISION_ENABLED_KEY = booleanPreferencesKey("vision_enabled")
        private const val DEFAULT_VISION_ENABLED = true

        private val VISION_MODEL_KEY = stringPreferencesKey("vision_model")
        private const val DEFAULT_VISION_MODEL = "auto"

        private val VISION_PROVIDER_KEY = stringPreferencesKey("vision_provider")
        private const val DEFAULT_VISION_PROVIDER = "auto"

        private val VISION_API_URL_KEY = stringPreferencesKey("vision_api_url")
        private const val DEFAULT_VISION_API_URL = ""

        private val VISION_API_KEY_KEY = stringPreferencesKey("vision_api_key")
        private const val DEFAULT_VISION_API_KEY = ""

        private val INNER_THOUGHT_ENABLED_KEY = booleanPreferencesKey("inner_thought_enabled")
        private const val DEFAULT_INNER_THOUGHT_ENABLED = false

        private val CONTEXT_COMPRESSION_KEY = stringPreferencesKey("context_compression_mode")
        private const val DEFAULT_CONTEXT_COMPRESSION = "off"

        private val COMPRESSION_KEEP_RATIO_KEY = intPreferencesKey("compression_keep_ratio")
        private const val DEFAULT_COMPRESSION_KEEP_RATIO = 50

        private val COMPRESSION_MIN_KEEP_KEY = intPreferencesKey("compression_min_keep")
        private const val DEFAULT_COMPRESSION_MIN_KEEP = 6

        private val YANDERE_MODE_ENABLED_KEY = booleanPreferencesKey("yandere_mode_enabled")
        private const val DEFAULT_YANDERE_MODE_ENABLED = false

        private val YANDERE_MODE_USAGE_STATS_KEY = booleanPreferencesKey("yandere_mode_usage_stats")
        private const val DEFAULT_YANDERE_MODE_USAGE_STATS = true

        private val YANDERE_MODE_INSTALLED_APPS_KEY = booleanPreferencesKey("yandere_mode_installed_apps")
        private const val DEFAULT_YANDERE_MODE_INSTALLED_APPS = true
    }

    object CompressionMode {
        const val OFF = "off"
        const val LOCAL = "local"
        const val AI = "ai"
        val ALL = listOf(OFF, LOCAL, AI)
    }

    object VisionModels {
        const val VISION_AUTO = "auto"
        const val VISION_GPT4O = "gpt-4o"
        const val VISION_GPT4_VISION = "gpt-4-vision-preview"
        const val VISION_CLAUDE_SONNET = "claude-3-5-sonnet-20241022"
        const val VISION_GEMINI_PRO = "gemini-1.5-pro-vision"
        const val VISION_DEEPSEEK_VL = "deepseek-vl"
        const val VISION_KIMI_K26 = "kimi-k2.6"

        val VISION_MODEL_OPTIONS = listOf(
            Triple(VISION_AUTO, "自动检测", "根据当前AI提供商自动选择视觉模型"),
            Triple(VISION_GPT4O, "GPT-4o", "OpenAI 多模态模型"),
            Triple(VISION_GPT4_VISION, "GPT-4 Vision", "OpenAI 视觉模型"),
            Triple(VISION_CLAUDE_SONNET, "Claude 3.5 Sonnet Vision", "Anthropic 视觉模型"),
            Triple(VISION_GEMINI_PRO, "Gemini Pro Vision", "Google 视觉模型"),
            Triple(VISION_DEEPSEEK_VL, "DeepSeek-VL", "DeepSeek 视觉模型"),
            Triple(VISION_KIMI_K26, "Kimi K2.6", "Moonshot AI 最新视觉模型")
        )

        fun getVisionModelDisplayName(modelId: String): String {
            return VISION_MODEL_OPTIONS.find { it.first == modelId }?.second ?: modelId
        }

        fun resolveVisionModel(visionModelSetting: String, providerName: String): String {
            if (visionModelSetting != VISION_AUTO) return visionModelSetting
            return when (providerName.lowercase()) {
                "openai", "partner" -> VISION_GPT4O
                "anthropic" -> VISION_CLAUDE_SONNET
                "gemini", "google" -> VISION_GEMINI_PRO
                "deepseek" -> VISION_DEEPSEEK_VL
                "kimi", "moonshot" -> VISION_KIMI_K26
                "custom" -> VISION_GPT4O // Default for custom, user should specify model explicitly
                else -> VISION_GPT4O
            }
        }
    }

    val contextLimitFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[CONTEXT_LIMIT_KEY]?.coerceIn(MIN_CONTEXT_LIMIT, MAX_CONTEXT_LIMIT) ?: DEFAULT_CONTEXT_LIMIT
    }

    suspend fun getContextLimit(): Int = contextLimitFlow.first()

    suspend fun setContextLimit(limit: Int) {
        dataStore.edit { prefs ->
            prefs[CONTEXT_LIMIT_KEY] = limit.coerceIn(MIN_CONTEXT_LIMIT, MAX_CONTEXT_LIMIT)
        }
    }

    val showReasoningFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_REASONING_KEY] ?: DEFAULT_SHOW_REASONING
    }

    suspend fun getShowReasoning(): Boolean = showReasoningFlow.first()

    suspend fun setShowReasoning(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_REASONING_KEY] = enabled }
    }

    val reasoningResponseFieldFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[REASONING_RESPONSE_FIELD_KEY] ?: DEFAULT_REASONING_RESPONSE_FIELD
    }

    suspend fun getReasoningResponseField(): String = reasoningResponseFieldFlow.first()

    suspend fun setReasoningResponseField(field: String) {
        dataStore.edit { prefs -> prefs[REASONING_RESPONSE_FIELD_KEY] = field }
    }

    val reasoningRequestFieldFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[REASONING_REQUEST_FIELD_KEY] ?: DEFAULT_REASONING_REQUEST_FIELD
    }

    suspend fun getReasoningRequestField(): String = reasoningRequestFieldFlow.first()

    suspend fun setReasoningRequestField(field: String) {
        dataStore.edit { prefs -> prefs[REASONING_REQUEST_FIELD_KEY] = field }
    }

    val sendReasoningFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SEND_REASONING_KEY] ?: DEFAULT_SEND_REASONING
    }

    suspend fun getSendReasoning(): Boolean = sendReasoningFlow.first()

    suspend fun setSendReasoning(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SEND_REASONING_KEY] = enabled }
    }

    val autoCollapseReasoningFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_COLLAPSE_REASONING_KEY] ?: DEFAULT_AUTO_COLLAPSE_REASONING
    }

    suspend fun getAutoCollapseReasoning(): Boolean = autoCollapseReasoningFlow.first()

    suspend fun setAutoCollapseReasoning(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AUTO_COLLAPSE_REASONING_KEY] = enabled }
    }

    val visionEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[VISION_ENABLED_KEY] ?: DEFAULT_VISION_ENABLED
    }

    suspend fun getVisionEnabled(): Boolean = visionEnabledFlow.first()

    suspend fun setVisionEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[VISION_ENABLED_KEY] = enabled }
    }

    val visionModelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[VISION_MODEL_KEY] ?: DEFAULT_VISION_MODEL
    }

    suspend fun getVisionModel(): String = visionModelFlow.first()

    suspend fun setVisionModel(model: String) {
        dataStore.edit { prefs -> prefs[VISION_MODEL_KEY] = model }
    }

    val visionProviderFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[VISION_PROVIDER_KEY] ?: DEFAULT_VISION_PROVIDER
    }

    suspend fun getVisionProvider(): String = visionProviderFlow.first()

    suspend fun setVisionProvider(provider: String) {
        dataStore.edit { prefs -> prefs[VISION_PROVIDER_KEY] = provider }
    }

    val visionApiUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[VISION_API_URL_KEY] ?: DEFAULT_VISION_API_URL
    }

    suspend fun getVisionApiUrl(): String = visionApiUrlFlow.first()

    suspend fun setVisionApiUrl(url: String) {
        dataStore.edit { prefs -> prefs[VISION_API_URL_KEY] = url }
    }

    val visionApiKeyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[VISION_API_KEY_KEY] ?: DEFAULT_VISION_API_KEY
    }

    suspend fun getVisionApiKey(): String = visionApiKeyFlow.first()

    suspend fun setVisionApiKey(key: String) {
        dataStore.edit { prefs -> prefs[VISION_API_KEY_KEY] = key }
    }

    val innerThoughtEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[INNER_THOUGHT_ENABLED_KEY] ?: DEFAULT_INNER_THOUGHT_ENABLED
    }

    suspend fun getInnerThoughtEnabled(): Boolean = innerThoughtEnabledFlow.first()

    suspend fun setInnerThoughtEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[INNER_THOUGHT_ENABLED_KEY] = enabled }
    }

    val contextCompressionModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[CONTEXT_COMPRESSION_KEY] ?: DEFAULT_CONTEXT_COMPRESSION
    }

    suspend fun getContextCompressionMode(): String = contextCompressionModeFlow.first()

    suspend fun setContextCompressionMode(mode: String) {
        dataStore.edit { prefs -> prefs[CONTEXT_COMPRESSION_KEY] = mode }
    }

    val compressionKeepRatioFlow: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[COMPRESSION_KEEP_RATIO_KEY] ?: DEFAULT_COMPRESSION_KEEP_RATIO) / 100f
    }

    suspend fun getCompressionKeepRatio(): Float = compressionKeepRatioFlow.first()

    suspend fun setCompressionKeepRatio(ratio: Float) {
        dataStore.edit { prefs -> prefs[COMPRESSION_KEEP_RATIO_KEY] = (ratio.coerceIn(0.1f, 0.9f) * 100).toInt() }
    }

    val compressionMinKeepFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[COMPRESSION_MIN_KEEP_KEY] ?: DEFAULT_COMPRESSION_MIN_KEEP
    }

    suspend fun getCompressionMinKeep(): Int = compressionMinKeepFlow.first()

    suspend fun setCompressionMinKeep(count: Int) {
        dataStore.edit { prefs -> prefs[COMPRESSION_MIN_KEEP_KEY] = count.coerceIn(2, 20) }
    }

    val yandereModeEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[YANDERE_MODE_ENABLED_KEY] ?: DEFAULT_YANDERE_MODE_ENABLED
    }

    suspend fun getYandereModeEnabled(): Boolean = yandereModeEnabledFlow.first()

    suspend fun setYandereModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[YANDERE_MODE_ENABLED_KEY] = enabled }
    }

    val yandereModeUsageStatsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[YANDERE_MODE_USAGE_STATS_KEY] ?: DEFAULT_YANDERE_MODE_USAGE_STATS
    }

    suspend fun getYandereModeUsageStats(): Boolean = yandereModeUsageStatsFlow.first()

    suspend fun setYandereModeUsageStats(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[YANDERE_MODE_USAGE_STATS_KEY] = enabled }
    }

    val yandereModeInstalledAppsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[YANDERE_MODE_INSTALLED_APPS_KEY] ?: DEFAULT_YANDERE_MODE_INSTALLED_APPS
    }

    suspend fun getYandereModeInstalledApps(): Boolean = yandereModeInstalledAppsFlow.first()

    suspend fun setYandereModeInstalledApps(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[YANDERE_MODE_INSTALLED_APPS_KEY] = enabled }
    }
}