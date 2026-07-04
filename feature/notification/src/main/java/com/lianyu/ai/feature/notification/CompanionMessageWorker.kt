package com.lianyu.ai.feature.notification

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lianyu.ai.common.wechat.WeChatBroadcast
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.database.repository.ChatMessageCrypto
import com.lianyu.ai.database.repository.filterDecrypted
import com.lianyu.ai.domain.AiServiceProvider
import com.lianyu.ai.domain.AiCompanionInfo
import com.lianyu.ai.domain.AiChatMessage
import com.lianyu.ai.domain.AiMessageType
import com.lianyu.ai.domain.ProactiveMessageSettings
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.common.AppForegroundTracker
import com.lianyu.ai.common.BanManager
import com.lianyu.ai.common.ChatDetailSettingsDataStoreProvider
import com.lianyu.ai.common.SecureLog
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 仅提取主动消息相关字段，避免跨 feature 依赖完整设置类。
 * 字段必须与 feature:chat 的 [CompanionChatDetailSettings] 中同名字段保持对齐，
 * 否则反序列化时 ignoreUnknownKeys=true 会静默丢弃。 */
@Serializable
data class ProactiveSettings(
    val proactiveEnabled: Boolean = true,
    /** 用户手动输入的间隔（分钟），优先使用；UI 可编辑范围 30~1440 */
    val proactiveIntervalMinutes: Int = 180,
    val proactiveMinIntervalMinutes: Int = 60,
    val proactiveMaxIntervalMinutes: Int = 720,
    val proactiveDailyLimit: Int = 6,
    /** 是否允许 AI 主动开启新话题 */
    val allowNewTopic: Boolean = true,
    /** 是否允许在主动消息后追加追问句 */
    val allowFollowUpMessage: Boolean = true,
    val doNotDisturbEnabled: Boolean = false,
    val dndStartMinutes: Int = 23 * 60,
    val dndEndMinutes: Int = 8 * 60,
    val allowLateNightMessage: Boolean = false,
    val allowPriorityMessageInDnd: Boolean = false,
    val blocked: Boolean = false
)

class CompanionMessageWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val aiServiceProvider: AiServiceProvider by lazy {
        ServiceRegistry.get(AiServiceProvider::class.java)
            ?: throw IllegalStateException("AiServiceProvider not registered in ServiceRegistry")
    }

    // 直接读取 chat_detail_settings DataStore，避免跨 feature 依赖
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (BanManager.isBanned(context)) {
                SecureLog.d("CompanionMessageWorker", "User is banned, skip proactive message")
                return@withContext Result.success()
            }

            val database = AppDatabase.getDatabase(context)
            val companionDao = database.companionDao()
            val chatMessageDao = database.chatMessageDao()

            val companions = companionDao.getAllCompanionsSync()
            if (companions.isEmpty()) return@withContext Result.success()

            // ── 筛选启用主动消息且未屏蔽的伴侣 ──
            val eligibleCompanions = companions.filter { companion ->
                runCatching { readCompanionSettings(companion.id) }.getOrNull()?.let { settings ->
                    settings.proactiveEnabled && !settings.blocked
                } ?: false
            }

            if (eligibleCompanions.isEmpty()) {
                SecureLog.d("CompanionMessageWorker", "No eligible companions (all disabled/blocked), reschedule")
                scheduleNext(context, null)
                return@withContext Result.success()
            }

            // 从符合条件的伴侣中随机选一个
            val randomCompanion = eligibleCompanions.random()
            val settings = runCatching { readCompanionSettings(randomCompanion.id) }.getOrNull()
                ?: ProactiveSettings()

            // ── 免打扰检查 ──
            if (settings.doNotDisturbEnabled && !settings.allowPriorityMessageInDnd) {
                val now = java.util.Calendar.getInstance()
                val totalMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                val inDndRange = if (settings.dndStartMinutes > settings.dndEndMinutes) {
                    // 跨午夜：如 23:00 ~ 08:00
                    totalMinutes >= settings.dndStartMinutes || totalMinutes < settings.dndEndMinutes
                } else {
                    totalMinutes in settings.dndStartMinutes until settings.dndEndMinutes
                }
                if (inDndRange && !settings.allowLateNightMessage) {
                    SecureLog.d("CompanionMessageWorker", "DND active for ${randomCompanion.name}, skip")
                    scheduleNext(context, settings)
                    return@withContext Result.success()
                }
            }

            // ── 每日上限检查（精确计数，跨天自动重置） ──
            if (settings.proactiveDailyLimit > 0) {
                val todayCount = getTodayProactiveCount(context, randomCompanion.id)
                if (todayCount >= settings.proactiveDailyLimit) {
                    SecureLog.d("CompanionMessageWorker", "Daily limit reached ($todayCount/${settings.proactiveDailyLimit}) for ${randomCompanion.name}")
                    scheduleNext(context, settings)
                    return@withContext Result.success()
                }
            }

            val recentMessages = chatMessageDao.getRecentMessagesSync(randomCompanion.id, 10)
                .map { ChatMessageCrypto.decryptFromStorage(it) }
                .filterDecrypted()

            // 传入自定义设置，让 shouldProactivelyMessage/generateProactiveMessage 按其行为
            val domainSettings = settings.toDomain()
            if (!aiServiceProvider.shouldProactivelyMessage(randomCompanion.toAiCompanionInfo(), recentMessages.toAiChatMessages(), domainSettings)) {
                scheduleNext(context, settings)
                return@withContext Result.success()
            }

            val messageContent = aiServiceProvider.generateProactiveMessage(randomCompanion.toAiCompanionInfo(), recentMessages.toAiChatMessages(), domainSettings)

            if (messageContent == null) {
                SecureLog.w("CompanionMessageWorker", "Proactive message is null, skipping")
                scheduleNext(context, settings)
                return@withContext Result.success()
            }

            // 安全检查：拦截 AI 主动消息中的违规内容（仅最终防线，不累计封禁）
            // AiService 生成时已做过 checkOutputSafety，这里只做兜底
            val outputSafety = com.lianyu.ai.common.ContentFilter.checkOutputSafety(messageContent)
            if (!outputSafety.isSafe) {
                SecureLog.w("CompanionMessageWorker", "Proactive message blocked by safety filter: ${outputSafety.reason}")
                // AI 输出违规不应累加用户封禁（见 Bug #1 根因 A3）
                scheduleNext(context, settings)
                return@withContext Result.success()
            }

            // ── 分段发送：按自然句号/感叹号/问号/换行拆分，逐条入库+广播 ──
            val segments = splitIntoSegments(messageContent)
            var totalSegmentsSent = 0

            for ((index, segment) in segments.withIndex()) {
                if (index > 0) {
                    // 段间延迟 1~2 秒，模拟真人分段打字效果
                    delay(Random.nextLong(1000L, 2000L))
                }

                val message = ChatMessage(
                    companionId = randomCompanion.id,
                    content = segment,
                    isFromUser = false
                )
                val messageId = chatMessageDao.insertMessage(ChatMessageCrypto.encryptForStorage(message))
                broadcastProactiveWeChatMessage(randomCompanion.id, messageId)
                totalSegmentsSent++
            }

            // 精确增加今日主动消息计数
            if (totalSegmentsSent > 0 && settings.proactiveDailyLimit > 0) {
                incrementTodayProactiveCount(context, randomCompanion.id, 1)
            }

            // 仅在最后一条段时推送通知，避免通知轰炸
            if (!AppForegroundTracker.isInForeground && segments.isNotEmpty()) {
                NotificationHelper.showCompanionMessageNotification(
                    context,
                    randomCompanion.name,
                    segments.first(), // 通知显示第一条即可
                    randomCompanion.id
                )
            }

            scheduleNext(context, settings)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /**
     * 将长文本拆分为自然短段落。
     * 按中文句号/感叹号/问号/换行分割，每段不超过60字。
     */
    private fun splitIntoSegments(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= 60) return listOf(trimmed)

        // 按句子边界分割
        val sentenceParts = trimmed.split(Regex("(?<=[。！？!?\n])"))
        val segments = mutableListOf<String>()
        var currentSegment = StringBuilder()

        for (part in sentenceParts) {
            val candidate = if (currentSegment.isEmpty()) part else "$currentSegment$part"
            if (candidate.length <= 60) {
                currentSegment = StringBuilder(candidate)
            } else {
                if (currentSegment.isNotEmpty()) {
                    segments.add(currentSegment.toString().trim())
                }
                currentSegment = StringBuilder(part)
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment.toString().trim())
        }

        // 兜底：如果某段仍然过长，强制按字数截断
        return segments.flatMap { seg ->
            if (seg.length <= 60) listOf(seg) else seg.chunked(60)
        }.filter { it.isNotBlank() }
    }

    private fun broadcastProactiveWeChatMessage(companionId: Long, messageId: Long) {
        val intent = Intent(WeChatBroadcast.ACTION_SEND_PROACTIVE).apply {
            setPackage(context.packageName)
            putExtra(WeChatBroadcast.EXTRA_COMPANION_ID, companionId)
            putExtra(WeChatBroadcast.EXTRA_MESSAGE_ID, messageId)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.applicationContext.sendBroadcast(intent, null)
            } else {
                context.applicationContext.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            SecureLog.w("CompanionMessageWorker", "Failed to send broadcast: ${e.message}")
        }
    }

    /**
     * 从 DataStore 读取指定伴侣的主动消息相关设置。
     * 直接读取与 ChatDetailSettingsStore 共享的同一 DataStore，避免跨 feature 依赖。
     */
    private suspend fun readCompanionSettings(companionId: Long): ProactiveSettings? {
        return runCatching {
            val dataStore = ChatDetailSettingsDataStoreProvider.get(context)
            val prefs = dataStore.data.first()
            val raw = prefs[stringPreferencesKey("companion_chat_detail_settings_map")] ?: return@runCatching null
            val settingsMap: Map<Long, ProactiveSettings> = json.decodeFromString(raw)
            settingsMap[companionId]
        }.getOrNull()
    }

    // ── 领域类型转换辅助 ──

    /** 将 Worker 侧 [ProactiveSettings] 映射为 domain 层 [ProactiveMessageSettings] */
    private fun ProactiveSettings.toDomain() = ProactiveMessageSettings(
        proactiveEnabled = proactiveEnabled,
        proactiveIntervalMinutes = proactiveIntervalMinutes,
        proactiveMinIntervalMinutes = proactiveMinIntervalMinutes,
        proactiveMaxIntervalMinutes = proactiveMaxIntervalMinutes,
        proactiveDailyLimit = proactiveDailyLimit,
        allowNewTopic = allowNewTopic,
        allowFollowUpMessage = allowFollowUpMessage,
        doNotDisturbEnabled = doNotDisturbEnabled,
        dndStartMinutes = dndStartMinutes,
        dndEndMinutes = dndEndMinutes,
        allowLateNightMessage = allowLateNightMessage,
        allowPriorityMessageInDnd = allowPriorityMessageInDnd,
        blocked = blocked
    )

    private fun com.lianyu.ai.database.model.CompanionEntity.toAiCompanionInfo() = AiCompanionInfo(
        id = id, name = name, personality = personality,
        age = age, backstory = backstory, speakingStyle = speakingStyle,
        systemPrompt = systemPrompt
    )

    private fun com.lianyu.ai.database.model.ChatMessage.toAiChatMessage() = AiChatMessage(
        isFromUser = isFromUser, content = content, timestamp = timestamp,
        type = when (type) {
            MessageType.IMAGE -> AiMessageType.IMAGE
            else -> AiMessageType.TEXT
        },
        companionId = companionId
    )

    private fun List<com.lianyu.ai.database.model.ChatMessage>.toAiChatMessages() = map { it.toAiChatMessage() }

    companion object {
        private const val WORK_NAME = "companion_message_work"

        /** 默认间隔兜底（当设置读取失败时使用） */
        private const val FALLBACK_MIN_MINUTES = 30L
        private const val FALLBACK_MAX_MINUTES = 120L

        private const val DAILY_COUNT_PREFS = "proactive_daily_count"

        /** 获取指定伴侣今日已发主动消息数（精确计数，不取近似） */
        private fun getTodayProactiveCount(context: Context, companionId: Long): Int {
            val prefs = context.getSharedPreferences(DAILY_COUNT_PREFS, Context.MODE_PRIVATE)
            val today = todayKey()
            val storedDate = prefs.getString("date_$companionId", null)
            return if (storedDate == today) prefs.getInt("count_$companionId", 0) else 0
        }

        /** 增加指定伴侣今日主动消息计数 */
        private fun incrementTodayProactiveCount(context: Context, companionId: Long, delta: Int) {
            val prefs = context.getSharedPreferences(DAILY_COUNT_PREFS, Context.MODE_PRIVATE)
            val today = todayKey()
            val count = getTodayProactiveCount(context, companionId) + delta
            prefs.edit()
                .putString("date_$companionId", today)
                .putInt("count_$companionId", count)
                .apply()
        }

        private fun todayKey(): String {
            val cal = java.util.Calendar.getInstance()
            return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        }

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * 外部入口：首次调度，使用默认间隔。
         * 使用 enqueueUniqueWork + REPLACE 确保只保留最新一次调度，
         * 消除 KeepAliveService 15min 心跳 + MainActivity 启动反复 schedule 导致的请求堆叠。
         */
        fun schedule(context: Context) {
            val delayMinutes = Random.nextInt(FALLBACK_MIN_MINUTES.toInt(), FALLBACK_MAX_MINUTES.toInt())

            val workRequest = OneTimeWorkRequestBuilder<CompanionMessageWorker>()
                .setConstraints(networkConstraints)
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        /**
         * 后续调度：优先使用用户手动输入的 [ProactiveSettings.proactiveIntervalMinutes]，
         * 未设置时回退到 min/max 区间随机。
         */
        private fun scheduleNext(context: Context, settings: ProactiveSettings?) {
            val delayMinutes = if (settings != null && settings.proactiveIntervalMinutes > 0) {
                // 用户手动输入的间隔优先，确保 ≥15 分钟，最大 1440 分钟（24h）
                settings.proactiveIntervalMinutes.coerceIn(15, 1440).toLong()
            } else {
                val minInterval = settings?.proactiveMinIntervalMinutes?.coerceAtLeast(15) ?: FALLBACK_MIN_MINUTES.toInt()
                val maxInterval = settings?.proactiveMaxIntervalMinutes?.coerceAtLeast(minInterval + 1) ?: FALLBACK_MAX_MINUTES.toInt()
                Random.nextInt(minInterval, maxInterval + 1).toLong()
            }

            val workRequest = OneTimeWorkRequestBuilder<CompanionMessageWorker>()
                .setConstraints(networkConstraints)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
