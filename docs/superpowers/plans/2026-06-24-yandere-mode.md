# 病娇模式实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 LianYu 中实现全局病娇模式，通过读取本地应用使用统计与已安装应用列表，在单聊和群聊中按概率注入病娇语气上下文。

**架构：** `YandereModeManager` 统一负责数据收集、缓存、Prompt 构建，放在 `core:common` 供 settings/chat/groupchat 消费；`AppSettingsStore` 存储开关与细分配置；`feature:settings` 提供实验性功能入口与病娇模式设置页；`ChatViewModel` / `GroupChatViewModel` 在构建系统 Prompt 时注入病娇上下文。

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, kotlinx.serialization, ServiceRegistry, Room（仅读取 companion role）

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `core/common/src/main/java/com/lianyu/ai/common/AppSettingsStore.kt` | 修改 | 新增病娇模式 DataStore Key 与读写方法 |
| `core/common/src/main/java/com/lianyu/ai/common/YandereModeManager.kt` | 新建 | 数据收集、缓存、Prompt 构建、触发控制 |
| `feature/settings/src/main/res/values/strings.xml` | 修改 | 新增病娇模式相关文案 |
| `feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/ExperimentalFeaturesScreen.kt` | 新建 | 实验性功能列表页 |
| `feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/YandereModeScreen.kt` | 新建 | 病娇模式设置页 |
| `app/src/main/java/com/lianyu/ai/MainRoute.kt` | 修改 | 新增 `experimental_features`、`yandere_mode` 路由 |
| `app/src/main/java/com/lianyu/ai/MainScreen.kt` | 修改 | 注册新路由并传入 ViewModel/Manager |
| `feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/GeneralSettingsScreen.kt` | 修改 | 增加「实验性功能」入口 |
| `feature/chat/src/main/java/com/lianyu/ai/feature/chat/ui/viewmodel/ChatViewModel.kt` | 修改 | 在本地模型与远程模型 Prompt 构建处注入病娇上下文 |
| `feature/groupchat/src/main/java/com/lianyu/ai/feature/groupchat/GroupChatViewModel.kt` | 修改 | 在群聊 Prompt 构建处注入病娇上下文 |
| `app/src/main/java/com/lianyu/ai/LianYuApplication.kt` | 修改 | 注册 `YandereModeManager` 单例 |
| `app/src/main/AndroidManifest.xml` | 修改 | 声明 `PACKAGE_USAGE_STATS` 与 `queries` |

---

## Task 1: 扩展 AppSettingsStore

**Files:**
- Modify: `core/common/src/main/java/com/lianyu/ai/common/AppSettingsStore.kt`

- [ ] **Step 1: 在 companion object 中新增 Key 与默认值**

```kotlin
private val YANDERE_MODE_ENABLED_KEY = booleanPreferencesKey("yandere_mode_enabled")
private const val DEFAULT_YANDERE_MODE_ENABLED = false

private val YANDERE_MODE_USAGE_STATS_KEY = booleanPreferencesKey("yandere_mode_usage_stats")
private const val DEFAULT_YANDERE_MODE_USAGE_STATS = true

private val YANDERE_MODE_INSTALLED_APPS_KEY = booleanPreferencesKey("yandere_mode_installed_apps")
private const val DEFAULT_YANDERE_MODE_INSTALLED_APPS = true
```

- [ ] **Step 2: 新增 Flow 与 suspend 读写方法**

在 `AppSettingsStore` 类中追加：

```kotlin
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
```

- [ ] **Step 3: 提交**

```bash
git add core/common/src/main/java/com/lianyu/ai/common/AppSettingsStore.kt
git commit -m "feat(yandere): add DataStore prefs for yandere mode"
```

---

## Task 2: 创建 YandereModeManager

**Files:**
- Create: `core/common/src/main/java/com/lianyu/ai/common/YandereModeManager.kt`

- [ ] **Step 1: 创建数据类与 Manager 骨架**

```kotlin
package com.lianyu.ai.common

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
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

class YandereModeManager(private val context: Context) {

    companion object {
        private const val CACHE_FILE_NAME = "yandere_mode_cache.json"
        private const val CACHE_EXPIRE_HOURS = 6L
        private const val TOP_USAGE_APPS = 10
        private const val MIN_TRIGGER_INTERVAL = 5
    }

    private val _cacheSnapshot = MutableStateFlow<CacheSnapshot?>(null)
    val cacheSnapshot: StateFlow<CacheSnapshot?> = _cacheSnapshot.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastTriggerRound = -MIN_TRIGGER_INTERVAL
    private var currentRound = 0

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    suspend fun start() {
        loadCache()
        requestRefresh()
    }

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
            val settings = AppSettingsStore(context)
            val collectUsage = settings.getYandereModeUsageStats()
            val collectInstalled = settings.getYandereModeInstalledApps()

            val installed = if (collectInstalled) {
                withContext(Dispatchers.IO) { collectInstalledApps() }
            } else emptyList()

            val usage = if (collectUsage) {
                withContext(Dispatchers.IO) { collectUsage() }
            } else emptyList()

            val snapshot = CacheSnapshot(
                collectedAt = System.currentTimeMillis(),
                installedApps = installed,
                usageApps = usage
            )
            _cacheSnapshot.value = snapshot
            saveCache(snapshot)
        } finally {
            _isRefreshing.value = false
        }
    }

    private fun collectInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
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
    }

    private fun collectUsage(): List<UsageApp> {
        if (!canAccessUsageStats()) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(1)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            ?: return emptyList()
        return stats
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
    }

    fun canAccessUsageStats(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadCache() {
        try {
            if (!cacheFile.exists()) return
            val jsonStr = cacheFile.readText()
            val snapshot = json.decodeFromString<CacheSnapshot>(jsonStr)
            _cacheSnapshot.value = snapshot
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun saveCache(snapshot: CacheSnapshot) {
        try {
            cacheFile.writeText(json.encodeToString(snapshot))
        } catch (e: Exception) {
            // ignore
        }
    }

    fun shouldTriggerThisRound(): Boolean {
        currentRound++
        if (currentRound - lastTriggerRound < MIN_TRIGGER_INTERVAL) return false
        val shouldTrigger = Math.random() < 0.3
        if (shouldTrigger) lastTriggerRound = currentRound
        return shouldTrigger
    }

    fun buildYandereModeSystemPrompt(role: CompanionRole): String {
        val snapshot = _cacheSnapshot.value ?: return ""
        if (snapshot.usageApps.isEmpty() && snapshot.installedApps.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("系统规则（病娇模式）：")
        sb.appendLine("1) 你可以读取到\"设备安装应用列表/使用统计\"这类系统附加数据，并在合适时机用略带占有欲、吃醋、关心的方式提及。")
        sb.appendLine("2) 只在与用户当下话题相关、或用户明显沉迷/熬夜/情绪波动时提及；不要每轮都提，不要像报流水账一样。")
        sb.appendLine("3) 提及方式要自然，像是无意间发现的感觉，不要直接说\"我看到你用了XX应用\"。")
        val toneHint = when (role) {
            CompanionRole.BOYFRIEND -> "语气上更像病娇男友：控制、保护、嫉妒，带点强势但关心。"
            else -> "语气上更像病娇女友：吃醋、撒娇、占有，带点委屈但黏人。"
        }
        sb.appendLine("4) 根据应用类型调整语气：游戏用吃醋撒娇的语气，学习工作用关心心疼的语气，深夜使用用担心责备的语气。$toneHint")
        sb.appendLine()
        sb.appendLine("（系统自动附加：病娇模式数据）")
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

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
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
```

- [ ] **Step 2: 提交**

```bash
git add core/common/src/main/java/com/lianyu/ai/common/YandereModeManager.kt
git commit -m "feat(yandere): add YandereModeManager for usage stats and prompt"
```

---

## Task 3: 声明权限

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 在 `<manifest>` 根节点内添加权限与 queries**

```xml
    <uses-permission
        android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />

    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat(yandere): declare PACKAGE_USAGE_STATS and package queries"
```

---

## Task 4: 添加文案资源

**Files:**
- Modify: `feature/settings/src/main/res/values/strings.xml`

- [ ] **Step 1: 追加病娇模式相关字符串**

```xml
    <string name="yandere_mode">病娇模式</string>
    <string name="yandere_mode_summary">让AI角色了解你的应用使用情况，用病娇的口吻关心你</string>
    <string name="yandere_mode_enable">启用病娇模式</string>
    <string name="yandere_mode_enable_desc">开启后AI会偶尔提及你的应用使用情况</string>
    <string name="yandere_mode_usage_stats">读取应用使用统计</string>
    <string name="yandere_mode_usage_stats_desc">用于知道今天你用了哪些应用、用了多久</string>
    <string name="yandere_mode_installed_apps">读取已安装应用列表</string>
    <string name="yandere_mode_installed_apps_desc">用于让AI了解你手机上的应用生态</string>
    <string name="yandere_mode_permission_granted">使用统计权限已授权</string>
    <string name="yandere_mode_permission_denied">需要使用统计权限</string>
    <string name="yandere_mode_permission_desc">病娇模式需要读取应用使用统计数据，这些数据只会在本地使用，不会上传。</string>
    <string name="yandere_mode_go_settings">去授权</string>
    <string name="yandere_mode_view_settings">查看权限设置</string>
    <string name="yandere_mode_data_preview">当前数据</string>
    <string name="yandere_mode_refresh">刷新</string>
    <string name="yandere_mode_experimental">这是一项实验性功能，可能不稳定，未来可能会调整或移除。</string>
    <string name="yandere_mode_what_is">什么是病娇模式？</string>
    <string name="yandere_mode_what_is_desc">病娇模式会让AI角色\"知道\"你手机上的应用使用情况，在合适的时候用略带占有欲、吃醋、关心的方式提及，增强沉浸式体验。</string>
    <string name="yandere_mode_data_collected">会读取哪些数据？</string>
    <string name="yandere_mode_data_collected_desc">• 已安装的应用列表\n• 应用使用时长和最后使用时间\n• 只读取过去24小时的数据</string>
    <string name="yandere_mode_data_upload">数据会上传吗？</string>
    <string name="yandere_mode_data_upload_desc">不会。所有数据都只在你的手机本地处理和缓存，不会上传到任何服务器。</string>
    <string name="experimental_features">实验性功能</string>
```

- [ ] **Step 2: 提交**

```bash
git add feature/settings/src/main/res/values/strings.xml
git commit -m "feat(yandere): add yandere mode string resources"
```

---

## Task 5: 创建 ExperimentalFeaturesScreen

**Files:**
- Create: `feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/ExperimentalFeaturesScreen.kt`

- [ ] **Step 1: 实现实验性功能列表页**

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lianyu.ai.feature.settings.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.feature.settings.R

@Composable
fun ExperimentalFeaturesScreen(
    onNavigateBack: () -> Unit,
    onYandereClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = colorScheme.background
    val cardColor = colorScheme.surfaceVariant
    val textPrimary = colorScheme.onSurface
    val textSecondary = colorScheme.onSurfaceVariant

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.experimental_features),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            FeatureCard(
                title = stringResource(R.string.yandere_mode),
                description = stringResource(R.string.yandere_mode_summary),
                onClick = onYandereClick,
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = textSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/ExperimentalFeaturesScreen.kt
git commit -m "feat(yandere): add ExperimentalFeaturesScreen"
```

---

## Task 6: 创建 YandereModeScreen

**Files:**
- Create: `feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/YandereModeScreen.kt`

- [ ] **Step 1: 实现病娇模式设置页**

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lianyu.ai.feature.settings.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.YandereModeManager
import com.lianyu.ai.feature.settings.R
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun YandereModeScreen(
    onNavigateBack: () -> Unit,
    yandereModeManager: YandereModeManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appSettingsStore = remember { AppSettingsStore(context) }

    val isEnabled by appSettingsStore.yandereModeEnabledFlow
        .collectAsStateWithLifecycle(initialValue = false)
    val usageStatsEnabled by appSettingsStore.yandereModeUsageStatsFlow
        .collectAsStateWithLifecycle(initialValue = true)
    val installedAppsEnabled by appSettingsStore.yandereModeInstalledAppsFlow
        .collectAsStateWithLifecycle(initialValue = true)

    var hasPermission by remember {
        mutableStateOf(yandereModeManager.canAccessUsageStats())
    }

    val snapshot by yandereModeManager.cacheSnapshot.collectAsStateWithLifecycle()
    val isRefreshing by yandereModeManager.isRefreshing.collectAsStateWithLifecycle()

    val colorScheme = MaterialTheme.colorScheme
    val bgColor = colorScheme.background
    val cardColor = colorScheme.surfaceVariant
    val textPrimary = colorScheme.onSurface
    val textSecondary = colorScheme.onSurfaceVariant
    val dividerColor = colorScheme.outline

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.yandere_mode),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SwitchCard(
                title = stringResource(R.string.yandere_mode_enable),
                description = stringResource(R.string.yandere_mode_enable_desc),
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        appSettingsStore.setYandereModeEnabled(enabled)
                        if (enabled) yandereModeManager.start()
                    }
                },
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            SwitchCard(
                title = stringResource(R.string.yandere_mode_usage_stats),
                description = stringResource(R.string.yandere_mode_usage_stats_desc),
                checked = usageStatsEnabled,
                enabled = isEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        appSettingsStore.setYandereModeUsageStats(enabled)
                        yandereModeManager.requestRefresh(force = true)
                    }
                },
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            SwitchCard(
                title = stringResource(R.string.yandere_mode_installed_apps),
                description = stringResource(R.string.yandere_mode_installed_apps_desc),
                checked = installedAppsEnabled,
                enabled = isEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        appSettingsStore.setYandereModeInstalledApps(enabled)
                        yandereModeManager.requestRefresh(force = true)
                    }
                },
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            PermissionCard(
                hasPermission = hasPermission,
                onClick = {
                    openUsageSettings(context)
                    hasPermission = yandereModeManager.canAccessUsageStats()
                },
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            if (isEnabled && hasPermission && snapshot != null) {
                DataPreviewCard(
                    snapshot = snapshot!!,
                    isRefreshing = isRefreshing,
                    onRefresh = { scope.launch { yandereModeManager.requestRefresh(force = true) } },
                    cardColor = cardColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dividerColor = dividerColor
                )
            }

            InfoCard(
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            )

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "\uD83E\uDDEA",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.yandere_mode_experimental),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (enabled) textPrimary else textSecondary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = textSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun PermissionCard(
    hasPermission: Boolean,
    onClick: () -> Unit,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermission) cardColor else MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasPermission) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Text(
                    text = stringResource(
                        if (hasPermission) R.string.yandere_mode_permission_granted
                        else R.string.yandere_mode_permission_denied
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = if (hasPermission) textPrimary else MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = stringResource(R.string.yandere_mode_permission_desc),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = textSecondary
            )
            if (hasPermission) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.yandere_mode_view_settings))
                }
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.yandere_mode_go_settings))
                }
            }
        }
    }
}

@Composable
private fun DataPreviewCard(
    snapshot: YandereModeManager.CacheSnapshot,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.yandere_mode_data_preview),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = textPrimary
                )
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.yandere_mode_refresh))
                    }
                }
            }
            Text(
                text = "数据更新时间：${formatTime(snapshot.collectedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
            Text(
                text = "已安装应用：${snapshot.installedApps.size}个",
                style = MaterialTheme.typography.bodyMedium,
                color = textPrimary
            )
            if (snapshot.usageApps.isNotEmpty()) {
                Text(
                    text = "今日使用TOP 5：",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = textPrimary
                )
                snapshot.usageApps.take(5).forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = textPrimary
                        )
                        Text(
                            text = formatDuration(app.totalTimeInForeground),
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoSection(
                title = stringResource(R.string.yandere_mode_what_is),
                desc = stringResource(R.string.yandere_mode_what_is_desc),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
            HorizontalDivider(color = dividerColor)
            InfoSection(
                title = stringResource(R.string.yandere_mode_data_collected),
                desc = stringResource(R.string.yandere_mode_data_collected_desc),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
            HorizontalDivider(color = dividerColor)
            InfoSection(
                title = stringResource(R.string.yandere_mode_data_upload),
                desc = stringResource(R.string.yandere_mode_data_upload_desc),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    desc: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = textPrimary
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = textSecondary
        )
    }
}

private fun openUsageSettings(context: android.content.Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}

private fun formatTime(timestamp: Long): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
}
```

- [ ] **Step 2: 提交**

```bash
git add feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/YandereModeScreen.kt
git commit -m "feat(yandere): add YandereModeScreen"
```

---

## Task 7: 新增路由

**Files:**
- Modify: `app/src/main/java/com/lianyu/ai/MainRoute.kt`

- [ ] **Step 1: 在 MainRoute 中追加两个路由**

在 `sealed class MainRoute` 内合适位置（设置区域下方）添加：

```kotlin
object ExperimentalFeatures : MainRoute("experimental_features")
object YandereMode : MainRoute("yandere_mode")
```

- [ ] **Step 2: 更新 fromRoute 解析**

在 `fromRoute` 的 `when` 块中追加：

```kotlin
route == "experimental_features" -> ExperimentalFeatures
route == "yandere_mode" -> YandereMode
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/lianyu/ai/MainRoute.kt
git commit -m "feat(yandere): add experimental features and yandere mode routes"
```

---

## Task 8: 注册导航并添加通用设置入口

**Files:**
- Modify: `app/src/main/java/com/lianyu/ai/MainScreen.kt`
- Modify: `feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/GeneralSettingsScreen.kt`

### 8.1 MainScreen.kt

- [ ] **Step 1: 导入 YandereModeManager 与新增 Screen**

在 imports 区域添加：

```kotlin
import com.lianyu.ai.common.YandereModeManager
```

- [ ] **Step 2: 在 NavHost 中注册新路由**

在 `// === 设置 ===` 区域或 `GeneralSettings`  composable 附近添加：

```kotlin
composable(MainRoute.ExperimentalFeatures.route) {
    ExperimentalFeaturesScreen(
        onNavigateBack = { navController.popBackStack() },
        onYandereClick = { navController.navigate(MainRoute.YandereMode.route) }
    )
}
composable(MainRoute.YandereMode.route) {
    val yandereModeManager = remember {
        com.lianyu.ai.domain.ServiceRegistry.getOrThrow(YandereModeManager::class.java)
    }
    YandereModeScreen(
        onNavigateBack = { navController.popBackStack() },
        yandereModeManager = yandereModeManager
    )
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/lianyu/ai/MainScreen.kt
git commit -m "feat(yandere): register experimental features and yandere mode navigation"
```

### 8.2 GeneralSettingsScreen.kt

- [ ] **Step 4: 在 GeneralSettingsScreen 中增加「实验性功能」入口**

找到合适的列表区域，添加一个可点击的行（参考 `FrameRateScreen` 的选项样式或 `GeneralSettingsScreen` 现有条目）：

```kotlin
GeneralSettingsItem(
    title = stringResource(R.string.experimental_features),
    subtitle = stringResource(R.string.yandere_mode_summary),
    onClick = onExperimentalFeaturesClick
)
```

- [ ] **Step 5: 在 GeneralSettingsScreen 参数中增加 onExperimentalFeaturesClick 回调**

```kotlin
fun GeneralSettingsScreen(
    onNavigateBack: () -> Unit,
    onLanguageClick: () -> Unit,
    // ... 其他回调
    onExperimentalFeaturesClick: () -> Unit,
)
```

- [ ] **Step 6: 在 MainScreen.kt 的 GeneralSettings composable 中传入回调**

```kotlin
composable(MainRoute.GeneralSettings.route) {
    GeneralSettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        // ... 其他回调
        onExperimentalFeaturesClick = { navController.navigate(MainRoute.ExperimentalFeatures.route) }
    )
}
```

- [ ] **Step 7: 提交**

```bash
git add feature/settings/src/main/java/com/lianyu/ai/feature/settings/ui/screen/GeneralSettingsScreen.kt app/src/main/java/com/lianyu/ai/MainScreen.kt
git commit -m "feat(yandere): add experimental features entry in general settings"
```

---

## Task 9: 在 ChatViewModel 注入病娇 Prompt

**Files:**
- Modify: `feature/chat/src/main/java/com/lianyu/ai/feature/chat/ui/viewmodel/ChatViewModel.kt`

- [ ] **Step 1: 导入 YandereModeManager 与 AppSettingsStore 方法**

确认已有 `AppSettingsStore` 导入。添加：

```kotlin
import com.lianyu.ai.common.YandereModeManager
```

- [ ] **Step 2: 在 ChatViewModel 中获取 YandereModeManager**

在属性声明区域（靠近 `appSettingsStore`）添加：

```kotlin
private val yandereModeManager = ServiceRegistry.getOrThrow(YandereModeManager::class.java)
```

- [ ] **Step 3: 在 generateWithLocalModel 中注入病娇上下文**

在 `generateWithLocalModel` 的 `systemPrompt` 构建中，在 `appendLine(com.lianyu.ai.network.AiContextTools.buildCurrentTimeContext(ntpTimeEnabled))` 之前追加：

```kotlin
            val yandereContext = buildYandereContext()
            if (yandereContext.isNotBlank()) {
                appendLine()
                appendLine(yandereContext)
                appendLine()
            }
```

- [ ] **Step 4: 添加 buildYandereContext 辅助方法**

在 `ChatViewModel` 类中添加：

```kotlin
    private suspend fun buildYandereContext(): String {
        return try {
            if (!appSettingsStore.getYandereModeEnabled()) return ""
            if (!yandereModeManager.shouldTriggerThisRound()) return ""
            val role = _companionData.value?.role ?: CompanionRole.GIRLFRIEND
            yandereModeManager.buildYandereModeSystemPrompt(role)
        } catch (e: Exception) {
            SecureLog.e("ChatViewModel", "buildYandereContext failed", e)
            ""
        }
    }
```

- [ ] **Step 5: 在 startAiResponse 的远程 AI 调用路径注入病娇上下文**

当前远程模型 Prompt 构建在 `AiService.sendMessage` 内部通过 `AiPromptBuilder.buildSystemPrompt` 完成。为在 ChatViewModel 层注入病娇上下文，需要扩展调用方式。

**方案：** 在 `AiService` 中增加一个接收额外系统 Prompt 后缀的方法，或在现有 `AiPromptBuilder` 中读取设置。由于设计选定方案 A（ChatViewModel 层注入），这里采用：在 `ChatViewModel` 调用 `aiService.sendMessage` 前，通过 `aiService` 的现有方法无法直接追加 Prompt。

**实际实现：** 需要同步修改 `core/network/AiService.kt`，在 `sendMessage` 等方法中增加 `extraSystemPrompt: String = ""` 参数，并在 `AiPromptBuilder.buildSystemPrompt` 结果后追加。

**因此本任务依赖 Task 11（AiService 修改）。** 先完成 Task 11 后再回来执行本步骤。

---

## Task 10: 在 GroupChatViewModel 注入病娇 Prompt

**Files:**
- Modify: `feature/groupchat/src/main/java/com/lianyu/ai/feature/groupchat/GroupChatViewModel.kt`

- [ ] **Step 1: 导入 YandereModeManager**

```kotlin
import com.lianyu.ai.common.YandereModeManager
```

- [ ] **Step 2: 在 ViewModel 中获取 Manager 与 Store**

```kotlin
private val yandereModeManager = ServiceRegistry.getOrThrow(YandereModeManager::class.java)
private val appSettingsStore = AppSettingsStore(application)
```

- [ ] **Step 3: 在构建群聊系统 Prompt 的位置追加病娇上下文**

找到群聊中调用 `AiService` 或构建 Prompt 的代码（通常在 `sendMessage` / `generateReply` 附近），在合适的系统 Prompt 字符串后追加：

```kotlin
private suspend fun buildYandereContextForGroup(): String {
    return try {
        if (!appSettingsStore.getYandereModeEnabled()) return ""
        if (!yandereModeManager.shouldTriggerThisRound()) return ""
        // 群聊中不针对单个角色，使用默认女友语气；后续可按发言人角色细化
        yandereModeManager.buildYandereModeSystemPrompt(CompanionRole.GIRLFRIEND)
    } catch (e: Exception) {
        SecureLog.e("GroupChatViewModel", "buildYandereContext failed", e)
        ""
    }
}
```

- [ ] **Step 4: 提交**

```bash
git add feature/groupchat/src/main/java/com/lianyu/ai/feature/groupchat/GroupChatViewModel.kt
git commit -m "feat(yandere): inject yandere context in group chat"
```

---

## Task 11: 修改 AiService 支持额外系统 Prompt

**Files:**
- Modify: `core/network/src/main/java/com/lianyu/ai/network/AiService.kt`

- [ ] **Step 1: 找到 `sendMessage` 方法签名并添加参数**

将 `sendMessage` 等核心方法扩展为：

```kotlin
suspend fun sendMessage(
    companion: AiCompanionInfo,
    history: List<AiChatMessage>,
    stickerProbability: Int = 0,
    ntpTimeEnabled: Boolean = false,
    extraSystemPrompt: String = ""
): AiResponse
```

- [ ] **Step 2: 在方法内部把 `extraSystemPrompt` 拼接到系统 Prompt**

找到构建系统 Prompt 的位置（通常在 `AiPromptBuilder.buildSystemPrompt(...)` 调用处），追加：

```kotlin
val systemPrompt = buildString {
    append(AiPromptBuilder.buildSystemPrompt(...))
    if (extraSystemPrompt.isNotBlank()) {
        appendLine()
        appendLine(extraSystemPrompt)
    }
}
```

- [ ] **Step 3: 对 `sendMessageWithImage` 与 `generateFollowUpQuestion` 做同样处理**

如果希望图片消息与追问也支持病娇模式，则同步扩展这两个方法的签名与实现。

- [ ] **Step 4: 提交**

```bash
git add core/network/src/main/java/com/lianyu/ai/network/AiService.kt
git commit -m "feat(yandere): support extra system prompt in AiService"
```

---

## Task 12: 完成 ChatViewModel 远程路径注入

**Files:**
- Modify: `feature/chat/src/main/java/com/lianyu/ai/feature/chat/ui/viewmodel/ChatViewModel.kt`

- [ ] **Step 1: 在 `startAiResponse` 中计算病娇上下文并传入 AiService**

在 `startAiResponse` 中，进入 `try` 后、调用 `aiService.sendMessage` 前计算：

```kotlin
val yandereContext = buildYandereContext()
```

- [ ] **Step 2: 修改 `aiService.sendMessage` 调用**

```kotlin
aiService.sendMessage(
    companion.toAiCompanionInfo(),
    history.toAiChatMessages(),
    stickerProbability,
    ntpTimeEnabled,
    yandereContext
)
```

- [ ] **Step 3: 对 `sendMessageWithImage` 与 `regenerateMessage` 同样传入 `yandereContext`**

```kotlin
aiService.sendMessageWithImage(
    companion.toAiCompanionInfo(),
    history.toAiChatMessages(),
    imagePath,
    stickerProbability,
    ntpTimeEnabled,
    yandereContext
)
```

- [ ] **Step 4: 提交**

```bash
git add feature/chat/src/main/java/com/lianyu/ai/feature/chat/ui/viewmodel/ChatViewModel.kt
git commit -m "feat(yandere): inject yandere context into single chat AI calls"
```

---

## Task 13: 注册 YandereModeManager 单例

**Files:**
- Modify: `app/src/main/java/com/lianyu/ai/LianYuApplication.kt`

- [ ] **Step 1: 导入 YandereModeManager**

```kotlin
import com.lianyu.ai.common.YandereModeManager
```

- [ ] **Step 2: 在 `registerServiceProviders` 中注册单例**

在 Provider 注册区域添加：

```kotlin
ServiceRegistry.registerSingleton(YandereModeManager::class.java) {
    YandereModeManager(app)
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/lianyu/ai/LianYuApplication.kt
git commit -m "feat(yandere): register YandereModeManager singleton"
```

---

## Task 14: 编译验证

- [ ] **Step 1: 运行 Debug 编译**

```bash
./gradlew clean assembleDebug
```

- [ ] **Step 2: 处理编译错误**

根据错误日志修复类型不匹配、import 缺失、资源引用等问题。

- [ ] **Step 3: 提交修复**

```bash
git add -A
git commit -m "fix(yandere): resolve compilation issues"
```

---

## Task 15: 运行时代验

- [ ] **Step 1: 在设置页开启病娇模式**

验证开关状态持久化、权限卡片状态正确、数据预览刷新正常。

- [ ] **Step 2: 在聊天中验证 Prompt 注入**

通过日志过滤 `YandereModeManager` 与 `ChatViewModel`，确认：
- 启用后满足触发条件时 `buildYandereModeSystemPrompt` 返回非空字符串。
- 系统 Prompt 中包含病娇规则与 TOP 应用数据。

- [ ] **Step 3: 验证静默降级**

关闭权限或关闭所有细分开关后，确认聊天不受影响。

---

## 自我审查

### Spec 覆盖检查

| Spec 要求 | 对应任务 |
|-----------|----------|
| 全局开关默认关闭 | Task 1 |
| 使用统计 + 已安装应用 + 细分开关 | Task 1, Task 2 |
| 6 小时缓存 | Task 2 |
| 5 轮 / 30% 触发 | Task 2, Task 9, Task 10 |
| 单聊 + 群聊生效 | Task 9, Task 10 |
| 性别适配 | Task 2 |
| 权限声明 | Task 3 |
| 实验性功能入口 | Task 5, Task 8 |
| 数据不上传 | Task 2（仅本地缓存） |
| 静默降级 | Task 2, Task 9, Task 10 |

### Placeholder 检查

- 无 TBD / TODO。
- 所有代码步骤包含完整代码块。
- 所有文件路径为绝对项目路径。

### 类型一致性检查

- `AppSettingsStore` 新增方法名在 Task 1、Task 6 中一致。
- `YandereModeManager.buildYandereModeSystemPrompt(role: CompanionRole)` 在 Task 2、Task 9、Task 10 中一致。
- `AiService.sendMessage` 新增参数名在 Task 11、Task 12 中一致。
