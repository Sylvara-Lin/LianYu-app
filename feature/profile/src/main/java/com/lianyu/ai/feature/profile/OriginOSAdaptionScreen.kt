package com.lianyu.ai.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.common.BatteryOptimizationHelper
import com.lianyu.ai.common.NativePermissionRequester
import com.lianyu.ai.common.OriginOSBatteryOptimizer
import com.lianyu.ai.common.RomUtils
import kotlinx.coroutines.delay

/**
 * OriginOS 6 适配中心。
 *
 * 集中展示并引导用户逐项开启 OriginOS 6 / IQOO 上的后台保活相关设置：
 *   1. 通知权限（运行时）
 *   2. 忽略电池优化
 *   3. 允许自启动
 *   4. 允许后台高耗电
 *   5. 后台弹出界面
 *   6. 精确闹钟（前台服务 6 小时超时后的兜底）
 *
 * 控制论模型：每项是一个状态机 S ∈ {未配置, 已配置}，传感器读取系统真实状态，
 * 用户点击 → 执行跳转（执行器）；onResume 后回到本页会重新读取状态（反馈）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginOSAdaptionScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    // 触发重新读取权限状态的开关（从设置页返回后递增）
    var refreshTick by remember { mutableStateOf(0) }

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        isVisible = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
            .background(colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.originos_adaption_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 设备信息卡片
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
            ) {
                DeviceInfoCard()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 适配项列表（依据 refreshTick 重新计算状态）
            AdaptionItemsGroup(isVisible, refreshTick)

            Spacer(modifier = Modifier.height(12.dp))

            // 说明卡片
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 200)) + slideInVertically(tween(400, delayMillis = 200)) { it / 4 }
            ) {
                GuideTextCard()
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // 回到本页时刷新状态：监听 onResume，递增 refreshTick 重新读取各项权限状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

// ============================================================================
// 设备信息卡片
// ============================================================================

@Composable
private fun DeviceInfoCard() {
    val colorScheme = MaterialTheme.colorScheme
    val isOriginOS6 = RomUtils.isVivo && RomUtils.isOriginOS6OrAbove()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.VerifiedUser,
                null,
                Modifier.size(28.dp),
                tint = if (isOriginOS6) Color(0xFF07C160) else colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = RomUtils.getRomDisplayName(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface
                )
                Text(
                    text = if (isOriginOS6) {
                        stringResource(R.string.originos_adaption_detected_6)
                    } else {
                        stringResource(R.string.originos_adaption_detected_other)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// 适配项列表
// ============================================================================

@Composable
private fun AdaptionItemsGroup(isVisible: Boolean, refreshTick: Int) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    // 依赖 refreshTick，每次从设置页返回后强制重新读取状态
    val states = remember(refreshTick) { computeAdaptionStates(context) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400, delayMillis = 80)) + slideInVertically(tween(400, delayMillis = 80)) { it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            states.forEachIndexed { index, item ->
                AdaptionItemRow(item)
                if (index < states.size - 1) {
                    Box(
                        Modifier.fillMaxWidth()
                            .padding(start = 44.dp)
                            .height(0.5.dp)
                            .background(colorScheme.outline)
                    )
                }
            }
        }
    }
}

private data class AdaptionItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val configured: Boolean,
    val action: () -> Unit
)

private fun computeAdaptionStates(context: android.content.Context): List<AdaptionItem> {
    return buildList {
        // 1. 通知权限
        add(
            AdaptionItem(
                icon = Icons.Filled.Notifications,
                title = context.getString(R.string.originos_adaption_notification),
                subtitle = context.getString(R.string.originos_adaption_notification_desc),
                configured = NativePermissionRequester.hasAllRequiredPermissions(context),
                action = { BatteryOptimizationHelper.openNotificationSettings(context) }
            )
        )
        // 2. 忽略电池优化
        add(
            AdaptionItem(
                icon = Icons.Filled.BatteryFull,
                title = context.getString(R.string.originos_adaption_battery),
                subtitle = context.getString(R.string.originos_adaption_battery_desc),
                configured = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context),
                action = {
                    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    } else {
                        BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
                    }
                }
            )
        )
        // 3. 自启动
        add(
            AdaptionItem(
                icon = Icons.Filled.PowerSettingsNew,
                title = context.getString(R.string.originos_adaption_autostart),
                subtitle = context.getString(R.string.originos_adaption_autostart_desc),
                // 自启动无法用 API 准确判断，始终显示为"去设置"
                configured = false,
                action = { BatteryOptimizationHelper.openAutoStartSettings(context) }
            )
        )
        // 4. 后台高耗电（OriginOS 专属）
        add(
            AdaptionItem(
                icon = Icons.Filled.Bolt,
                title = context.getString(R.string.originos_adaption_background_power),
                subtitle = context.getString(R.string.originos_adaption_background_power_desc),
                configured = false,
                action = { OriginOSBatteryOptimizer.openBackgroundPowerSettings(context) }
            )
        )
        // 5. 后台弹出界面（OriginOS 专属）
        add(
            AdaptionItem(
                icon = Icons.Filled.Schedule,
                title = context.getString(R.string.originos_adaption_popup),
                subtitle = context.getString(R.string.originos_adaption_popup_desc),
                configured = false,
                action = { OriginOSBatteryOptimizer.openBackgroundPopupSettings(context) }
            )
        )
        // 6. 精确闹钟（前台服务超时后的兜底重启依赖）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            add(
                AdaptionItem(
                    icon = Icons.Outlined.Alarm,
                    title = context.getString(R.string.originos_adaption_exact_alarm),
                    subtitle = context.getString(R.string.originos_adaption_exact_alarm_desc),
                    configured = alarmManager.canScheduleExactAlarms(),
                    action = {
                        runCatching {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            // 兜底跳设置详情页
                            NativePermissionRequester.openAppDetailsSettings(context)
                        }
                    }
                )
            )
        }
    }
}

@Composable
private fun AdaptionItemRow(item: AdaptionItem) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = item.action).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, item.title, Modifier.size(24.dp), tint = Color(0xFF07C160))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    color = colorScheme.onSurface
                )
                if (item.configured) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        Modifier.size(16.dp),
                        tint = Color(0xFF07C160)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ============================================================================
// 说明卡片
// ============================================================================

@Composable
private fun GuideTextCard() {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, Modifier.size(20.dp), tint = colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.originos_adaption_guide_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            OriginOSBatteryOptimizer.getBatteryOptimizationGuideText(),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
            color = colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.originos_adaption_guide_footer),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colorScheme.onSurfaceVariant
        )
    }
}
