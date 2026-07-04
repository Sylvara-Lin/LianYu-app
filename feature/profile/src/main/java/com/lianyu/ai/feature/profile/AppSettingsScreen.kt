package com.lianyu.ai.feature.profile

import android.app.Activity
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Token
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.FrameRateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lianyu.ai.uicommon.component.BackgroundPermissionsCard
import com.lianyu.ai.uicommon.theme.ThemeMode
import com.lianyu.ai.uicommon.theme.ThemeViewModel
import com.lianyu.ai.uicommon.theme.WeChatDarkBackground
import com.lianyu.ai.uicommon.theme.WeChatDarkCard
import com.lianyu.ai.uicommon.theme.WeChatDarkDivider
import com.lianyu.ai.uicommon.theme.WeChatDarkTextPrimary
import com.lianyu.ai.uicommon.theme.WeChatDarkTextSecondary
import com.lianyu.ai.uicommon.theme.WeChatLightBackground
import com.lianyu.ai.uicommon.theme.WeChatLightDivider
import com.lianyu.ai.uicommon.theme.WeChatLightTextPrimary
import com.lianyu.ai.uicommon.theme.WeChatLightTextSecondary
import androidx.compose.foundation.isSystemInDarkTheme

data class SettingsItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit,
    onLanguageClick: () -> Unit,
    onFrameRateClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onTtsSettingsClick: () -> Unit = {},
    onTokenUsageClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeViewModel: ThemeViewModel = viewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val backgroundColor = if (isDark) WeChatDarkBackground else WeChatLightBackground
    val cardColor = if (isDark) WeChatDarkCard else Color.White
    val textPrimary = if (isDark) WeChatDarkTextPrimary else WeChatLightTextPrimary
    val textSecondary = if (isDark) WeChatDarkTextSecondary else WeChatLightTextSecondary
    val dividerColor = if (isDark) WeChatDarkDivider else WeChatLightDivider

    val currentRate = FrameRateManager.getSavedFrameRate(context)
    val settingsStore = remember { AppSettingsStore(context) }
    val scope = rememberCoroutineScope()

    val showReasoning by settingsStore.showReasoningFlow.collectAsState(initial = false)
    val sendReasoning by settingsStore.sendReasoningFlow.collectAsState(initial = false)
    val autoCollapse by settingsStore.autoCollapseReasoningFlow.collectAsState(initial = true)
    val respField by settingsStore.reasoningResponseFieldFlow.collectAsState(initial = "reasoning_content")
    val reqField by settingsStore.reasoningRequestFieldFlow.collectAsState(initial = "reasoning_content")

    var showReasoningDialog by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
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
                            contentDescription = stringResource(R.string.back),
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.app_settings_title),
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
                .background(backgroundColor)
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 第一组：语言 + 帧率 + TTS
            SettingsGroup(
                items = listOf(
                    SettingsItemData(
                        icon = Icons.Filled.Language,
                        title = stringResource(R.string.language),
                        subtitle = stringResource(R.string.language_desc),
                        onClick = onLanguageClick
                    ),
                    SettingsItemData(
                        icon = Icons.Filled.Refresh,
                        title = stringResource(R.string.framerate),
                        subtitle = currentRate.label,
                        onClick = onFrameRateClick
                    ),
                    SettingsItemData(
                        icon = Icons.Filled.RecordVoiceOver,
                        title = "TTS 语音设置",
                        subtitle = "配置语音合成服务",
                        onClick = onTtsSettingsClick
                    ),
                    SettingsItemData(
                        icon = Icons.Filled.Token,
                        title = "Token 使用统计",
                        subtitle = "查看AI对话Token消耗情况",
                        onClick = onTokenUsageClick
                    )
                ),
                isDark = isDark,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 第二组：思考设置
            SettingsGroup(
                items = listOf(
                    SettingsItemData(
                        icon = Icons.Filled.Psychology,
                        title = "思考设置",
                        subtitle = if (showReasoning) "已启用思考过程显示" else "思考过程显示已关闭",
                        onClick = { showReasoningDialog = true }
                    )
                ),
                isDark = isDark,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 第三组：检查更新
            SettingsGroup(
                items = listOf(
                    SettingsItemData(
                        icon = Icons.Filled.SystemUpdate,
                        title = stringResource(R.string.check_new_version),
                        subtitle = stringResource(R.string.check_new_version_desc),
                        onClick = onCheckUpdateClick
                    )
                ),
                isDark = isDark,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 第四组：权限与后台运行
            BackgroundPermissionsCard(
                isVisible = isVisible,
                textPrimaryColor = textPrimary,
                textSecondaryColor = textSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showReasoningDialog) {
        var localShow by remember { mutableStateOf(showReasoning) }
        var localSend by remember { mutableStateOf(sendReasoning) }
        var localCollapse by remember { mutableStateOf(autoCollapse) }
        var localResp by remember { mutableStateOf(respField) }
        var localReq by remember { mutableStateOf(reqField) }

        AlertDialog(
            onDismissRequest = { showReasoningDialog = false },
            title = { Text("思考设置") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用思考过程显示")
                        Switch(
                            checked = localShow,
                            onCheckedChange = { localShow = it }
                        )
                    }
                    if (localShow) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("思考完成时自动折叠")
                            Switch(
                                checked = localCollapse,
                                onCheckedChange = { localCollapse = it }
                            )
                        }
                        OutlinedTextField(
                            value = localResp,
                            onValueChange = { localResp = it },
                            label = { Text("响应字段名") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        OutlinedTextField(
                            value = localReq,
                            onValueChange = { localReq = it },
                            label = { Text("请求字段名") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("发送思考内容")
                            Switch(
                                checked = localSend,
                                onCheckedChange = { localSend = it }
                            )
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
                    showReasoningDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReasoningDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SettingsGroup(
    items: List<SettingsItemData>,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color
) {
    val cardColor = if (isDark) WeChatDarkCard else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFF2F2F7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = textPrimary
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
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

            if (index < items.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 64.dp)
                        .height(0.5.dp)
                        .background(dividerColor)
                )
            }
        }
    }
}
