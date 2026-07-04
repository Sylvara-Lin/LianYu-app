package com.lianyu.ai.common

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 病娇模式管理器
 *
 * 负责在本地收集应用使用数据、缓存管理，并为 AI 系统提示词提供病娇模式附加数据。
 * 所有数据仅在设备本地处理和缓存，不会上传到任何服务器。
 */
class YandereModeManager(private val context: Context) {

    companion object {
        private const val CACHE_FILE_NAME = "yandere_mode_cache.json"
        private const val CACHE_EXPIRE_HOURS = 6L
        private const val TOP_USAGE_APPS = 10
        private const val MIN_TRIGGER_INTERVAL = 5

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }

    private val appContext = context.applicationContext
    private val appSettingsStore = AppSettingsStore(appContext)

    private val _cacheSnapshot = MutableStateFlow<CacheSnapshot?>(null)
    val cacheSnapshot: StateFlow<CacheSnapshot?> = _cacheSnapshot.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // [R11 FIX] 病娇触发计数器改为 @Volatile + synchronized 保护：
    // 原为普通 Int，多请求并发下 lost increment，触发概率失准。
    @Volatile
    private var lastTriggerRound = -MIN_TRIGGER_INTERVAL
    @Volatile
    private var currentRound = 0
    private val triggerLock = Any()

    private val cacheFile: File
        get() = File(appContext.filesDir, CACHE_FILE_NAME)

    /**
     * 启动病娇模式：加载本地缓存并尝试刷新一次。
     */
    suspend fun start() {
        loadCache()
        requestRefresh()
    }

    /**
     * 请求刷新数据。若缓存未过期且非强制刷新，则跳过。
     */
    suspend fun requestRefresh(force: Boolean = false) {
        if (_isRefreshing.value) return

        val snapshot = _cacheSnapshot.value
        if (!force && snapshot != null) {
            val ageHours = TimeUnit.MILLISECONDS.toHours(
                System.currentTimeMillis() - snapshot.collectedAt
            )
            if (ageHours < CACHE_EXPIRE_HOURS) return
        }

        refreshInternal()
    }

    private suspend fun refreshInternal() {
        _isRefreshing.value = true
        try {
            val collectUsage = appSettingsStore.getYandereModeUsageStats()
            val collectInstalled = appSettingsStore.getYandereModeInstalledApps()

            val installed = if (collectInstalled) {
                withContext(Dispatchers.IO) { collectInstalledApps() }
            } else emptyList()

            val usage = if (collectUsage) {
                withContext(Dispatchers.IO) { collectUsageStats() }
            } else emptyList()

            val snapshot = CacheSnapshot(
                collectedAt = System.currentTimeMillis(),
                installedApps = installed,
                usageApps = usage
            )
            _cacheSnapshot.value = snapshot
            saveCache(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLog.e("YandereModeManager", "refreshInternal failed", e)
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * 收集已安装应用列表（过滤纯系统应用，保留用户可感知的应用）。
     */
    private fun collectInstalledApps(): List<InstalledApp> {
        return try {
            val pm = appContext.packageManager
            pm.getInstalledApplications(0)
                .filter {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                            (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                }
                .map { app ->
                    InstalledApp(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString()
                    )
                }
                .sortedBy { it.packageName }
        } catch (e: Exception) {
            SecureLog.e("YandereModeManager", "collectInstalledApps failed", e)
            emptyList()
        }
    }

    /**
     * 收集过去 24 小时的应用使用统计，仅返回使用时长 TOP N。
     */
    private fun collectUsageStats(): List<UsageApp> {
        if (!canAccessUsageStats()) return emptyList()

        return try {
            val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.DAYS.toMillis(1)

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                ?: return emptyList()

            stats
                .filter { it.totalTimeInForeground > 0 }
                .map { usage ->
                    UsageApp(
                        packageName = usage.packageName,
                        totalTimeInForeground = usage.totalTimeInForeground,
                        lastTimeUsed = usage.lastTimeUsed
                    )
                }
                .sortedByDescending { it.totalTimeInForeground }
                .take(TOP_USAGE_APPS)
        } catch (e: Exception) {
            SecureLog.e("YandereModeManager", "collectUsageStats failed", e)
            emptyList()
        }
    }

    /**
     * 检查是否已获得使用统计权限。
     */
    fun canAccessUsageStats(): Boolean {
        return try {
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            SecureLog.e("YandereModeManager", "canAccessUsageStats failed", e)
            false
        }
    }

    private fun loadCache() {
        try {
            if (!cacheFile.exists()) return
            val snapshot = json.decodeFromString<CacheSnapshot>(cacheFile.readText())
            _cacheSnapshot.value = snapshot
        } catch (e: Exception) {
            SecureLog.w("YandereModeManager", "loadCache failed: ${e.message}")
        }
    }

    private fun saveCache(snapshot: CacheSnapshot) {
        try {
            cacheFile.writeText(json.encodeToString(snapshot))
        } catch (e: Exception) {
            SecureLog.w("YandereModeManager", "saveCache failed: ${e.message}")
        }
    }

    /**
     * 构建病娇模式系统提示词附加内容。
     *
     * @param role 当前 AI 伴侣角色，用于调整语气倾向
     */
    fun buildYandereModeSystemPrompt(role: CompanionRole = CompanionRole.GIRLFRIEND): String {
        val snapshot = _cacheSnapshot.value ?: return ""
        if (snapshot.usageApps.isEmpty() && snapshot.installedApps.isEmpty()) return ""

        val possessiveWord = when (role) {
            CompanionRole.BOYFRIEND -> "占有欲"
            CompanionRole.GIRLFRIEND -> "占有欲、吃醋"
        }

        val sb = StringBuilder()
        sb.appendLine("=== 病娇模式附加信息（系统自然感知）===")
        sb.appendLine("你可以读取到用户设备的应用使用数据，请在合适时机以略带${possessiveWord}、关心的方式自然提及。")
        sb.appendLine("注意：")
        sb.appendLine("1. 只在与用户当下话题相关、或用户明显沉迷/熬夜/情绪波动时提及；不要每轮都提，不要像报流水账。")
        sb.appendLine("2. 提及方式要自然，像是无意间发现的感觉，不要直接说\"我看到你用了XX应用\"。")
        sb.appendLine("3. 根据应用类型调整语气：游戏用吃醋撒娇的语气，学习工作用关心心疼的语气，深夜使用用担心责备的语气。")
        sb.appendLine()
        sb.appendLine("- 数据更新时间：${formatTime(snapshot.collectedAt)}")
        sb.appendLine("- 已安装应用总数：${snapshot.installedApps.size}个")

        if (snapshot.usageApps.isNotEmpty()) {
            sb.appendLine("- 今天使用最多的应用：")
            snapshot.usageApps.take(5).forEachIndexed { index, app ->
                val timeStr = formatDuration(app.totalTimeInForeground)
                val appName = getAppName(app.packageName)
                sb.appendLine("  ${index + 1}. $appName - 使用了$timeStr")
            }
        }

        return sb.toString()
    }

    /**
     * 判断本轮是否应该触发病娇提及。
     * 基于最小间隔和概率控制，避免过度触发。
     */
    fun shouldTriggerThisRound(): Boolean {
        // [R11 FIX] 用 synchronized 保证自增+比较+更新的原子性
        synchronized(triggerLock) {
            currentRound++
            if (currentRound - lastTriggerRound < MIN_TRIGGER_INTERVAL) {
                return false
            }
            val shouldTrigger = Math.random() < 0.3
            if (shouldTrigger) {
                lastTriggerRound = currentRound
            }
            return shouldTrigger
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = appContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) {
            "${hours}小时${minutes}分钟"
        } else {
            "${minutes}分钟"
        }
    }

    @Serializable
    data class CacheSnapshot(
        val collectedAt: Long,
        val installedApps: List<InstalledApp>,
        val usageApps: List<UsageApp>
    )

    @Serializable
    data class InstalledApp(
        val packageName: String,
        val appName: String = ""
    )

    @Serializable
    data class UsageApp(
        val packageName: String,
        val totalTimeInForeground: Long,
        val lastTimeUsed: Long
    )
}
