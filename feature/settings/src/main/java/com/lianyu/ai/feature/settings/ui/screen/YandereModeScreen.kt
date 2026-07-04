package com.lianyu.ai.feature.settings.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.YandereModeManager
import com.lianyu.ai.feature.settings.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 病娇模式设置页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YandereModeScreen(
    onNavigateBack: () -> Unit,
    yandereModeManager: YandereModeManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { AppSettingsStore(context) }
    val colorScheme = MaterialTheme.colorScheme

    val isEnabled by settingsStore.yandereModeEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val collectUsage by settingsStore.yandereModeUsageStatsFlow.collectAsStateWithLifecycle(initialValue = true)
    val collectInstalled by settingsStore.yandereModeInstalledAppsFlow.collectAsStateWithLifecycle(initialValue = true)
    val snapshot by yandereModeManager.cacheSnapshot.collectAsStateWithLifecycle()
    val isRefreshing by yandereModeManager.isRefreshing.collectAsStateWithLifecycle()

    var permissionCheckTick by remember { mutableIntStateOf(0) }
    val hasPermission = remember(permissionCheckTick) { yandereModeManager.canAccessUsageStats() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheckTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.yandere_mode),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = colorScheme.onSurface
                        )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 主开关
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.yandere_mode_enable),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    try {
                                        settingsStore.setYandereModeEnabled(enabled)
                                        if (enabled) {
                                            yandereModeManager.start()
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        // 静默降级
                                    }
                                }
                            }
                        )
                    }
                    Text(
                        text = stringResource(R.string.yandere_mode_enable_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            // 细分配置
            AnimatedSettingsGroup(isVisible = isEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.yandere_mode_usage_stats),
                            subtitle = stringResource(R.string.yandere_mode_usage_stats_desc),
                            checked = collectUsage,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsStore.setYandereModeUsageStats(enabled)
                                    if (isEnabled) yandereModeManager.requestRefresh(force = true)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colorScheme.outline.copy(alpha = 0.3f))
                        SettingSwitchRow(
                            title = stringResource(R.string.yandere_mode_installed_apps),
                            subtitle = stringResource(R.string.yandere_mode_installed_apps_desc),
                            checked = collectInstalled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsStore.setYandereModeInstalledApps(enabled)
                                    if (isEnabled) yandereModeManager.requestRefresh(force = true)
                                }
                            }
                        )
                    }
                }
            }

            // 权限状态
            AnimatedSettingsGroup(isVisible = isEnabled) {
                val permissionCardColor = if (hasPermission) colorScheme.primaryContainer else colorScheme.tertiaryContainer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = permissionCardColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (hasPermission) colorScheme.onPrimaryContainer else colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (hasPermission) stringResource(R.string.yandere_permission_granted) else stringResource(R.string.yandere_permission_required),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = stringResource(R.string.yandere_permission_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                        if (!hasPermission) {
                            Button(
                                onClick = { openUsageSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.yandere_go_grant_permission))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { openUsageSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.yandere_view_permission_settings))
                            }
                        }
                    }
                }
            }

            // 数据预览
            AnimatedSettingsGroup(isVisible = isEnabled && hasPermission && snapshot != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
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
                                text = stringResource(R.string.yandere_current_data),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                TextButton(onClick = { scope.launch { yandereModeManager.requestRefresh(force = true) } }) {
                                    Text(stringResource(R.string.yandere_refresh))
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.yandere_data_update_time, formatTime(snapshot!!.collectedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.yandere_installed_apps_count, snapshot!!.installedApps.size),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (snapshot!!.usageApps.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.yandere_top_usage_apps),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            snapshot!!.usageApps.take(5).forEach { app ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatDuration(app.totalTimeInForeground),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 功能说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.yandere_what_is),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.yandere_what_is_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                    HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
                    Text(
                        text = stringResource(R.string.yandere_what_data),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.yandere_what_data_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                    HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
                    Text(
                        text = stringResource(R.string.yandere_upload_question),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.yandere_upload_answer),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                }
            }

            // 实验性提示
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.yandere_experimental_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnimatedSettingsGroup(isVisible: Boolean, content: @Composable () -> Unit) {
    if (!isVisible) return
    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 4 }
    ) {
        content()
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openUsageSettings(context: android.content.Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
}
