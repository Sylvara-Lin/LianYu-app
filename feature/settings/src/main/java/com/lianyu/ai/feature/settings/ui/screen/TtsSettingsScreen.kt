@file:OptIn(ExperimentalMaterial3Api::class)

package com.lianyu.ai.feature.settings.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.network.tts.TtsConfig
import com.lianyu.ai.network.tts.TtsProvider
import com.lianyu.ai.network.tts.TtsService
import com.lianyu.ai.network.tts.TtsVoice
import com.lianyu.ai.network.tts.ChatTtsConfig
import com.lianyu.ai.network.tts.ChatTtsMode
import com.lianyu.ai.network.tts.LocalTtsCatalog
import com.lianyu.ai.network.tts.LocalTtsModel
import com.lianyu.ai.network.tts.LocalTtsModelManager
import com.lianyu.ai.network.tts.LocalTtsUiState
import com.lianyu.ai.network.tts.LocalTtsUiStatus
import com.lianyu.ai.uicommon.theme.PetalPrimary
import com.lianyu.ai.uicommon.theme.PetalPrimaryContainer
import com.lianyu.ai.uicommon.theme.PetalOnPrimaryContainer
import com.lianyu.ai.uicommon.theme.PetalOnSurfaceVariant
import com.lianyu.ai.uicommon.theme.PetalSurface
import com.lianyu.ai.uicommon.theme.PetalSurfaceContainer
import com.lianyu.ai.uicommon.theme.PetalGreen
import com.lianyu.ai.uicommon.theme.PetalError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TtsSettingsScreen(
    onNavigateBack: () -> Unit,
    isDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val ttsService = remember { TtsService.getInstance(context) }

    var isVisible by remember { mutableStateOf(false) }
    var ttsEnabled by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(TtsProvider.ANDROID) }
    var selectedVoiceId by remember { mutableStateOf("") }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var showVoiceDropdown by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val config = remember {
        TtsConfig.fromSharedPreferences(context)
    }

    var aliyunKey by remember { mutableStateOf(config.aliyunKeyId) }
    var aliyunSecret by remember { mutableStateOf(config.aliyunKeySecret) }
    var aliyunAppKey by remember { mutableStateOf(config.aliyunAppKey) }
    var baiduKey by remember { mutableStateOf(config.baiduApiKey) }
    var baiduSecret by remember { mutableStateOf(config.baiduSecretKey) }
    var xunfeiAppId by remember { mutableStateOf(config.xunfeiAppId) }
    var xunfeiKey by remember { mutableStateOf(config.xunfeiApiKey) }
    var xunfeiSecret by remember { mutableStateOf(config.xunfeiApiSecret) }
    var azureKey by remember { mutableStateOf(config.azureSubscriptionKey) }
    var azureRegion by remember { mutableStateOf(config.azureRegion) }
    var volcengineAppId by remember { mutableStateOf(config.volcengineAppId) }
    var volcengineToken by remember { mutableStateOf(config.volcengineToken) }
    var volcengineCluster by remember { mutableStateOf(config.volcengineCluster) }
    var sfApiKey by remember { mutableStateOf(config.siliconflowApiKey) }
    var sfCustomVoiceId by remember { mutableStateOf(config.siliconflowCustomVoiceId) }
    var sfUseGlobalKey by remember { mutableStateOf(config.siliconflowUseGlobalKey) }
    var sfTtsModel by remember { mutableStateOf(config.siliconflowTtsModel) }
    var sfSpeed by remember { mutableStateOf(config.siliconflowSpeed) }
    var sfGain by remember { mutableStateOf(config.siliconflowGain) }
    var sfSampleRate by remember { mutableStateOf(config.siliconflowSampleRate) }
    var sfUseCustomTts by remember { mutableStateOf(config.customTtsUrl.isNotBlank()) }
    var customTtsUrl by remember { mutableStateOf(config.customTtsUrl) }
    var customTtsApiKey by remember { mutableStateOf(config.customTtsApiKey) }
    var customTtsModel by remember { mutableStateOf(config.customTtsModel) }
    var customTtsVoiceId by remember { mutableStateOf(config.customTtsVoiceId) }
    var showSfModelDropdown by remember { mutableStateOf(false) }
    var showSfRateDropdown by remember { mutableStateOf(false) }

    // 本地离线 TTS 状态
    var localTtsSpeed by remember { mutableStateOf(config.localTtsSpeed) }
    var localTtsSid by remember { mutableStateOf(config.localTtsSid) }
    val localTtsManager = remember { ttsService.localTtsManager }
    val localTtsState by localTtsManager.state.collectAsState()
    var showLocalTtsModelDropdown by remember { mutableStateOf(false) }

    // 聊天页分段队列 TTS 配置（朗读模式 / 跳过括号 / 美化 / 去重）
    val chatTtsCfg = remember { ChatTtsConfig.fromSharedPreferences(context) }
    var chatTtsMode by remember { mutableStateOf(chatTtsCfg.mode) }
    var skipParentheses by remember { mutableStateOf(chatTtsCfg.skipParentheses) }
    var chatTtsAutoDedup by remember { mutableStateOf(chatTtsCfg.autoDedup) }
    var chatTtsBeautify by remember { mutableStateOf(chatTtsCfg.beautify) }
    var showChatTtsModeDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        ttsEnabled = prefs.getBoolean("tts_enabled", false)
        val providerName = prefs.getString("tts_provider", TtsProvider.ANDROID.name)
        selectedProvider = TtsProvider.entries.find { it.name == providerName } ?: TtsProvider.ANDROID
        selectedVoiceId = prefs.getString("tts_voice_${selectedProvider.name}", "") ?: ""

        delay(100)
        isVisible = true
    }

    val voices = remember(selectedProvider) {
        ttsService.getVoices(selectedProvider)
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.background
    val textPrimaryColor = colorScheme.onSurface
    val textSecondaryColor = colorScheme.onSurfaceVariant
    val textTertiaryColor = colorScheme.outlineVariant
    val cardBg = colorScheme.surfaceVariant

    fun saveSettings() {
        val newConfig = TtsConfig(
            aliyunKeyId = aliyunKey,
            aliyunKeySecret = aliyunSecret,
            aliyunAppKey = aliyunAppKey,
            baiduApiKey = baiduKey,
            baiduSecretKey = baiduSecret,
            xunfeiAppId = xunfeiAppId,
            xunfeiApiKey = xunfeiKey,
            xunfeiApiSecret = xunfeiSecret,
            azureSubscriptionKey = azureKey,
            azureRegion = azureRegion,
            volcengineAppId = volcengineAppId,
            volcengineToken = volcengineToken,
            volcengineCluster = volcengineCluster,
            siliconflowApiKey = sfApiKey,
            siliconflowCustomVoiceId = sfCustomVoiceId,
            siliconflowUseGlobalKey = sfUseGlobalKey,
            siliconflowTtsModel = sfTtsModel,
            siliconflowSpeed = sfSpeed,
            siliconflowGain = sfGain,
            siliconflowSampleRate = sfSampleRate,
            customTtsUrl = if (sfUseCustomTts) customTtsUrl else "",
            customTtsApiKey = customTtsApiKey,
            customTtsModel = customTtsModel,
            customTtsVoiceId = customTtsVoiceId,
            localTtsSpeed = localTtsSpeed,
            localTtsSid = localTtsSid
        )
        
        TtsConfig.saveToSharedPreferences(context, newConfig)
        ttsService.updateConfig(newConfig)
        
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("tts_enabled", ttsEnabled)
            putString("tts_provider", selectedProvider.name)
            putString("tts_voice_${selectedProvider.name}", selectedVoiceId)
            apply()
        }
        ttsService.setProvider(selectedProvider)
    }

    fun saveChatTtsSettings() {
        val cfg = ChatTtsConfig(
            mode = chatTtsMode,
            skipParentheses = skipParentheses,
            autoDedup = chatTtsAutoDedup,
            beautify = chatTtsBeautify
        )
        ChatTtsConfig.saveToSharedPreferences(context, cfg)
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
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = PetalPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "TTS 语音设置",
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
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TtsToggleCard(
                        enabled = ttsEnabled,
                        onToggle = {
                            ttsEnabled = it
                            saveSettings()
                        },
                        isDarkTheme = isDarkTheme,
                        cardBg = cardBg,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor
                    )

                    AnimatedVisibility(
                        visible = ttsEnabled,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProviderSelectionCard(
                                selectedProvider = selectedProvider,
                                onProviderSelect = {
                                    selectedProvider = it
                                    selectedVoiceId = ""
                                    saveSettings()
                                },
                                showDropdown = showProviderDropdown,
                                onDropdownToggle = { showProviderDropdown = it },
                                isDarkTheme = isDarkTheme,
                                cardBg = cardBg,
                                textPrimaryColor = textPrimaryColor,
                                textSecondaryColor = textSecondaryColor
                            )

                            VoiceSelectionCard(
                                voices = voices,
                                selectedVoiceId = selectedVoiceId,
                                onVoiceSelect = {
                                    selectedVoiceId = it
                                    saveSettings()
                                },
                                showDropdown = showVoiceDropdown,
                                onDropdownToggle = { showVoiceDropdown = it },
                                isDarkTheme = isDarkTheme,
                                cardBg = cardBg,
                                textPrimaryColor = textPrimaryColor,
                                textSecondaryColor = textSecondaryColor
                            )

                            if (selectedProvider == TtsProvider.SHERPA_LOCAL) {
                                // 本地离线 TTS 卡片（独立于 ApiKeyConfigCard，直接访问 screen 作用域）
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(cardBg)
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = "${selectedProvider.displayName} 配置",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimaryColor
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LocalTtsConfigContent(
                                        localTtsState = localTtsState,
                                        localTtsSpeed = localTtsSpeed,
                                        onLocalTtsSpeedChange = { localTtsSpeed = it; saveSettings() },
                                        localTtsSid = localTtsSid,
                                        onLocalTtsSidChange = { localTtsSid = it; saveSettings() },
                                        showModelDropdown = showLocalTtsModelDropdown,
                                        onShowModelDropdown = { showLocalTtsModelDropdown = it },
                                        onSelectModel = {
                                            scope.launch { localTtsManager.selectModel(it) }
                                        },
                                        onDownload = {
                                            scope.launch {
                                                localTtsManager.startDownload(localTtsState.modelId)
                                            }
                                        },
                                        onCancelDownload = {
                                            scope.launch { localTtsManager.cancelDownload() }
                                        },
                                        onEnable = {
                                            scope.launch { localTtsManager.enable() }
                                        },
                                        onDisable = {
                                            scope.launch { localTtsManager.disable() }
                                        },
                                        onDelete = {
                                            scope.launch { localTtsManager.deleteDownloadedModel() }
                                        },
                                        isDarkTheme = isDarkTheme,
                                        cardBg = cardBg,
                                        textPrimaryColor = textPrimaryColor,
                                        textSecondaryColor = textSecondaryColor,
                                        context = context
                                    )
                                }
                            } else {
                                ApiKeyConfigCard(
                                    provider = selectedProvider,
                                    aliyunKey = aliyunKey,
                                    onAliyunKeyChange = { aliyunKey = it },
                                    aliyunSecret = aliyunSecret,
                                    onAliyunSecretChange = { aliyunSecret = it },
                                    aliyunAppKey = aliyunAppKey,
                                    onAliyunAppKeyChange = { aliyunAppKey = it },
                                    baiduKey = baiduKey,
                                    onBaiduKeyChange = { baiduKey = it },
                                    baiduSecret = baiduSecret,
                                    onBaiduSecretChange = { baiduSecret = it },
                                    xunfeiAppId = xunfeiAppId,
                                    onXunfeiAppIdChange = { xunfeiAppId = it },
                                    xunfeiKey = xunfeiKey,
                                    onXunfeiKeyChange = { xunfeiKey = it },
                                    xunfeiSecret = xunfeiSecret,
                                    onXunfeiSecretChange = { xunfeiSecret = it },
                                    azureKey = azureKey,
                                    onAzureKeyChange = { azureKey = it },
                                    azureRegion = azureRegion,
                                    onAzureRegionChange = { azureRegion = it },
                                    volcengineAppId = volcengineAppId,
                                    onVolcengineAppIdChange = { volcengineAppId = it },
                                    volcengineToken = volcengineToken,
                                    onVolcengineTokenChange = { volcengineToken = it },
                                    volcengineCluster = volcengineCluster,
                                    onVolcengineClusterChange = { volcengineCluster = it },
                                    sfApiKey = sfApiKey,
                                    onSfApiKeyChange = { sfApiKey = it },
                                    sfCustomVoiceId = sfCustomVoiceId,
                                    onSfCustomVoiceIdChange = { sfCustomVoiceId = it },
                                    sfUseGlobalKey = sfUseGlobalKey,
                                    onSfUseGlobalKeyChange = { sfUseGlobalKey = it },
                                    sfTtsModel = sfTtsModel,
                                    onSfTtsModelChange = { sfTtsModel = it },
                                    sfSpeed = sfSpeed,
                                    onSfSpeedChange = { sfSpeed = it },
                                    sfGain = sfGain,
                                    onSfGainChange = { sfGain = it },
                                    sfSampleRate = sfSampleRate,
                                    onSfSampleRateChange = { sfSampleRate = it },
                                    sfUseCustomTts = sfUseCustomTts,
                                    onSfUseCustomTtsChange = { sfUseCustomTts = it },
                                    customTtsUrl = customTtsUrl,
                                    onCustomTtsUrlChange = { customTtsUrl = it },
                                    customTtsApiKey = customTtsApiKey,
                                    onCustomTtsApiKeyChange = { customTtsApiKey = it },
                                    customTtsModel = customTtsModel,
                                    onCustomTtsModelChange = { customTtsModel = it },
                                    customTtsVoiceId = customTtsVoiceId,
                                    onCustomTtsVoiceIdChange = { customTtsVoiceId = it },
                                    showSfModelDropdown = showSfModelDropdown,
                                    onShowSfModelDropdown = { showSfModelDropdown = it },
                                    showSfRateDropdown = showSfRateDropdown,
                                    onShowSfRateDropdown = { showSfRateDropdown = it },
                                    isDarkTheme = isDarkTheme,
                                    cardBg = cardBg,
                                    textPrimaryColor = textPrimaryColor,
                                    textSecondaryColor = textSecondaryColor,
                                    textTertiaryColor = textTertiaryColor
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isTesting = true
                                            testResult = null
                                            saveSettings()
                                            val result = ttsService.testProvider(selectedProvider)
                                            isTesting = false
                                            testResult = if (result) "✓ 连接成功" else "✗ 连接失败，请检查配置"
                                            snackbarHostState.showSnackbar(testResult!!)
                                        }
                                    },
                                    enabled = !isTesting,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PetalGreen.copy(alpha = 0.15f),
                                        contentColor = PetalGreen
                                    )
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = PetalGreen,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("测试连接", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isSynthesizing = true
                                            testResult = null
                                            saveSettings()
                                            
                                            if (selectedProvider == TtsProvider.ANDROID) {
                                                ttsService.setProvider(TtsProvider.ANDROID)
                                            }
                                            
                                            val audioPath = ttsService.testWithSampleText(
                                                selectedProvider,
                                                "你好，这是一个语音合成测试。"
                                            )
                                            isSynthesizing = false
                                            testResult = if (audioPath != null) "✓ 合成成功: ${audioPath.substringAfterLast("/")}" else "✗ 合成失败"
                                            snackbarHostState.showSnackbar(testResult!!)
                                        }
                                    },
                                    enabled = !isSynthesizing && !isTesting,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PetalPrimaryContainer,
                                        contentColor = PetalOnPrimaryContainer
                                    )
                                ) {
                                    if (isSynthesizing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = PetalOnPrimaryContainer,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("试听", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            testResult?.let {
                                Text(
                                    text = it,
                                    fontSize = 13.sp,
                                    color = if (it.contains("成功")) PetalGreen else PetalError,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // ── 聊天页分段队列 TTS 设置卡片 ──
                            ChatReadAloudSettingsCard(
                                chatTtsMode = chatTtsMode,
                                onModeSelect = { chatTtsMode = it; saveChatTtsSettings() },
                                showModeDropdown = showChatTtsModeDropdown,
                                onModeDropdownToggle = { showChatTtsModeDropdown = it },
                                skipParentheses = skipParentheses,
                                onSkipParenthesesChange = { skipParentheses = it; saveChatTtsSettings() },
                                autoDedup = chatTtsAutoDedup,
                                onAutoDedupChange = { chatTtsAutoDedup = it; saveChatTtsSettings() },
                                beautify = chatTtsBeautify,
                                onBeautifyChange = { chatTtsBeautify = it; saveChatTtsSettings() },
                                isDarkTheme = isDarkTheme,
                                cardBg = cardBg,
                                textPrimaryColor = textPrimaryColor,
                                textSecondaryColor = textSecondaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "启用 TTS 语音",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimaryColor
            )
            Text(
                text = "开启后 AI 回复将使用语音播放",
                fontSize = 12.sp,
                color = textSecondaryColor
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun ProviderSelectionCard(
    selectedProvider: TtsProvider,
    onProviderSelect: (TtsProvider) -> Unit,
    showDropdown: Boolean,
    onDropdownToggle: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp)
    ) {
        Text(
            text = "语音提供商",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimaryColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onDropdownToggle(!showDropdown) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedProvider.displayName,
                    fontSize = 14.sp,
                    color = textPrimaryColor
                )
                Icon(
                    imageVector = if (showDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = textSecondaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { onDropdownToggle(false) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                TtsProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = provider.displayName,
                                    color = textPrimaryColor,
                                    fontSize = 14.sp
                                )
                                if (provider == selectedProvider) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = PetalPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onProviderSelect(provider)
                            onDropdownToggle(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSelectionCard(
    voices: List<TtsVoice>,
    selectedVoiceId: String,
    onVoiceSelect: (String) -> Unit,
    showDropdown: Boolean,
    onDropdownToggle: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    val selectedVoice = voices.find { it.id == selectedVoiceId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp)
    ) {
        Text(
            text = "选择音色",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimaryColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onDropdownToggle(!showDropdown) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedVoice?.let { "${it.name} (${it.gender})" } ?: "请选择音色",
                    fontSize = 14.sp,
                    color = if (selectedVoice != null) textPrimaryColor else textSecondaryColor
                )
                Icon(
                    imageVector = if (showDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = textSecondaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { onDropdownToggle(false) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(
                                        text = "${voice.name} (${voice.gender})",
                                        color = textPrimaryColor,
                                        fontSize = 14.sp
                                    )
                                    if (voice.description.isNotEmpty()) {
                                        Text(
                                            text = voice.description,
                                            color = textSecondaryColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (voice.id == selectedVoiceId) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = PetalPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onVoiceSelect(voice.id)
                            onDropdownToggle(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyConfigCard(
    provider: TtsProvider,
    aliyunKey: String,
    onAliyunKeyChange: (String) -> Unit,
    aliyunSecret: String,
    onAliyunSecretChange: (String) -> Unit,
    aliyunAppKey: String,
    onAliyunAppKeyChange: (String) -> Unit,
    baiduKey: String,
    onBaiduKeyChange: (String) -> Unit,
    baiduSecret: String,
    onBaiduSecretChange: (String) -> Unit,
    xunfeiAppId: String,
    onXunfeiAppIdChange: (String) -> Unit,
    xunfeiKey: String,
    onXunfeiKeyChange: (String) -> Unit,
    xunfeiSecret: String,
    onXunfeiSecretChange: (String) -> Unit,
    azureKey: String,
    onAzureKeyChange: (String) -> Unit,
    azureRegion: String,
    onAzureRegionChange: (String) -> Unit,
    volcengineAppId: String,
    onVolcengineAppIdChange: (String) -> Unit,
    volcengineToken: String,
    onVolcengineTokenChange: (String) -> Unit,
    volcengineCluster: String,
    onVolcengineClusterChange: (String) -> Unit,
    sfApiKey: String,
    onSfApiKeyChange: (String) -> Unit,
    sfCustomVoiceId: String,
    onSfCustomVoiceIdChange: (String) -> Unit,
    sfUseGlobalKey: Boolean,
    onSfUseGlobalKeyChange: (Boolean) -> Unit,
    sfTtsModel: String,
    onSfTtsModelChange: (String) -> Unit,
    sfSpeed: String,
    onSfSpeedChange: (String) -> Unit,
    sfGain: String,
    onSfGainChange: (String) -> Unit,
    sfSampleRate: Int,
    onSfSampleRateChange: (Int) -> Unit,
    sfUseCustomTts: Boolean,
    onSfUseCustomTtsChange: (Boolean) -> Unit,
    customTtsUrl: String,
    onCustomTtsUrlChange: (String) -> Unit,
    customTtsApiKey: String,
    onCustomTtsApiKeyChange: (String) -> Unit,
    customTtsModel: String,
    onCustomTtsModelChange: (String) -> Unit,
    customTtsVoiceId: String,
    onCustomTtsVoiceIdChange: (String) -> Unit,
    showSfModelDropdown: Boolean,
    onShowSfModelDropdown: (Boolean) -> Unit,
    showSfRateDropdown: Boolean,
    onShowSfRateDropdown: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    textTertiaryColor: Color
) {
    val dividerColor = MaterialTheme.colorScheme.outline
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp)
    ) {
        Text(
            text = "${provider.displayName} 配置",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimaryColor
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (provider) {
            TtsProvider.ALIYUN -> {
                TtsTextField(value = aliyunKey, onValueChange = onAliyunKeyChange, label = "Access Key ID", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = aliyunSecret, onValueChange = onAliyunSecretChange, label = "Access Key Secret", isPassword = true, isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = aliyunAppKey, onValueChange = onAliyunAppKeyChange, label = "App Key", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
            }
            TtsProvider.BAIDU -> {
                TtsTextField(value = baiduKey, onValueChange = onBaiduKeyChange, label = "API Key", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = baiduSecret, onValueChange = onBaiduSecretChange, label = "Secret Key", isPassword = true, isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
            }
            TtsProvider.XUNFEI -> {
                TtsTextField(value = xunfeiAppId, onValueChange = onXunfeiAppIdChange, label = "App ID", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = xunfeiKey, onValueChange = onXunfeiKeyChange, label = "API Key", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = xunfeiSecret, onValueChange = onXunfeiSecretChange, label = "API Secret", isPassword = true, isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
            }
            TtsProvider.MICROSOFT -> {
                TtsTextField(value = azureKey, onValueChange = onAzureKeyChange, label = "Subscription Key", isPassword = true, isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = azureRegion, onValueChange = onAzureRegionChange, label = "Region (如: eastasia)", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
            }
            TtsProvider.VOLCENGINE -> {
                TtsTextField(value = volcengineAppId, onValueChange = onVolcengineAppIdChange, label = "App ID", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = volcengineToken, onValueChange = onVolcengineTokenChange, label = "Token", isPassword = true, isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = volcengineCluster, onValueChange = onVolcengineClusterChange, label = "Cluster (可选)", isDarkTheme = isDarkTheme, dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
            }
            TtsProvider.SILICONFLOW -> {
                // 提供者切换：默认 vs 自定义
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("使用自定义 TTS 端点", fontSize = 13.sp, color = textPrimaryColor)
                    Switch(checked = sfUseCustomTts, onCheckedChange = onSfUseCustomTtsChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = PetalPrimary))
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (sfUseCustomTts) {
                    // ── 自定义 TTS ──
                    TtsTextField(value = customTtsUrl, onValueChange = onCustomTtsUrlChange,
                        label = "自定义 TTS URL", isDarkTheme = isDarkTheme, dividerColor = dividerColor,
                        textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    TtsTextField(value = customTtsApiKey, onValueChange = onCustomTtsApiKeyChange,
                        label = "自定义 API Key", isPassword = true, isDarkTheme = isDarkTheme,
                        dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    TtsTextField(value = customTtsModel, onValueChange = onCustomTtsModelChange,
                        label = "自定义模型名称", isDarkTheme = isDarkTheme, dividerColor = dividerColor,
                        textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    TtsTextField(value = customTtsVoiceId, onValueChange = onCustomTtsVoiceIdChange,
                        label = "自定义音色 voice_id", isDarkTheme = isDarkTheme, dividerColor = dividerColor,
                        textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                } else {
                    // ── 硅基流动 ──
                    // 复用全局 Key 开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("复用全局 SiliconFlow API Key", fontSize = 13.sp, color = textPrimaryColor)
                        Switch(checked = sfUseGlobalKey, onCheckedChange = onSfUseGlobalKeyChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = PetalPrimary))
                    }
                    if (!sfUseGlobalKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TtsTextField(value = sfApiKey, onValueChange = onSfApiKeyChange,
                            label = "API Key", isPassword = true, isDarkTheme = isDarkTheme,
                            dividerColor = dividerColor, textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)
                    }

                    // 模型选择下拉
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("TTS 模型", fontSize = 13.sp, color = textSecondaryColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onShowSfModelDropdown(!showSfModelDropdown) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sfTtsModel.ifBlank { "未选择" }, fontSize = 14.sp, color = textPrimaryColor)
                            Icon(
                                if (showSfModelDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(expanded = showSfModelDropdown, onDismissRequest = { onShowSfModelDropdown(false) }) {
                            listOf(
                                "FunAudioLLM/CosyVoice2-0.5B" to "CosyVoice2 (推荐)",
                                "fnlp/MOSS-TTSD-v0.5" to "MOSS-TTSD"
                            ).forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    onSfTtsModelChange(value); onShowSfModelDropdown(false)
                                })
                            }
                        }
                    }

                    // 采样率选择
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("采样率", fontSize = 13.sp, color = textSecondaryColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onShowSfRateDropdown(!showSfRateDropdown) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${sfSampleRate} Hz", fontSize = 14.sp, color = textPrimaryColor)
                            Icon(
                                if (showSfRateDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(expanded = showSfRateDropdown, onDismissRequest = { onShowSfRateDropdown(false) }) {
                            listOf(8000, 16000, 22050, 44100).forEach { rate ->
                                DropdownMenuItem(text = { Text("$rate Hz") }, onClick = {
                                    onSfSampleRateChange(rate); onShowSfRateDropdown(false)
                                })
                            }
                        }
                    }

                    // 速度
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("语速: ${sfSpeed}", fontSize = 13.sp, color = textSecondaryColor)
                    Slider(
                        value = sfSpeed.toFloatOrNull() ?: 1.0f,
                        onValueChange = { onSfSpeedChange(String.format("%.1f", it)) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(thumbColor = PetalPrimary, activeTrackColor = PetalPrimary)
                    )

                    // 增益
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("增益: ${sfGain} dB", fontSize = 13.sp, color = textSecondaryColor)
                    Slider(
                        value = sfGain.toFloatOrNull() ?: 0f,
                        onValueChange = { onSfGainChange(String.format("%.0f", it)) },
                        valueRange = -10f..10f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(thumbColor = PetalPrimary, activeTrackColor = PetalPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                TtsTextField(value = sfCustomVoiceId, onValueChange = onSfCustomVoiceIdChange,
                    label = "自定义音色 voice_id (可选)", isDarkTheme = isDarkTheme, dividerColor = dividerColor,
                    textPrimaryColor = textPrimaryColor, textSecondaryColor = textSecondaryColor)

                // 音色定制平台链接
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("没有自定义音色？", fontSize = 12.sp, color = textSecondaryColor)
                    Text("前往添加>>", fontSize = 12.sp, color = PetalPrimary,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data = android.net.Uri.parse("https://voice.gbkgov.cn/")
                            context.startActivity(intent)
                        })
                }
            }
            TtsProvider.ANDROID -> {
                Text(
                    text = "使用系统内置 TTS 引擎，无需配置 API Key\n建议在系统设置中安装高质量TTS引擎以获得更好效果",
                    fontSize = 13.sp,
                    color = textSecondaryColor
                )
            }
            TtsProvider.SHERPA_LOCAL -> {
                // 本地离线 TTS 配置在 ApiKeyConfigCard 外部独立渲染（见 TtsSettingsScreen 主体）
                // 此分支不会到达，但 when 穷尽性要求覆盖
            }
        }
    }
}

/**
 * 本地离线 TTS 配置卡片（sherpa-onnx）。
 *
 * UI 结构：
 * - 模型选择下拉（LocalTtsCatalog.all）
 * - 状态行：未下载/下载中 X% (i/N)/就绪/已启用/失败
 * - 按钮组：下载/取消/启用/禁用/删除（按状态显示）
 * - sid 滑动条（多音色模型才显示）
 * - 速度滑动条
 * - 手动放置路径提示
 */
@Composable
private fun LocalTtsConfigContent(
    localTtsState: LocalTtsUiState,
    localTtsSpeed: Float,
    onLocalTtsSpeedChange: (Float) -> Unit,
    localTtsSid: Int,
    onLocalTtsSidChange: (Int) -> Unit,
    showModelDropdown: Boolean,
    onShowModelDropdown: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    context: Context
) {
    val model = localTtsState.model
    val status = localTtsState.status
    val isDownloading = status == LocalTtsUiStatus.DOWNLOADING
    val isReady = status == LocalTtsUiStatus.READY
    val isEnabled = status == LocalTtsUiStatus.ENABLED
    val canDownload = model.files.any { it.downloadUrl.isNotBlank() } && !isDownloading && !isEnabled

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 模型选择
        Text("本地模型", fontSize = 13.sp, color = textSecondaryColor)
        Box {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onShowModelDropdown(!showModelDropdown) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(model.displayName, fontSize = 14.sp, color = textPrimaryColor)
                Icon(
                    if (showModelDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = showModelDropdown, onDismissRequest = { onShowModelDropdown(false) }) {
                LocalTtsCatalog.all.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.displayName, fontSize = 14.sp) },
                        onClick = { onSelectModel(m.id); onShowModelDropdown(false) },
                        leadingIcon = if (m.id == model.id) {
                            { Icon(Icons.Filled.Check, null, tint = PetalGreen, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }

        // 状态行
        val statusText = when (status) {
            LocalTtsUiStatus.NOT_DOWNLOADED -> "未下载"
            LocalTtsUiStatus.DOWNLOADING -> {
                val pct = localTtsState.progressPercent
                val fi = localTtsState.currentFileIndex + 1
                val tot = localTtsState.totalFiles
                "下载中 $pct% ($fi/$tot)"
            }
            LocalTtsUiStatus.READY -> "就绪（点击启用）"
            LocalTtsUiStatus.ENABLED -> "已启用"
            LocalTtsUiStatus.FAILED -> "失败: ${localTtsState.errorMessage ?: "未知"}"
        }
        val statusColor = when (status) {
            LocalTtsUiStatus.ENABLED -> PetalGreen
            LocalTtsUiStatus.READY -> PetalPrimary
            LocalTtsUiStatus.FAILED -> PetalError
            LocalTtsUiStatus.DOWNLOADING -> textSecondaryColor
            LocalTtsUiStatus.NOT_DOWNLOADED -> textSecondaryColor
        }
        Text(statusText, fontSize = 13.sp, color = statusColor, fontWeight = FontWeight.Medium)

        // 按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canDownload) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PetalPrimaryContainer, contentColor = PetalOnPrimaryContainer)
                ) { Text("下载", fontSize = 13.sp) }
            }
            if (isDownloading) {
                Button(
                    onClick = onCancelDownload,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PetalError.copy(alpha = 0.15f), contentColor = PetalError)
                ) { Text("取消", fontSize = 13.sp) }
            }
            if (isReady) {
                Button(
                    onClick = onEnable,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PetalGreen.copy(alpha = 0.15f), contentColor = PetalGreen)
                ) { Text("启用", fontSize = 13.sp) }
            }
            if (isEnabled) {
                Button(
                    onClick = onDisable,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = textPrimaryColor)
                ) { Text("禁用", fontSize = 13.sp) }
            }
            if (status != LocalTtsUiStatus.NOT_DOWNLOADED && !isDownloading) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PetalError.copy(alpha = 0.1f), contentColor = PetalError)
                ) { Text("删除", fontSize = 13.sp) }
            }
        }

        // 无下载源提示
        if (status == LocalTtsUiStatus.NOT_DOWNLOADED && model.files.all { it.downloadUrl.isBlank() }) {
            Text(
                text = "未配置下载源。请手动将模型文件放入：\n${model.modelDir(context).absolutePath}",
                fontSize = 12.sp, color = textSecondaryColor
            )
        }

        // sid 滑动条（仅多音色模型显示）
        if (model.numSpeakers > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("音色 sid: $localTtsSid / ${model.numSpeakers - 1}", fontSize = 13.sp, color = textSecondaryColor)
            Slider(
                value = localTtsSid.toFloat(),
                onValueChange = { onLocalTtsSidChange(it.toInt()) },
                valueRange = 0f..(model.numSpeakers - 1).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = PetalPrimary, activeTrackColor = PetalPrimary)
            )
        }

        // 速度滑动条
        Spacer(modifier = Modifier.height(4.dp))
        Text("语速: ${String.format("%.1f", localTtsSpeed)}", fontSize = 13.sp, color = textSecondaryColor)
        Slider(
            value = localTtsSpeed,
            onValueChange = { onLocalTtsSpeedChange(String.format("%.1f", it).toFloat()) },
            valueRange = 0.5f..2.0f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = PetalPrimary, activeTrackColor = PetalPrimary)
        )

        // 说明
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "sherpa-onnx 端上推理，无需联网。\n模型文件需放入上述目录后点击「启用」。",
            fontSize = 12.sp, color = textSecondaryColor
        )
    }
}

@Composable
private fun TtsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    isDarkTheme: Boolean,
    dividerColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = textSecondaryColor) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PetalPrimary,
            unfocusedBorderColor = dividerColor,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = textPrimaryColor,
            unfocusedTextColor = textPrimaryColor
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true
    )
}

/**
 * 聊天页分段队列 TTS 设置卡片。
 *
 * 参考反编译代码的 SettingsActivity：
 * - KEY_VOICE_MODE（静音/语音条/朗读）
 * - key_skip_parentheses（跳过括号内心戏）
 * - KEY_VOICE_AUTO_DEDUP（自动去重）
 * - KEY_VOICE_BEAUTIFY（音频美化）
 */
@Composable
private fun ChatReadAloudSettingsCard(
    chatTtsMode: ChatTtsMode,
    onModeSelect: (ChatTtsMode) -> Unit,
    showModeDropdown: Boolean,
    onModeDropdownToggle: (Boolean) -> Unit,
    skipParentheses: Boolean,
    onSkipParenthesesChange: (Boolean) -> Unit,
    autoDedup: Boolean,
    onAutoDedupChange: (Boolean) -> Unit,
    beautify: Boolean,
    onBeautifyChange: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "聊天页朗读",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimaryColor
        )
        Text(
            text = "AI 回复落地后按句子边界分段朗读，边合成边播，支持防重复与音频美化",
            fontSize = 12.sp,
            color = textSecondaryColor
        )

        // 朗读模式选择
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onModeDropdownToggle(!showModeDropdown) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "朗读模式",
                        fontSize = 14.sp,
                        color = textPrimaryColor
                    )
                    Text(
                        text = chatTtsMode.displayName + " · " + chatTtsMode.description,
                        fontSize = 12.sp,
                        color = textSecondaryColor
                    )
                }
                Icon(
                    imageVector = if (showModeDropdown) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = textSecondaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showModeDropdown,
                onDismissRequest = { onModeDropdownToggle(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                ChatTtsMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(mode.displayName, fontSize = 14.sp, color = textPrimaryColor)
                                Text(mode.description, fontSize = 12.sp, color = textSecondaryColor)
                            }
                        },
                        onClick = { onModeSelect(mode) },
                        leadingIcon = {
                            if (mode == chatTtsMode) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = PetalGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }
        }

        // 跳过括号内心戏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("跳过括号内心戏", fontSize = 14.sp, color = textPrimaryColor)
                Text("不朗读 <...> (...) （...） 内的内容", fontSize = 12.sp, color = textSecondaryColor)
            }
            Switch(
                checked = skipParentheses,
                onCheckedChange = onSkipParenthesesChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // 自动去重
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动去重", fontSize = 14.sp, color = textPrimaryColor)
                Text("不重复朗读已读过的内容", fontSize = 12.sp, color = textSecondaryColor)
            }
            Switch(
                checked = autoDedup,
                onCheckedChange = onAutoDedupChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // 音频美化
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("音频美化", fontSize = 14.sp, color = textPrimaryColor)
                Text("使用均衡器预设优化人声（部分设备不支持）", fontSize = 12.sp, color = textSecondaryColor)
            }
            Switch(
                checked = beautify,
                onCheckedChange = onBeautifyChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
