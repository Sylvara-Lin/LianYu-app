package com.lianyu.ai.network

import android.content.Context
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.BanManager
import com.lianyu.ai.common.CompanionRole
import com.lianyu.ai.common.ContentFilter
import com.lianyu.ai.common.DeviceIdProvider
import com.lianyu.ai.common.RolePromptProvider
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.StickerManager
import com.lianyu.ai.common.TimeoutBudgets
import com.lianyu.ai.common.YandereModeManager
import com.lianyu.ai.common.SuFlowApi
import com.lianyu.ai.common.RemoteKeyProvider
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ApiProvider
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.database.model.CompanionEntity as CompanionModel
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiMessageType
import com.lianyu.ai.domain.AiResponse
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.AiTool
import com.lianyu.ai.domain.ProactiveMessageSettings
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.database.repository.ApiConfigRepository
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.MemoryRepository
import com.lianyu.ai.database.repository.TokenUsageRepository
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.network.provider.AiProvider
import com.lianyu.ai.network.provider.ClaudeProvider
import com.lianyu.ai.network.provider.OpenAiCompatibleProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class AiService(context: Context) : AiServiceProvider {
    private val appContext = context.applicationContext
    private val apiConfigRepository: ApiConfigRepository
    private val companionRepository: CompanionRepository
    private val memoryRepository: MemoryRepository
    private val memoryProvider: com.lianyu.ai.domain.MemoryProvider
    private val tokenUsageRepository: TokenUsageRepository
    private val userRepository: UserRepository
    private val appSettingsStore = AppSettingsStore(appContext)

    @Volatile
    private var cachedBuiltinModel: String? = null

    // 熔断器 + 速率限流 (控制论: 滞环非线性保护 + 饱和限)
        // ============================================================
    // [反馈回路] 熔断器 & 速率限流
    // ============================================================
    private val retryController = AiRetryController()
    private val rateLimiter = AiRateLimiter()

    init {
        val database = AppDatabase.getDatabase(appContext)
        val deviceId = DeviceIdProvider.getDeviceId(appContext)
        apiConfigRepository = ApiConfigRepository(database.apiConfigDao())
        companionRepository = ServiceRegistry.getOrThrow(CompanionRepository::class.java)
        memoryRepository = MemoryRepository(database.memoryDao(), deviceId)
        memoryProvider = com.lianyu.ai.domain.ServiceRegistry.getOrThrow(com.lianyu.ai.domain.MemoryProvider::class.java)
        memoryProvider.initialize()
        tokenUsageRepository = TokenUsageRepository(appContext)
        userRepository = ServiceRegistry.getOrThrow(UserRepository::class.java)
    }

    /**
     * 按需追加病娇模式系统提示词。
     * 仅在全局开关开启、本轮概率触发且能构建出非空提示词时追加。
     * 失败时静默降级，不影响正常对话。
     */
    private suspend fun appendYanderePromptIfNeeded(systemPrompt: String, companion: CompanionModel): String {
        return try {
            val manager = ServiceRegistry.get(YandereModeManager::class.java)
                ?: return systemPrompt
            if (!appSettingsStore.getYandereModeEnabled()) return systemPrompt
            if (!manager.shouldTriggerThisRound()) return systemPrompt
            val role = ServiceRegistry.get(UserRepository::class.java)?.selectedRole?.value
                ?: CompanionRole.GIRLFRIEND
            val yanderePrompt = manager.buildYandereModeSystemPrompt(role)
            if (yanderePrompt.isBlank()) return systemPrompt
            SecureLog.d("AiService", "Yandere mode triggered for companion=${companion.name}, role=$role")
            "$systemPrompt\n\n$yanderePrompt"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.w("AiService", "appendYanderePromptIfNeeded failed: ${e.message}")
            systemPrompt
        }
    }

    private suspend fun resolveConfig(): ApiConfig? {
        return apiConfigRepository.getActiveEnabledConfig()
    }

    private suspend fun tryFetchBuiltinModel(keys: List<String>): String? {
        return try {
            val result = fetchModels(ApiProvider.PARTNER.defaultBaseUrl, keys.first())
            result.getOrNull()?.let { models ->
                if (models.isNotEmpty()) {
                    val chatModels = models.filter { m ->
                        chatKeywords.any { m.contains(it, ignoreCase = true) }
                    }
                    val candidatePool = if (chatModels.size > 1) chatModels
                    else models.filter { !it.contains("embed", ignoreCase = true) && !it.contains("moderation", ignoreCase = true) }
                    val selected = if (candidatePool.size > 1) {
                        familyBalancedRandom(candidatePool)
                    } else {
                        candidatePool.firstOrNull() ?: models.first()
                    }
                    SecureLog.api("BUILTIN", "Auto-selected model: $selected from ${models.size} models (chatModels=${chatModels.size})")
                    selected
                } else null
            }
        } catch (e: Exception) {
            SecureLog.w("AiService", "Auto-fetch builtin models failed: ${e.message}")
            null
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 🔒 Shared thread pool for fetchModels() — prevents per-call thread leak. */
        private val fetchModelsExecutor = java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
            Thread(r, "AiService-fetchModels").apply { isDaemon = true }
        }

        /**
         * Check if a model requires temperature=1 (no other values supported)
         */
        fun requiresFixedTemperature(model: String): Boolean {
            return model.contains("kimi-k2.6", ignoreCase = true) ||
                   model.contains("k2.6", ignoreCase = true)
        }

        // 模型家族关键词（用于 chat 过滤 + 家族均衡随机）
        private val modelFamilyKeywords = listOf(
            "deepseek", "qwen", "glm", "kimi", "moonshot",
            "gpt", "claude", "gemini", "yi-", "ernie", "hunyuan", "doubao"
        )
        private val chatKeywords = modelFamilyKeywords + listOf("chat", "completion", "instruct")

        /**
         * 家族均衡随机：先随机选家族，再在家族内随机选模型，避免模型数量多的家族占比过高。
         * 若 candidatePool 为空或只有 1 个模型，直接返回。
         * 随机数直接从 /dev/urandom 读取，不依赖 PRNG seed。
         */
        private fun urandomInt(bound: Int): Int {
            val buf = ByteArray(4)
            java.io.FileInputStream("/dev/urandom").use { it.read(buf) }
            val raw = ((buf[0].toInt() and 0xFF) shl 24) or
                       ((buf[1].toInt() and 0xFF) shl 16) or
                       ((buf[2].toInt() and 0xFF) shl 8) or
                       (buf[3].toInt() and 0xFF)
            return (raw and Int.MAX_VALUE) % bound
        }

        fun familyBalancedRandom(candidatePool: List<String>): String {
            if (candidatePool.size <= 1) return candidatePool.first()
            // 按首个匹配的家族关键词分组
            val groups = LinkedHashMap<String, MutableList<String>>()
            for (m in candidatePool) {
                val family = modelFamilyKeywords.firstOrNull { m.contains(it, ignoreCase = true) } ?: "other"
                groups.getOrPut(family) { mutableListOf() }.add(m)
            }
            // 先随机选家族，再在家族内随机
            val families = groups.keys.toList()
            val chosenFamily = families[urandomInt(families.size)]
            val pool = groups[chosenFamily]!!
            return pool[urandomInt(pool.size)]
        }

        private val okHttpClient: OkHttpClient by lazy {
            val builder = OkHttpClient.Builder()

            if (false) {
                builder.addInterceptor(
                    HttpLoggingInterceptor(RedactingLogger()).apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                    }
                )
            }

            builder.addInterceptor(NetworkLogger())

            builder.addInterceptor(RetryInterceptor(maxRetries = 2, initialDelayMs = 300))

            builder.addInterceptor(RequestSecurityInterceptor(shouldSignRequest = ::shouldSignRequest))

            RequestSecurityInterceptor.enforceTls(builder)

            builder.certificatePinner(CertificatePins.certificatePinner)
            builder
                .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                // [P1 FIX] 超时值归一到 TimeoutBudgets，与 ChatViewModel 层对齐
                .callTimeout(TimeoutBudgets.CHAT_VM_API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .connectTimeout(TimeoutBudgets.HTTP_CONNECT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TimeoutBudgets.HTTP_READ_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TimeoutBudgets.HTTP_WRITE_MS, TimeUnit.MILLISECONDS)
                .pingInterval(TimeoutBudgets.HTTP_PING_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        // All-trusting client for CUSTOM providers with skipCertVerify enabled.
        // Only used when config.skipCertVerify == true && config.provider == ApiProvider.CUSTOM.
        // Skips certificate chain validation and hostname verification entirely.
        // WARNING: This disables MITM protection — only for self-hosted/internal servers.
        private val unpinnedClient: OkHttpClient by lazy {
            try {
                val trustAllCerts = object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(trustAllCerts), java.security.SecureRandom())
                OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts)
                    .hostnameVerifier { _, _ -> true }
                    .connectionPool(okhttp3.ConnectionPool(3, 5, TimeUnit.MINUTES))
                    .connectTimeout(TimeoutBudgets.HTTP_CONNECT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(TimeoutBudgets.HTTP_READ_MS, TimeUnit.MILLISECONDS)
                    .writeTimeout(TimeoutBudgets.HTTP_WRITE_MS, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
            } catch (e: Exception) {
                SecureLog.e("AiService", "Failed to create unpinnedClient, falling back to okHttpClient", e)
                okHttpClient
            }
        }

        // Standard TLS client without certificate pinning — used for auto-fallback
        // when a hardcoded pin expires. Still validates certs against system trust store.
        private val standardTlsClient: OkHttpClient by lazy {
            okHttpClient.newBuilder()
                .certificatePinner(okhttp3.CertificatePinner.DEFAULT)
                .build()
        }

        /**
         * Executes a request with adaptive certificate handling:
         * 1. Try with pinned client (hardcoded pins + dynamic pin if exists)
         * 2. On SSLPeerUnverifiedException (pin expired) → auto-fallback to standard TLS
         * 3. If skipCertVerify is enabled → use unpinnedClient directly
         *
         * This means: when a provider rotates their cert, the app auto-adapts
         * without user intervention, while still maintaining standard TLS security.
         */
        private fun executeAdaptive(
            config: ApiConfig,
            request: okhttp3.Request,
            client: OkHttpClient = getEffectiveClient(config)
        ): okhttp3.Response {
            // If user explicitly enabled skipCertVerify, go straight to unpinned
            if (config.skipCertVerify && config.provider != ApiProvider.PARTNER) {
                return unpinnedClient.newCall(request).execute()
            }

            return try {
                client.newCall(request).execute()
            } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
                // Pin expired (cert rotated but still valid per system trust store)
                SecureLog.w("AiService", "🔐 Pin expired for ${config.provider}, auto-fallback to standard TLS")
                standardTlsClient.newCall(request).execute()
            }
        }

        /**
         * Returns the appropriate OkHttpClient for the given API config.
         * - Any provider with skipCertVerify (except PARTNER) → unpinnedClient (trusts all certs)
         * - PARTNER provider → partnerHttpClient (custom relay, HTTP for now)
         * - All others → okHttpClient (with cert pinning + TLS enforcement)
         */
        private fun getEffectiveClient(config: ApiConfig): OkHttpClient {
            // PARTNER (custom relay relay) — uses dedicated client
            if (config.provider == ApiProvider.PARTNER) {
                return partnerHttpClient
            }
            // Any user-configured provider (CUSTOM, DeepSeek, OpenAI, etc.) can skip cert verification
            if (config.skipCertVerify) {
                return unpinnedClient
            }
            return okHttpClient
        }

        // Dedicated client for custom relay (PARTNER) — avoids creating a new
        // OkHttpClient on every call (which leaks connection pools and dispatcher threads).
        // Uses longer connect timeout since self-hosted servers may be slower to accept.
        private val partnerHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .certificatePinner(CertificatePins.certificatePinner)
                .connectionPool(okhttp3.ConnectionPool(3, 5, TimeUnit.MINUTES))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        // [M9 FIX] 轻量请求专用客户端单例（Judge/Generation 等高频小请求）
        // 原每次 callOpenAiCompatibleLight 都 newBuilder().build()，有 dispatcher/拦截器链开销。
        // readTimeout=20s 短于主客户端，快速失败防 Judge 队列堵塞。
        private val lightHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TimeoutBudgets.HTTP_CONNECT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(TimeoutBudgets.HTTP_WRITE_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        // [M9 FIX] 视觉请求专用客户端单例（图片上传大 body，需更长 writeTimeout）
        private val visionHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TimeoutBudgets.HTTP_CONNECT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TimeoutBudgets.HTTP_READ_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        /**
         * Checks if a response body is HTML (non-JSON) and throws a clear error.
         * Some providers (e.g. Xunfei Spark) return HTML error pages on auth failure
         * with HTTP 200, which would otherwise crash JSON parsing with a cryptic error.
         */
        private fun ensureNotHtml(body: String, response: okhttp3.Response) {
            val trimmed = body.trimStart()
            if (trimmed.startsWith("<!") || trimmed.startsWith("<html", ignoreCase = true)) {
                val hint = when {
                    response.code == 401 || response.code == 403 ->
                        " (请检查API密钥/APIPassword是否正确)"
                    response.code == 404 ->
                        " (请检查API地址和模型名是否正确)"
                    else -> " (HTTP ${response.code}，请检查API配置)"
                }
                throw Exception("服务器返回了网页而非API响应$hint")
            }
        }

        private fun shouldSignRequest(request: okhttp3.Request): Boolean {
            val host = request.url.host.lowercase()
            return host == "api.lianyu.ai" || host.endsWith(".lianyu.ai")
        }

        private var context: Context? = null

        fun initialize(ctx: Context) {
            context = ctx.applicationContext
        }

        private val retrofit: Retrofit by lazy {
            Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl("https://api.openai.com/")
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }

        private val openAiApi: OpenAiApi by lazy { retrofit.create(OpenAiApi::class.java) }
        private val anthropicApi: AnthropicApi by lazy { retrofit.create(AnthropicApi::class.java) }
        private val geminiApi: GeminiApi by lazy { retrofit.create(GeminiApi::class.java) }

        private val keyRoundRobinIndex = AtomicInteger(Random.nextInt(Int.MAX_VALUE))
        private val keyLastUsed = ConcurrentHashMap<String, Long>()
        private val keyCooldownUntil = ConcurrentHashMap<String, Long>()

        // ── Provider registry ──
        private val providers: Map<ApiProvider, AiProvider> = mapOf(
            ApiProvider.OPENAI to OpenAiCompatibleProvider(),
            ApiProvider.DEEPSEEK to OpenAiCompatibleProvider(),
            ApiProvider.DASHSCOPE to OpenAiCompatibleProvider(),
            ApiProvider.KIMI to OpenAiCompatibleProvider(),
            ApiProvider.GEMINI to OpenAiCompatibleProvider(),
            ApiProvider.XIAOMI to OpenAiCompatibleProvider(),
            ApiProvider.ZHIPU to OpenAiCompatibleProvider(),
            ApiProvider.SILICONFLOW to OpenAiCompatibleProvider(),
            ApiProvider.OPENROUTER to OpenAiCompatibleProvider(),
            ApiProvider.GROQ to OpenAiCompatibleProvider(),
            ApiProvider.CUSTOM to OpenAiCompatibleProvider(),
            ApiProvider.PARTNER to OpenAiCompatibleProvider(),
            ApiProvider.IFLYTEK to OpenAiCompatibleProvider(),
            ApiProvider.ANTHROPIC to ClaudeProvider(),
        )

        private fun providerFor(config: ApiConfig): AiProvider {
            // CUSTOM provider respects formatHint: choose Claude format for "anthropic" hint
            if (config.provider == ApiProvider.CUSTOM && config.formatHint == "anthropic") {
                return ClaudeProvider()
            }
            return providers[config.provider] ?: OpenAiCompatibleProvider()
        }

        init {
            AiProvider.okHttpClient = okHttpClient
            AiProvider.keySelector = ::selectApiKey
            AiProvider.keyFailureHandler = ::markKeyFailed
        }

        const val KEY_MIN_INTERVAL_MS = 800L
        const val KEY_FAILURE_COOLDOWN_MS = 5000L

        fun selectApiKey(config: ApiConfig): Pair<Int, List<String>> {
            val allKeys = config.getAllApiKeys()
            if (allKeys.size <= 1) return 0 to allKeys
            val now = System.currentTimeMillis()
            var attempts = 0
            while (attempts < allKeys.size * 2) {
                val idx = keyRoundRobinIndex.getAndIncrement() % allKeys.size
                val key = allKeys[idx]
                val cooldownUntil = keyCooldownUntil[key] ?: 0L
                if (now >= cooldownUntil && (keyLastUsed[key] ?: 0L) + KEY_MIN_INTERVAL_MS <= now) {
                    keyLastUsed[key] = now
                    SecureLog.d("AiService", "Key轮询: 使用 #${idx + 1}/${allKeys.size}")
                    return idx to allKeys
                }
                attempts++
            }
            val fallbackIdx = keyRoundRobinIndex.getAndIncrement() % allKeys.size
            keyLastUsed[allKeys[fallbackIdx]] = now
            return fallbackIdx to allKeys
        }

        fun markKeyFailed(key: String) {
            keyCooldownUntil[key] = System.currentTimeMillis() + KEY_FAILURE_COOLDOWN_MS
            SecureLog.w("AiService", "Key失败冷却5s: ${key.take(8)}...")
        }

        fun resetKeyState() {
            keyCooldownUntil.clear()
            keyLastUsed.clear()
        }
    }

    /**
     * 发送消息（非流式，兼容旧接口）
     */
    suspend fun sendMessage(companion: CompanionModel?, history: List<ChatMessage>, stickerProbability: Int = 30, ntpTimeEnabled: Boolean = false): AiResponse {
        if (companion == null) return AiResponse("抱歉，找不到角色信息。")

        return SecureLog.timed("AiService", "sendMessage") {
            withContext(Dispatchers.IO) {
                val config = resolveConfig()
                    ?: return@withContext AiResponse("请先配置并启用可用的API。在「我」->「API设置」中添加密钥并测试连接。")

                if (config.model.isBlank()) {
                    return@withContext AiResponse("模型名未配置，请在「API设置」中重新测试连接以自动选择模型。")
                }

                val sortedHistory = history.sortedBy { it.timestamp }
                // C4: Differential privacy — sanitize PII from messages sent to 3rd-party APIs.
                // custom relay (self-hosted) skips sanitization; user data stays on your server.
                val sanitizedHistory = if (config.provider == ApiProvider.PARTNER) {
                    sortedHistory
                } else {
                    sortedHistory.map { msg ->
                        if (msg.isFromUser) msg.copy(content = com.lianyu.ai.common.safety.DifferentialPrivacyFilter.sanitize(msg.content))
                        else msg
                    }
                }
                val lastUserMessage = sanitizedHistory.lastOrNull { it.isFromUser }?.content ?: ""
                val contextLimit = appSettingsStore.getContextLimit()
                val innerThoughtEnabled = appSettingsStore.getInnerThoughtEnabled()
                val compressionMode = appSettingsStore.getContextCompressionMode()
                val keepRatio = appSettingsStore.getCompressionKeepRatio()
                val minKeep = appSettingsStore.getCompressionMinKeep()
                val memoryContext = memoryProvider.getMemoryContext(companion.id, null, lastUserMessage, contextLimit)
                val stickerManager = StickerManager.getInstance(appContext)
                val availableStickers = stickerManager.getAllStickers().mapNotNull { sticker ->
                    val displayName = sticker.description?.takeIf {
                        it.isNotBlank() && !it.startsWith("sticker_") && it.length <= 20
                    } ?: sticker.name.removePrefix("sticker_").removeSuffix(".png").takeIf { it.isNotBlank() && it.length <= 20 }
                    if (displayName.isNullOrBlank() || displayName.length > 20) null else displayName
                }.distinct()
                val role = userRepository.selectedRole.value
                val baseSystemPrompt = AiPromptBuilder.buildSystemPrompt(companion, memoryContext, lastUserMessage, availableStickers, stickerProbability, innerThoughtEnabled, ntpTimeEnabled = false, role = role)
                val systemPrompt = appendYanderePromptIfNeeded(baseSystemPrompt, companion)
                val messages = buildMessages(sanitizedHistory, systemPrompt, lastUserMessage, contextLimit, compressionMode = compressionMode, memoryContext = memoryContext, keepRatio = keepRatio, minKeep = minKeep)

                SecureLog.api("SEND", "provider=${config.provider}, model=${config.model}, messages=${messages.size}, contextLimit=$contextLimit, stickerProb=$stickerProbability, stickers=${availableStickers.size}")

                try {
                    val (rawResponse, reasoning) = when (config.provider) {
                        ApiProvider.OPENAI, ApiProvider.DEEPSEEK, ApiProvider.DASHSCOPE, ApiProvider.KIMI, ApiProvider.GEMINI, ApiProvider.XIAOMI, ApiProvider.ZHIPU, ApiProvider.SILICONFLOW, ApiProvider.OPENROUTER, ApiProvider.GROQ, ApiProvider.CUSTOM, ApiProvider.IFLYTEK, ApiProvider.PARTNER -> {
                            callOpenAiCompatibleWithReasoning(config, messages)
                        }
                        ApiProvider.ANTHROPIC -> {
                            val resp = callAnthropic(config, messages, systemPrompt)
                            Pair(resp, null)
                        }
                    }
                    if (rawResponse.isBlank()) {
                        throw Exception("API返回空内容，请检查模型名是否正确")
                    }
                    
                    recordTokenUsage(companion.id, messages.size, rawResponse.length)

                    val cleaned = AiPromptBuilder.applyPersonaPostProcessing(rawResponse, sortedHistory)
                    SecureLog.api("SEND", "Response length=${cleaned.length}")

                    val safetyResult = ContentFilter.checkOutputSafety(cleaned)
                    if (!safetyResult.isSafe) {
                        SecureLog.w("AiService", "Output safety violation: ${safetyResult.level} - ${safetyResult.reason}")
                        // [FIX] AI 输出不应记录用户封禁——模型生成内容不是用户责任
                        return@withContext AiResponse("抱歉，我无法继续这个话题。")
                    }

                    AiResponse(cleaned, reasoning)
                } catch (e: Exception) {
                    SecureLog.e("AiService", "sendMessage failed", e)
                    throw Exception(formatApiException(e))
                }
            }
        }
    }

    suspend fun generateProactiveMessage(companion: CompanionModel, recentMessages: List<ChatMessage>, settings: ProactiveMessageSettings? = null): String? {
        return withContext(Dispatchers.IO) {
            val config = resolveConfig()
            if (config == null) {
                SecureLog.w("AiService", "No active API config, skipping proactive message")
                return@withContext null
            }
            if (config.model.isBlank()) {
                SecureLog.w("AiService", "Model not configured, skipping proactive message")
                return@withContext null
            }

            val sortedMessages = recentMessages.sortedBy { it.timestamp }
            val lastUserMessage = sortedMessages.lastOrNull { it.isFromUser }?.content ?: ""
            val contextLimit = appSettingsStore.getContextLimit()
            val memoryContext = memoryProvider.getMemoryContext(companion.id, null, lastUserMessage, contextLimit)

            val systemPrompt = buildProactiveSystemPrompt(companion, memoryContext, settings)
            val contextMessages = AiPromptBuilder.buildProactiveContext(sortedMessages, companion)

            val messages = listOf(
                Message("system", systemPrompt),
                Message("user", contextMessages),
                Message("user", "以${companion.name}的身份，继续刚才的对话。要求：\n1. 15-50字，像真人聊天一样自然\n2. 直接接上一条话茬，不要重新开场、不要回忆之前说过的话\n3. 如果用户最后一条是问题，直接回答它\n4. 带语气词（呀/呢/啦/嘛/哼/嘿嘿/诶/哇/呜呜/嘤）\n5. 可以撒娇/嘴硬/分享小事/突然温柔/反问\n6. 禁止括号，禁止AI感词汇，禁止说教\n7. 必须结合当前时间和场景（上面已提供），让内容贴合现在这个时间段该做的事和情绪")
            )

            try {
                val rawResponse = when (config.provider) {
                    ApiProvider.OPENAI, ApiProvider.DEEPSEEK, ApiProvider.DASHSCOPE, ApiProvider.KIMI, ApiProvider.GEMINI, ApiProvider.XIAOMI, ApiProvider.ZHIPU, ApiProvider.SILICONFLOW, ApiProvider.OPENROUTER, ApiProvider.GROQ, ApiProvider.CUSTOM, ApiProvider.IFLYTEK, ApiProvider.PARTNER -> {
                        callOpenAiCompatible(config, messages)
                    }
                    ApiProvider.ANTHROPIC -> {
                        callAnthropic(config, messages, systemPrompt)
                    }
                }
                val cleaned = AiPromptBuilder.applyPersonaPostProcessing(rawResponse, sortedMessages)
                val singleLine = cleaned
                    .replace(Regex("\\r\\n|\\r|\\n+"), "，")
                    .replace(Regex("，{2,}"), "，")
                    .trimStart('，', ',', '.', '。', ' ')
                    .trim()

                val safetyResult = ContentFilter.checkOutputSafety(singleLine)
                if (!safetyResult.isSafe) {
                    SecureLog.w("AiService", "Proactive output safety violation: ${safetyResult.level} - ${safetyResult.reason}")
                    // [C4 FIX] AI 生成内容不应累加用户封禁
                    return@withContext null
                }

                singleLine
            } catch (e: Exception) {
                SecureLog.w("AiService", "Proactive message failed: ${e.message}")
                null
            }
        }
    }

    suspend fun sendMessageWithCustomSystem(
        companion: CompanionModel?,
        history: List<ChatMessage>,
        customSystemPrompt: String,
        stickerProbability: Int = 30,
        companionNameMap: Map<Long, String> = emptyMap()
    ): String {
        if (companion == null) return "抱歉，找不到角色信息。"

        return SecureLog.timed("AiService", "sendMessageWithCustomSystem") {
            withContext(Dispatchers.IO) {
                val config = resolveConfig()
                    ?: return@withContext "请先配置并启用可用的API。在「我」->「API设置」中添加密钥并测试连接。"

                if (config.model.isBlank()) {
                    return@withContext "模型名未配置，请在「API设置」中重新测试连接以自动选择模型。"
                }

                val sortedHistory = history.sortedBy { it.timestamp }
                val lastUserMessage = sortedHistory.lastOrNull { it.isFromUser }?.content ?: ""
                val contextLimit = appSettingsStore.getContextLimit()
                val compressionMode = appSettingsStore.getContextCompressionMode()
                val keepRatio = appSettingsStore.getCompressionKeepRatio()
                val minKeep = appSettingsStore.getCompressionMinKeep()
                val memCtx = if (companion != null) memoryProvider.getMemoryContext(companion.id, null, lastUserMessage, contextLimit) else ""
                val messages = buildMessages(sortedHistory, customSystemPrompt, lastUserMessage, contextLimit, companionNameMap, compressionMode, memoryContext = memCtx, keepRatio = keepRatio, minKeep = minKeep)

                SecureLog.api("SEND-CUSTOM", "provider=${config.provider}, model=${config.model}, messages=${messages.size}")

                try {
                    val rawResponse = when (config.provider) {
                        ApiProvider.OPENAI, ApiProvider.DEEPSEEK, ApiProvider.DASHSCOPE, ApiProvider.KIMI, ApiProvider.GEMINI, ApiProvider.XIAOMI, ApiProvider.ZHIPU, ApiProvider.SILICONFLOW, ApiProvider.OPENROUTER, ApiProvider.GROQ, ApiProvider.CUSTOM, ApiProvider.IFLYTEK, ApiProvider.PARTNER -> {
                            callOpenAiCompatible(config, messages)
                        }
                        ApiProvider.ANTHROPIC -> {
                            callAnthropic(config, messages, customSystemPrompt)
                        }
                    }
                    if (rawResponse.isBlank()) throw Exception("API返回空内容")
                    val cleaned = AiPromptBuilder.applyPersonaPostProcessing(rawResponse, sortedHistory)

                    // 输出安全检查（与 sendMessage 保持一致）
                    val safetyResult = ContentFilter.checkOutputSafety(cleaned)
                    if (!safetyResult.isSafe) {
                        SecureLog.w("AiService", "sendMessageWithCustomSystem output blocked: ${safetyResult.reason}")
                        // [C4 FIX] AI 生成内容不应累加用户封禁
                        return@withContext "抱歉，我无法继续这个话题。"
                    }

                    cleaned
                } catch (e: Exception) {
                    SecureLog.e("AiService", "sendMessageWithCustomSystem failed", e)
                    throw Exception(formatApiException(e))
                }
            }
        }
    }

    private suspend fun callOpenAiCompatibleForJudge(judgePrompt: String): String {
        val config = resolveConfig()
            ?: return """{"shouldMention":false,"target":"NONE","confidence":0.0}"""

        val messages = listOf(
            Message("system", "你是@提及判断器。只返回JSON格式结果。"),
            Message("user", judgePrompt)
        )

        return try {
            callOpenAiCompatibleLight(config, messages, temperature = 0.1, maxTokens = 100)
        } catch (e: Exception) {
            SecureLog.w("AiService", "Judge call failed after all keys: ${e.message}")
            """{"shouldMention":false,"target":"NONE","confidence":0.0}"""
        }
    }

    private suspend fun callOpenAiCompatibleForGeneration(generationPrompt: String): String {
        val config = resolveConfig()
            ?: return "请先配置并启用可用的API。"

        val messages = listOf(
            Message("system", "你是专业的人设/角色设定生成器。"),
            Message("user", generationPrompt)
        )

        return try {
            callOpenAiCompatibleLight(config, messages, temperature = 0.7, maxTokens = 2000)
        } catch (e: Exception) {
            SecureLog.w("AiService", "Generation call failed after all keys: ${e.message}")
            ""
        }
    }

    private suspend fun callOpenAiCompatibleLight(
        config: ApiConfig,
        messages: List<Message>,
        temperature: Double,
        maxTokens: Int
    ): String {
        val baseUrl = normalizeOpenAiBaseUrl(config.baseUrl)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val allKeys = resolveKeysWithPartnerFallback(config).second
        var lastException: Exception? = null

        // [M9 FIX] 复用轻量客户端单例：原每次调用 getEffectiveClient(config).newBuilder().build()
        // 创建新 OkHttpClient，虽共享连接池但新建 dispatcher + 拦截器链。Judge/Generation 调用频繁有开销。
        val lightClient = lightHttpClient

        for (keyIndex in allKeys.indices) {
            val currentKey = allKeys[keyIndex]
            try {
                val jsonArray = org.json.JSONArray()
                for (msg in messages) {
                    val msgObj = org.json.JSONObject()
                    msgObj.put("role", msg.role)
                    msgObj.put("content", msg.content)
                    jsonArray.put(msgObj)
                }
                val jsonBody = org.json.JSONObject()
                jsonBody.put("model", config.model)
                jsonBody.put("messages", jsonArray)
                if (!requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", temperature)
                }
                // 根据API提供商选择正确的参数名称
                val maxTokensParam = if (usesMaxCompletionTokens(config.provider)) {
                    "max_completion_tokens"
                } else {
                    "max_tokens"
                }
                jsonBody.put(maxTokensParam, maxTokens)

                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                // 根据API提供商选择最优的认证方式
                if (prefersApiKeyHeader(config.provider)) {
                    requestBuilder.addHeader("api-key", currentKey)
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer $currentKey")
                }
                val request = requestBuilder
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = executeAdaptive(config, request, lightClient)
                val body = response.body?.string() ?: throw Exception("Empty response")
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                ensureNotHtml(body, response)
                val parsed = json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) throw Exception(parsed.error.message ?: "API error")

                SecureLog.api("LIGHT", "Key ${keyIndex + 1}/${allKeys.size} success!")
                return parsed.choices?.firstOrNull()?.message?.content ?: ""
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                SecureLog.w("AiService", "Light call Key ${keyIndex + 1}/${allKeys.size} timeout: ${e.message}")
                if (keyIndex < allKeys.size - 1) continue else throw lastException
            } catch (e: Exception) {
                lastException = e
                SecureLog.w("AiService", "Light call Key ${keyIndex + 1}/${allKeys.size} failed: ${e.message}")
                if (keyIndex < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }

    data class BalanceInfo(
        val totalLimit: Double?,
        val totalUsed: Double?,
        val totalAvailable: Double?,
        val remainingBalance: Double?,
        val rawSubscription: String?,
        val rawUsage: String?
    )

    suspend fun getActiveConfig(): ApiConfig? {
        return resolveConfig()
    }

    suspend fun queryBalanceWithConfig(config: ApiConfig): Result<BalanceInfo> = withContext(Dispatchers.IO) {
        try {
            queryBalanceInternal(config)
        } catch (e: Exception) {
            SecureLog.e("AiService", "queryBalanceWithConfig failed", e)
            Result.failure(e)
        }
    }

    suspend fun queryBalance(configId: Long? = null): Result<BalanceInfo> = withContext(Dispatchers.IO) {
        try {
            val config = if (configId != null) {
                apiConfigRepository.getConfigById(configId)
            } else {
                resolveConfig()
            } ?: return@withContext Result.failure(Exception("未找到API配置"))
            queryBalanceInternal(config)
        } catch (e: Exception) {
            SecureLog.e("AiService", "queryBalance failed", e)
            Result.failure(e)
        }
    }

    private suspend fun queryBalanceInternal(config: ApiConfig): Result<BalanceInfo> {
        var keysToTry = config.getUserApiKeys().takeIf { it.isNotEmpty() }
            ?: config.getAllApiKeys()
        
        // PARTNER 模式下，读取用户配置密钥（强制刷新）
        if (keysToTry.isEmpty() && config.provider == ApiProvider.PARTNER) {
            SecureLog.d("AiService", "PARTNER queryBalance: fetching keys from remote server...")
            val remoteKeys = com.lianyu.ai.common.RemoteKeyProvider.fetchKeysAsync(appContext, forceRefresh = true)
            if (remoteKeys.isNotEmpty()) {
                keysToTry = remoteKeys
                SecureLog.d("AiService", "Using ${remoteKeys.size} remote keys for balance query")
            } else {
                return Result.failure(Exception("无法从服务器获取密钥"))
            }
        }
        
        if (keysToTry.isEmpty()) return Result.failure(Exception("API Key 未配置"))

        val baseUrl = normalizeOpenAiBaseUrl(config.baseUrl).trimEnd('/')
        // Use appropriate client: unpinned when skipCertVerify is enabled (any provider), otherwise pinned
        val balanceClient = if (config.skipCertVerify) {
            unpinnedClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        } else {
            OkHttpClient.Builder()
                .certificatePinner(CertificatePins.certificatePinner)
                // [P0 FIX] 余额查询超时对齐
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        var totalLimit: Double? = null
        var totalUsed: Double? = null
        var totalAvailable: Double? = null
        var rawSub: String? = null
        var rawUsage: String? = null
        var lastError: Exception? = null

        for (key in keysToTry) {
            try {
                val subEndpoints = listOf("/dashboard/billing/subscription", "/v1/dashboard/billing/subscription")
                for (subEndpoint in subEndpoints) {
                    try {
                        val subReq = okhttp3.Request.Builder()
                            .url("$baseUrl$subEndpoint")
                            .addHeader("Authorization", "Bearer $key")
                            .get().build()
                        val subRes = executeAdaptive(config, subReq, balanceClient)
                        val subBody = subRes.body?.string() ?: ""
                        rawSub = subBody
                        if (subRes.isSuccessful && subBody.isNotBlank()) {
                            val subObj = runCatching { org.json.JSONObject(subBody) }.getOrNull()
                            if (subObj != null) {
                                totalLimit = subObj.optDouble("hard_limit_usd", subObj.optDouble("total_granted", totalLimit ?: 0.0))
                                    .takeIf { it > 0 }
                                totalUsed = subObj.optDouble("total_used", 0.0)
                                totalAvailable = subObj.optDouble("total_available", 0.0).takeIf { it > 0 }
                            }
                            break
                        }
                    } catch (_: Exception) {}
                }

                val usageEndpoints = listOf("/dashboard/billing/usage", "/v1/dashboard/billing/usage")
                for (usageEndpoint in usageEndpoints) {
                    try {
                        val usageReq = okhttp3.Request.Builder()
                            .url("$baseUrl$usageEndpoint")
                            .addHeader("Authorization", "Bearer $key")
                            .get().build()
                        val usageRes = executeAdaptive(config, usageReq, balanceClient)
                        val usageBody = usageRes.body?.string() ?: ""
                        rawUsage = usageBody
                        if (usageRes.isSuccessful && usageBody.isNotBlank()) {
                            val usageObj = runCatching { org.json.JSONObject(usageBody) }.getOrNull()
                            if (usageObj != null) {
                                val usageTotal = usageObj.optDouble("total_usage", -1.0)
                                if (usageTotal >= 0) totalUsed = usageTotal / 100.0
                            }
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (totalLimit != null || totalAvailable != null) {
                    SecureLog.api("BALANCE", "Query success with user key")
                    break
                }
            } catch (e: Exception) {
                lastError = e
                continue
            }
        }

        val remaining = when {
            totalAvailable != null -> totalAvailable
            totalLimit != null && totalUsed != null -> totalLimit - totalUsed
            else -> null
        }

        SecureLog.api("BALANCE", "Query result: limit=$totalLimit used=$totalUsed available=$totalAvailable remaining=$remaining")
        return Result.success(BalanceInfo(totalLimit, totalUsed, totalAvailable, remaining, rawSub, rawUsage))
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String, provider: ApiProvider? = null, skipCertVerify: Boolean = false): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedBaseUrl = normalizeOpenAiBaseUrl(baseUrl)
                val url = normalizedBaseUrl.trimEnd('/') + "/models"
                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                if (provider != null && prefersApiKeyHeader(provider)) {
                    requestBuilder.addHeader("api-key", apiKey)
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
                val request = requestBuilder.get().build()

                SecureLog.api("MODELS", "Fetching models from ${url.take(60)}...")

                val fetchClient = when {
                    provider == ApiProvider.PARTNER -> partnerHttpClient  // custom relay
                    skipCertVerify -> unpinnedClient  // any user-configured provider can skip
                    else -> okHttpClient
                }

                val response = runCatching {
                // 🔒 FIX: Use shared thread pool instead of per-call newSingleThreadExecutor
                //    Prevents native thread leak from repeated fetchModels() calls
                val future = fetchModelsExecutor.submit<okhttp3.Response> {
                    try {
                        fetchClient.newCall(request).execute()
                    } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
                        // Pin expired — auto-fallback to standard TLS
                        SecureLog.w("AiService", "🔐 Pin expired for models fetch, auto-fallback to standard TLS")
                        standardTlsClient.newCall(request).execute()
                    }
                }
                future.get(25, java.util.concurrent.TimeUnit.SECONDS)
            }.getOrElse { e ->
                throw if (e is java.util.concurrent.TimeoutException)
                    java.net.SocketTimeoutException("Request timeout after 25s (DNS/proxy may be unreachable)")
                else if (e is java.util.concurrent.ExecutionException) e.cause ?: e
                else e
            }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))

                if (!response.isSuccessful) {
                    SecureLog.api("MODELS", "HTTP ${response.code}")
                    return@withContext Result.failure(Exception("HTTP " + response.code))
                }

                // Some providers (e.g. Xunfei) don't support /models and return HTML
                if (body.trimStart().startsWith("<!") || body.trimStart().startsWith("<html", ignoreCase = true)) {
                    return@withContext Result.failure(Exception("该API不支持模型列表查询"))
                }

                val modelsResponse = json.decodeFromString<ModelsListResponse>(body)
                if (modelsResponse.error != null) {
                    SecureLog.api("MODELS", "API error: ${modelsResponse.error.message}")
                    return@withContext Result.failure(Exception(modelsResponse.error.message ?: "Unknown error"))
                }

                val models = modelsResponse.data?.mapNotNull { it.id } ?: emptyList()
                SecureLog.api("MODELS", "Found ${models.size} models")
                Result.success(models)
            } catch (e: Exception) {
                SecureLog.e("AiService", "fetchModels failed", e)
                Result.failure(e)
            }
        }
    }

    private fun normalizeOpenAiBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (
            trimmed.endsWith("/v1") ||
            trimmed.endsWith("/v1beta") ||
            trimmed.endsWith("/v4") ||
            trimmed.endsWith("/openai") ||
            trimmed.endsWith("/compatible-mode/v1")
        ) {
            trimmed
        } else {
            "$trimmed/v1"
        }
    }

    /**
     * 判断API提供商是否需要使用 max_completion_tokens 参数（而非 max_tokens）
     * 小米MiMo等新版本API使用此参数名
     */
    private fun usesMaxCompletionTokens(provider: ApiProvider): Boolean {
        return provider == ApiProvider.XIAOMI
    }

    /**
     * 判断API提供商是否优先使用 api-key 认证头（而非 Authorization: Bearer）
     */
    private fun prefersApiKeyHeader(provider: ApiProvider): Boolean {
        return provider == ApiProvider.XIAOMI
    }

    /**
     * 判断是否需要发送主动消息。不需要时直接返回 false，节省 API 调用。
     */
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

    /**
     * 后处理：严格执行人设规则
     * 1. 截断过长回复
     * 2. 检测最近5轮内的重复词
     */
    private fun extractDirectReply(text: String): String {
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

    private fun formatApiException(error: Throwable): String {
        val isTimeout = error is java.net.SocketTimeoutException ||
                error.message?.contains("timeout", ignoreCase = true) == true ||
                error.message?.contains("timed out", ignoreCase = true) == true

        if (isTimeout) {
            return "[TOAST]网络连接超时，请检查网络后重试"
        }

        val message = when (error) {
            is HttpException -> {
                val errorBody = error.response()?.errorBody()?.string()
                val parsedMessage = errorBody?.let { body ->
                    runCatching { json.decodeFromString<ChatCompletionResponse>(body).error?.message }.getOrNull()
                        ?: runCatching { json.decodeFromString<AnthropicResponse>(body).error?.message }.getOrNull()
                        ?: runCatching { json.decodeFromString<GeminiResponse>(body).error?.message }.getOrNull()
                }
                parsedMessage ?: "HTTP ${error.code()} ${error.message()}"
            }
            else -> error.message
        }?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

        return "API调用失败：$message"
    }

    fun buildSystemPromptForLocal(companion: CompanionModel, memoryContext: String = "", lastUserMessage: String = "", availableStickers: List<String> = emptyList(), stickerProbability: Int = 30, innerThoughtEnabled: Boolean = false, ntpTimeEnabled: Boolean = false, role: CompanionRole = CompanionRole.GIRLFRIEND): String =
        AiPromptBuilder.buildSystemPromptForLocal(companion, memoryContext, lastUserMessage, availableStickers, stickerProbability, innerThoughtEnabled, ntpTimeEnabled, role)

    // [P2-1] buildProactiveSystemPrompt 副本签名与 AiPromptBuilder 不同（含 settings: ProactiveMessageSettings?），
    // AiPromptBuilder 版用 role 参数。保留此副本避免行为漂移，仅内部调用委托到 AiContextTools/AiPromptBuilder。
    private fun buildProactiveSystemPrompt(companion: CompanionModel, memoryContext: String = "", settings: ProactiveMessageSettings? = null): String {
        val role = CompanionRole.GIRLFRIEND  // 主动消息固定 GIRLFRIEND 角色（原副本行为）
        // 根据自定义设置注入话题策略（AiPromptBuilder 版无此逻辑，此处保留差异）
        val topicRule = when {
            settings == null -> ""
            !settings.allowNewTopic -> "\n=== 话题策略（重要）===\n你必须承接上一条话题继续聊，禁止主动开启全新话题。如果不知道说什么，就围绕用户最近提到的内容延伸或追问。\n"
            else -> ""
        }
        val followUpHint = if (settings != null && !settings.allowFollowUpMessage) {
            "\n注意：本次不要追加追问句，说完核心内容即可。\n"
        } else ""
        // 委托 AiPromptBuilder 构建主体，再插入 topicRule/followUpHint
        val base = AiPromptBuilder.buildProactiveSystemPrompt(companion, memoryContext, role = role)
        // AiPromptBuilder 版已含 persona+memory+timeContext+personaRules，此处需在 memory 后插入策略
        // 简化：若 topicRule/followUpHint 非空，追加到末尾（语义等价，不影响主流程）
        return if (topicRule.isNotBlank() || followUpHint.isNotBlank()) {
            base + "\n" + topicRule + followUpHint
        } else base
    }

    // [P2-1] CompressedContext 委托 AiContextTools.CompressedContext
    private data class CompressedContext(
        val summary: String,
        val keptMessages: List<ChatMessage>,
        val compressedCount: Int
    ) {
        companion object {
            fun from(other: AiContextTools.CompressedContext): CompressedContext =
                CompressedContext(other.summary, other.keptMessages, other.compressedCount)
        }
    }

    private suspend fun compressContextWithAi(
        messages: List<ChatMessage>,
        companionName: String,
        memoryContext: String = ""
    ): String {
        if (messages.isEmpty()) return ""

        val chatText = messages.joinToString("\n") { msg ->
            val role = if (msg.isFromUser) "用户" else companionName
            "$role: ${msg.content}"
        }

        val memoryHint = if (memoryContext.isNotBlank()) {
            "\n\n=== 已有的长期记忆（以下内容不需要重复提取，只需关注未记录的新信息） ===\n$memoryContext"
        } else ""

        val summaryPrompt = """请将以下对话历史压缩成一段简洁的摘要（150字以内）。
要求：
1. 提取关键话题、情感变化、用户提到的个人信息/偏好/约定
2. 省略闲聊和重复内容
3. 用自然语言描述，不要用列表格式
4. 如果已有记忆中包含的信息，简要带过即可，重点突出新信息$memoryHint

对话历史：
$chatText

摘要："""

        try {
            val config = resolveConfig() ?: return AiContextTools.buildLocalSummary(messages, memoryContext = memoryContext)
            val apiMessages = listOf(
                Message("system", "你是一个对话摘要助手，擅长提取关键信息并压缩文本。"),
                Message("user", summaryPrompt)
            )

            val rawResponse = callOpenAiCompatible(config, apiMessages)
            var cleaned = rawResponse.trim()
                .replace(Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>"), "")
            cleaned = cleaned.lines().firstOrNull { it.isNotBlank() } ?: cleaned

            return "=== AI压缩摘要（已压缩${messages.size}条消息，结合${if (memoryContext.isNotBlank()) "已有记忆" else "无记忆"}） ===\n$cleaned"
        } catch (e: Exception) {
            SecureLog.w("AiService", "AI compression failed, falling back to local: ${e.message}")
            return AiContextTools.buildLocalSummary(messages, memoryContext = memoryContext)
        }
    }

    private suspend fun buildMessages(
        history: List<ChatMessage>,
        systemPrompt: String,
        lastUserMessage: String = "",
        contextLimit: Int = 12,
        companionNameMap: Map<Long, String> = emptyMap(),
        compressionMode: String = AppSettingsStore.CompressionMode.OFF,
        memoryContext: String = "",
        keepRatio: Float = 0.5f,
        minKeep: Int = 6
    ): List<Message> {
        val messages = mutableListOf<Message>()
        messages.add(Message("system", systemPrompt))

        val compressed = when (compressionMode) {
            AppSettingsStore.CompressionMode.LOCAL -> CompressedContext.from(AiContextTools.compressContext(history, contextLimit, companionNameMap, memoryContext, keepRatio, minKeep))
            AppSettingsStore.CompressionMode.AI -> run {
                if (history.size <= contextLimit) CompressedContext("", history, 0)
                else {
                    val keepRecent = maxOf(minKeep, (contextLimit * keepRatio).toInt().coerceAtLeast(minKeep))
                    val oldMessages = history.dropLast(keepRecent)
                    val recentMessages = history.takeLast(keepRecent)
                    val summary = compressContextWithAi(oldMessages, companionNameMap.values.firstOrNull() ?: "AI", memoryContext)
                    CompressedContext(summary, recentMessages, oldMessages.size)
                }
            }
            else -> CompressedContext("", history.takeLast(contextLimit), 0)
        }

        if (compressed.summary.isNotBlank()) {
            messages.add(Message("system", compressed.summary))
            SecureLog.api("CONTEXT", "Compressed ${compressed.compressedCount} old messages into summary (${compressed.summary.length} chars)")
        }

        val recentHistory = compressed.keptMessages

        // [FIX] 先过滤掉空的 assistant 消息，避免 API 报错 "assistant message must not be empty"
        val filteredHistory = recentHistory.filterNot { msg ->
            !msg.isFromUser && msg.content.replace("\u200B", "").isBlank()
        }

        val lastMsg = filteredHistory.lastOrNull()
        val isLastFromUser = lastMsg?.isFromUser == true

        filteredHistory.forEach { msg ->
            val content = if (msg.isFromUser && msg.content.startsWith("[") && msg.content.endsWith("]")) {
                val inner = msg.content.removeSurrounding("[", "]")
                val label = when {
                    inner.startsWith("sticker_", ignoreCase = true) -> "表情包"
                    inner.length > 20 -> "表情包"
                    else -> inner
                }
                "用户发送了一个表情包：[$label]"
            } else {
                msg.content
            }
            messages.add(Message(
                role = if (msg.isFromUser) "user" else "assistant",
                content = content
            ))
        }

        if (!isLastFromUser && lastUserMessage.isNotBlank()) {
            messages.add(Message(
                role = "user",
                content = lastUserMessage
            ))
        }

        return messages
    }

    suspend fun callOpenAiCompatibleForTest(config: ApiConfig, messages: List<Message>): String {
        val baseUrl = normalizeOpenAiBaseUrl(config.baseUrl)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val (startIdx, allKeys) = resolveKeysWithPartnerFallback(config)
        var lastException: Exception? = null

        val jsonArray = org.json.JSONArray()
        for (msg in messages) {
            val msgObj = org.json.JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            jsonArray.put(msgObj)
        }

        for (i in 0 until allKeys.size) {
            val keyIndex = (startIdx + i) % allKeys.size
            val currentKey = allKeys[keyIndex]
            try {
                val jsonBody = org.json.JSONObject()
                jsonBody.put("model", config.model)
                jsonBody.put("messages", jsonArray)
                if (!requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", 0.7)
                }
                val maxTokensParam = if (usesMaxCompletionTokens(config.provider)) {
                    "max_completion_tokens"
                } else {
                    "max_tokens"
                }
                jsonBody.put(maxTokensParam, 100)

                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                if (prefersApiKeyHeader(config.provider)) {
                    requestBuilder.addHeader("api-key", currentKey)
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer $currentKey")
                }
                val request = requestBuilder
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = executeAdaptive(config, request)
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val errorMsg = if (body.trimStart().startsWith("{")) {
                        runCatching { json.decodeFromString<ChatCompletionResponse>(body).error?.message }.getOrNull()
                    } else null
                    throw Exception(errorMsg ?: "HTTP ${response.code}: 服务器返回错误页面")
                }

                ensureNotHtml(body, response)
                val parsed = json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) {
                    throw Exception(parsed.error.message ?: "API返回错误")
                }

                var content = parsed.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("API返回空内容")
                content = stripThinkingContent(content)
                return content
            } catch (e: Exception) {
                lastException = e
                markKeyFailed(currentKey)
                SecureLog.w("AiService", "Test Key #${keyIndex + 1}/${allKeys.size} 失败: ${e.message}")
                if (i < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }

    private suspend fun callOpenAiCompatible(config: ApiConfig, messages: List<Message>): String {
        return callOpenAiCompatibleWithReasoning(config, messages).first
    }

    /**
     * 为 PARTNER 提供商获取远程密钥的回退逻辑。
     * PARTNER 的 apiKey 存储在用户配置上，本地 apiKey 字段为空。
     * 如果本地没有可用密钥，则从 RemoteKeyProvider 动态获取。
     */
    private suspend fun resolveKeysWithPartnerFallback(config: ApiConfig): Pair<Int, List<String>> {
        val (startIdx, keys) = selectApiKey(config)
        if (keys.isEmpty() && config.provider == ApiProvider.PARTNER) {
            SecureLog.d("AiService", "PARTNER keys empty, fetching from RemoteKeyProvider...")
            val remoteKeys = com.lianyu.ai.common.RemoteKeyProvider.fetchKeysAsync(appContext, forceRefresh = false)
            if (remoteKeys.isNotEmpty()) {
                SecureLog.d("AiService", "Fetched ${remoteKeys.size} remote keys for PARTNER send path")
                return 0 to remoteKeys
            }
            SecureLog.w("AiService", "RemoteKeyProvider returned no keys for PARTNER")
        }
        return startIdx to keys
    }

    private suspend fun callOpenAiCompatibleWithReasoning(config: ApiConfig, messages: List<Message>): Pair<String, String?> {
        // 委托到带工具参数的重载，不传工具（保持向后兼容）
        val result = callOpenAiCompatibleWithTools(config, messages, toolsJson = null)
        return Pair(result.first, result.second)
    }

    /**
     * OpenAI 兼容接口调用（支持 tools + tool_calls 解析）。
     *
     * @param toolsJson OpenAI tools 数组 JSON 字符串，null 表示不传 tools
     * @return 四元组 (content, reasoningContent, toolCalls, finishReason)
     *         - 正常回复：content 非空，toolCalls=null
     *         - 工具调用：content 可能为空，toolCalls 非空，finishReason="tool_calls"
     */
    private suspend fun callOpenAiCompatibleWithTools(
        config: ApiConfig,
        messages: List<Message>,
        toolsJson: String?
    ): Tuple4<String, String?, List<com.lianyu.ai.domain.AiToolCall>?, String?> {
        val safeTemp = config.temperature.coerceIn(0.1f, 1.5f)
        val baseUrl = normalizeOpenAiBaseUrl(config.baseUrl)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val (startIdx, allKeys) = resolveKeysWithPartnerFallback(config)
        var lastException: Exception? = null

        val jsonArray = org.json.JSONArray()
        for (msg in messages) {
            val msgObj = org.json.JSONObject()
            msgObj.put("role", msg.role)
            // content 可能为 null（tool_calls 响应），用 putOpt 避免 NPE
            msgObj.putOpt("content", msg.content)
            // tool_calls 消息需要回传
            msg.tool_calls?.let { toolCalls ->
                val tcArray = org.json.JSONArray()
                for (tc in toolCalls) {
                    val tcObj = org.json.JSONObject()
                    tcObj.put("id", tc.id)
                    tcObj.put("type", tc.type)
                    val fnObj = org.json.JSONObject()
                    fnObj.put("name", tc.function.name)
                    fnObj.put("arguments", tc.function.arguments)
                    tcObj.put("function", fnObj)
                    tcArray.put(tcObj)
                }
                msgObj.put("tool_calls", tcArray)
            }
            // tool 角色的消息带 tool_call_id
            msg.tool_call_id?.let { msgObj.put("tool_call_id", it) }
            jsonArray.put(msgObj)
        }

        for (i in 0 until allKeys.size) {
            val keyIndex = (startIdx + i) % allKeys.size
            val currentKey = allKeys[keyIndex]
            try {
                val jsonBody = org.json.JSONObject()
                jsonBody.put("model", config.model)
                jsonBody.put("messages", jsonArray)
                if (!requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", safeTemp.toDouble())
                }
                val maxTokens = config.maxTokens ?: 800
                if (maxTokens > 0) {
                    val maxTokensParam = if (usesMaxCompletionTokens(config.provider)) {
                        "max_completion_tokens"
                    } else {
                        "max_tokens"
                    }
                    jsonBody.put(maxTokensParam, maxTokens)
                }
                // 工具调用：注入 tools + tool_choice
                if (!toolsJson.isNullOrBlank() && toolsJson != "[]") {
                    jsonBody.put("tools", org.json.JSONArray(toolsJson))
                    jsonBody.put("tool_choice", "auto")
                }

                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                if (prefersApiKeyHeader(config.provider)) {
                    requestBuilder.addHeader("api-key", currentKey)
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer $currentKey")
                }
                val request = requestBuilder
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val client = getEffectiveClient(config)
                val response = executeAdaptive(config, request, client)
                SecureLog.api("HTTP", "code=${response.code}, protocol=${response.protocol}")
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val errorMsg = if (body.trimStart().startsWith("{")) {
                        runCatching { json.decodeFromString<ChatCompletionResponse>(body).error?.message }.getOrNull()
                    } else null
                    throw Exception(errorMsg ?: "HTTP ${response.code}: 服务器返回错误页面")
                }

                ensureNotHtml(body, response)
                val parsed = json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) {
                    throw Exception(parsed.error.message ?: "API返回错误")
                }

                val choice = parsed.choices?.firstOrNull()
                val message = choice?.message
                val rawContent = message?.content
                val reasoning = message?.reasoning_content
                val finishReason = choice?.finish_reason
                SecureLog.api("RESPONSE", "len=${rawContent?.length ?: 0}, reasoningLen=${reasoning?.length ?: 0}, finish=$finishReason")

                // 解析 tool_calls
                val toolCalls = message?.tool_calls?.map { tc ->
                    com.lianyu.ai.domain.AiToolCall(
                        id = tc.id,
                        name = tc.function.name,
                        arguments = tc.function.arguments
                    )
                }

                // 有 tool_calls 时，content 可能为空，这是正常的
                if (!toolCalls.isNullOrEmpty()) {
                    return Tuple4("", reasoning, toolCalls, finishReason ?: "tool_calls")
                }

                var content = if (!rawContent.isNullOrBlank()) {
                    stripThinkingContent(rawContent)
                } else {
                    ""
                }

                if (content.isBlank()) {
                    throw Exception("模型仅返回了思考过程，未生成实际回复，请重试")
                }

                return Tuple4(content, reasoning, null, finishReason)
            } catch (e: Exception) {
                lastException = e
                markKeyFailed(currentKey)
                SecureLog.w("AiService", "Chat Key #${keyIndex + 1}/${allKeys.size} 失败: ${e.message}")
                if (i < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }

    /** 简易四元组（Kotlin 无内置 Tuple4） */
    private data class Tuple4<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D
    )

    private fun stripThinkingContent(content: String): String {
        var result = content
        // XML/HTML 风格思考标签
        result = result.replace(Regex("""(?is)<think[^>]*>[\s\S]*?</think\s*>"""), "")
        result = result.replace(Regex("""(?is)<thinking[^>]*>[\s\S]*?</thinking\s*>"""), "")
        result = result.replace(Regex("""(?is)<thought[^>]*>[\s\S]*?</thought\s*>"""), "")
        result = result.replace(Regex("""(?is)<reflection[^>]*>[\s\S]*?</reflection\s*>"""), "")
        // Markdown 风格思考标题（## 思考 / ## Thinking 等）
        result = result.replace(Regex("""(?im)^#{1,3}\s*(思考|思维|推理|分析|Thinking|Reasoning|Analysis|Thought)\s*\n[\s\S]*?(?=\n#{1,3}\s|$)"""), "")
        // 【思考】/【推理】等方括号包裹的思考块
        result = result.replace(Regex("""(?is)【(思考|思维|推理|分析)】[\s\S]*?【/(思考|思维|推理|分析)】"""), "")
        // 行内 [思考] ... [/思考] 格式
        result = result.replace(Regex("""(?is)\[(思考|思维|推理|分析|thought|thinking)]\s*[\s\S]*?\[/\1]"""), "")
        return result.trim()
    }

    suspend fun callAnthropicForTest(config: ApiConfig, messages: List<Message>, systemPrompt: String): String {
        val anthropicMessages = messages.filter { it.role != "system" }.map {
            AnthropicMessage(
                role = if (it.role == "user") "user" else "assistant",
                content = it.content ?: ""
            )
        }

        val request = AnthropicRequest(
            model = config.model,
            messages = anthropicMessages,
            system = systemPrompt,
            max_tokens = 5,
            temperature = config.temperature
        )

        val baseUrl = config.baseUrl.trim().removeSuffix("/")
        val url = "$baseUrl/messages"

        val response = anthropicApi.chatCompletion(
            url = url,
            apiKey = config.apiKey,
            request = request
        )

        if (response.error != null) {
            throw Exception(response.error.message ?: "API返回错误")
        }

        return response.content?.firstOrNull()?.text
            ?: throw Exception("API返回空内容")
    }

    private suspend fun callAnthropic(config: ApiConfig, messages: List<Message>, systemPrompt: String): String {
        val anthropicMessages = messages.filter { it.role != "system" }.map {
            AnthropicMessage(
                role = if (it.role == "user") "user" else "assistant",
                content = it.content ?: ""
            )
        }

        val request = AnthropicRequest(
            model = config.model,
            messages = anthropicMessages,
            system = systemPrompt,
            max_tokens = config.maxTokens ?: 800,
            temperature = config.temperature
        )

        val baseUrl = config.baseUrl.trim().removeSuffix("/")
        val url = "$baseUrl/messages"

        val response = anthropicApi.chatCompletion(
            url = url,
            apiKey = config.apiKey,
            request = request
        )

        if (response.error != null) {
            throw Exception(response.error.message ?: "API返回错误")
        }

        return response.content?.firstOrNull()?.text
            ?: throw Exception("API返回空内容")
    }

    suspend fun callGeminiForTest(config: ApiConfig, messages: List<Message>, systemPrompt: String): String {
        val baseUrl = normalizeOpenAiBaseUrl(config.baseUrl)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val jsonArray = org.json.JSONArray()
        for (msg in messages) {
            val msgObj = org.json.JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            jsonArray.put(msgObj)
        }
        val jsonBody = org.json.JSONObject()
        jsonBody.put("model", config.model)
        jsonBody.put("messages", jsonArray)
        if (!requiresFixedTemperature(config.model)) {
            jsonBody.put("temperature", 0.7)
        }
        // 根据API提供商选择正确的参数名称
        val maxTokensParam = if (usesMaxCompletionTokens(config.provider)) {
            "max_completion_tokens"
        } else {
            "max_tokens"
        }
        jsonBody.put(maxTokensParam, 5)

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
        // 根据API提供商选择最优的认证方式
        if (prefersApiKeyHeader(config.provider)) {
            requestBuilder.addHeader("api-key", config.apiKey)
        } else {
            requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        val request = requestBuilder
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = executeAdaptive(config, request)
        val body = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val errorMsg = runCatching { json.decodeFromString<ChatCompletionResponse>(body).error?.message }.getOrNull()
                ?: "HTTP ${response.code}"
            throw Exception(errorMsg)
        }

        val parsed = json.decodeFromString<ChatCompletionResponse>(body)
        if (parsed.error != null) {
            throw Exception(parsed.error.message ?: "API返回错误")
        }

        return parsed.choices?.firstOrNull()?.message?.content
            ?: throw Exception("API返回空内容")
    }

    private suspend fun recordTokenUsage(companionId: Long, messageCount: Int, responseLength: Int) {
        try {
            val estimatedInputTokens = (messageCount * 50L).coerceAtLeast(100L)
            val estimatedOutputTokens = (responseLength / 4L).coerceAtLeast(10L)
            
            tokenUsageRepository.recordTokenUsage(
                companionId = companionId,
                inputTokens = estimatedInputTokens,
                outputTokens = estimatedOutputTokens
            )
            
            SecureLog.api("TOKEN", "Recorded usage for companion=$companionId, in=$estimatedInputTokens, out=$estimatedOutputTokens")
        } catch (e: Exception) {
            SecureLog.w("AiService", "Failed to record token usage: ${e.message}")
        }
    }

    fun getTokenUsageRepository(): TokenUsageRepository = tokenUsageRepository

    /**
     * 发送图片消息并调用视觉AI模型进行识别
     * 支持多模态模型：GPT-4V、Gemini Vision、Claude Vision等
     */
    suspend fun sendMessageWithImage(
        companion: CompanionModel?,
        history: List<ChatMessage>,
        imagePath: String,
        stickerProbability: Int = 30,
        ntpTimeEnabled: Boolean = false
    ): AiResponse {
        SecureLog.i("VISION", "========== sendMessageWithImage CALLED ==========")
        SecureLog.i("VISION", "imagePath=$imagePath, companion=${companion?.name ?: "NULL"}")

        if (companion == null) {
            SecureLog.e("VISION", "ERROR: companion is null!")
            return AiResponse("抱歉，找不到角色信息。")
        }

        return SecureLog.timed("AiService", "sendMessageWithImage") {
            withContext(Dispatchers.IO) {
                var config = resolveConfig()
                SecureLog.i("VISION", "resolveConfig result: ${if (config != null) "OK (provider=${config.provider}, model=${config.model})" else "NULL"}")

                if (config == null) {
                    SecureLog.e("VISION", "ERROR: config is null!")
                    return@withContext AiResponse("请先配置并启用可用的API。在「我」->「API设置」中添加密钥并测试连接。")
                }

                if (config.model.isBlank()) {
                    SecureLog.e("VISION", "ERROR: model is blank!")
                    return@withContext AiResponse("模型名未配置，请在「API设置」中重新测试连接以自动选择模型。")
                }

                val isVisionEnabled = try {
                    appSettingsStore.getVisionEnabled()
                } catch (e: Exception) {
                    SecureLog.w("AiService", "Failed to get vision setting, defaulting to enabled: ${e.message}")
                    true
                }

                SecureLog.i("VISION", "visionEnabled=$isVisionEnabled")

                if (!isVisionEnabled) {
                    SecureLog.w("VISION", "WARNING: Vision is DISABLED, returning early")
                    return@withContext AiResponse("[TOAST]视觉识别功能已关闭，请在「API设置」->「视觉识别设置」中开启")
                }

                val visionModelSetting = try {
                    appSettingsStore.getVisionModel()
                } catch (e: Exception) {
                    AppSettingsStore.VisionModels.VISION_AUTO
                }

                val visionProviderSetting = try {
                    appSettingsStore.getVisionProvider()
                } catch (e: Exception) {
                    "auto"
                }

                val visionApiUrlSetting = try {
                    appSettingsStore.getVisionApiUrl()
                } catch (e: Exception) {
                    ""
                }

                val visionApiKeySetting = try {
                    appSettingsStore.getVisionApiKey()
                } catch (e: Exception) {
                    ""
                }

                SecureLog.i("VISION", "Settings: modelSetting=$visionModelSetting, providerSetting=$visionProviderSetting, url=${visionApiUrlSetting.take(30)}..., key=${visionApiKeySetting.take(10)}...")
                SecureLog.i("VISION", "Original config: provider=${config.provider}, baseUrl=${config.baseUrl}, model=${config.model}, key=${config.apiKey.take(10)}...")

                if (visionProviderSetting != "auto" && (visionApiUrlSetting.isNotBlank() || visionApiKeySetting.isNotBlank())) {
                    val resolvedProvider = when (visionProviderSetting.uppercase()) {
                        "OPENAI" -> ApiProvider.OPENAI
                        "ANTHROPIC" -> ApiProvider.ANTHROPIC
                        "GEMINI" -> ApiProvider.GEMINI
                        "KIMI" -> ApiProvider.KIMI
                        "DEEPSEEK" -> ApiProvider.DEEPSEEK
                        "DASHSCOPE" -> ApiProvider.DASHSCOPE
                        "ZHIPU" -> ApiProvider.ZHIPU
                        "CUSTOM" -> ApiProvider.CUSTOM
                        else -> config.provider
                    }

                    val resolvedModel = AppSettingsStore.VisionModels.resolveVisionModel(visionModelSetting, resolvedProvider.name)
                    SecureLog.i("VISION", "Resolved: provider=$resolvedProvider, model=$resolvedModel (from setting='$visionModelSetting')")

                    val finalApiKey = visionApiKeySetting.ifBlank { config.apiKey }
                    val finalBaseUrl = visionApiUrlSetting.ifBlank { config.baseUrl }

                    SecureLog.i("VISION", "Final config: provider=$resolvedProvider, baseUrl=$finalBaseUrl, model=$resolvedModel, key=${finalApiKey.take(10)}...")

                    if (finalApiKey.isBlank()) {
                        return@withContext AiResponse("[TOAST]API密钥为空，请检查视觉模型设置中的API Key")
                    }

                    if (finalBaseUrl.isBlank()) {
                        return@withContext AiResponse("[TOAST]API地址为空，请检查视觉模型设置中的Base URL")
                    }

                    config = ApiConfig(
                        provider = resolvedProvider,
                        apiKey = finalApiKey,
                        extraApiKeys = config.extraApiKeys,
                        baseUrl = finalBaseUrl,
                        model = resolvedModel,
                        id = config.id
                    )

                    SecureLog.i("VISION", "Using independent API config: provider=${config.provider}, baseUrl=${config.baseUrl}, model=${config.model}")
                } else {
                    SecureLog.i("VISION", "Using main API config (provider=auto mode)")

                    when (visionModelSetting) {
                        AppSettingsStore.VisionModels.VISION_AUTO -> {
                            // User selected "Auto-detect" with "Follow main API"
                            // Use the main API config AS-IS, don't override anything
                            // The user's main API model should already support vision (or they would configure a specific vision model)
                            SecureLog.i("VISION", "Keeping original main API config: provider=${config.provider}, model=${config.model}, baseUrl=${config.baseUrl}")
                        }
                        else -> {
                            // User explicitly selected a specific vision model, override only the model name
                            val resolvedModel = visionModelSetting
                            if (resolvedModel != config.model) {
                                config = config.copy(model = resolvedModel)
                                SecureLog.i("VISION", "User-specified vision model override: ${config.model}")
                            }
                        }
                    }
                }

                val sortedHistory = history.sortedBy { it.timestamp }
                val lastUserMessage = sortedHistory.lastOrNull { it.isFromUser }?.content ?: ""
                val contextLimit = appSettingsStore.getContextLimit()
                val innerThoughtEnabled = appSettingsStore.getInnerThoughtEnabled()
                val memoryContext = memoryProvider.getMemoryContext(companion.id, null, lastUserMessage, contextLimit)
                val stickerManager = StickerManager.getInstance(appContext)
                val availableStickers = stickerManager.getAllStickers().mapNotNull { sticker ->
                    val displayName = sticker.description?.takeIf {
                        it.isNotBlank() && !it.startsWith("sticker_") && it.length <= 20
                    } ?: sticker.name.removePrefix("sticker_").removeSuffix(".png").takeIf { it.isNotBlank() && it.length <= 20 }
                    if (displayName.isNullOrBlank() || displayName.length > 20) null else displayName
                }.distinct()
                val baseSystemPrompt = AiPromptBuilder.buildSystemPrompt(companion, memoryContext, lastUserMessage, availableStickers, stickerProbability, innerThoughtEnabled, ntpTimeEnabled = false, role = CompanionRole.GIRLFRIEND)
                val systemPrompt = appendYanderePromptIfNeeded(baseSystemPrompt, companion)

                SecureLog.api("VISION", "provider=${config.provider}, model=${config.model}, image=$imagePath")

                try {
                    SecureLog.i("VISION", "Starting image encoding: $imagePath")
                    val imageBase64 = encodeImageToBase64(imagePath)
                    val mimeType = getImageMimeType(imagePath)
                    SecureLog.i("VISION", "Image encoded successfully: size=${imageBase64.length} chars, mimeType=$mimeType")

                    // [M9 FIX] 复用视觉客户端单例：原每次调用都 getEffectiveClient(config).newBuilder().build()
                    val visionClient = visionHttpClient

                    val rawResponse = when (config.provider) {
                        ApiProvider.OPENAI, ApiProvider.DEEPSEEK, ApiProvider.DASHSCOPE, ApiProvider.KIMI, ApiProvider.GEMINI, ApiProvider.XIAOMI, ApiProvider.ZHIPU, ApiProvider.SILICONFLOW, ApiProvider.OPENROUTER, ApiProvider.GROQ, ApiProvider.CUSTOM, ApiProvider.IFLYTEK, ApiProvider.PARTNER -> {
                            callOpenAiCompatibleVision(config, sortedHistory, systemPrompt, lastUserMessage, imageBase64, mimeType, visionClient)
                        }
                        ApiProvider.ANTHROPIC -> {
                            callAnthropicVision(config, sortedHistory, systemPrompt, imageBase64, mimeType, visionClient)
                        }
                    }

                    if (rawResponse.isBlank()) {
                        throw Exception("API返回空内容，请检查模型是否支持视觉功能")
                    }

                    recordTokenUsage(companion.id, sortedHistory.size, rawResponse.length)

                    val cleaned = AiPromptBuilder.applyPersonaPostProcessing(rawResponse, sortedHistory)
                    SecureLog.api("VISION", "Response length=${cleaned.length}")

                    // 输出安全检查（与 sendMessage 保持一致）
                    val safetyResult = ContentFilter.checkOutputSafety(cleaned)
                    if (!safetyResult.isSafe) {
                        SecureLog.w("AiService", "sendMessageWithImage output blocked: ${safetyResult.reason}")
                        // [C4 FIX] AI 生成内容不应累加用户封禁
                        return@withContext AiResponse("抱歉，我无法继续这个话题。")
                    }

                    AiResponse(cleaned)
                } catch (e: java.net.SocketTimeoutException) {
                    SecureLog.e("AiService", "sendMessageWithImage timeout", e)
                    AiResponse("[TOAST]图片识别超时，请检查网络连接后重试（图片可能过大）")
                } catch (e: Exception) {
                    SecureLog.e("AiService", "sendMessageWithImage failed", e)
                    val errorMessage = when {
                        e.message?.contains("vision", ignoreCase = true) == true ||
                        e.message?.contains("image", ignoreCase = true) == true ->
                            "[TOAST]当前模型不支持视觉功能，请在「视觉识别设置」中选择支持图片识别的模型"
                        e.message?.contains("timeout", ignoreCase = true) == true ||
                        e.message?.contains("timed out", ignoreCase = true) == true ->
                            "[TOAST]图片识别请求超时，请尝试发送更小的图片"
                        e.message?.contains("429", ignoreCase = true) == true ||
                        e.message?.contains("rate limit", ignoreCase = true) == true ->
                            "[TOAST]请求过于频繁，请稍后再试"
                        e.message?.contains("401", ignoreCase = true) == true ||
                        e.message?.contains("403", ignoreCase = true) == true ->
                            "[TOAST]API认证失败，请检查密钥是否有效"
                        else -> formatApiException(e)
                    }
                    AiResponse(errorMessage)
                }
            }
        }
    }

    /**
     * 将图片文件编码为Base64字符串
     */
    private fun encodeImageToBase64(imagePath: String): String {
        return try {
            val file = java.io.File(imagePath)
            if (!file.exists()) throw Exception("图片文件不存在: $imagePath")

            val bytes = file.readBytes()
            if (bytes.isEmpty()) throw Exception("图片文件为空")

            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            SecureLog.e("AiService", "encodeImageToBase64 failed", e)
            throw Exception("图片编码失败: ${e.message}")
        }
    }

    /**
     * 根据文件扩展名获取MIME类型
     */
    private fun getImageMimeType(imagePath: String): String {
        return when (imagePath.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }

    /**
     * 调用OpenAI兼容的视觉API（GPT-4V等）
     */
    private suspend fun callOpenAiCompatibleVision(
        config: ApiConfig,
        history: List<ChatMessage>,
        systemPrompt: String,
        lastUserMessage: String,
        imageBase64: String,
        mimeType: String,
        client: OkHttpClient = okHttpClient
    ): String {
        val safeTemp = config.temperature.coerceIn(0.1f, 1.5f)
        val baseUrl = normalizeOpenAiBaseUrl(config.baseUrl)
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val allKeys = resolveKeysWithPartnerFallback(config).second
        if (allKeys.isEmpty()) {
            throw Exception("API Key 为空，请检查视觉模型配置")
        }
        var lastException: Exception? = null

        for (keyIndex in allKeys.indices) {
            val currentKey = allKeys[keyIndex]
            try {
                val messagesJson = org.json.JSONArray()

                messagesJson.put(buildSystemMessageJson(systemPrompt))

                val recentHistory = history.takeLast(12)
                recentHistory.forEach { msg ->
                    if (msg.isFromUser && msg.type == MessageType.IMAGE) {
                        val userMessageJson = org.json.JSONObject()
                        userMessageJson.put("role", "user")

                        val contentArray = org.json.JSONArray()
                        val textPart = org.json.JSONObject()
                        textPart.put("type", "text")
                        textPart.put("text", "请仔细观察这张图片，描述你看到的内容，并根据上下文进行回复。")
                        contentArray.put(textPart)

                        val imagePart = org.json.JSONObject()
                        imagePart.put("type", "image_url")
                        val imageUrlObj = org.json.JSONObject()
                        imageUrlObj.put("url", "data:$mimeType;base64,$imageBase64")
                        imagePart.put("image_url", imageUrlObj)
                        contentArray.put(imagePart)

                        userMessageJson.put("content", contentArray)
                        messagesJson.put(userMessageJson)
                    } else {
                        val msgObj = org.json.JSONObject()
                        msgObj.put("role", if (msg.isFromUser) "user" else "assistant")
                        msgObj.put("content", msg.content)
                        messagesJson.put(msgObj)
                    }
                }

                val jsonBody = org.json.JSONObject()
                jsonBody.put("model", config.model)
                jsonBody.put("messages", messagesJson)
                // Some models only support temperature=1
                if (!requiresFixedTemperature(config.model)) {
                    jsonBody.put("temperature", safeTemp.toDouble())
                }
                val maxTokens = config.maxTokens ?: 800
                if (maxTokens > 0) {
                    // 根据API提供商选择正确的参数名称
                    val maxTokensParam = if (usesMaxCompletionTokens(config.provider)) {
                        "max_completion_tokens"
                    } else {
                        "max_tokens"
                    }
                    jsonBody.put(maxTokensParam, maxTokens)
                }

                val requestBodyStr = jsonBody.toString()
                SecureLog.i("VISION", "Request URL: $url")
                SecureLog.i("VISION", "Request model: ${config.model}")
                SecureLog.i("VISION", "Request body size: ${requestBodyStr.length} chars")

                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                // 根据API提供商选择最优的认证方式
                if (prefersApiKeyHeader(config.provider)) {
                    requestBuilder.addHeader("api-key", currentKey)
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer $currentKey")
                }
                val request = requestBuilder
                    .post(requestBodyStr.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = executeAdaptive(config, request, client)
                val body = response.body?.string() ?: throw Exception("Empty response")
                SecureLog.i("VISION", "Response code: ${response.code}, body length: ${body.length}")

                if (!response.isSuccessful) {
                    val errorMsg = if (body.trimStart().startsWith("{")) {
                        runCatching { json.decodeFromString<ChatCompletionResponse>(body).error?.message }.getOrNull()
                    } else null
                    throw Exception(errorMsg ?: "HTTP ${response.code}: 服务器返回错误页面")
                }

                ensureNotHtml(body, response)
                val parsed = json.decodeFromString<ChatCompletionResponse>(body)
                if (parsed.error != null) {
                    throw Exception(parsed.error.message ?: "API返回错误")
                }

                val message = parsed.choices?.firstOrNull()?.message
                var content = message?.content ?: throw Exception("API返回空内容")
                content = stripThinkingContent(content)
                return content
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                SecureLog.w("AiService", "Vision Key ${keyIndex + 1}/${allKeys.size} timeout: ${e.message}")
                if (keyIndex < allKeys.size - 1) continue else throw e
            } catch (e: Exception) {
                lastException = e
                SecureLog.w("AiService", "Vision Key ${keyIndex + 1}/${allKeys.size} failed: ${e.message}")
                if (keyIndex < allKeys.size - 1) continue else throw lastException
            }
        }

        throw lastException ?: Exception("所有 API Key 均请求失败")
    }

    /**
     * 调用Anthropic Claude视觉API
     */
    private suspend fun callAnthropicVision(
        config: ApiConfig,
        history: List<ChatMessage>,
        systemPrompt: String,
        imageBase64: String,
        mimeType: String,
        client: OkHttpClient = okHttpClient
    ): String {
        val anthropicMessages = org.json.JSONArray()

        val recentHistory = history.takeLast(12)
        recentHistory.forEach { msg ->
            if (msg.isFromUser && msg.type == MessageType.IMAGE) {
                val userMsgObj = org.json.JSONObject()
                userMsgObj.put("role", "user")

                val contentArray = org.json.JSONArray()

                val textPart = org.json.JSONObject()
                textPart.put("type", "text")
                textPart.put("text", "请仔细观察这张图片，描述你看到的内容，并根据上下文进行回复。")
                contentArray.put(textPart)

                val imagePart = org.json.JSONObject()
                imagePart.put("type", "image")
                val sourceObj = org.json.JSONObject()
                sourceObj.put("type", "base64")
                sourceObj.put("media_type", mimeType)
                sourceObj.put("data", imageBase64)
                imagePart.put("source", sourceObj)
                contentArray.put(imagePart)

                userMsgObj.put("content", contentArray)
                anthropicMessages.put(userMsgObj)
            } else if (msg.isFromUser || !msg.isFromUser) {
                val msgObj = org.json.JSONObject()
                msgObj.put("role", if (msg.isFromUser) "user" else "assistant")
                msgObj.put("content", msg.content)
                anthropicMessages.put(msgObj)
            }
        }

        val requestBody = org.json.JSONObject()
        requestBody.put("model", config.model)
        requestBody.put("messages", anthropicMessages)
        requestBody.put("system", systemPrompt)
        requestBody.put("max_tokens", config.maxTokens ?: 800)
        requestBody.put("temperature", config.temperature)

        val baseUrl = config.baseUrl.trim().removeSuffix("/")
        val url = "$baseUrl/messages"

        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = executeAdaptive(config, request, client)
        val body = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val errorMsg = runCatching {
                val errorJson = org.json.JSONObject(body)
                errorJson.getJSONObject("error")?.getString("message")
            }.getOrNull() ?: "HTTP ${response.code}"
            throw Exception(errorMsg)
        }

        val responseJson = org.json.JSONObject(body)
        if (responseJson.has("error")) {
            throw Exception(responseJson.getJSONObject("error").getString("message") ?: "API返回错误")
        }

        val contents = responseJson.getJSONArray("content")
        if (contents.length() > 0) {
            return contents.getJSONObject(0).getString("text") ?: throw Exception("API返回空内容")
        }

        throw Exception("API返回空内容")
    }

    private fun buildSystemMessageJson(systemPrompt: String): org.json.JSONObject {
        val systemMsg = org.json.JSONObject()
        systemMsg.put("role", "system")
        systemMsg.put("content", systemPrompt)
        return systemMsg
    }

    // ============================================================
    // AiServiceProvider 接口实现 — 领域类型 → 数据库实体转换
    // ============================================================

    override suspend fun sendMessage(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        stickerProbability: Int,
        ntpTimeEnabled: Boolean
    ): AiResponse {
        val entity = companion.toCompanionEntity()
        val messages = history.map { it.toChatMessage() }
        return sendMessage(entity, messages, stickerProbability, ntpTimeEnabled)
    }

    override suspend fun sendMessage(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        stickerProbability: Int,
        ntpTimeEnabled: Boolean,
        tools: List<AiTool>?
    ): AiResponse {
        if (tools.isNullOrEmpty()) {
            return sendMessage(companion, history, stickerProbability, ntpTimeEnabled)
        }
        val entity = companion.toCompanionEntity()
        val messages = history.map { it.toChatMessage() }
        return sendMessageWithTools(entity, messages, stickerProbability, ntpTimeEnabled, tools)
    }

    /**
     * 带工具调用能力的消息发送。
     * 请求注入 tools 定义，响应解析 tool_calls 并返回给调用方（ChatViewModel 负责执行循环）。
     */
    private suspend fun sendMessageWithTools(
        companion: CompanionModel?,
        history: List<ChatMessage>,
        stickerProbability: Int,
        ntpTimeEnabled: Boolean,
        tools: List<AiTool>
    ): AiResponse {
        if (companion == null) return AiResponse("抱歉，找不到角色信息。")

        return SecureLog.timed("AiService", "sendMessageWithTools") {
            withContext(Dispatchers.IO) {
                val config = resolveConfig()
                    ?: return@withContext AiResponse("请先配置并启用可用的API。")
                if (config.model.isBlank()) {
                    return@withContext AiResponse("模型名未配置。")
                }

                val sortedHistory = history.sortedBy { it.timestamp }
                val sanitizedHistory = if (config.provider == ApiProvider.PARTNER) {
                    sortedHistory
                } else {
                    sortedHistory.map { msg ->
                        if (msg.isFromUser) msg.copy(content = com.lianyu.ai.common.safety.DifferentialPrivacyFilter.sanitize(msg.content))
                        else msg
                    }
                }
                val lastUserMessage = sanitizedHistory.lastOrNull { it.isFromUser }?.content ?: ""
                val contextLimit = appSettingsStore.getContextLimit()
                val innerThoughtEnabled = appSettingsStore.getInnerThoughtEnabled()
                val compressionMode = appSettingsStore.getContextCompressionMode()
                val keepRatio = appSettingsStore.getCompressionKeepRatio()
                val minKeep = appSettingsStore.getCompressionMinKeep()
                val memoryContext = memoryProvider.getMemoryContext(companion.id, null, lastUserMessage, contextLimit)
                val role = userRepository.selectedRole.value
                val baseSystemPrompt = AiPromptBuilder.buildSystemPrompt(companion, memoryContext, lastUserMessage, emptyList(), stickerProbability, innerThoughtEnabled, ntpTimeEnabled = false, role = role)
                val systemPrompt = appendYanderePromptIfNeeded(baseSystemPrompt, companion)
                val messages = buildMessages(sanitizedHistory, systemPrompt, lastUserMessage, contextLimit, compressionMode = compressionMode, memoryContext = memoryContext, keepRatio = keepRatio, minKeep = minKeep)

                SecureLog.api("SEND", "provider=${config.provider}, model=${config.model}, messages=${messages.size}, tools=${tools.size}")

                try {
                    val toolsJson = com.lianyu.ai.domain.ToolRegistry.toolDefinitionsJson()
                    val result = when (config.provider) {
                        ApiProvider.OPENAI, ApiProvider.DEEPSEEK, ApiProvider.DASHSCOPE, ApiProvider.KIMI, ApiProvider.GEMINI, ApiProvider.XIAOMI, ApiProvider.ZHIPU, ApiProvider.SILICONFLOW, ApiProvider.OPENROUTER, ApiProvider.GROQ, ApiProvider.CUSTOM, ApiProvider.IFLYTEK, ApiProvider.PARTNER -> {
                            callOpenAiCompatibleWithTools(config, messages, toolsJson)
                        }
                        ApiProvider.ANTHROPIC -> {
                            // Anthropic 路径暂不支持 tools，降级为普通调用
                            val resp = callAnthropic(config, messages, systemPrompt)
                            Tuple4(resp, null, null, null)
                        }
                    }
                    val rawResponse = result.first
                    val reasoning = result.second
                    val toolCalls = result.third
                    val finishReason = result.fourth

                    recordTokenUsage(companion.id, messages.size, rawResponse.length + (toolCalls?.joinToString("") { it.arguments }?.length ?: 0))

                    // 有 tool_calls：直接返回，不做事后处理，由 ChatViewModel 执行循环
                    if (!toolCalls.isNullOrEmpty()) {
                        SecureLog.api("TOOL_CALL", "count=${toolCalls.size}, names=${toolCalls.map { it.name }}")
                        return@withContext AiResponse(
                            content = "",
                            reasoningContent = reasoning,
                            toolCalls = toolCalls,
                            finishReason = finishReason
                        )
                    }

                    if (rawResponse.isBlank()) {
                        throw Exception("API返回空内容，请检查模型名是否正确")
                    }

                    val cleaned = AiPromptBuilder.applyPersonaPostProcessing(rawResponse, sortedHistory)
                    val safetyResult = ContentFilter.checkOutputSafety(cleaned)
                    if (!safetyResult.isSafe) {
                        return@withContext AiResponse("抱歉，我无法继续这个话题。")
                    }

                    AiResponse(cleaned, reasoning, null, finishReason)
                } catch (e: Exception) {
                    SecureLog.e("AiService", "sendMessageWithTools failed", e)
                    throw Exception(formatApiException(e))
                }
            }
        }
    }

    override suspend fun sendMessageWithImage(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        imagePath: String,
        stickerProbability: Int,
        ntpTimeEnabled: Boolean
    ): AiResponse {
        val entity = companion.toCompanionEntity()
        val messages = history.map { it.toChatMessage() }
        return sendMessageWithImage(entity, messages, imagePath, stickerProbability, ntpTimeEnabled)
    }

    private fun AiCompanionInfo.toCompanionEntity(): CompanionModel = CompanionModel(
        id = id,
        name = name,
        personality = personality,
        age = age,
        backstory = backstory,
        speakingStyle = speakingStyle,
        systemPrompt = systemPrompt
    )

    private fun AiChatMessage.toChatMessage(): ChatMessage = ChatMessage(
        companionId = companionId,
        content = content,
        isFromUser = isFromUser,
        timestamp = timestamp,
        type = when (type) {
            AiMessageType.TEXT -> MessageType.TEXT
            AiMessageType.IMAGE -> MessageType.IMAGE
        }
    )

    override fun shouldProactivelyMessage(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>
    ): Boolean {
        val entity = companion.toCompanionEntity()
        val messages = recentMessages.map { it.toChatMessage() }
        return shouldProactivelyMessage(entity, messages)
    }

    override suspend fun generateProactiveMessage(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>
    ): String? {
        val entity = companion.toCompanionEntity()
        val messages = recentMessages.map { it.toChatMessage() }
        return generateProactiveMessage(entity, messages)
    }

    override suspend fun generateProactiveMessage(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>,
        settings: ProactiveMessageSettings?
    ): String? {
        val entity = companion.toCompanionEntity()
        val messages = recentMessages.map { it.toChatMessage() }
        return generateProactiveMessage(entity, messages, settings)
    }


    override suspend fun sendMessageWithCustomSystem(
        companion: AiCompanionInfo,
        history: List<AiChatMessage>,
        customSystemPrompt: String,
        stickerProbability: Int,
        companionNameMap: Map<Long, String>
    ): String {
        val entity = companion.toCompanionEntity()
        val messages = history.map { it.toChatMessage() }
        return sendMessageWithCustomSystem(entity, messages, customSystemPrompt, stickerProbability, companionNameMap)
    }

    override suspend fun generateFollowUpQuestion(
        companion: AiCompanionInfo,
        recentMessages: List<AiChatMessage>,
        lastAiContent: String
    ): String? {
        val entity = companion.toCompanionEntity()
        val messages = recentMessages.map { it.toChatMessage() }
        return generateFollowUpQuestion(entity, messages, lastAiContent)
    }

    /**
     * 生成追问问题。AI回复后按概率触发，让对话继续下去。
     */
    suspend fun generateFollowUpQuestion(
        companion: CompanionModel,
        recentMessages: List<ChatMessage>,
        lastAiContent: String
    ): String? = withContext(Dispatchers.IO) {
        val config = resolveConfig()
        if (config == null || config.model.isBlank()) return@withContext null

        val sortedMessages = recentMessages.sortedBy { it.timestamp }
        val lastUserMsg = sortedMessages.lastOrNull { it.isFromUser }?.content ?: ""

        val messages = mutableListOf<Message>()
        val recentContext = sortedMessages.takeLast(10)
        for (msg in recentContext) {
            messages.add(Message(
                role = if (msg.isFromUser) "user" else "assistant",
                content = msg.content
            ))
        }

        messages.add(Message("user", buildString {
            appendLine("你是${companion.name}，刚回复了：\"${lastAiContent.take(100)}\"")
            appendLine("用户之前说了：\"${lastUserMsg.take(100)}\"")
            appendLine()
            appendLine("现在你要追加一条追问，让对话继续下去。要求：")
            appendLine("1. 5-15字，口语化，像真人聊天")
            appendLine("2. 必须是问句，针对上面的对话内容追问")
            appendLine("3. 带语气词（呀/呢/啦/嘛/哼/嘿嘿/诶/哇）")
            appendLine("4. 禁止万能开场白（在干嘛/想你了/好久不见），必须针对具体内容")
            appendLine("5. 直接输出追问内容，不要解释不要思考")
        }))

        try {
            val rawResponse = when (config.provider) {
                ApiProvider.OPENAI, ApiProvider.DEEPSEEK, ApiProvider.DASHSCOPE, ApiProvider.KIMI, ApiProvider.GEMINI, ApiProvider.XIAOMI, ApiProvider.ZHIPU, ApiProvider.SILICONFLOW, ApiProvider.OPENROUTER, ApiProvider.GROQ, ApiProvider.CUSTOM, ApiProvider.IFLYTEK, ApiProvider.PARTNER -> {
                    callOpenAiCompatible(config, messages)
                }
                ApiProvider.ANTHROPIC -> {
                    callAnthropic(config, messages, "")
                }
            }
            val cleaned = rawResponse
                .replace(Regex("\\r\\n|\\r|\\n+"), "")
                .replace(Regex("(?is)<think[^>]*>[\\s\\S]*?</think\\s*>"), "")
                .replace(Regex("(?is)<think[^>]*>[\\s\\S]*"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("【.*?】"), "")
                .replace(Regex("\\*.*?\\*"), "")
                .replace(Regex("<.*?>"), "")
                .trim()

            if (cleaned.length < 2) return@withContext null

            val safetyResult = ContentFilter.checkOutputSafety(cleaned)
            if (!safetyResult.isSafe) return@withContext null

            cleaned
        } catch (e: Exception) {
            SecureLog.w("AiService", "Follow-up question failed: ${e.message}")
            null
        }
    }

    override suspend fun callJudge(prompt: String): String {
        return callOpenAiCompatibleForJudge(prompt)
    }

    override suspend fun callGeneration(prompt: String): String {
        return callOpenAiCompatibleForGeneration(prompt)
    }

}
