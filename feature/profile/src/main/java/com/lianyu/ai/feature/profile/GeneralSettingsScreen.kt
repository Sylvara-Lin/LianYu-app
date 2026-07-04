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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.FrameRateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 总设置页 — 收纳次要设置项，按功能领域分为 3 组。
 *
 *   1. 外观与对话  — 语言、帧率、思考设置
 *   2. 平台集成    — 微信、QQ机器人
 *   3. 系统与维护  — TTS、Token、更新、权限
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    onNavigateBack: () -> Unit,
    onLanguageClick: () -> Unit,
    onFrameRateClick: () -> Unit,
    onTtsSettingsClick: () -> Unit,
    onTokenUsageClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onWeChatClick: () -> Unit,
    onQQBotClick: () -> Unit,
    onDataBackupClick: () -> Unit,
    onOriginOSAdaptionClick: () -> Unit = {},
    onCoffeeClick: () -> Unit = {},
    onExperimentalFeaturesClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { delay(80); isVisible = true }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colorScheme.background).windowInsetsPadding(WindowInsets.statusBars),
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.general_settings), fontWeight = FontWeight.SemiBold) },
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

            // === 第一组：外观与对话 ===
            val currentFrameRate = FrameRateManager.getSavedFrameRate(context)
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.Language, stringResource(R.string.language), stringResource(R.string.language_desc), onLanguageClick),
                    MenuItemData(Icons.Filled.Refresh, stringResource(R.string.framerate), currentFrameRate.label, onFrameRateClick),
                    MenuItemData(Icons.Filled.Science, stringResource(R.string.experimental_features), stringResource(R.string.experimental_features_desc), onExperimentalFeaturesClick),
                    ThinkingSettingsEntry()
                ),
                isVisible = isVisible, delayMillis = 100
            )

            Spacer(modifier = Modifier.height(12.dp))

            // === 第二组：平台集成 ===
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.ChatBubble, stringResource(R.string.wechat_settings), stringResource(R.string.wechat_settings_desc), onWeChatClick),
                    MenuItemData(Icons.Filled.ChatBubble, stringResource(R.string.qqbot_settings), stringResource(R.string.qqbot_settings_desc), onQQBotClick),
                    MenuItemData(Icons.Filled.Coffee, stringResource(R.string.coffee_title), stringResource(R.string.coffee_desc), onCoffeeClick)
                ),
                isVisible = isVisible, delayMillis = 160
            )

            Spacer(modifier = Modifier.height(12.dp))

            // === 第三组：系统与维护 ===
            SolidMenuGroup(
                items = listOf(
                    MenuItemData(Icons.Filled.RecordVoiceOver, stringResource(R.string.tts_settings), stringResource(R.string.tts_settings_desc), onTtsSettingsClick),
                    MenuItemData(Icons.Filled.Token, stringResource(R.string.token_usage), stringResource(R.string.token_usage_desc), onTokenUsageClick),
                    MenuItemData(Icons.Filled.SystemUpdate, stringResource(R.string.check_new_version), stringResource(R.string.check_new_version_desc), onCheckUpdateClick),
                    MenuItemData(Icons.Filled.SaveAlt, stringResource(R.string.data_backup), stringResource(R.string.data_backup_desc), onDataBackupClick),
                    MenuItemData(Icons.Filled.Tune, stringResource(R.string.originos_adaption), stringResource(R.string.originos_adaption_desc), onOriginOSAdaptionClick)
                ),
                isVisible = isVisible, delayMillis = 220
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 权限管理卡片（内嵌在系统与维护组下方）
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 280)) + slideInVertically(tween(400, delayMillis = 280)) { it / 4 }
            ) {
                PermissionSettingsCard()
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ============================================================================
// 思考设置入口（带弹窗）
// ============================================================================

@Composable
private fun ThinkingSettingsEntry(): MenuItemData {
    val context = LocalContext.current
    val settingsStore = remember { AppSettingsStore(context) }
    val showReasoning by settingsStore.showReasoningFlow.collectAsState(initial = false)
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        ThinkingSettingsDialog(showDialog, settingsStore)
    }

    return MenuItemData(
        icon = Icons.Filled.Psychology,
        title = stringResource(R.string.thinking_settings),
        subtitle = if (showReasoning) stringResource(R.string.thinking_enabled) else stringResource(R.string.thinking_disabled),
        onClick = { showDialog.value = true }
    )
}

@Composable
private fun ThinkingSettingsDialog(showDialog: MutableState<Boolean>, settingsStore: AppSettingsStore) {
    val scope = rememberCoroutineScope()
    val showReasoning by settingsStore.showReasoningFlow.collectAsState(initial = false)
    val sendReasoning by settingsStore.sendReasoningFlow.collectAsState(initial = false)
    val autoCollapse by settingsStore.autoCollapseReasoningFlow.collectAsState(initial = true)
    val respField by settingsStore.reasoningResponseFieldFlow.collectAsState(initial = "reasoning_content")
    val reqField by settingsStore.reasoningRequestFieldFlow.collectAsState(initial = "reasoning_content")

    var localShow by remember { mutableStateOf(showReasoning) }
    var localSend by remember { mutableStateOf(sendReasoning) }
    var localCollapse by remember { mutableStateOf(autoCollapse) }
    var localResp by remember { mutableStateOf(respField) }
    var localReq by remember { mutableStateOf(reqField) }

    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text("思考设置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("启用思考过程显示"); Switch(checked = localShow, onCheckedChange = { localShow = it })
                }
                if (localShow) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("思考完成时自动折叠"); Switch(checked = localCollapse, onCheckedChange = { localCollapse = it })
                    }
                    OutlinedTextField(localResp, { localResp = it }, label = { Text("响应字段名") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    OutlinedTextField(localReq, { localReq = it }, label = { Text("请求字段名") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("发送思考内容"); Switch(checked = localSend, onCheckedChange = { localSend = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    settingsStore.setShowReasoning(localShow)
                    settingsStore.setAutoCollapseReasoning(localCollapse)
                    settingsStore.setReasoningResponseField(localResp)
                    settingsStore.setReasoningRequestField(localReq)
                    settingsStore.setSendReasoning(localSend)
                }
                showDialog.value = false
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { showDialog.value = false }) { Text("取消") } }
    )
}

// ============================================================================
// 权限管理卡片
// ============================================================================

@Composable
private fun PermissionSettingsCard() {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PermissionItem(stringResource(R.string.auto_start), stringResource(R.string.auto_start_desc)) {
            com.lianyu.ai.common.BatteryOptimizationHelper.openAutoStartSettings(context)
        }
        PermissionDivider()
        PermissionItem(stringResource(R.string.battery_whitelist), stringResource(R.string.battery_whitelist_desc)) {
            if (!com.lianyu.ai.common.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
                com.lianyu.ai.common.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            else
                com.lianyu.ai.common.BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
        }
        PermissionDivider()
        PermissionItem(stringResource(R.string.notification_permission), stringResource(R.string.notification_permission_desc)) {
            com.lianyu.ai.common.BatteryOptimizationHelper.openNotificationSettings(context)
        }
    }
}

@Composable
private fun PermissionItem(title: String, subtitle: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PowerSettingsNew, title, Modifier.size(24.dp), tint = Color(0xFF07C160))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp), color = colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PermissionDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 36.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outline))
}

// ============================================================================
// 权限管理卡片
// ============================================================================
