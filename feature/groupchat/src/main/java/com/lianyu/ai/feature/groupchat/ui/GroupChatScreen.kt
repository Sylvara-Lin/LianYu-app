package com.lianyu.ai.feature.groupchat.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.lianyu.ai.uicommon.theme.ThemeViewModel
import com.lianyu.ai.uicommon.theme.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lianyu.ai.uicommon.component.rememberHorizontalSwipeGuard
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.lianyu.ai.feature.groupchat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lianyu.ai.database.model.GroupMessage
import com.lianyu.ai.common.StickerManager
import com.lianyu.ai.common.StickerInfo
import com.lianyu.ai.common.PermissionManager
import com.lianyu.ai.uicommon.component.ChatInputExtensionPanel
import com.lianyu.ai.uicommon.component.StickerPanel
import com.lianyu.ai.uicommon.component.getChatBackground
import com.lianyu.ai.uicommon.component.getChatBackgroundKey
import com.lianyu.ai.uicommon.component.isCustomBackground
import com.lianyu.ai.uicommon.component.rememberBackgroundBitmap
import com.lianyu.ai.feature.groupchat.GroupChatViewModel
import com.lianyu.ai.feature.groupchat.GroupChatViewModelFactory
import com.lianyu.ai.common.HardwareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val stickerManager = remember { StickerManager.getInstance(context) }

    val viewModel: GroupChatViewModel = viewModel(
        factory = GroupChatViewModelFactory(context.applicationContext as Application, groupId)
    )
    val userAvatar by viewModel.userAvatar.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val groupData by viewModel.groupData.collectAsState()
    val companions by viewModel.allCompanions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRegenerating by viewModel.isRegenerating.collectAsState()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val systemInDarkTheme = isSystemInDarkTheme()
    val themeViewModel: ThemeViewModel = viewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemInDarkTheme
    }
    val perfTier = remember { HardwareInfo.tier }

    var showExtensionPanel by remember { mutableStateOf(false) }
    var showStickerPanel by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch { snackbarHostState.showSnackbar("相机功能开发中...") }
        } else {
            if (PermissionManager.shouldShowRationale(context as android.app.Activity, PermissionManager.CAMERA)) {
                scope.launch { snackbarHostState.showSnackbar(PermissionManager.getPermissionRationale(PermissionManager.CAMERA)) }
            } else {
                scope.launch { snackbarHostState.showSnackbar("相机权限被拒绝，请在设置中开启") }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val path = copyUriToCache(context, it, "image")
                    if (path != null) {
                        viewModel.sendImageMessage(path)
                    } else {
                        snackbarHostState.showSnackbar("图片读取失败")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("发送图片失败: ${e.message}")
                }
            }
        }
    }

    fun handleAlbumClick() {
        showExtensionPanel = false
        if (PermissionManager.canPickImageWithoutPermission()) {
            imagePickerLauncher.launch("image/*")
        } else {
            val requiredPermissions = PermissionManager.getImagePickPermissions()
            if (requiredPermissions.isEmpty()) {
                imagePickerLauncher.launch("image/*")
            } else if (PermissionManager.hasPermissions(context, requiredPermissions.toList())) {
                imagePickerLauncher.launch("image/*")
            } else {
                scope.launch { snackbarHostState.showSnackbar("需要存储权限才能选择图片") }
            }
        }
    }

    fun handleCameraClick() {
        showExtensionPanel = false
        if (PermissionManager.hasPermission(context, PermissionManager.CAMERA)) {
            scope.launch { snackbarHostState.showSnackbar("相机功能开发中...") }
        } else {
            cameraPermissionLauncher.launch(PermissionManager.CAMERA)
        }
    }

    val stickerPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val path = copyUriToCache(context, it, "sticker")
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

    // ====== 关键优化：导航动画完成后再渲染消息列表 ======
    var messagesReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        messagesReady = true
    }

    var hasDoneInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(messages, messagesReady) {
        if (messagesReady && messages.isNotEmpty() && !hasDoneInitialScroll) {
            listState.scrollToItem((messages.size - 1).coerceAtLeast(0))
            hasDoneInitialScroll = true
        }
    }

    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        Log.d("HIIR","触发下滑:${lastMessageId}")
        if (hasDoneInitialScroll && messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            listState.scrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    // Background: global > default
    val defaultBgColor = MaterialTheme.colorScheme.background
    var targetBgColor by remember { mutableStateOf(defaultBgColor) }
    var chatBgGradient by remember { mutableStateOf<Brush?>(null) }
    var isCustomBg by remember { mutableStateOf(false) }
    var customBgKey by remember { mutableStateOf("") }
    val customBgPainter = if (isCustomBg && customBgKey.isNotEmpty()) {
        rememberBackgroundBitmap(customBgKey)
    } else null
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val effectiveKey = getChatBackgroundKey(context)
            val (color, gradient) = if (isCustomBackground(effectiveKey)) {
                Color.Transparent to null
            } else {
                getChatBackground(context, isDarkTheme)
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
    val backgroundColor = if (isDarkTheme) MaterialTheme.colorScheme.background else chatBgColor

    val glassIntensity = when (perfTier) {
        HardwareInfo.Tier.ULTRA -> 1.0f
        HardwareInfo.Tier.HIGH -> 0.85f
        HardwareInfo.Tier.MEDIUM -> 0.5f
        HardwareInfo.Tier.LOW -> 0.2f
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        // Main content
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
            // Messages list
            if (messagesReady) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(rememberHorizontalSwipeGuard())
                        .pointerInput(Unit) { detectTapGestures { keyboardController?.hide() } },
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp,
                        top = 120.dp, // Space for floating top bar
                        bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val companion = companions.find { it.id == message.companionId }
                        GroupChatBubble(
                            message = message, companion = companion,
                            isUser = message.companionId == -1L,
                            userAvatar = userAvatar, userName = userName
                        )
                    }

                    if (isRegenerating) {
                        item(key = "regenerating_indicator") {
                            GroupRegeneratingBubble()
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth())
            }

            // Input bar at bottom
            var showMentionPicker by remember { mutableStateOf(false) }
            var inputText by remember { mutableStateOf("") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                StickerPanel(
                    isVisible = showStickerPanel,
                    onStickerClick = { sticker ->
                        viewModel.sendUserSticker(sticker)
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

                ChatInputExtensionPanel(
                    isVisible = showExtensionPanel,
                    onAlbumClick = { handleAlbumClick() },
                    onCameraClick = { handleCameraClick() },
                    onVideoCallClick = {},
                    onLocationClick = {},
                    onVoiceInputClick = {},
                    onStickerClick = {
                        showExtensionPanel = false
                        showStickerPanel = !showStickerPanel
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 加号按钮
                        IconButton(
                            onClick = {
                                showExtensionPanel = !showExtensionPanel
                                showStickerPanel = false
                                showMentionPicker = false
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "更多功能",
                                tint = if (showExtensionPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // @艾特按钮
                        IconButton(
                            onClick = { showMentionPicker = !showMentionPicker },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AlternateEmail,
                                contentDescription = "@艾特",
                                tint = if (showMentionPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 输入框
                        Box(
                            modifier = Modifier.weight(1f).height(40.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 0.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (inputText.isEmpty()) {
                                            Text("输入消息...",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        innerTextField()
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (inputText.isNotBlank() && !isLoading) {
                                            viewModel.sendMessage(inputText.trim())
                                            inputText = ""
                                        }
                                    }
                                ),
                                maxLines = 4,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        // 发送按钮
                        val canSend = inputText.isNotBlank() && !isLoading
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = canSend) {
                                    viewModel.sendMessage(inputText.trim())
                                    inputText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "发送",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 角色选择器弹窗
                AnimatedVisibility(
                    visible = showMentionPicker && companions.isNotEmpty(),
                    enter = androidx.compose.animation.expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                    exit = androidx.compose.animation.shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
                ) {
                    val activeCompanionIds = groupData?.getCompanionIdList() ?: emptyList()
                    val activeCompanions = companions.filter { activeCompanionIds.contains(it.id) }

                    if (activeCompanions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "选择要@的角色",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeCompanions.forEach { companion ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                inputText = "$inputText@${companion.name} "
                                                showMentionPicker = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 角色头像
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (companion.avatarUrl != null) {
                                                AsyncImage(
                                                    model = companion.avatarUrl,
                                                    contentDescription = companion.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    text = companion.name.firstOrNull()?.toString() ?: "?",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = companion.name,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating top bar - overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
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
                        contentDescription = stringResource(R.string.group_chat),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isLoading,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
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
                                    text = stringResource(R.string.group_typing),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 群头像
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (groupData?.avatarUrl != null) {
                                        AsyncImage(
                                            model = groupData?.avatarUrl,
                                            contentDescription = groupData?.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = groupData?.name?.firstOrNull()?.toString() ?: "?",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = groupData?.name ?: stringResource(R.string.group_chat),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                // 群详情按钮
                IconButton(
                    onClick = { onNavigateToDetail(groupId) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "群详情",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupImageMessageBubble(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    // TODO(C3): Use EncryptedFileHelper for transparent decryption of media files
    // val helper = EncryptedFileHelper(context)
    val imageFile = remember(imagePath) { File(imagePath) }

    if (imageFile.exists()) {
        AsyncImage(
            model = imageFile.absolutePath,
            contentDescription = "图片",
            modifier = modifier
                .widthIn(max = 200.dp)
                .heightIn(max = 260.dp)
                .width(180.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier
                .size(120.dp, 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "图片",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun copyUriToCache(context: android.content.Context, uri: Uri, prefix: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val extension = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "application/zip" -> "zip"
            else -> "tmp"
        }
        val fileName = "${prefix}_${System.currentTimeMillis()}.$extension"
        // TODO(C3): Use EncryptedFileHelper for transparent encryption of media on write
        // val helper = EncryptedFileHelper(context)
        val cacheFile = File(context.cacheDir, fileName)
        inputStream.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        cacheFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun GroupStickerMessageBubble(
    stickerName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(stickerName) {
        val manager = StickerManager.getInstance(context)
        var sticker = manager.findStickerByDescription(stickerName)
        if (sticker == null && !stickerName.endsWith(".png")) {
            sticker = manager.findStickerByDescription("$stickerName.png")
        }
        if (sticker != null) {
            bitmap = manager.loadStickerBitmap(sticker.path)
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = stickerName,
            modifier = modifier
                .sizeIn(maxWidth = 140.dp, maxHeight = 140.dp)
                .width(120.dp)
                .height(120.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stickerName.take(2),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GroupRegeneratingBubble() {
    val aiBubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val infiniteTransition = rememberInfiniteTransition(label = "group_regenerate")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, 150), RepeatMode.Reverse), label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, 300), RepeatMode.Reverse), label = "dot3"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Spacer(modifier = Modifier.width(48.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(aiBubbleColor)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "正在重新生成",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                repeat(3) { i ->
                    val alpha = listOf(dot1Alpha, dot2Alpha, dot3Alpha)[i]
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .alpha(alpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    if (i < 2) Spacer(modifier = Modifier.width(3.dp))
                }
            }
        }
    }
}

@Composable
fun GroupChatBubble(
    message: GroupMessage,
    companion: com.lianyu.ai.database.model.CompanionEntity?,
    isUser: Boolean,
    userAvatar: String?,
    userName: String
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time = remember(message.timestamp) { dateFormat.format(Date(message.timestamp)) }

    val userBubbleColor = MaterialTheme.colorScheme.primary
    val aiBubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (companion?.avatarUrl != null) {
                    AsyncImage(
                        model = companion.avatarUrl,
                        contentDescription = companion.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = companion?.name?.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (!isUser && companion != null) {
                Text(
                    text = companion.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .drawBehind {
                        val cr = 18.dp.toPx()
                        val tw = 6.dp.toPx()
                        val th = 8.dp.toPx()
                        val w = size.width
                        val h = size.height
                        if (isUser) {
                            drawPath(Path().apply {
                                moveTo(cr, 0f); lineTo(w - cr - tw, 0f)
                                quadraticTo(w - tw, 0f, w - tw, cr)
                                lineTo(w - tw, h - cr - th)
                                quadraticTo(w - tw, h - th * 0.5f, w, h - th * 0.5f)
                                quadraticTo(w - tw * 0.3f, h, w - cr - tw, h)
                                lineTo(cr, h); quadraticTo(0f, h, 0f, h - cr)
                                lineTo(0f, cr); quadraticTo(0f, 0f, cr, 0f); close()
                            }, userBubbleColor)
                        } else {
                            drawPath(Path().apply {
                                moveTo(cr + tw, 0f); lineTo(w - cr, 0f)
                                quadraticTo(w, 0f, w, cr); lineTo(w, h - cr)
                                quadraticTo(w, h, w - cr, h); lineTo(cr + tw, h)
                                quadraticTo(tw * 0.3f, h, 0f, h - th * 0.5f)
                                quadraticTo(tw, h - th * 0.5f, tw, h - cr - th)
                                lineTo(tw, cr); quadraticTo(tw, 0f, cr + tw, 0f); close()
                            }, aiBubbleColor)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val systemTags = setOf("语音", "图片", "视频", "文件", "位置", "红包", "转账")
                val isStickerMessage = message.content.startsWith("[") && message.content.endsWith("]") &&
                        message.content.removeSurrounding("[", "]") !in systemTags
                val isImageMessage = message.content.startsWith("[图片]")
                val imagePath = if (isImageMessage) {
                    message.content.removePrefix("[图片] ").trim()
                } else null

                when {
                    isStickerMessage -> {
                        GroupStickerMessageBubble(
                            stickerName = message.content.removeSurrounding("[", "]")
                        )
                    }
                    isImageMessage && imagePath != null -> {
                        GroupImageMessageBubble(
                            imagePath = imagePath
                        )
                    }
                    else -> {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 21.sp),
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            softWrap = true
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (isUser) TextAlign.End else TextAlign.Start
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (userAvatar != null) {
                    AsyncImage(
                        model = userAvatar,
                        contentDescription = userName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = userName.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
