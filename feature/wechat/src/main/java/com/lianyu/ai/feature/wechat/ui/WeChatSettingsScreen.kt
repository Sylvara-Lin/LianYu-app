package com.lianyu.ai.feature.wechat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeChatSettingsScreen(
    onNavigateBack: () -> Unit,
    onBindClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: WeChatViewModel = viewModel(factory = remember { WeChatViewModelFactory(context.applicationContext as android.app.Application) })
    val uiState by viewModel.uiState.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCompanionDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("微信设置", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Status Card
            WeChatStatusCard(
                isLoggedIn = uiState.isLoggedIn,
                accountId = uiState.account?.ilinkBotId,
                customName = uiState.customBotName,
                onBindClick = onBindClick,
                onUnbindClick = { showLogoutDialog = true },
                onRenameClick = { showRenameDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // FIXME(P2): 缺少主动消息频率配置 UI。
            //  需要增加：1) 是否启用亲密度动态调度 2) 自定义间隔分钟数输入
            //  详见 docs/wechat-known-issues.md #5
            // Settings Items
            Text(
                text = "功能设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SettingItem(
                icon = Icons.Outlined.Message,
                title = "消息通知",
                subtitle = "微信消息到达时推送通知",
                trailing = {
                    Switch(
                        checked = uiState.notifyEnabled,
                        onCheckedChange = { viewModel.toggleNotifyEnabled(it) }
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))

            SettingItem(
                icon = Icons.Outlined.Link,
                title = "自动回复",
                subtitle = "收到微信消息后自动调用 AI 回复",
                trailing = {
                    Switch(
                        checked = uiState.autoReply,
                        onCheckedChange = { viewModel.toggleAutoReply(it) }
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))

            SettingItem(
                icon = Icons.Outlined.Message,
                title = "消息转发",
                subtitle = "将 AI 消息同步发送到微信",
                trailing = {
                    Switch(
                        checked = uiState.forwardEnabled,
                        onCheckedChange = { viewModel.toggleForwardEnabled(it) }
                    )
                }
            )

            if (uiState.availableCompanions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))

                SettingItem(
                    icon = Icons.Outlined.Message,
                    title = "默认 AI 伴侣",
                    subtitle = uiState.availableCompanions.find { it.id == uiState.defaultCompanionId }?.name
                        ?: "未选择（使用第一个）",
                    trailing = {
                        TextButton(onClick = { showCompanionDialog = true }) {
                            Text("选择")
                        }
                    }
                )
            }

            if (uiState.userCompanionMappings.isNotEmpty() && uiState.availableCompanions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))

                Text(
                    text = "微信用户人设分配",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                uiState.userCompanionMappings.entries.forEachIndexed { index, (wechatUserId, companionId) ->
                    var showMappingDialog by remember { mutableStateOf(false) }
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    val mappedCompanionName = uiState.availableCompanions.find { it.id == companionId }?.name
                        ?: "未知 (ID: $companionId)"

                    SettingItem(
                        icon = Icons.Outlined.Link,
                        title = "用户 $wechatUserId",
                        subtitle = "人设: $mappedCompanionName",
                        trailing = {
                            Row {
                                TextButton(onClick = { showMappingDialog = true }) {
                                    Text("切换")
                                }
                                IconButton(onClick = { showDeleteConfirm = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "删除映射",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )

                    if (index < uiState.userCompanionMappings.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    if (showMappingDialog) {
                        AlertDialog(
                            onDismissRequest = { showMappingDialog = false },
                            title = { Text("为用户 $wechatUserId 选择 AI 伴侣") },
                            text = {
                                Column {
                                    uiState.availableCompanions.forEach { companion ->
                                        TextButton(
                                            onClick = {
                                                viewModel.setUserCompanionMapping(wechatUserId, companion.id)
                                                showMappingDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                companion.name,
                                                color = if (companion.id == companionId)
                                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { showMappingDialog = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("删除映射") },
                            text = { Text("确定要删除用户 $wechatUserId 的人设映射吗？删除后将使用默认 AI 伴侣。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.removeUserCompanionMapping(wechatUserId)
                                    showDeleteConfirm = false
                                }) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info
            Text(
                text = "说明",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Text(
                    text = "• 基于腾讯 ilink 协议，仅支持私聊\n" +
                           "• 绑定后请用微信给机器人发消息以激活会话\n" +
                           "• Token 有效期约数天，过期后需重新绑定",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("解除绑定") },
            text = { Text("确定要解除微信绑定吗？解除后将无法通过微信接收消息。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout()
                    showLogoutDialog = false
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCompanionDialog) {
        AlertDialog(
            onDismissRequest = { showCompanionDialog = false },
            title = { Text("选择默认 AI 伴侣") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.setDefaultCompanionId(null)
                            showCompanionDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "自动分配（使用第一个）",
                            color = if (uiState.defaultCompanionId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    uiState.availableCompanions.forEach { companion ->
                        TextButton(
                            onClick = {
                                viewModel.setDefaultCompanionId(companion.id)
                                showCompanionDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                companion.name,
                                color = if (companion.id == uiState.defaultCompanionId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCompanionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(uiState.customBotName ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("设置机器人名字") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCustomBotName(newName.takeIf { it.isNotBlank() })
                    showRenameDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WeChatStatusCard(
    isLoggedIn: Boolean,
    accountId: String?,
    customName: String?,
    onBindClick: () -> Unit,
    onUnbindClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isLoggedIn) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isLoggedIn) Icons.Filled.CheckCircle else Icons.Outlined.Warning,
            contentDescription = null,
            tint = if (isLoggedIn) Color(0xFF4CAF50) else Color(0xFFFFA000),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isLoggedIn) {
                customName ?: "微信已绑定"
            } else {
                "未绑定微信"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isLoggedIn && !accountId.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ID: $accountId",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isLoggedIn) {
                TextButton(
                    onClick = onRenameClick,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "改名",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = onUnbindClick,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "解除绑定",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                TextButton(
                    onClick = onBindClick,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "立即绑定",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        trailing()
    }
}
