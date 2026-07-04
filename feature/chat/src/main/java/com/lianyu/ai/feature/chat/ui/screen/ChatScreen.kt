package com.lianyu.ai.feature.chat.ui.screen

import android.util.Log

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import com.lianyu.ai.uicommon.theme.ThemeViewModel
import com.lianyu.ai.uicommon.theme.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.math.abs
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.unit.DpOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.lianyu.ai.feature.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity as CompanionModel
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.feature.chat.ui.viewmodel.ChatViewModel
import com.lianyu.ai.feature.chat.ui.viewmodel.ChatViewModelFactory
import com.lianyu.ai.feature.chat.ui.viewmodel.ChatUiEvent
import com.lianyu.ai.feature.chat.data.ChatDetailSettingsStore
import com.lianyu.ai.uicommon.component.WeChatChatInputBar
import com.lianyu.ai.uicommon.component.ChatInputExtensionPanel
import com.lianyu.ai.uicommon.component.StickerPanel
import com.lianyu.ai.common.StickerInfo
import com.lianyu.ai.common.StickerManager
import com.lianyu.ai.uicommon.component.CompanionAvatar
import com.lianyu.ai.uicommon.component.UserAvatar
import com.lianyu.ai.uicommon.component.VoiceRecorder
import com.lianyu.ai.uicommon.component.VoiceMessageBubble
import com.lianyu.ai.uicommon.component.getChatBackground
import com.lianyu.ai.uicommon.component.getChatBackgroundByKey
import com.lianyu.ai.uicommon.component.getChatBackgroundKey
import com.lianyu.ai.uicommon.component.isCustomBackground
import com.lianyu.ai.uicommon.component.rememberBackgroundBitmap
import com.lianyu.ai.network.tts.ChatTtsMode
import com.lianyu.ai.feature.chat.voice.ChatTtsState
import com.lianyu.ai.uicommon.theme.AdaptiveSizing
import com.lianyu.ai.uicommon.theme.rememberAdaptiveSizing
import com.lianyu.ai.common.ReadStatusManager
import com.lianyu.ai.common.HardwareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun ChatScreen(
    companionId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToVoiceCall: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val stickerManager = remember { StickerManager.getInstance(context) }

    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext as Application, companionId)
    )
    var showExtensionPanel by remember { mutableStateOf(false) }

    // File picker for sticker import
    val stickerPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val path = copyUriToCache(context, it)
                    if (path != null) {
                        val count = stickerManager.importStickerZip(path)
                        snackbarHostState.showSnackbar("成功导入 $count 个表情包")
                    } else {
                        snackbarHostState.showSnackbar("文件读取失败")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("导入失败: ${e.message}")
                }
            }
        }
    }

    // Image picker for album
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            showExtensionPanel = false
            scope.launch {
                try {
                    val imagePath = copyUriToCache(context, it)
                    if (imagePath != null) {
                        viewModel.sendImageMessage(imagePath)
                    } else {
                        snackbarHostState.showSnackbar("图片读取失败")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("图片处理失败: ${e.message}")
                }
            }
        }
    }

    // Camera launcher for taking photos
    var cameraPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            showExtensionPanel = false
            cameraPhotoUri?.let { photoUri ->
                scope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(photoUri)
                        val cacheFile = java.io.File(context.cacheDir, "sent_photo_${System.currentTimeMillis()}.jpg")
                        inputStream?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        viewModel.sendImageMessage(cacheFile.absolutePath)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("拍照失败: ${e.message}")
                    }
                }
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val photoFile = java.io.File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            photoFile.parentFile?.mkdirs()
            photoFile.createNewFile()
            val photoUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.lianyu.fileprovider", photoFile
            )
            cameraPhotoUri = photoUri
            cameraLauncher.launch(photoUri)
        } else {
            scope.launch { snackbarHostState.showSnackbar("相机权限被拒绝") }
        }
    }

    // Audio recording permission launcher with pending action
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            pendingAudioAction?.invoke()
        } else {
            scope.launch { snackbarHostState.showSnackbar("需要麦克风权限才能使用语音功能") }
        }
        pendingAudioAction = null
    }

    // Video picker for album
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            showExtensionPanel = false
            scope.launch {
                try {
                    val videoPath = copyUriToCache(context, it)
                    if (videoPath != null) {
                        viewModel.sendVideoMessage(videoPath)
                    } else {
                        snackbarHostState.showSnackbar("视频读取失败")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("视频处理失败: ${e.message}")
                }
            }
        }
    }

    val userAvatar by viewModel.userAvatar.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMoreMessages by viewModel.hasMoreMessages.collectAsState()
    val companionData by viewModel.companionData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val typingText by viewModel.typingText.collectAsState()
    val isRegenerating by viewModel.isRegenerating.collectAsState()
    val isReasoning by viewModel.isReasoning.collectAsState()
    val reasoningText by viewModel.reasoningText.collectAsState()
    val availableApis by viewModel.availableApis.collectAsState()
    val currentApi by viewModel.currentApi.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 聊天页 TTS 朗读状态
    val ttsState by viewModel.ttsState.collectAsState()
    val ttsConfig = remember { viewModel.getTtsConfig() }
    var ttsModeMenu by remember { mutableStateOf(false) }

    val themeViewModel: ThemeViewModel = viewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val perfTier = remember { HardwareInfo.tier }
    val adaptiveSizing = rememberAdaptiveSizing()

    // Per-companion chat settings
    val settingsStore = remember { ChatDetailSettingsStore(context) }
    val detailSettings by settingsStore.settingsFlow(companionId).collectAsState(initial = com.lianyu.ai.feature.chat.data.CompanionChatDetailSettings())

    LaunchedEffect(Unit) {
        ReadStatusManager.markAsRead(context, companionId)
        viewModel.refreshCompanionData()
    }

    // 收集 ViewModel 一次性副作用事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatUiEvent.Error -> snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Long
                )
                is ChatUiEvent.ContentBlocked -> snackbarHostState.showSnackbar(
                    message = "内容已拦截: ${event.reason}",
                    duration = SnackbarDuration.Long
                )
                is ChatUiEvent.Info -> snackbarHostState.showSnackbar(event.message)
                is ChatUiEvent.StreamCompleted -> { /* 流式完成，不需要用户感知 */ }
            }
        }
    }

    var hasScrolledToBottom by remember { mutableStateOf(false) }

    // LazyColumn 实际 item 总数（与 LazyColumn 内部 item 声明保持一致）
    val itemCount = messages.size +
        (if (isLoadingMore) 1 else 0) +
        (if (isTyping) 1 else 0) +
        (if (isRegenerating) 1 else 0) +
        (if (isReasoning && reasoningText.isNotBlank()) 1 else 0)

    // 列表状态始终存在；空消息列表时不显示转圈，而是正常展示输入栏
    val listState = remember { LazyListState() }

    // 首次有消息时同步滚动到底部，避免首帧闪现顶部
    LaunchedEffect(messages.isNotEmpty()) {
        if (messages.isNotEmpty() && !hasScrolledToBottom) {
            hasScrolledToBottom = true
            listState.scrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    // 检查当前是否在底部附近
    val isAtBottom = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index >= totalItems - 2
        }
    }

    // 通过 snapshotFlow 追踪用户是否在底部附近，避免布局更新时序导致的误判
    var wasAtBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { isAtBottom.value }.collect { nearBottom ->
            wasAtBottom = nearBottom
        }
    }

    // 新消息时自动滚动到底部：用户消息始终滚动，AI 消息仅在用户位于底部时滚动
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastMessage = messages.lastOrNull()
        val isMyMessage = lastMessage?.isFromUser == true
        if (wasAtBottom || isMyMessage) {
            val currentItemCount = messages.size +
                (if (isLoadingMore) 1 else 0) +
                (if (isTyping) 1 else 0) +
                (if (isRegenerating) 1 else 0) +
                (if (isReasoning && reasoningText.isNotBlank()) 1 else 0)
            listState.scrollToItem((currentItemCount - 1).coerceAtLeast(0))
        }
    }

    // AI 流式回复时持续滚动到底部（typingText 变化但消息数不变）
    LaunchedEffect(typingText) {
        if (typingText.isNotBlank() && wasAtBottom) {
            listState.scrollToItem((itemCount - 1).coerceAtLeast(0))
        }
    }

    // AI 深度推理时持续滚动到底部（reasoningText 变化但消息数不变）
    LaunchedEffect(reasoningText) {
        if (reasoningText.isNotBlank() && wasAtBottom) {
            listState.scrollToItem((itemCount - 1).coerceAtLeast(0))
        }
    }

    // 键盘弹出时滚动到底部
    LaunchedEffect(WindowInsets.ime.getBottom(LocalDensity.current)) {
        if (itemCount > 0) listState.scrollToItem((itemCount - 1).coerceAtLeast(0))
    }

    // ── 上划加载历史消息 ──
    var prevMessageCount by remember { mutableStateOf(0) }
    var isLoadingMoreTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(listState.firstVisibleItemIndex, hasMoreMessages) {
        // 首次进入时列表可能尚未滚动到底，避免此时误触发加载历史
        if (!hasScrolledToBottom) return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 2 && hasMoreMessages && !isLoadingMore && !isLoadingMoreTriggered) {
            isLoadingMoreTriggered = true
            prevMessageCount = messages.size
            viewModel.loadMoreHistory()
        }
    }

    // 加载完成后恢复滚动位置
    LaunchedEffect(isLoadingMore, prevMessageCount) {
        if (!isLoadingMore && prevMessageCount > 0 && messages.size > prevMessageCount) {
            val addedCount = messages.size - prevMessageCount
            listState.scrollToItem(addedCount.coerceAtLeast(0), scrollOffset = 0)
            prevMessageCount = 0
            isLoadingMoreTriggered = false
        }
    }

    // Background: per-companion > global > default
    val defaultBackground = MaterialTheme.colorScheme.background
    var targetBgColor by remember { mutableStateOf(defaultBackground) }
    var chatBgGradient by remember { mutableStateOf<Brush?>(null) }
    var isCustomBg by remember { mutableStateOf(false) }
    var customBgKey by remember { mutableStateOf("") }
    val customBgPainter = if (isCustomBg && customBgKey.isNotEmpty()) {
        rememberBackgroundBitmap(customBgKey)
    } else null
    val globalBgKey = getChatBackgroundKey(context)
    LaunchedEffect(detailSettings, isDarkTheme, globalBgKey) {
        withContext(Dispatchers.IO) {
            val effectiveKey = if (detailSettings.useGlobalBackground || detailSettings.backgroundKey == null) {
                globalBgKey
            } else {
                detailSettings.backgroundKey ?: "default"
            }
            val (color, gradient) = if (isCustomBackground(effectiveKey)) {
                Color.Transparent to null
            } else {
                getChatBackgroundByKey(context, effectiveKey, isDarkTheme)
            }
            val custom = isCustomBackground(effectiveKey)
            withContext(Dispatchers.Main) {
                targetBgColor = color
                chatBgGradient = gradient
                isCustomBg = custom
                customBgKey = if (custom) effectiveKey else ""
            }
        }
    }

    val chatBgColor by animateColorAsState(targetBgColor, tween(300), label = "bgColor")
    val backgroundColor = if (isDarkTheme && !isCustomBg) {
        MaterialTheme.colorScheme.background
    } else if (isDarkTheme && isCustomBg) {
        MaterialTheme.colorScheme.background
    } else {
        chatBgColor
    }

    val glassIntensity = when (perfTier) {
        HardwareInfo.Tier.ULTRA -> 1.0f
        HardwareInfo.Tier.HIGH -> 0.85f
        HardwareInfo.Tier.MEDIUM -> 0.5f
        HardwareInfo.Tier.LOW -> 0.2f
    }

    // Voice recording
    var showStickerPanel by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var isCanceling by remember { mutableStateOf(false) }
    val voiceRecorder = remember { VoiceRecorder.getInstance(context) }

    // Voice recording timer & actual recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            voiceRecorder.start()
            while (isRecording) {
                delay(1000)
                if (isRecording) recordingDuration++
            }
        }
    }

    // Sticker data loaded from StickerManager
    val stickers = remember { mutableStateListOf<StickerInfo>() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        )

        // Background layer
        if (isCustomBg && customBgPainter != null) {
            Image(
                painter = customBgPainter,
                contentDescription = stringResource(R.string.chat_background),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Main content - Column with messages and input only
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .then(
                    if (isCustomBg) Modifier.background(Color.Transparent)
                    else if (!isDarkTheme && chatBgGradient != null) Modifier.background(chatBgGradient!!)
                    else Modifier.background(backgroundColor)
                )
        ) {
            // Messages list - takes full space, top bar is overlay
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(rememberHorizontalSwipeGuard())
                    .pointerInput(Unit) { detectTapGestures { keyboardController?.hide() } },
                contentPadding = PaddingValues(
                    start = adaptiveSizing.listHorizontalPadding, end = adaptiveSizing.listHorizontalPadding,
                    top = 120.dp,
                    bottom = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 顶部加载指示器
                if (isLoadingMore) {
                    item(key = "load_more_indicator") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "加载更早的消息...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    if (message.id > 0 && message.id % 5 == 0L) {
                        android.util.Log.w("ChatScreen", "[ChatScreen] rendering message id=${message.id} content='${message.content.take(20)}'")
                    }
                    ChatBubble(
                        message = message,
                        companionData = companionData,
                        isUser = message.isFromUser,
                        userAvatar = userAvatar,
                        userName = userName,
                        onVoiceClick = { /* TODO: play voice message */ },
                        onRecall = { msg -> viewModel.recallMessage(msg) },
                        onRegenerate = { msg -> viewModel.regenerateMessage(msg) },
                        adaptiveSizing = adaptiveSizing,
                        isDarkTheme = isDarkTheme
                    )
                }

                if (isTyping) {
                    item(key = "typing_indicator") {
                        TypingIndicatorBubble(
                            companionData = companionData,
                            typingText = typingText,
                            adaptiveSizing = adaptiveSizing,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }

                if (isRegenerating) {
                    item(key = "regenerating_indicator") {
                        RegeneratingBubble(
                            companionData = companionData,
                            adaptiveSizing = adaptiveSizing
                        )
                    }
                }

                if (isReasoning && reasoningText.isNotBlank()) {
                    item(key = "reasoning_indicator") {
                        ReasoningBubble(
                            reasoningText = reasoningText,
                            adaptiveSizing = adaptiveSizing
                        )
                    }
                }
            }

            // Bottom panels and input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // 聊天页 TTS 朗读状态条（朗读中显示，可停止）
                if (ttsState == ChatTtsState.SPEAKING) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI 正在朗读...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.stopTts() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "停止朗读",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Sticker panel
                StickerPanel(
                    isVisible = showStickerPanel,
                    onStickerClick = { sticker ->
                        viewModel.sendSticker(sticker)
                        showStickerPanel = false
                    },
                    onImportClick = {
                        stickerPickerLauncher.launch("application/zip")
                    },
                    onDeleteAllClick = {
                        scope.launch {
                            val manager = StickerManager.getInstance(context)
                            val success = manager.deleteAllImportedStickers()
                            if (success) {
                                snackbarHostState.showSnackbar("已删除全部表情包")
                            } else {
                                snackbarHostState.showSnackbar("删除失败")
                            }
                        }
                    }
                )

                // Extension panel
                ChatInputExtensionPanel(
                    isVisible = showExtensionPanel,
                    availableApis = availableApis,
                    currentApi = currentApi,
                    onSwitchApi = { provider -> viewModel.switchApi(provider) },
                    onAlbumClick = {
                        imagePickerLauncher.launch("image/*")
                    },
                    onCameraClick = {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    onVideoCallClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            onNavigateToVoiceCall(companionId)
                        } else {
                            pendingAudioAction = { onNavigateToVoiceCall(companionId) }
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onLocationClick = { /* TODO: share location */ },
                    onVoiceInputClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            showExtensionPanel = false
                            showVoiceRecorder = true
                        } else {
                            pendingAudioAction = {
                                showExtensionPanel = false
                                showVoiceRecorder = true
                            }
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStickerClick = {
                        showExtensionPanel = false
                        showStickerPanel = !showStickerPanel
                    }
                )

                // Input bar
                val isBlocked = detailSettings.blocked
                if (isBlocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "你已拉黑该联系人",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        WeChatChatInputBar(
                            onSendMessage = { msg ->
                                android.util.Log.w("ChatScreen", "[ChatScreen] onSendMessage: '${msg.take(30)}'")
                                viewModel.sendMessage(msg)
                            },
                            isLoading = isLoading,
                            availableApis = availableApis,
                            currentApi = currentApi,
                            onSwitchApi = { provider ->
                                viewModel.switchApi(provider)
                            },
                            onPlusClick = {
                                showExtensionPanel = !showExtensionPanel
                                showStickerPanel = false
                            },
                            onVoiceRecordStart = {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    showExtensionPanel = false
                                    showStickerPanel = false
                                    isCanceling = false
                                    isRecording = true
                                    showVoiceRecorder = true
                                } else {
                                    pendingAudioAction = {
                                        showExtensionPanel = false
                                        showStickerPanel = false
                                        isCanceling = false
                                        isRecording = true
                                        showVoiceRecorder = true
                                    }
                                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onVoiceRecordStop = {
                                val audioPath = voiceRecorder.stop()
                                isRecording = false
                                showVoiceRecorder = false
                                if (audioPath != null && recordingDuration >= 1) {
                                    viewModel.sendVoiceMessage(audioPath, recordingDuration)
                                }
                            },
                            onVoiceRecordCancel = {
                                voiceRecorder.cancel()
                                isRecording = false
                                showVoiceRecorder = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Voice recording overlay
        if (showVoiceRecorder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "录音中...",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${recordingDuration}s",
                        fontSize = 48.sp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "点击按钮操作",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    val audioPath = voiceRecorder.stop()
                                    isRecording = false
                                    showVoiceRecorder = false
                                    if (audioPath != null && recordingDuration >= 1) {
                                        viewModel.sendVoiceMessage(audioPath, recordingDuration)
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "发送",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 14.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.error)
                                .clickable {
                                    voiceRecorder.cancel()
                                    isRecording = false
                                    showVoiceRecorder = false
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "取消",
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Floating top bar - overlay on top of messages
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 48.dp,
                    start = 16.dp, end = 16.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
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
                        contentDescription = stringResource(R.string.send),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isLoading,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        },
                        label = "title_switch"
                    ) { loading ->
                        if (loading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.typing),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CompanionAvatar(
                                    avatarUrl = companionData?.avatarUrl,
                                    name = companionData?.name,
                                    size = 24.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = companionData?.name ?: "",
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // AI生成标识
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = if (isDarkTheme) 0.2f else 0.12f
                                            )
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "AI生成仅供参考",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onNavigateToDetail(companionId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "详情",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 聊天页 TTS 朗读模式切换按钮（静音/语音条/语音朗读）
                Box {
                    IconButton(
                        onClick = { ttsModeMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (ttsConfig.mode == ChatTtsMode.SILENT)
                                Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = "朗读模式",
                            tint = if (ttsConfig.mode == ChatTtsMode.SILENT)
                                MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = ttsModeMenu,
                        onDismissRequest = { ttsModeMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        ChatTtsMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = mode.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setTtsMode(mode)
                                    ttsModeMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (mode == ChatTtsMode.SILENT)
                                            Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = if (mode == ttsConfig.mode) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // Voice call button
                IconButton(
                    onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            onNavigateToVoiceCall(companionId)
                        } else {
                            pendingAudioAction = { onNavigateToVoiceCall(companionId) }
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "语音通话",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
