package com.lianyu.ai.feature.chat.ui.screen

import android.app.Application
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lianyu.ai.feature.chat.R
import com.lianyu.ai.feature.chat.data.ChatDetailSettingsStore

import com.lianyu.ai.feature.chat.ui.viewmodel.ChatViewModel
import com.lianyu.ai.feature.chat.ui.viewmodel.ChatViewModelFactory
import com.lianyu.ai.uicommon.component.ChatBackgroundPickerDialog
import com.lianyu.ai.uicommon.component.getChatBackgroundKey
import com.lianyu.ai.common.AppSettingsStore

@Composable
fun ChatDetailScreen(
    companionId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ChatDetailSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext as Application, companionId)
    )
    val companionData by viewModel.companionData.collectAsState()
    val settings by store.settingsFlow(companionId).collectAsState(initial = com.lianyu.ai.feature.chat.data.CompanionChatDetailSettings())
    val appSettingsStore = remember { AppSettingsStore(context) }
    var innerThoughtEnabled by remember { mutableStateOf(false) }

    // 读取心理活动开关状态
    LaunchedEffect(Unit) {
        innerThoughtEnabled = appSettingsStore.getInnerThoughtEnabled()
    }

    var showBgPicker by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    text = "聊天详情",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }

            // Companion info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (companionData?.avatarUrl != null) {
                        AsyncImage(
                            model = companionData?.avatarUrl,
                            contentDescription = companionData?.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = companionData?.name?.firstOrNull()?.toString() ?: "?",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = companionData?.name ?: "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!companionData?.personality.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = companionData?.personality ?: "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                // Status tags
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (settings.blocked) {
                        StatusTag("已拉黑", Color(0xFFFF3B30))
                    } else if (settings.doNotDisturbEnabled) {
                        StatusTag("免打扰", Color(0xFFFF9500))
                    } else if (!settings.proactiveEnabled) {
                        StatusTag("主动消息关闭", MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        StatusTag("正常", Color(0xFF34C759))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chat appearance section
            SectionTitle("聊天外观")
            SettingsCard {
                SettingsRow(title = "当前聊天背景", subtitle = backgroundName(settings.backgroundKey, context)) {
                    showBgPicker = true
                }
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "使用全局背景",
                    checked = settings.useGlobalBackground,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(useGlobalBackground = checked) }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Proactive message section
            SectionTitle("主动消息")
            SettingsCard {
                SettingsToggleRow(
                    title = "允许主动发消息",
                    checked = settings.proactiveEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(proactiveEnabled = checked) }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(title = "主动消息间隔", subtitle = intervalLabel(settings.proactiveIntervalMinutes)) {
                    showIntervalDialog = true
                }
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "允许主动开启新话题",
                    checked = settings.allowNewTopic,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(allowNewTopic = checked) }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "允许深夜消息",
                    checked = settings.allowLateNightMessage,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(allowLateNightMessage = checked) }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "允许连续追问",
                    checked = settings.allowFollowUpMessage,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(allowFollowUpMessage = checked) }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "显示心理活动",
                    subtitle = "AI回复中包含（脸红）（开心）等内心描写",
                    checked = innerThoughtEnabled,
                    onCheckedChange = { checked ->
                        innerThoughtEnabled = checked
                        scope.launch {
                            appSettingsStore.setInnerThoughtEnabled(checked)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "精确时间感知",
                    subtitle = "通过NTP网络校时获取精确时间，避免设备时钟不准",
                    checked = settings.ntpTimeEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(ntpTimeEnabled = checked) }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sticker section
            SectionTitle("表情包")
            SettingsCard {
                SettingsSliderRow(
                    title = "AI发送表情包概率",
                    subtitle = "${settings.stickerProbability}%",
                    value = settings.stickerProbability / 100f,
                    onValueChange = { value ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(stickerProbability = (value * 100).toInt()) }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Do not disturb section
            SectionTitle("免打扰")
            SettingsCard {
                SettingsToggleRow(
                    title = "免打扰",
                    checked = settings.doNotDisturbEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(doNotDisturbEnabled = checked) }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "夜间免打扰 (23:00-08:00)",
                    checked = settings.dndStartMinutes == 23 * 60 && settings.dndEndMinutes == 8 * 60,
                    onCheckedChange = { checked ->
                        scope.launch {
                            store.updateSettings(companionId) {
                                if (checked) it.copy(dndStartMinutes = 23 * 60, dndEndMinutes = 8 * 60)
                                else it.copy(dndStartMinutes = 0, dndEndMinutes = 0)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Relationship section
            SectionTitle("关系与隐私")
            SettingsCard {
                if (settings.blocked) {
                    DangerRow(title = "取消拉黑", color = Color(0xFF34C759)) {
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(blocked = false) }
                        }
                    }
                } else {
                    DangerRow(title = "拉黑", color = Color(0xFFFF3B30)) {
                        showBlockConfirm = true
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                DangerRow(title = "清空聊天记录", color = Color(0xFFFF3B30)) {
                    showClearConfirm = true
                }
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                DangerRow(title = "重置聊天设置", color = Color(0xFFFF9500)) {
                    showResetConfirm = true
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Background picker dialog
    if (showBgPicker) {
        val currentKey = if (settings.useGlobalBackground) getChatBackgroundKey(context) else (settings.backgroundKey ?: "default")
        ChatBackgroundPickerDialog(
            currentKey = currentKey,
            onDismiss = { showBgPicker = false },
            onSelect = { key ->
                scope.launch {
                    store.updateSettings(companionId) { it.copy(backgroundKey = key, useGlobalBackground = false) }
                }
                showBgPicker = false
            }
        )
    }

    // Block confirm dialog
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("确认拉黑") },
            text = { Text("拉黑后对方将不再主动发消息。你仍可以手动发送消息。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            store.updateSettings(companionId) { it.copy(blocked = true) }
                        }
                        showBlockConfirm = false
                    }
                ) { Text("拉黑", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text("取消") }
            }
        )
    }

    // Clear messages confirm dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("确认清空") },
            text = { Text("清空后将无法恢复，确定要清空聊天记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.clearChatHistory()
                        }
                        showClearConfirm = false
                    }
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    // Reset settings confirm dialog
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("确认重置") },
            text = { Text("所有聊天设置将恢复默认值，包括背景、主动消息、免打扰等。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            store.resetSettings(companionId)
                        }
                        showResetConfirm = false
                    }
                ) { Text("重置", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }

    // Interval input dialog
    if (showIntervalDialog) {
        IntervalInputDialog(
            currentMinutes = settings.proactiveIntervalMinutes,
            onDismiss = { showIntervalDialog = false },
            onConfirm = { minutes ->
                scope.launch {
                    store.updateSettings(companionId) { it.copy(proactiveIntervalMinutes = minutes) }
                }
                showIntervalDialog = false
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(text = "›", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            valueRange = 0f..1f,
            steps = 9,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun DangerRow(title: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 15.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun backgroundName(key: String?, context: android.content.Context): String {
    return when (key) {
        null, "default" -> "默认白色"
        "warm_pink" -> "暖粉色"
        "lavender" -> "薰衣草"
        "ocean" -> "海洋蓝"
        "forest" -> "森林绿"
        "sunset" -> "日落橙"
        "night" -> "夜空"
        else -> if (key.startsWith("custom_")) "自定义图片" else "未知"
    }
}

private fun intervalLabel(minutes: Int): String {
    return when {
        minutes < 60 -> "$minutes 分钟"
        minutes % 60 == 0 -> "${minutes / 60} 小时"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
    }
}

@Composable
private fun IntervalInputDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var inputText by remember { mutableStateOf(currentMinutes.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "主动消息间隔",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "设置AI主动发消息的最小间隔时间（分钟）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { input ->
                        inputText = input
                        errorText = null
                    },
                    label = { Text("间隔（分钟）") },
                    suffix = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "范围：30~1440 分钟（0.5~24 小时），当前：${intervalLabel(currentMinutes)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = inputText.toIntOrNull()
                    if (minutes == null) {
                        errorText = "请输入有效数字"
                    } else if (minutes < 30) {
                        errorText = "最小间隔30分钟"
                    } else if (minutes > 1440) {
                        errorText = "最大间隔1440分钟（24小时）"
                    } else {
                        onConfirm(minutes)
                    }
                }
            ) { Text("确定", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
