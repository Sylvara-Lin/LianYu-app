package com.lianyu.ai.feature.coffee.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianyu.ai.feature.coffee.data.model.OrderHistoryEntry
import kotlinx.coroutines.launch

// 瑞幸品牌色
private val LuckinBlue = Color(0xFF0066CC)
private val LuckinDarkBlue = Color(0xFF003D7A)
private val LuckinLightBlue = Color(0xFFE6F0FF)
private val LuckinRed = Color(0xFFE1251B)
private val LuckinGray = Color(0xFFF5F5F5)

/**
 * 瑞幸咖啡独立设置页。
 *
 * 路由：coffee/settings
 * 模板参考：GeneralSettingsScreen（Material3 TopAppBar + Scaffold + Card 分组）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeSettingsScreen(
    onBack: () -> Unit,
    onReplaceToken: () -> Unit,
    onQueryOrder: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: CoffeeViewModel = viewModel(factory = CoffeeViewModel.factory(context))
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearTokenDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var tokenSavedDays by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshTokenStatus()
        tokenSavedDays = viewModel.tokenSavedDaysAgo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("瑞幸设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LuckinBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LuckinGray)
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === Section 1: 账号 ===
            item {
                SettingsSectionHeader("账号")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = LuckinBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MCP Token", fontWeight = FontWeight.Bold, color = LuckinDarkBlue)
                                Text(
                                    if (uiState.isTokenConfigured) "已配置 · 保存于 $tokenSavedDays 天前"
                                    else "未配置",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onReplaceToken,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (uiState.isTokenConfigured) "替换 Token" else "配置 Token", color = LuckinBlue)
                            }
                            if (uiState.isTokenConfigured) {
                                OutlinedButton(
                                    onClick = { showClearTokenDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("清除", color = LuckinRed)
                                }
                            }
                        }
                    }
                }
            }

            // === Section 2: 订单历史 ===
            item {
                SettingsSectionHeader("订单历史")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = LuckinBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("最近 ${uiState.orderHistory.size} 条订单", fontWeight = FontWeight.Bold, color = LuckinDarkBlue)
                            Spacer(Modifier.weight(1f))
                            if (uiState.orderHistory.isNotEmpty()) {
                                TextButton(onClick = { showClearHistoryDialog = true }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = LuckinRed, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("清空", color = LuckinRed, fontSize = 12.sp)
                                }
                            }
                        }
                        if (uiState.orderHistory.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("暂无订单记录", fontSize = 13.sp, color = Color.Gray)
                        } else {
                            Spacer(Modifier.height(8.dp))
                            uiState.orderHistory.forEach { entry ->
                                OrderHistoryRow(entry = entry, onClick = { onQueryOrder(entry.orderIdStr) })
                            }
                        }
                    }
                }
            }

            // === Section 3: 关于 ===
            item {
                SettingsSectionHeader("关于")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = LuckinLightBlue)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("瑞幸 MCP 服务", fontWeight = FontWeight.Bold, color = LuckinDarkBlue)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Token 从 open.lkcoffee.com/mcp 登录获取，有效期约 30 天\n" +
                                "• 与瑞幸账号会话绑定，严禁泄露\n" +
                                "• 存储在应用私有目录，卸载后清除\n" +
                                "• 仅支持到店自取，不支持外送",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    // 清除 Token 确认
    if (showClearTokenDialog) {
        AlertDialog(
            onDismissRequest = { showClearTokenDialog = false },
            title = { Text("清除 Token") },
            text = { Text("清除后需重新从 open.lkcoffee.com/mcp 获取 Token 才能下单。确认清除？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearTokenDialog = false
                    scope.launch {
                        viewModel.clearToken()
                        tokenSavedDays = 0
                    }
                }) { Text("清除", color = LuckinRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearTokenDialog = false }) { Text("取消") }
            }
        )
    }

    // 清空订单历史确认
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("清空订单历史") },
            text = { Text("将清除本地保存的 ${uiState.orderHistory.size} 条订单记录，不影响瑞幸侧订单。确认？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    viewModel.clearOrderHistory()
                }) { Text("清空", color = LuckinRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        color = Color.Gray,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun OrderHistoryRow(entry: OrderHistoryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Coffee, contentDescription = null, tint = LuckinBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.deptName.ifBlank { "瑞幸订单" }, fontSize = 14.sp, color = LuckinDarkBlue, fontWeight = FontWeight.Medium)
            Text(
                "¥${"%.2f".format(entry.discountPrice)} · ${formatHistoryDate(entry.createdAt)}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
}

private fun formatHistoryDate(timestampMs: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
    return sdf.format(java.util.Date(timestampMs))
}
