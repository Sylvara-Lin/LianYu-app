package com.lianyu.ai.feature.settings.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ApiProvider
import com.lianyu.ai.feature.settings.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import com.lianyu.ai.common.SecureLog

// ============================================================
// Petal Color Constants
// ============================================================

internal val PetalBackgroundStart = Color(0xFFFBF9F8)
internal val PetalBackgroundEnd = Color(0xFFFFF0F3)
internal val PetalPrimary = Color(0xFF894C5C)
internal val PetalPrimaryContainer = Color(0xFFF4A7B9)
internal val PetalOnPrimaryContainer = Color(0xFF733949)
internal val PetalSurface = Color(0xFFFFFFFF)
internal val PetalSurfaceContainer = Color(0xFFEFEDED)
internal val PetalSurfaceContainerLow = Color(0xFFF5F3F3)
internal val PetalSecondaryContainer = Color(0xFFEBDCDF)
internal val PetalOnSurface = Color(0xFF1B1C1C)
internal val PetalOnSurfaceVariant = Color(0xFF524346)
internal val PetalOutlineVariant = Color(0xFFD6C1C5)
internal val PetalError = Color(0xFFBA1A1A)
internal val PetalErrorContainer = Color(0xFFFFDAD6)
internal val PetalGreen = Color(0xFF10A37F)
internal val PetalGreenLight = Color(0xFFE8F5E9)
internal val PetalOrange = Color(0xFFFFA726)

// ═══ 自定义中继 error code mapping ═══
private fun mapErrorCode(code: String): String = when (code) {
    "upstream_unreachable" -> "上游不通"
    "account_blocked" -> "已冻结"
    "key_disabled" -> "密钥已禁用"
    "network_error" -> "网络不通"
    "timeout" -> "超时"
    else -> "失败"
}

// ============================================================
// PetalStatChip - 状态标签小组件
// ============================================================

@Composable
fun PetalStatChip(icon: String, text: String, color: Color, isDarkTheme: Boolean) {
    val bgColor = if (isDarkTheme) color.copy(alpha = 0.12f) else PetalSurfaceContainerLow

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = icon,
                color = color,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================
// ApiConfigEditDialog - 内联 API 配置编辑弹窗
// ============================================================

@Composable
private fun PetalApiConfigEditDialog(
    config: ApiConfig,
    connectionResult: SettingsViewModel.ConnectionResult,
    onDismiss: () -> Unit,
    onSave: (ApiConfig) -> Unit,
    onTest: (ApiConfig) -> Unit,
    isDarkTheme: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    onFetchModels: ((String, String, Boolean) -> Unit)? = null,
    availableModels: List<String> = emptyList(),
    modelFetchState: SettingsViewModel.ModelFetchState = SettingsViewModel.ModelFetchState()
) {
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline
    val textTertiary = MaterialTheme.colorScheme.outlineVariant

    var apiKey by remember { mutableStateOf(config.apiKey) }
    var extraApiKeys by remember { mutableStateOf(config.extraApiKeys) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    var temperature by remember { mutableFloatStateOf(config.temperature) }
    var maxTokens by remember { mutableStateOf(config.maxTokens?.toString() ?: "") }
    var showModelDropdown by remember { mutableStateOf(false) }
    var skipCertVerify by remember { mutableStateOf(config.skipCertVerify) }
    var formatHint by remember { mutableStateOf(config.formatHint) }
    var lastFetchedParams by remember { mutableStateOf("") }
    val context = LocalContext.current

    val isPartner = config.provider == ApiProvider.PARTNER
    val isCustom = config.provider == ApiProvider.CUSTOM
    val isValid = apiKey.isNotBlank() || baseUrl.isNotBlank() || isPartner
    val hasModels = availableModels.isNotEmpty()

    val selectedModelText = when {
        modelFetchState.isLoading -> "正在获取模型列表..."
        hasModels -> model.ifEmpty { "请选择模型" }
        isPartner && apiKey.isBlank() -> "密钥将从服务器自动获取，直接点击测试"
        else -> "填写密钥后自动拉取模型"
    }

    LaunchedEffect(apiKey, baseUrl, config.provider) {
        val fetchParams = baseUrl.trim() + "|" + apiKey.trim()
        // PARTNER：baseUrl 非空即可自动拉取（密钥可空，由服务器下发）
        // CUSTOM：baseUrl 和 apiKey 都非空才自动拉取
        val shouldFetch = baseUrl.isNotBlank() &&
                (isPartner || (isCustom && apiKey.isNotBlank())) &&
                fetchParams != lastFetchedParams
        if (shouldFetch) {
            delay(600)
            lastFetchedParams = fetchParams
            val keyToUse = apiKey.trim()
            onFetchModels?.invoke(baseUrl, keyToUse, skipCertVerify)
        }
    }

    LaunchedEffect(availableModels, config.provider) {
        if ((isPartner || isCustom) && availableModels.isNotEmpty()) {
            model = if (isPartner) {
                // [FIX] PARTNER 始终本地随机，避免 server randomModel 固定导致每次相同
                val serverModel = com.lianyu.ai.common.RemoteKeyProvider.getRandomModel(context)
                    ?.takeIf { it.isNotBlank() && availableModels.contains(it) }
                val chosenModel = if (availableModels.size > 1) {
                    com.lianyu.ai.network.AiService.familyBalancedRandom(availableModels)
                } else {
                    serverModel ?: availableModels.first()
                }
                SecureLog.d("PetalApiCards", "PARTNER auto-select model: chosen=$chosenModel, server=$serverModel, available=${availableModels.size}")
                chosenModel
            } else {
                // CUSTOM: auto-select first only when model is blank
                if (model.isBlank()) availableModels.first() else model
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBackground,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "${config.provider.displayName} 配置",
                color = textPrimaryColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PARTNER / 自定义中继: 简化界面，仅显示连接状态
                if (isPartner) {
                    connectionResult.errorMessage?.let { error ->
                        Text(text = error, fontSize = 12.sp, color = PetalError)
                    }
                    if (connectionResult.status == SettingsViewModel.ConnectionStatus.CONNECTED) {
                        Text(
                            text = "自定义中继 已连接",
                            color = PetalGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (connectionResult.status == SettingsViewModel.ConnectionStatus.TESTING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = PetalPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在测试连接...", fontSize = 14.sp, color = textSecondaryColor)
                        }
                    } else {
                        Text(
                            text = "点击下方「测试」验证 自定义中继 连接",
                            color = textSecondaryColor,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!isPartner) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（主）", color = textSecondaryColor) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PetalPrimary,
                        unfocusedBorderColor = dividerColor,
                        focusedContainerColor = cardBackground,
                        unfocusedContainerColor = cardBackground,
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = extraApiKeys,
                    onValueChange = { extraApiKeys = it },
                    label = { Text("备用 API Key（逗号分隔，轮询切换）", color = textSecondaryColor) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PetalPrimary,
                        unfocusedBorderColor = dividerColor,
                        focusedContainerColor = cardBackground,
                        unfocusedContainerColor = cardBackground,
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    placeholder = {
                        Text("sk-xxx,sk-xxx,...", color = textTertiary, fontSize = 12.sp)
                    }
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL", color = textSecondaryColor) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PetalPrimary,
                        unfocusedBorderColor = dividerColor,
                        focusedContainerColor = cardBackground,
                        unfocusedContainerColor = cardBackground,
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true
                )

                if (hasModels && onFetchModels != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedModelText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model", color = textSecondaryColor) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showModelDropdown = !showModelDropdown },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PetalPrimary,
                                unfocusedBorderColor = dividerColor,
                                focusedContainerColor = cardBackground,
                                unfocusedContainerColor = cardBackground,
                                focusedTextColor = textPrimaryColor,
                                unfocusedTextColor = textPrimaryColor
                            ),
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showModelDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "展开",
                                    modifier = Modifier.clickable { showModelDropdown = !showModelDropdown },
                                    tint = textSecondaryColor
                                )
                            },
                            singleLine = true
                        )
                        if (!modelFetchState.isLoading) {
                            DropdownMenu(
                                expanded = showModelDropdown,
                                onDismissRequest = { showModelDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                availableModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m, color = textPrimaryColor, fontSize = 14.sp) },
                                        onClick = {
                                            model = m
                                            showModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (modelFetchState.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = PetalPrimary
                        )
                        Text(
                            text = "正在获取模型列表...",
                            fontSize = 12.sp,
                            color = textSecondaryColor
                        )
                    }

                    modelFetchState.errorMessage?.let { error ->
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = PetalError
                        )
                    }

                    if (hasModels && !modelFetchState.isLoading) {
                        Button(
                            onClick = {
                                // 重新获取模型列表时清空已选模型，触发自动重新随机
                                model = ""
                                lastFetchedParams = ""
                                val keyToUse = apiKey.trim().ifBlank {
                                    ApiConfig.BUILTIN_KEYS[ApiProvider.PARTNER]?.firstOrNull() ?: ""
                                }
                                onFetchModels?.invoke(baseUrl, keyToUse, skipCertVerify)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PetalPrimary.copy(alpha = 0.15f),
                                contentColor = PetalPrimary
                            )
                        ) {
                            Text(if (hasModels) "重新获取模型列表" else "获取模型列表", fontSize = 13.sp)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model", color = textSecondaryColor) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PetalPrimary,
                            unfocusedBorderColor = dividerColor,
                            focusedContainerColor = cardBackground,
                            unfocusedContainerColor = cardBackground,
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true
                    )
                }
                } // end !isPartner (fields)

                // Temperature — 所有 Provider 可见
                Text(
                    text = "Temperature: ${String.format("%.1f", temperature)}",
                    color = textPrimaryColor,
                    fontSize = 14.sp
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = PetalPrimary,
                        activeTrackColor = PetalPrimary
                    )
                )

                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                    label = { Text("Max Tokens", color = textSecondaryColor) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PetalPrimary,
                        unfocusedBorderColor = dividerColor,
                        focusedContainerColor = cardBackground,
                        unfocusedContainerColor = cardBackground,
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true
                )

                // API 格式选择 — 仅对自定义 API 显示
                if (isCustom) {
                    var showFormatDropdown by remember { mutableStateOf(false) }
                    val formatOptions = mapOf(
                        "openai" to "OpenAI 兼容",
                        "anthropic" to "Anthropic 兼容"
                    )
                    val selectedFormatText = formatOptions[formatHint] ?: "OpenAI 兼容"

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedFormatText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("API 格式", color = textSecondaryColor) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFormatDropdown = !showFormatDropdown },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PetalPrimary,
                                unfocusedBorderColor = dividerColor,
                                focusedContainerColor = cardBackground,
                                unfocusedContainerColor = cardBackground,
                                focusedTextColor = textPrimaryColor,
                                unfocusedTextColor = textPrimaryColor
                            ),
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showFormatDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "展开",
                                    modifier = Modifier.clickable { showFormatDropdown = !showFormatDropdown },
                                    tint = textSecondaryColor
                                )
                            },
                            singleLine = true
                        )
                        DropdownMenu(
                            expanded = showFormatDropdown,
                            onDismissRequest = { showFormatDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            formatOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = textPrimaryColor, fontSize = 14.sp) },
                                    onClick = {
                                        formatHint = key
                                        showFormatDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 跳过证书验证 — 仅对非 PARTNER 的 provider 显示（PARTNER 始终固定证书）
                if (!isPartner) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "跳过证书验证",
                                color = textPrimaryColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "⚠️ 仅限自托管/内网服务器，开启后不再验证 SSL 证书",
                                color = PetalOrange,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = skipCertVerify,
                            onCheckedChange = { skipCertVerify = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PetalOrange,
                                checkedTrackColor = PetalOrange.copy(alpha = 0.3f),
                                uncheckedThumbColor = dividerColor,
                                uncheckedTrackColor = dividerColor.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val currentConfig = if (isPartner) config else config.copy(
                            apiKey = apiKey.trim(),
                            extraApiKeys = extraApiKeys.trim(),
                            baseUrl = baseUrl.trim(),
                            model = model.trim(),
                            temperature = temperature,
                            maxTokens = maxTokens.toIntOrNull(),
                            skipCertVerify = skipCertVerify,
                            formatHint = formatHint
                        )
                        onTest(currentConfig)
                    },
                    enabled = isValid && connectionResult.status != SettingsViewModel.ConnectionStatus.TESTING,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PetalGreenLight,
                        contentColor = PetalGreen
                    )
                ) {
                    if (connectionResult.status == SettingsViewModel.ConnectionStatus.TESTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = PetalGreen,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isPartner) "验证 自定义中继 连接" else "测试")
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            if (isPartner) config else config.copy(
                                apiKey = apiKey.trim(),
                                extraApiKeys = extraApiKeys.trim(),
                                baseUrl = baseUrl.trim(),
                                model = model.trim(),
                                temperature = temperature,
                                maxTokens = maxTokens.toIntOrNull(),
                                skipCertVerify = skipCertVerify,
                                formatHint = formatHint
                            )
                        )
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = PetalPrimary)
                ) {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = textSecondaryColor
                )
            ) {
                Text("取消")
            }
        }
    )
}

// ============================================================
// PetalApiCard - 主要 API 配置卡片 (PARTNER)
// ============================================================

@Composable
fun PetalApiCard(
    config: ApiConfig,
    connectionResult: SettingsViewModel.ConnectionResult,
    isExpanded: Boolean,
    isActive: Boolean,
    onExpandToggle: () -> Unit,
    onEdit: (ApiConfig) -> Unit,
    onTest: (ApiConfig) -> Unit,
    onToggleEnabled: () -> Unit,
    onSelectActive: () -> Unit,
    onFetchModels: (String, String, String, Boolean) -> Unit,
    fetchedModels: Map<String, List<String>>,
    modelFetchStates: Map<String, SettingsViewModel.ModelFetchState>,
    testedConfigs: Map<String, ApiConfig>,
    connectionKey: String,
    isDarkTheme: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    balanceInfo: com.lianyu.ai.network.AiService.BalanceInfo? = null,
    balanceQueryFailed: Boolean = false,
    onQueryBalance: (() -> Unit)? = null
) {
    var editingConfig by remember { mutableStateOf<ApiConfig?>(null) }
    val cardColor = if (isDarkTheme) PetalPrimaryContainer.copy(alpha = 0.15f) else PetalSurface

    // 余额查询：连接成功后才自动查一次
    var balanceQueried by remember { mutableStateOf(false) }
    val isConnected = connectionResult.status == SettingsViewModel.ConnectionStatus.CONNECTED
    LaunchedEffect(isConnected) {
        if (isConnected && !balanceQueried) {
            onQueryBalance?.invoke()
            balanceQueried = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onExpandToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.provider.displayName,
                        color = textPrimaryColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (config.name.isNotEmpty()) {
                        Text(
                            text = config.name,
                            color = textSecondaryColor,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Connection status indicator
                    val statusColor = when (connectionResult.status) {
                        SettingsViewModel.ConnectionStatus.CONNECTED -> PetalGreen
                        SettingsViewModel.ConnectionStatus.FAILED -> PetalError
                        SettingsViewModel.ConnectionStatus.TESTING -> PetalOrange
                        SettingsViewModel.ConnectionStatus.UNKNOWN -> textSecondaryColor
                    }
                    val statusText = when (connectionResult.status) {
                        SettingsViewModel.ConnectionStatus.CONNECTED ->
                            if (connectionResult.latencyMs > 0) "已连接 ${connectionResult.latencyMs}ms" else "已连接"
                        SettingsViewModel.ConnectionStatus.FAILED -> {
                            val err = connectionResult.errorCode
                            if (err != null) mapErrorCode(err) else "失败"
                        }
                        SettingsViewModel.ConnectionStatus.TESTING -> "测试中"
                        SettingsViewModel.ConnectionStatus.UNKNOWN -> "未测试"
                    }
                    PetalStatChip(
                        icon = "\u25CF",
                        text = statusText,
                        color = statusColor,
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = textSecondaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Model info
                if (config.provider == ApiProvider.PARTNER) {
                    Text("模型: 自动分配", color = PetalGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (connectionResult.status == SettingsViewModel.ConnectionStatus.CONNECTED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column {
                            Text("延迟: ${connectionResult.latencyMs}ms | 状态: 已连接",
                                color = PetalGreen, fontSize = 12.sp)
                            connectionResult.clientId?.let { cid ->
                                Text("Client ID: $cid",
                                    color = PetalGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                            connectionResult.groupName?.let { gn ->
                                Text("组: $gn | 剩余: $${String.format("%.2f", connectionResult.remainingQuota)}",
                                    color = PetalGreen.copy(alpha = 0.8f), fontSize = 11.sp)
                            }
                            if (connectionResult.rpmLimit > 0) {
                                Text("RPM: ${connectionResult.rpmLimit} | 日限额: $${connectionResult.dailyLimit ?: "∞"}",
                                    color = PetalGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    } else if (connectionResult.status == SettingsViewModel.ConnectionStatus.FAILED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val errCode = connectionResult.errorCode ?: "unknown"
                        val errLabel = mapErrorCode(errCode)
                        Column {
                            Text("$errLabel | error=${errCode} | latency=${connectionResult.latencyMs}ms",
                                color = PetalError, fontSize = 12.sp)
                            connectionResult.clientId?.let { cid ->
                                Text("Client ID: $cid",
                                    color = PetalError.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                            connectionResult.errorMessage?.let { msg ->
                                Text(msg, color = PetalError.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (config.model.isNotEmpty()) {
                    Text(
                        text = "模型: ${config.model}",
                        color = textSecondaryColor,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Base URL
                if (config.baseUrl.isNotEmpty() && config.provider != ApiProvider.PARTNER) {
                    Text(
                        text = "地址: ${config.baseUrl}",
                        color = textSecondaryColor.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Balance info
                if (balanceInfo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PetalSurfaceContainerLow)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "全服余额",
                            color = textSecondaryColor,
                            fontSize = 12.sp
                        )
                        if (balanceInfo.remainingBalance != null) {
                            val bal = balanceInfo.remainingBalance
                            Text(
                                text = "$${"%.2f".format(bal)}",
                                color = PetalGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "查询中...",
                                color = textSecondaryColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (balanceQueryFailed) {
                    Text(
                        text = "余额查询失败",
                        color = PetalError,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        editingConfig = testedConfigs[connectionKey] ?: config
                    }) {
                        Text("编辑", color = PetalPrimary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { onTest(config) }) {
                        Text("测试", color = PetalPrimary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onSelectActive) {
                        Text("设为活跃", color = PetalPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // 编辑弹窗 - INLINE inside PetalApiCard
    editingConfig?.let { editing ->
        val providerModels = fetchedModels[editing.provider.name] ?: emptyList()
        val fetchState = modelFetchStates[editing.provider.name] ?: SettingsViewModel.ModelFetchState()
        PetalApiConfigEditDialog(
            config = editing,
            connectionResult = connectionResult,
            onDismiss = { editingConfig = null },
            onSave = { updated: ApiConfig ->
                onEdit(updated)
                editingConfig = null
            },
            onTest = { testConfig: ApiConfig -> onTest(testConfig) },
            isDarkTheme = isDarkTheme,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            onFetchModels = { baseUrl: String, apiKey: String, skipCertVerify: Boolean ->
                onFetchModels(baseUrl, apiKey, editing.provider.name, skipCertVerify)
            },
            availableModels = providerModels,
            modelFetchState = fetchState
        )
    }
}

// ============================================================
// PetalSavedApiCard - 已保存的其他 API 配置卡片
// ============================================================

@Composable
fun PetalSavedApiCard(
    config: ApiConfig,
    connectionResult: SettingsViewModel.ConnectionResult,
    isExpanded: Boolean,
    isActive: Boolean,
    onExpandToggle: () -> Unit,
    onEdit: (ApiConfig) -> Unit,
    onDelete: () -> Unit,
    onTest: (ApiConfig) -> Unit,
    onToggleEnabled: () -> Unit,
    onSelectActive: () -> Unit,
    onFetchModels: (String, String, String, Boolean) -> Unit,
    fetchedModels: Map<String, List<String>>,
    modelFetchStates: Map<String, SettingsViewModel.ModelFetchState>,
    testedConfigs: Map<String, ApiConfig>,
    connectionKey: String,
    isDarkTheme: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    balanceInfo: com.lianyu.ai.network.AiService.BalanceInfo? = null,
    balanceQueryFailed: Boolean = false,
    onQueryBalance: (() -> Unit)? = null
) {
    var editingConfig by remember { mutableStateOf<ApiConfig?>(null) }
    val cardColor = if (isDarkTheme) PetalPrimaryContainer.copy(alpha = 0.08f) else PetalSurfaceContainer

    // 余额查询：连接成功后才自动查一次
    var balanceQueried by remember { mutableStateOf(false) }
    val isConnected = connectionResult.status == SettingsViewModel.ConnectionStatus.CONNECTED
    LaunchedEffect(isConnected) {
        if (isConnected && !balanceQueried) {
            onQueryBalance?.invoke()
            balanceQueried = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onExpandToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.provider.displayName,
                        color = textPrimaryColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (config.name.isNotEmpty()) {
                        Text(
                            text = config.name,
                            color = textSecondaryColor,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Active indicator
                    if (isActive) {
                        PetalStatChip(
                            icon = "\u2713",
                            text = "活跃",
                            color = PetalGreen,
                            isDarkTheme = isDarkTheme
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Connection status indicator
                    val statusColor = when (connectionResult.status) {
                        SettingsViewModel.ConnectionStatus.CONNECTED -> PetalGreen
                        SettingsViewModel.ConnectionStatus.FAILED -> PetalError
                        SettingsViewModel.ConnectionStatus.TESTING -> PetalOrange
                        SettingsViewModel.ConnectionStatus.UNKNOWN -> textSecondaryColor
                    }
                    val statusText = when (connectionResult.status) {
                        SettingsViewModel.ConnectionStatus.CONNECTED ->
                            if (connectionResult.latencyMs > 0) "已连接 ${connectionResult.latencyMs}ms" else "已连接"
                        SettingsViewModel.ConnectionStatus.FAILED -> {
                            val err = connectionResult.errorCode
                            if (err != null) mapErrorCode(err) else "失败"
                        }
                        SettingsViewModel.ConnectionStatus.TESTING -> "测试中"
                        SettingsViewModel.ConnectionStatus.UNKNOWN -> "未测试"
                    }
                    PetalStatChip(
                        icon = "\u25CF",
                        text = statusText,
                        color = statusColor,
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = textSecondaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Model info
                if (config.provider == ApiProvider.PARTNER) {
                    Text("模型: 自动分配", color = PetalGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (connectionResult.status == SettingsViewModel.ConnectionStatus.CONNECTED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column {
                            Text("延迟: ${connectionResult.latencyMs}ms | 状态: 已连接",
                                color = PetalGreen, fontSize = 12.sp)
                            connectionResult.clientId?.let { cid ->
                                Text("Client ID: $cid",
                                    color = PetalGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                            connectionResult.groupName?.let { gn ->
                                Text("组: $gn | 剩余: $${String.format("%.2f", connectionResult.remainingQuota)}",
                                    color = PetalGreen.copy(alpha = 0.8f), fontSize = 11.sp)
                            }
                            if (connectionResult.rpmLimit > 0) {
                                Text("RPM: ${connectionResult.rpmLimit} | 日限额: $${connectionResult.dailyLimit ?: "∞"}",
                                    color = PetalGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    } else if (connectionResult.status == SettingsViewModel.ConnectionStatus.FAILED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val errCode = connectionResult.errorCode ?: "unknown"
                        val errLabel = mapErrorCode(errCode)
                        Column {
                            Text("$errLabel | error=${errCode} | latency=${connectionResult.latencyMs}ms",
                                color = PetalError, fontSize = 12.sp)
                            connectionResult.clientId?.let { cid ->
                                Text("Client ID: $cid",
                                    color = PetalError.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                            connectionResult.errorMessage?.let { msg ->
                                Text(msg, color = PetalError.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (config.model.isNotEmpty()) {
                    Text(
                        text = "模型: ${config.model}",
                        color = textSecondaryColor,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Base URL
                if (config.baseUrl.isNotEmpty() && config.provider != ApiProvider.PARTNER) {
                    Text(
                        text = "地址: ${config.baseUrl}",
                        color = textSecondaryColor.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Balance info
                if (balanceInfo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PetalSurfaceContainerLow)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "余额查询",
                            color = textSecondaryColor,
                            fontSize = 12.sp
                        )
                        if (balanceInfo.remainingBalance != null) {
                            val bal = balanceInfo.remainingBalance
                            Text(
                                text = "$${"%.2f".format(bal)}",
                                color = PetalGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "查询中...",
                                color = textSecondaryColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (balanceQueryFailed) {
                    Text(
                        text = "余额查询失败",
                        color = PetalError,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = PetalError, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = {
                        editingConfig = testedConfigs[connectionKey] ?: config
                    }) {
                        Text("编辑", color = PetalPrimary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { onTest(config) }) {
                        Text("测试", color = PetalPrimary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onSelectActive) {
                        Text("设为活跃", color = PetalPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // 编辑弹窗 - INLINE inside PetalSavedApiCard
    editingConfig?.let { editing ->
        val providerModels = fetchedModels[editing.provider.name] ?: emptyList()
        val fetchState = modelFetchStates[editing.provider.name] ?: SettingsViewModel.ModelFetchState()
        PetalApiConfigEditDialog(
            config = editing,
            connectionResult = connectionResult,
            onDismiss = { editingConfig = null },
            onSave = { updated: ApiConfig ->
                onEdit(updated)
                editingConfig = null
            },
            onTest = { testConfig: ApiConfig -> onTest(testConfig) },
            isDarkTheme = isDarkTheme,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            onFetchModels = { baseUrl: String, apiKey: String, skipCertVerify: Boolean ->
                onFetchModels(baseUrl, apiKey, editing.provider.name, skipCertVerify)
            },
            availableModels = providerModels,
            modelFetchState = fetchState
        )
    }
}

// ============================================================
// PetalAddApiButton - 添加 API 配置按钮
// ============================================================

@Composable
fun PetalAddApiButton(onClick: () -> Unit, isDarkTheme: Boolean) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PetalPrimary
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "添加API",
            modifier = Modifier.size(18.dp),
            tint = PetalPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "添加API配置",
            color = PetalPrimary,
            fontSize = 14.sp
        )
    }
}
