@file:OptIn(ExperimentalMaterial3Api::class)

package com.lianyu.ai.feature.settings.ui.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.database.model.ApiProvider
import com.lianyu.ai.feature.settings.R
import com.lianyu.ai.feature.settings.ui.viewmodel.SettingsViewModel
import com.lianyu.ai.uicommon.theme.ThemeMode
import com.lianyu.ai.uicommon.theme.ThemeViewModel
import com.lianyu.ai.uicommon.theme.WeChatDarkBackground
import com.lianyu.ai.uicommon.theme.WeChatDarkCard
import com.lianyu.ai.uicommon.theme.WeChatDarkDivider
import com.lianyu.ai.uicommon.theme.WeChatDarkTextPrimary
import com.lianyu.ai.uicommon.theme.WeChatDarkTextSecondary
import com.lianyu.ai.uicommon.theme.WeChatDarkTextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun VisionModelSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val visionEnabled by viewModel.visionEnabled.collectAsState()
    val visionModel by viewModel.visionModel.collectAsState()
    val visionProvider by viewModel.visionProvider.collectAsState()
    val visionApiUrl by viewModel.visionApiUrl.collectAsState()
    val visionApiKey by viewModel.visionApiKey.collectAsState()

    var isVisible by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }

    var apiKeyVisible by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var customModelName by remember { mutableStateOf("") }
    var showTestResultDialog by remember { mutableStateOf(false) }

    // Initialize customModelName from DataStore only once when screen loads or provider changes to CUSTOM
    LaunchedEffect(visionProvider) {
        if (visionProvider == "CUSTOM") {
            // Only sync from DataStore if customModelName is currently empty (first time switching to CUSTOM)
            if (customModelName.isBlank()) {
                val isPreset = AppSettingsStore.VisionModels.VISION_MODEL_OPTIONS.any { it.first == visionModel }
                if (!isPreset && visionModel.isNotBlank()) {
                    customModelName = visionModel
                }
            }
        }
    }

    val scope = rememberCoroutineScope()

    val themeViewModel: ThemeViewModel = viewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val backgroundColor = if (isDarkTheme) WeChatDarkBackground else PetalBackgroundStart
    val textPrimaryColor = if (isDarkTheme) WeChatDarkTextPrimary else PetalOnSurface
    val textSecondaryColor = if (isDarkTheme) WeChatDarkTextSecondary else PetalOnSurfaceVariant
    val textTertiaryColor = if (isDarkTheme) WeChatDarkTextTertiary else PetalOutlineVariant

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDarkTheme) WeChatDarkCard else Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigateBack() }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = PetalPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "视觉模型设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor
                )

                Box(modifier = Modifier.size(40.dp))
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
            ) {
                Text(
                    text = "配置图片识别的视觉AI模型及API连接信息",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = textTertiaryColor,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 150)) +
                        slideInVertically(tween(400, delayMillis = 150)) { it / 4 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isDarkTheme) WeChatDarkCard else PetalSurface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PetalPrimaryContainer.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "👁", fontSize = 24.dp.value.sp, modifier = Modifier.padding(4.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "视觉识别功能", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimaryColor)
                            Text(text = "启用后，发送图片时将使用视觉AI模型进行分析", fontSize = 12.sp, color = textSecondaryColor)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "启用视觉识别", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimaryColor)
                            Text(text = if (visionEnabled) "已开启 - 图片将被AI分析和理解" else "已关闭 - 图片作为普通附件发送", fontSize = 12.sp, color = textSecondaryColor)
                        }

                        Switch(
                            checked = visionEnabled,
                            onCheckedChange = { viewModel.setVisionEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PetalPrimary,
                                checkedTrackColor = PetalPrimaryContainer.copy(alpha = 0.5f),
                                uncheckedThumbColor = PetalOutlineVariant,
                                uncheckedTrackColor = PetalSurfaceContainer
                            )
                        )
                    }

                    if (visionEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                            Text(text = "选择视觉模型", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimaryColor)

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = AppSettingsStore.VisionModels.getVisionModelDisplayName(visionModel),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("预设模型", color = textSecondaryColor) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showModelDropdown = !showModelDropdown },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PetalPrimary,
                                        unfocusedBorderColor = if (isDarkTheme) WeChatDarkDivider else PetalSurfaceContainer,
                                        focusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
                                        unfocusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
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

                                androidx.compose.material3.DropdownMenu(
                                    expanded = showModelDropdown,
                                    onDismissRequest = { showModelDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    AppSettingsStore.VisionModels.VISION_MODEL_OPTIONS.forEach { (modelId: String, displayName: String, description: String) ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(text = displayName, color = if (modelId == visionModel) PetalPrimary else textPrimaryColor, fontSize = 14.sp, fontWeight = if (modelId == visionModel) FontWeight.Bold else FontWeight.Normal)
                                                    Text(text = description, color = textSecondaryColor, fontSize = 11.sp)
                                                }
                                            },
                                            onClick = {
                                                viewModel.setVisionModel(modelId)
                                                showModelDropdown = false
                                            },
                                            modifier = Modifier.background(if (modelId == visionModel) PetalPrimaryContainer.copy(alpha = 0.1f) else Color.Transparent)
                                        )
                                    }
                                }
                            }

                            androidx.compose.material3.HorizontalDivider(color = if (isDarkTheme) WeChatDarkDivider else PetalSurfaceContainer, thickness = 1.dp)

                            var useCustomApi by remember { mutableStateOf(visionProvider != "auto") }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (useCustomApi) PetalPrimary.copy(alpha = 0.08f) else Color.Gray.copy(alpha = 0.05f))
                                    .clickable {
                                        useCustomApi = !useCustomApi
                                        viewModel.setVisionProvider(if (useCustomApi) "CUSTOM" else "auto")
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = if (useCustomApi) "🔧 自定义API配置（独立）" else "📡 跟随主API设置", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimaryColor)
                                    Text(
                                        text = if (useCustomApi) "为视觉模型单独配置API地址、密钥和模型名" else "直接使用主聊天API的连接信息",
                                        fontSize = 12.sp,
                                        color = textSecondaryColor,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                Switch(
                                    checked = useCustomApi,
                                    onCheckedChange = { newValue ->
                                        useCustomApi = newValue
                                        viewModel.setVisionProvider(if (newValue) "CUSTOM" else "auto")
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = if (useCustomApi) PetalPrimary else Color.Gray,
                                        checkedTrackColor = if (useCustomApi) PetalPrimaryContainer.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }

                            if (useCustomApi) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                    androidx.compose.material3.HorizontalDivider(color = if (isDarkTheme) WeChatDarkDivider else PetalSurfaceContainer)

                                    OutlinedTextField(
                                        value = customModelName,
                                        onValueChange = { newValue ->
                                            customModelName = newValue
                                            if (newValue.trim().isNotBlank()) {
                                                viewModel.setVisionModel(newValue.trim())
                                            }
                                        },
                                        label = { Text("模型名称 *", color = textSecondaryColor) },
                                        placeholder = { Text("如: kimi-k2.6, gpt-4o, claude-3-5-sonnet-20241022", color = textTertiaryColor, fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (customModelName.isNotBlank()) PetalGreen else PetalPrimary,
                                            unfocusedBorderColor = if (customModelName.isNotBlank()) PetalGreen else if (isDarkTheme) WeChatDarkDivider else PetalSurfaceContainer,
                                            focusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
                                            unfocusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
                                            focusedTextColor = textPrimaryColor,
                                            unfocusedTextColor = textPrimaryColor
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        supportingText = {
                                            if (customModelName.isBlank()) {
                                                Text("⚠️ 必填：请输入支持视觉功能的模型名称", color = PetalOrange, fontSize = 11.sp)
                                            } else {
                                                Text("✓ 已输入: $customModelName", color = PetalGreen, fontSize = 11.sp)
                                            }
                                        }
                                    )

                                    Text(text = "🌐 API 厂商预设（点击自动填充）", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimaryColor)

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val providerPresets = listOf(
                                            Triple("OpenAI", "https://api.openai.com/v1/", "gpt-4o"),
                                            Triple("Kimi/Moonshot", "https://api.moonshot.cn/v1/", "kimi-k2.6"),
                                            Triple("DeepSeek", "https://api.deepseek.com/v1/", "deepseek-chat"),
                                            Triple("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/", "qwen-vl-max"),
                                            Triple("智谱GLM", "https://open.bigmodel.cn/api/paas/v4/", "glm-4v"),
                                            Triple("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-1.5-pro")
                                        )

                                        items(providerPresets.size) { index ->
                                            val (name, url, model) = providerPresets[index]
                                            androidx.compose.material3.FilterChip(
                                                selected = false,
                                                onClick = {
                                                    customModelName = model
                                                    viewModel.setVisionModel(model)
                                                    viewModel.setVisionApiUrl(url)
                                                },
                                                label = { Text(name, fontSize = 12.sp) },
                                                leadingIcon = null,
                                                modifier = Modifier.height(32.dp)
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = visionApiUrl,
                                        onValueChange = { viewModel.setVisionApiUrl(it) },
                                        label = { Text("API 地址 (Base URL) *", color = textSecondaryColor) },
                                        placeholder = { Text("https://api.openai.com/v1/ 或中转站地址", color = textTertiaryColor, fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (visionApiUrl.isNotBlank()) PetalGreen else PetalPrimary,
                                            unfocusedBorderColor = if (visionApiUrl.isNotBlank()) PetalGreen else if (isDarkTheme) WeChatDarkDivider else PetalSurfaceContainer,
                                            focusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
                                            unfocusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
                                            focusedTextColor = textPrimaryColor,
                                            unfocusedTextColor = textPrimaryColor
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                                    )

                                    OutlinedTextField(
                                        value = visionApiKey,
                                        onValueChange = { viewModel.setVisionApiKey(it) },
                                        label = { Text("API 密钥 (Key) *", color = textSecondaryColor) },
                                        placeholder = { Text("sk-... 或其他格式的密钥", color = textTertiaryColor, fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (visionApiKey.isNotBlank()) PetalGreen else PetalPrimary,
                                            unfocusedBorderColor = if (visionApiKey.isNotBlank()) PetalGreen else if (isDarkTheme) WeChatDarkDivider else PetalSurfaceContainer,
                                            focusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
                                            unfocusedContainerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
                                            focusedTextColor = textPrimaryColor,
                                            unfocusedTextColor = textPrimaryColor
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                        trailingIcon = {
                                            Icon(
                                                imageVector = if (apiKeyVisible) Icons.Filled.Check else Icons.Filled.KeyboardArrowDown,
                                                contentDescription = if (apiKeyVisible) "隐藏密钥" else "显示密钥",
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable { apiKeyVisible = !apiKeyVisible },
                                                tint = textSecondaryColor
                                            )
                                        }
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(PetalPrimary.copy(alpha = 0.08f))
                                            .clickable(enabled = !isTestingConnection) {
                                                scope.launch {
                                                    isTestingConnection = true
                                                    testResult = null
                                                    try {
                                                        val baseUrl = visionApiUrl.trim().removeSuffix("/")
                                                        val testKey = visionApiKey.trim()
                                                        val testModel = if (visionProvider == "CUSTOM" && customModelName.isNotBlank()) {
                                                            customModelName.trim()
                                                        } else {
                                                            AppSettingsStore.VisionModels.resolveVisionModel(visionModel, visionProvider)
                                                        }

                                                        if (baseUrl.isBlank() || testKey.isBlank()) {
                                                            testResult = "✗ 请填写 API 地址和密钥"
                                                            // [P4 FIX] return@launch 在 try 内会跳过 finally 的 isTestingConnection=false
                                                            isTestingConnection = false
                                                            return@launch
                                                        }

                                                        val result = viewModel.testVisionConnection(
                                                            provider = visionProvider,
                                                            baseUrl = baseUrl,
                                                            apiKey = testKey,
                                                            model = testModel
                                                        )

                                                        result.onSuccess { message ->
                                                            testResult = "✓ $message"
                                                        }.onFailure { error ->
                                                            testResult = "✗ 连接失败: ${error.message}"
                                                        }

                                                        showTestResultDialog = true
                                                    } catch (e: Exception) {
                                                        testResult = "✗ 测试异常: ${e.message ?: e.javaClass.simpleName}"
                                                        showTestResultDialog = true
                                                    } finally {
                                                        isTestingConnection = false
                                                    }
                                                }
                                            }
                                            .padding(vertical = 14.dp, horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isTestingConnection) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = PetalPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = "测试连接中...", color = PetalPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        } else {
                                            Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = PetalPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = "测试 API 连接", color = PetalPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    if (testResult != null) {
                                        val isSuccess = testResult!!.startsWith("✓")
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSuccess) PetalGreen.copy(alpha = 0.08f) else Color.Red.copy(alpha = 0.08f))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = testResult!!, color = if (isSuccess) PetalGreen else Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    if (visionProvider != "auto") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(PetalGreen.copy(alpha = 0.05f))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = "✓ 当前使用独立API配置", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PetalGreen)
                                            Text(text = "模型: ${AppSettingsStore.VisionModels.getVisionModelDisplayName(visionModel)} | Provider: ${if (visionProvider == "auto") "跟随主API" else visionProvider}", fontSize = 11.sp, color = textSecondaryColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = isVisible && visionEnabled,
                enter = fadeIn(tween(400, delayMillis = 250)) +
                        slideInVertically(tween(400, delayMillis = 250)) { it / 4 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDarkTheme) WeChatDarkCard else PetalSurface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = "📋 配置说明", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimaryColor)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoItem(icon = "�", title = "跟随主API（推荐）", content = "选择「跟随主API设置」时，将自动使用您在API设置页面配置的连接信息，无需重复填写。", textColor = textPrimaryColor, secondaryColor = textSecondaryColor)
                        InfoItem(icon = "�", title = "独立配置", content = "如果视觉模型需要使用不同的API提供商（如用Kimi Key调用Kimi K2.6），请在此处单独填写URL和Key。", textColor = textPrimaryColor, secondaryColor = textSecondaryColor)
                        InfoItem(icon = "⚠️", title = "注意事项", content = "确保所选模型与API提供商匹配！例如：Kimi K2.6 必须搭配 Moonshot 的 Key 和 URL。", textColor = textPrimaryColor, secondaryColor = textSecondaryColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showTestResultDialog && testResult != null) {
        TestResultDialog(
            testResult = testResult,
            isDarkTheme = isDarkTheme,
            onDismiss = { showTestResultDialog = false }
        )
    }
}

@Composable
private fun InfoItem(icon: String, title: String, content: String, textColor: Color, secondaryColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
            Text(text = content, fontSize = 12.sp, color = secondaryColor, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun TestResultDialog(
    testResult: String?,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    if (testResult == null) return

    val isSuccess = testResult.startsWith("✓")

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDarkTheme) WeChatDarkCard else Color.White,
        icon = {
            Icon(
                imageVector = if (isSuccess) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (isSuccess) PetalGreen else Color.Red,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = if (isSuccess) "连接成功！" else "连接失败",
                color = if (isSuccess) PetalGreen else Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isSuccess) "视觉模型API配置正确，可以正常使用图片识别功能" else "无法连接到API服务器，请检查以下配置：",
                    color = if (isDarkTheme) WeChatDarkTextPrimary else PetalOnSurface,
                    fontSize = 14.sp
                )

                if (!isSuccess) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = testResult.removePrefix("✗ ").removePrefix("✓ "),
                            color = Color.Red,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "常见问题排查：\n• API 地址是否正确（注意末尾的 /v1）\n• API Key 是否有效且未过期\n• 模型名称是否支持视觉功能\n• 网络连接是否正常",
                        color = if (isDarkTheme) WeChatDarkTextSecondary else PetalOnSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PetalGreen.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = testResult.removePrefix("✓ "),
                            color = PetalGreen,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("知道了", color = PetalPrimary, fontWeight = FontWeight.Medium)
            }
        }
    )
}
