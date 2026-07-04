@file:OptIn(ExperimentalMaterial3Api::class)

package com.lianyu.ai.feature.settings.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ApiProvider
import com.lianyu.ai.domain.ModelState
import com.lianyu.ai.domain.ModelStatus
import com.lianyu.ai.feature.settings.R
import com.lianyu.ai.feature.settings.ui.viewmodel.SettingsViewModel
import com.lianyu.ai.uicommon.theme.PetalPrimary
import com.lianyu.ai.uicommon.theme.ThemeMode
import com.lianyu.ai.uicommon.theme.ThemeViewModel
import com.lianyu.ai.common.AppSettingsStore
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val configs by viewModel.configs.collectAsState(initial = emptyList())
    val modelStates by viewModel.modelStates.collectAsState()
    val localModelState by viewModel.localModelState.collectAsState()
    val fetchedModels by viewModel.fetchedModels.collectAsState()
    val modelFetchStates by viewModel.modelFetchStates.collectAsState()
    val balanceInfo by viewModel.balanceInfo.collectAsState()
    val balanceQueryFailed by viewModel.balanceQueryFailed.collectAsState()
    // [R7 FIX] connectionStatus/testedConfigs 现在是 StateFlow，需 collectAsState
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val testedConfigs by viewModel.testedConfigs.collectAsState()
    val visionEnabled by viewModel.visionEnabled.collectAsState()
    val visionModel by viewModel.visionModel.collectAsState()
    var expandedProvider by remember { mutableStateOf<ApiProvider?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    var newConfigDialog by remember { mutableStateOf<ApiConfig?>(null) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var showVisionModelSettings by remember { mutableStateOf(false) }

    var showApiTestDialog by remember { mutableStateOf(false) }
    var apiTestResult by remember { mutableStateOf<ApiTestDialogData?>(null) }
    val themeViewModel: ThemeViewModel = viewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.saveResult.collect { result ->
            when (result) {
                is SettingsViewModel.SaveResult.Success -> snackbarHostState.showSnackbar(result.message)
                is SettingsViewModel.SaveResult.Error -> snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.background
    val textPrimaryColor = colorScheme.onSurface
    val textSecondaryColor = colorScheme.onSurfaceVariant
    val textTertiaryColor = colorScheme.outlineVariant
    val dividerColor = colorScheme.outline
    val cardBackground = colorScheme.surfaceVariant

    LaunchedEffect(Unit) {
        viewModel.refreshLocalModel()
        viewModel.refreshConnectionStatus()
        viewModel.refreshPartnerQuota()
        delay(100)
        isVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.testCompletionEvent.collect { event ->
            apiTestResult = ApiTestDialogData(
                isSuccess = event.isSuccess,
                providerName = event.providerName,
                latencyMs = event.latencyMs,
                errorMessage = event.errorMessage
            )
            showApiTestDialog = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            // ====== Petal Soft Top App Bar ======
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
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
                    text = "API 设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = PetalPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
            ) {
                Text(
                    text = stringResource(R.string.settings_hint),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = textTertiaryColor,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====== API Cards Section ======
            ApiCardsSection(
                isVisible = isVisible,
                configs = configs,
                viewModel = viewModel,
                expandedProvider = expandedProvider,
                onExpandedProviderChange = { expandedProvider = it },
                fetchedModels = fetchedModels,
                modelFetchStates = modelFetchStates,
                isDarkTheme = isDarkTheme,
                textPrimaryColor = textPrimaryColor,
                textSecondaryColor = textSecondaryColor,
                balanceInfo = balanceInfo,
                balanceQueryFailed = balanceQueryFailed,
                showProviderPicker = showProviderPicker,
                onShowProviderPickerChange = { showProviderPicker = it },
                // [R7 FIX] 传入 StateFlow 解构后的 Map 值
                connectionStatus = connectionStatus,
                testedConfigs = testedConfigs
            )

            // ====== Vision Model Settings Section ======
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 200)) +
                        slideInVertically(tween(400, delayMillis = 200)) { it / 4 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showVisionModelSettings = true }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PetalPrimaryContainer.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "👁",
                                fontSize = 22.dp.value.sp
                            )
                        }

                        Column {
                            Text(
                                text = "视觉模型设置",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimaryColor
                            )
                            Text(
                                text = if (visionEnabled) "已启用 - ${AppSettingsStore.VisionModels.getVisionModelDisplayName(visionModel)}" else "点击配置图片识别",
                                fontSize = 12.sp,
                                color = textSecondaryColor
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "进入设置",
                        tint = textSecondaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====== Local Model Section ======
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(400, delayMillis = 250)) +
                        slideInVertically(tween(400, delayMillis = 250)) { it / 4 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val modelEntries = modelStates.entries.toList()
                    modelEntries.forEach { (modelId, state) ->
                        ModelSelectionCard(
                            state = state,
                            onSelect = { viewModel.selectModel(modelId) },
                            onDownload = { viewModel.downloadModel(modelId) },
                            onCancel = { viewModel.cancelGemmaDownload() },
                            onEnable = { viewModel.enableGemma() },
                            onDisable = { viewModel.disableGemma() },
                            onDelete = { viewModel.deleteGemma() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // New API Config Dialog
    newConfigDialog?.let { newConfig ->
        val providerModels = fetchedModels[newConfig.provider.name] ?: emptyList()
        val fetchState = modelFetchStates[newConfig.provider.name] ?: SettingsViewModel.ModelFetchState()
        ApiConfigEditDialog(
            config = newConfig,
            connectionResult = SettingsViewModel.ConnectionResult(SettingsViewModel.ConnectionStatus.UNKNOWN),
            onDismiss = { newConfigDialog = null },
            onSave = { config: ApiConfig ->
                viewModel.saveConfig(config)
                newConfigDialog = null
            },
            onTest = { testConfig: ApiConfig -> viewModel.testConnection(testConfig) },
            onFetchModels = { baseUrl: String, apiKey: String, skipCertVerify: Boolean ->
                viewModel.fetchModels(baseUrl, apiKey, newConfig.provider.name, skipCertVerify)
            },
            isDarkTheme = isDarkTheme,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            availableModels = providerModels,
            modelFetchState = fetchState
        )
    }

    // Provider Picker Dialog
    if (showProviderPicker) {
        val presetProviders = listOf(
            ApiProvider.OPENAI,
            ApiProvider.DEEPSEEK,
            ApiProvider.DASHSCOPE,
            ApiProvider.KIMI,
            ApiProvider.ZHIPU,
            ApiProvider.SILICONFLOW,
            ApiProvider.OPENROUTER,
            ApiProvider.GROQ,
            ApiProvider.GEMINI,
            ApiProvider.ANTHROPIC,
            ApiProvider.XIAOMI,
            ApiProvider.IFLYTEK,
            ApiProvider.CUSTOM
        )
        val cardBackground = MaterialTheme.colorScheme.surfaceVariant
        val dividerColor = MaterialTheme.colorScheme.outline

        AlertDialog(
            onDismissRequest = { showProviderPicker = false },
            containerColor = cardBackground,
            title = {
                Text(
                    text = "选择 API 提供商",
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetProviders.forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(dividerColor.copy(alpha = 0.3f))
                                .clickable {
                                    showProviderPicker = false
                                    newConfigDialog = ApiConfig(
                                        provider = provider,
                                        apiKey = "",
                                        baseUrl = provider.defaultBaseUrl,
                                        model = provider.defaultModel
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProviderLogo(
                                provider = provider,
                                size = 36.dp,
                                cornerRadius = 10.dp
                            )
                            Column {
                                Text(
                                    text = provider.displayName,
                                    color = textPrimaryColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = provider.defaultBaseUrl,
                                    color = textSecondaryColor,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProviderPicker = false }) {
                    Text("取消", color = PetalPrimary)
                }
            }
        )
    }

    if (showVisionModelSettings) {
        VisionModelSettingsScreen(
            onNavigateBack = { showVisionModelSettings = false },
            viewModel = viewModel
        )
    }

    if (showApiTestDialog && apiTestResult != null) {
        ApiTestResultDialog(
            data = apiTestResult!!,
            isDarkTheme = isDarkTheme,
            onDismiss = { showApiTestDialog = false }
        )
    }
}

data class ApiTestDialogData(
    val isSuccess: Boolean,
    val providerName: String,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

@Composable
private fun ApiTestResultDialog(
    data: ApiTestDialogData,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        icon = {
            Icon(
                imageVector = if (data.isSuccess) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (data.isSuccess) PetalGreen else PetalError,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = if (data.isSuccess) "连接成功！" else "连接失败",
                color = if (data.isSuccess) PetalGreen else PetalError,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (data.isSuccess) "${data.providerName} API 连接测试通过" else "${data.providerName} API 无法连接，请检查配置",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )

                if (data.isSuccess && data.latencyMs > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PetalGreen.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "⏱ 响应延迟: ${data.latencyMs}ms", color = PetalGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = when {
                            data.latencyMs < 500 -> "🚀 延迟优秀，连接速度很快"
                            data.latencyMs < 1500 -> "✅ 延迟正常，可以正常使用"
                            else -> "⚠️ 延迟较高，可能影响体验"
                        }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                if (!data.isSuccess && data.errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PetalErrorContainer.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = data.errorMessage, color = PetalError, fontSize = 13.sp)
                    }

                    Text(
                        text = "常见问题：\n• API Key 是否正确\n• Base URL 是否完整（含 /v1）\n• 网络连接是否正常\n• 该服务商是否支持当前模型",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = PetalPrimary, fontWeight = FontWeight.Medium)
            }
        }
    )
}

// ==================== Extracted Sections ====================

@Composable
private fun ApiCardsSection(
    isVisible: Boolean,
    configs: List<ApiConfig>,
    viewModel: SettingsViewModel,
    expandedProvider: ApiProvider?,
    onExpandedProviderChange: (ApiProvider?) -> Unit,
    fetchedModels: Map<String, List<String>>,
    modelFetchStates: Map<String, SettingsViewModel.ModelFetchState>,
    isDarkTheme: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    balanceInfo: com.lianyu.ai.network.AiService.BalanceInfo?,
    balanceQueryFailed: Boolean,
    showProviderPicker: Boolean,
    onShowProviderPickerChange: (Boolean) -> Unit,
    // [R7 FIX] 传入 StateFlow 解构后的 Map 值
    connectionStatus: Map<String, SettingsViewModel.ConnectionResult>,
    testedConfigs: Map<String, ApiConfig>
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400, delayMillis = 150)) +
                slideInVertically(tween(400, delayMillis = 150)) { it / 4 }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val partnerConfig = configs.firstOrNull { it.provider == ApiProvider.PARTNER }
                ?: ApiConfig(
                    provider = ApiProvider.PARTNER,
                    apiKey = "",
                    extraApiKeys = "",
                    baseUrl = ApiProvider.PARTNER.defaultBaseUrl,
                    model = ApiProvider.PARTNER.defaultModel
                )

            val partnerExpanded = expandedProvider == ApiProvider.PARTNER

            PetalApiCard(
                config = partnerConfig,
                connectionResult = connectionStatus[viewModel.connectionKey(partnerConfig)]
                    ?: SettingsViewModel.ConnectionResult(SettingsViewModel.ConnectionStatus.UNKNOWN),
                isExpanded = partnerExpanded,
                isActive = partnerConfig.isEnabled,
                onExpandToggle = {
                    onExpandedProviderChange(if (partnerExpanded) null else ApiProvider.PARTNER)
                },
                onEdit = { selectedConfig: ApiConfig ->
                    viewModel.saveConfig(selectedConfig)
                },
                onTest = { selectedConfig: ApiConfig ->
                    viewModel.testConnection(selectedConfig)
                },
                onToggleEnabled = { viewModel.toggleConfigEnabled(partnerConfig) },
                onSelectActive = { viewModel.selectActiveConfig(partnerConfig) },
                onFetchModels = { baseUrl: String, apiKey: String, provider: String, skipCertVerify: Boolean ->
                    viewModel.fetchModels(baseUrl, apiKey, provider, skipCertVerify)
                },
                fetchedModels = fetchedModels,
                modelFetchStates = modelFetchStates,
                testedConfigs = testedConfigs,
                connectionKey = viewModel.connectionKey(partnerConfig),
                isDarkTheme = isDarkTheme,
                textPrimaryColor = textPrimaryColor,
                textSecondaryColor = textSecondaryColor,
                balanceInfo = null,
                balanceQueryFailed = false,
                onQueryBalance = null  // PARTNER uses handshake group quota, not /balance
            )

            configs.filter { it.provider != ApiProvider.PARTNER }.forEach { config ->
                val result = connectionStatus[viewModel.connectionKey(config)]
                    ?: SettingsViewModel.ConnectionResult(SettingsViewModel.ConnectionStatus.UNKNOWN)
                val isExpanded = expandedProvider == config.provider

                PetalSavedApiCard(
                    config = config,
                    connectionResult = result,
                    isExpanded = isExpanded,
                    isActive = config.isEnabled,
                    onExpandToggle = {
                        onExpandedProviderChange(if (isExpanded) null else config.provider)
                    },
                    onEdit = { it: ApiConfig -> viewModel.saveConfig(it) },
                    onDelete = { viewModel.deleteConfig(config) },
                    onTest = { viewModel.testConnection(config) },
                    onToggleEnabled = { viewModel.toggleConfigEnabled(config) },
                    onSelectActive = { viewModel.selectActiveConfig(config) },
                    onFetchModels = { baseUrl: String, apiKey: String, provider: String, skipCertVerify: Boolean ->
                        viewModel.fetchModels(baseUrl, apiKey, provider, skipCertVerify)
                    },
                    fetchedModels = fetchedModels,
                    modelFetchStates = modelFetchStates,
                    testedConfigs = testedConfigs,
                    connectionKey = viewModel.connectionKey(config),
                    isDarkTheme = isDarkTheme,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor
                )
            }

            PetalAddApiButton(
                onClick = {
                    onShowProviderPickerChange(true)
                },
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@Composable
private fun VisionModelSection(
    isVisible: Boolean,
    visionEnabled: Boolean,
    visionModel: String,
    isDarkTheme: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    onVisionEnabledChanged: (Boolean) -> Unit,
    onVisionModelChanged: (String) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400, delayMillis = 200)) +
                slideInVertically(tween(400, delayMillis = 200)) { it / 4 }
    ) {
        VisionModelSettingsCard(
            visionEnabled = visionEnabled,
            visionModel = visionModel,
            isDarkTheme = isDarkTheme,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            onVisionEnabledChanged = onVisionEnabledChanged,
            onVisionModelChanged = onVisionModelChanged
        )

        Spacer(modifier = Modifier.height(8.dp))

        ApiTutorialCard(isDarkTheme, textPrimaryColor, textSecondaryColor)
    }
}

// ==================== Reused Components (unchanged logic) ====================

@Composable
fun ApiConfigEditDialog(
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
            // PARTNER 模式下，如果用户没有填写密钥，使用空字符串触发由用户配置
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
                SecureLog.d("SettingsScreen", "PARTNER auto-select model: chosen=$chosenModel, server=$serverModel, available=${availableModels.size}")
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
                        val currentConfig = config.copy(
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
                        Text("测试")
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            config.copy(
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

