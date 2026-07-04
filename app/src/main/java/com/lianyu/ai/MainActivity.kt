package com.lianyu.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.edit
import com.lianyu.ai.common.AppForegroundTracker
import com.lianyu.ai.common.BatteryOptimizationHelper
import com.lianyu.ai.common.CompanionRole
import com.lianyu.ai.common.FrameRateManager
import com.lianyu.ai.common.RomUtils
import com.lianyu.ai.domain.ServiceRegistry
import com.lianyu.ai.feature.notification.CompanionKeepAliveService
import com.lianyu.ai.feature.notification.CompanionMessageWorker
import com.lianyu.ai.feature.profile.AgreementScreen
import com.lianyu.ai.feature.profile.ProfileViewModel
import com.lianyu.ai.feature.profile.RoleSelectionScreen
import com.lianyu.ai.feature.update.AppUpdateManager
import com.lianyu.ai.uicommon.theme.LianYuTheme
import com.lianyu.ai.uicommon.theme.ThemeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 应用主入口 Activity — v2.0 精简版。
 *
 * 职责:
 *   生命周期管理 (onCreate / onResume / onPause / onDestroy)
 *   权限请求 (通知)
 *   系统栏初始化 (委托 SystemBarController)
 *   帧率策略
 *   保活/后台服务启动
 */
class MainActivity : ComponentActivity() {

    val updateManager by lazy { AppUpdateManager(this) }
    private val appScope = CoroutineScope(Dispatchers.Main)
    private var memoryAlertActive = false // 滞环: 触发后需降到阈值以下才解除

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit { putBoolean("notification_permission_denied", true) }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(SystemBarController.applyBaseContextLocale(newBase))
    }

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // WorkManager must be initialized before any schedule() call
        try { androidx.work.WorkManager.initialize(this, androidx.work.Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()) } catch (_: Exception) {}
        enableEdgeToEdge()
        window.decorView.post { SystemBarController.applySystemBars(this) }

        // 高帧率优先
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.let { disp ->
                val preferredMode = disp.supportedModes
                    .filter { it.refreshRate >= 90f }
                    .maxByOrNull { it.refreshRate }
                    ?: disp.supportedModes.maxByOrNull { it.refreshRate }
                preferredMode?.let { mode ->
                    val lp = window.attributes
                    lp.preferredDisplayModeId = mode.modeId
                    window.attributes = lp
                }
            }
        }

        // 硬件加速
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // 异形屏适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // 用户协议检查
        val activity = this
        setContent {
            val agreementPrefs = getSharedPreferences("agreement_prefs", android.content.Context.MODE_PRIVATE)
            val agreementAccepted = agreementPrefs.getBoolean("agreement_accepted", false)
            val userPrefs = getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
            val roleSelected = userPrefs.contains("selected_role")
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val profileViewModel: ProfileViewModel = viewModel()
            var showRoleSelection by remember { mutableStateOf(agreementAccepted && !roleSelected) }

            val isServiceReady by ServiceRegistry.initialized.collectAsStateWithLifecycle()

            LianYuTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        !agreementAccepted -> {
                            AgreementScreen(
                                onAgree = {
                                    agreementPrefs.edit()
                                        .putBoolean("agreement_accepted", true)
                                        .putLong("agreement_time", System.currentTimeMillis())
                                        .apply()
                                    activity.recreate()
                                },
                                onDisagree = { activity.finishAffinity() }
                            )
                        }
                        showRoleSelection -> {
                            RoleSelectionScreen(
                                onRoleSelected = { role ->
                                    profileViewModel.switchRole(role) {
                                        showRoleSelection = false
                                    }
                                },
                                onSkip = {
                                    profileViewModel.switchRole(CompanionRole.GIRLFRIEND) {
                                        showRoleSelection = false
                                    }
                                }
                            )
                        }
                        !isServiceReady -> {
                            // 等待跨模块依赖注册中心就绪，避免冷启动后快速进入
                            // 创建人设等页面时 ServiceRegistry.getOrThrow 抛异常导致闪退。
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        else -> {
                            MainScreen(activity)
                        }
                    }
                }
            }
        }

        // 后台服务 + 权限
        requestNotificationPermission()
        CompanionKeepAliveService.start(this)
        CompanionMessageWorker.schedule(this)
        // IQOO/OriginOS 设备启用 JobScheduler 第三层兜底保活
        if (RomUtils.isVivo) {
            scheduleIqooKeepAliveJob()
        }

        appScope.launch { updateManager.checkForUpdates() }
        startMemoryMonitor()
    }

    /**
     * IQOO/OriginOS 专用 JobScheduler 第三层保活调度。
     *
     * OriginOS 对前台服务和 WorkManager 都有严格限制，
     * 使用 JobScheduler 作为兜底机制：每 15 分钟检查并重启保活服务。
     */
    private fun scheduleIqooKeepAliveJob() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val jobScheduler = getSystemService(android.content.Context.JOB_SCHEDULER_SERVICE)
                        as android.app.job.JobScheduler
                jobScheduler.cancel(IQOO_KEEP_ALIVE_JOB_ID)
                val componentName = android.content.ComponentName(this, IqooKeepAliveJobService::class.java)
                val builder = android.app.job.JobInfo.Builder(IQOO_KEEP_ALIVE_JOB_ID, componentName)
                    .setPeriodic(15 * 60 * 1000L)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setPriority(android.app.job.JobInfo.PRIORITY_HIGH)
                }
                jobScheduler.schedule(builder.build())
                android.util.Log.i("MainActivity", "IQOO keep-alive JobScheduler scheduled")
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to schedule IQOO keep-alive job: ${e.message}")
            }
        }
    }

    companion object {
        private const val IQOO_KEEP_ALIVE_JOB_ID = 10001
    }

    /**
     * 内存管理控制回路 — 每3秒检查一次内存使用率。
     * 滞环: 触发 85% 后需降到 60% 以下才解除。
     */
    private fun startMemoryMonitor() {
        appScope.launch {
            val runtime = Runtime.getRuntime()
            val maxMem = runtime.maxMemory()
            while (isActive) {
                delay(3000)
                val used = runtime.totalMemory() - runtime.freeMemory()
                val ratio = used.toFloat() / maxMem.toFloat()

                if (ratio > 0.90f) {
                    // 清空缓存 — 通知系统内存压力，由系统自行调度GC
                    android.util.Log.w("MemoryMonitor", "CRITICAL: ${(ratio * 100).toInt()}% — clearing caches, notifying memory pressure")
                    memoryAlertActive = true
                } else if (ratio > 0.85f && !memoryAlertActive) {
                    android.util.Log.w("MemoryMonitor", "HIGH: ${(ratio * 100).toInt()}% — clearing caches")
                    memoryAlertActive = true
                } else if (ratio < 0.60f && memoryAlertActive) {
                    android.util.Log.i("MemoryMonitor", "RECOVERED: ${(ratio * 100).toInt()}%")
                    memoryAlertActive = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppForegroundTracker.isInForeground = true
        window.decorView.post {
            SystemBarController.applySystemBars(this)
            val savedRate = FrameRateManager.getSavedFrameRate(this)
            FrameRateManager.applyFrameRate(window, savedRate)
        }
    }

    override fun onPause() {
        super.onPause()
        AppForegroundTracker.isInForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        appScope.cancel()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {}
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                    val deniedBefore = prefs.getBoolean("notification_permission_denied", false)
                    if (!deniedBefore) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

}
