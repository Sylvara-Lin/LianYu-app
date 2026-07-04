package com.lianyu.ai.feature.chat.ui.screen

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lianyu.ai.feature.chat.ui.viewmodel.ChatViewModel
import com.lianyu.ai.feature.chat.ui.viewmodel.ChatViewModelFactory
import com.lianyu.ai.feature.chat.voice.VoiceCallManager
import com.lianyu.ai.network.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 语音通话状态
 */
enum class CallState {
    DIALING, CONNECTING, CONNECTED, ENDED
}

/**
 * 音频输出设备
 */
enum class AudioOutput {
    BLUETOOTH, EARPIECE, SPEAKER
}

/**
 * 语音通话页面 - 双向实时语音通话
 *
 * 基于 sherpa-onnx 流式识别 + TTS 语音合成，实现完整的 AI 语音对话：
 * - AI 接听后主动问候
 * - 用户说话 → 流式识别 → 发送给AI → AI回复 → TTS播放 → 循环
 * - 支持打断（用户说话时 AI 正在播放会被打断）
 * - 麦克风/扬声器切换
 * - 蓝牙/听筒/扬声器三路音频切换
 */
@Composable
fun VoiceCallScreen(
    companionId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext as Application, companionId)
    )
    val companionData by viewModel.companionData.collectAsState()

    // ── 通话状态 ──
    var callState by remember { mutableStateOf(CallState.DIALING) }
    var isMicEnabled by remember { mutableStateOf(true) }
    var currentAudioOutput by remember { mutableStateOf(AudioOutput.EARPIECE) }
    var hasBluetoothDevice by remember { mutableStateOf(false) }
    var callDuration by remember { mutableIntStateOf(0) }
    var userSpeakingText by remember { mutableStateOf("") }
    var aiSpeakingText by remember { mutableStateOf("") }
    var isAiSpeaking by remember { mutableStateOf(false) }

    // ── 语音引擎 ──
    val voiceManager = remember { VoiceCallManager(context) }
    val ttsService = remember { TtsService.getInstance(context) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentAiJob by remember { mutableStateOf<Job?>(null) }

    // ── 权限 ──
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("需要麦克风权限才能进行语音通话") }
            onNavigateBack()
        }
    }

    // ── 权限检查 ──
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ── 音频管理 ──
    fun getAudioManager(): AudioManager {
        return context.getSystemService(AudioManager::class.java)
    }

    fun applyAudioDevice() {
        val am = getAudioManager()
        when (currentAudioOutput) {
            AudioOutput.BLUETOOTH -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val btDevice = devices?.find { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                    if (btDevice != null) am.setCommunicationDevice(btDevice)
                } else {
                    am.isSpeakerphoneOn = false
                    am.startBluetoothSco()
                }
            }
            AudioOutput.EARPIECE -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val earpiece = devices?.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?: devices?.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (earpiece != null) am.setCommunicationDevice(earpiece)
                } else {
                    am.isSpeakerphoneOn = false
                    am.stopBluetoothSco()
                }
            }
            AudioOutput.SPEAKER -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val speaker = devices?.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) am.setCommunicationDevice(speaker)
                } else {
                    am.isSpeakerphoneOn = true
                    am.stopBluetoothSco()
                }
            }
        }
    }

    // ── AI 打断 ──
    fun interruptAi() {
        mediaPlayer?.apply {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        isAiSpeaking = false
        currentAiJob?.cancel()
        currentAiJob = null
    }

    // ── TTS 播放 ──
    fun speakText(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val audioPath = ttsService.synthesize(text)
                if (audioPath != null) {
                    withContext(Dispatchers.Main) {
                        aiSpeakingText = text
                        isAiSpeaking = true

                        val player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            setDataSource(audioPath)
                            setOnPreparedListener {
                                applyAudioDevice()
                                start()
                            }
                            setOnCompletionListener {
                                isAiSpeaking = false
                                aiSpeakingText = ""
                                release()
                                mediaPlayer = null
                                // 播放完毕，恢复录音
                                if (isMicEnabled && callState == CallState.CONNECTED) {
                                    voiceManager.startListening()
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                isAiSpeaking = false
                                release()
                                mediaPlayer = null
                                if (isMicEnabled && callState == CallState.CONNECTED) {
                                    voiceManager.startListening()
                                }
                                true
                            }
                            prepareAsync()
                        }
                        mediaPlayer = player
                    }
                } else {
                    // TTS 失败，继续录音
                    if (isMicEnabled && callState == CallState.CONNECTED) {
                        voiceManager.startListening()
                    }
                }
            } catch (e: Exception) {
                if (isMicEnabled && callState == CallState.CONNECTED) {
                    voiceManager.startListening()
                }
            }
        }
    }

    // ── 发送用户语音识别结果给 AI ──
    fun sendToAi(userText: String) {
        if (callState != CallState.CONNECTED) return

        scope.launch(Dispatchers.IO) {
            try {
                val aiReply = viewModel.sendVoiceCallMessage(userText)
                if (aiReply != null && aiReply.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        speakText(aiReply)
                    }
                } else {
                    // AI 无回复，继续录音
                    withContext(Dispatchers.Main) {
                        if (isMicEnabled && callState == CallState.CONNECTED) {
                            voiceManager.startListening()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isMicEnabled && callState == CallState.CONNECTED) {
                        voiceManager.startListening()
                    }
                }
            }
        }
    }

    // ── 通话控制 ──
    fun acceptCall() {
        callState = CallState.CONNECTING
        // 互斥保护：通知 ViewModel 通话激活，禁用聊天页 TTS 朗读，避免与通话抢 TtsService/AudioManager
        viewModel.setCallActive(true)
        scope.launch(Dispatchers.IO) {
            // 初始化语音识别引擎
            voiceManager.init()

            // 设置音频模式
            val am = getAudioManager()
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                hasBluetoothDevice = devices?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO } == true
            } else {
                hasBluetoothDevice = am.isBluetoothScoAvailableOffCall
            }
            currentAudioOutput = if (hasBluetoothDevice) AudioOutput.BLUETOOTH else AudioOutput.EARPIECE

            withContext(Dispatchers.Main) {
                applyAudioDevice()
                callState = CallState.CONNECTED
            }

            // 配置语音回调
            voiceManager.onPartialResult = { partialText ->
                if (partialText.isNotEmpty()) {
                    userSpeakingText = partialText
                    // 如果 AI 正在说话且用户说了超过2个字，打断
                    if (isAiSpeaking && partialText.length > 2) {
                        interruptAi()
                    }
                }
            }
            voiceManager.onFinalResult = { finalText ->
                userSpeakingText = ""
                if (finalText.isNotBlank() && callState == CallState.CONNECTED) {
                    voiceManager.stopListening()
                    sendToAi(finalText)
                }
            }

            // AI 主动问候
            delay(300)
            val greeting = "喂，你好呀~"
            withContext(Dispatchers.Main) { speakText(greeting) }
        }
    }

    fun hangUp() {
        callState = CallState.ENDED
        interruptAi()
        voiceManager.stopListening()
        voiceManager.destroy()

        // 恢复音频模式
        try {
            val am = getAudioManager()
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                am.isSpeakerphoneOn = false
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        // 互斥保护解除：通知 ViewModel 通话结束，恢复聊天页 TTS 可用
        viewModel.setCallActive(false)
        onNavigateBack()
    }

    fun rejectCall() {
        hangUp()
    }

    // ── 通话计时器 ──
    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED) {
            callDuration = 0
            while (callState == CallState.CONNECTED) {
                delay(1000)
                callDuration++
            }
        }
    }

    // ── 清理 ──
    DisposableEffect(Unit) {
        onDispose {
            interruptAi()
            voiceManager.destroy()
            try {
                val am = getAudioManager()
                am.mode = AudioManager.MODE_NORMAL
            } catch (_: Exception) {}
            // 互斥保护解除兜底：防止异常退出时遗漏 setCallActive(false)
            viewModel.setCallActive(false)
        }
    }

    // ── 权限不足时显示空界面 ──
    if (!hasAudioPermission && callState == CallState.DIALING) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Text(
                text = "需要麦克风权限",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        return
    }

    // ── UI ──
    Box(modifier = Modifier.fillMaxSize()) {
        // 流动渐变背景
        FlowingGradientBackground()

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = { hangUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // 通话状态
            Text(
                text = when (callState) {
                    CallState.DIALING -> "正在拨号..."
                    CallState.CONNECTING -> "正在连接..."
                    CallState.CONNECTED -> formatDuration(callDuration)
                    CallState.ENDED -> "通话结束"
                },
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 连接指示器
            when (callState) {
                CallState.DIALING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { index -> PulsingDot(delayMillis = index * 200) }
                    }
                }
                CallState.CONNECTING -> { PulsingDot() }
                CallState.CONNECTED -> {
                    Text(
                        text = when {
                            isAiSpeaking -> "对方正在说话..."
                            voiceManager.isListening -> "正在聆听..."
                            !isMicEnabled -> "麦克风已关闭"
                            else -> "已连接"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                CallState.ENDED -> {}
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // 头像
            Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                if (callState == CallState.CONNECTED) {
                    PulsingGlowRing(modifier = Modifier.size(180.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    PulsingGlowRing(modifier = Modifier.size(220.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), delayMillis = 500)
                }
                Box(
                    modifier = Modifier.size(140.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (companionData?.avatarUrl != null) {
                        AsyncImage(
                            model = companionData?.avatarUrl,
                            contentDescription = companionData?.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = companionData?.name?.firstOrNull()?.toString() ?: "?",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 名字
            Text(
                text = companionData?.name ?: "",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 说话文本
            if (userSpeakingText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = userSpeakingText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            if (isAiSpeaking && aiSpeakingText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = aiSpeakingText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // 控制按钮
            if (callState == CallState.DIALING || callState == CallState.CONNECTING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 拒绝
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .background(Color(0xFFFF3B30)).clickable { rejectCall() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CallEnd, "挂断", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    // 接受
                    Box(
                        modifier = Modifier.size(72.dp).clip(CircleShape)
                            .background(Color(0xFF34C759)).clickable { acceptCall() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "接听",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "点击接听开始通话",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            } else if (callState == CallState.CONNECTED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 麦克风
                    CallControlButton(
                        icon = if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                        label = if (isMicEnabled) "麦克风已开" else "麦克风已关",
                        isActive = isMicEnabled,
                        onClick = {
                            isMicEnabled = !isMicEnabled
                            if (isMicEnabled) {
                                voiceManager.startListening()
                            } else {
                                voiceManager.stopListening()
                            }
                        }
                    )
                    // 挂断
                    Box(
                        modifier = Modifier.size(72.dp).clip(CircleShape)
                            .background(Color(0xFFFF3B30)).clickable { hangUp() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CallEnd, "挂断", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    // 扬声器
                    CallControlButton(
                        icon = Icons.Filled.VolumeUp,
                        label = when (currentAudioOutput) {
                            AudioOutput.BLUETOOTH -> "蓝牙"
                            AudioOutput.EARPIECE -> "听筒"
                            AudioOutput.SPEAKER -> "扬声器"
                        },
                        isActive = true,
                        onClick = {
                            currentAudioOutput = when (currentAudioOutput) {
                                AudioOutput.BLUETOOTH -> AudioOutput.EARPIECE
                                AudioOutput.EARPIECE -> AudioOutput.SPEAKER
                                AudioOutput.SPEAKER -> if (hasBluetoothDevice) AudioOutput.BLUETOOTH else AudioOutput.EARPIECE
                            }
                            applyAudioDevice()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        )
    }
}

// ──────────────── UI 组件 ────────────────

@Composable
private fun FlowingGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val offset1 by infiniteTransition.animateFloat(0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "flow1")
    val offset2 by infiniteTransition.animateFloat(PI.toFloat(), 3f * PI.toFloat(),
        infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Restart), label = "flow2")
    val offset3 by infiniteTransition.animateFloat(PI.toFloat() / 2, 2.5f * PI.toFloat(),
        infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart), label = "flow3")

    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(background, background, surfaceVariant)))
        drawFlowingBlob(
            size.width * 0.3f + cos(offset1) * size.width * 0.2f,
            size.height * 0.3f + sin(offset1 * 0.7f) * size.height * 0.15f,
            size.width * 0.45f, primary.copy(alpha = 0.25f)
        )
        drawFlowingBlob(
            size.width * 0.7f + cos(offset2 * 0.8f) * size.width * 0.18f,
            size.height * 0.5f + sin(offset2) * size.height * 0.12f,
            size.width * 0.4f, secondary.copy(alpha = 0.2f)
        )
        drawFlowingBlob(
            size.width * 0.5f + cos(offset3 * 0.6f) * size.width * 0.15f,
            size.height * 0.7f + sin(offset3 * 0.9f) * size.height * 0.1f,
            size.width * 0.5f, tertiary.copy(alpha = 0.15f)
        )
        drawRect(onSurface.copy(alpha = 0.03f))
    }
}

private fun DrawScope.drawFlowingBlob(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(
        Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.5f), Color.Transparent),
            center = Offset(cx, cy), radius = r),
        radius = r, center = Offset(cx, cy)
    )
}

@Composable
private fun PulsingGlowRing(modifier: Modifier = Modifier, color: Color, delayMillis: Int = 0) {
    val t = rememberInfiniteTransition(label = "glow")
    val scale by t.animateFloat(0.8f, 1.2f,
        infiniteRepeatable(tween(2000, delayMillis = delayMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_scale")
    val alpha by t.animateFloat(0.6f, 0.2f,
        infiniteRepeatable(tween(2000, delayMillis = delayMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_alpha")
    Box(modifier = modifier.scale(scale).alpha(alpha).clip(CircleShape).background(color))
}

@Composable
private fun PulsingDot(delayMillis: Int = 0) {
    val t = rememberInfiniteTransition(label = "dot")
    val alpha by t.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(800, delayMillis = delayMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot_alpha")
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape)
                .background(if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, Modifier.size(24.dp),
                tint = if (isActive) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
