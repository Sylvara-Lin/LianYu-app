package com.lianyu.ai.uicommon.component

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.media.MediaPlayer

@Composable
fun VoiceMessageBubble(
    audioPath: String,
    duration: Int,
    isUser: Boolean,
    onPlayComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val iconColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val waveColor = if (isUser) Color(0xFF34C759).copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        } else {
            if (mediaPlayer == null || mediaPlayer?.currentPosition == 0) {
                try {
                    val player = MediaPlayer().apply {
                        setDataSource(audioPath)
                        prepareAsync()
                        setOnPreparedListener { mp ->
                            mp.start()
                            isPlaying = true
                        }
                        setOnCompletionListener { mp ->
                            isPlaying = false
                            currentPosition = 0L
                            onPlayComplete?.invoke()
                            mp.seekTo(0)
                        }
                    }
                    mediaPlayer?.release()
                    mediaPlayer = player
                } catch (e: Exception) {
                    isPlaying = false
                }
            } else {
                mediaPlayer?.start()
                isPlaying = true
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            delay(100)
            currentPosition = (mediaPlayer?.currentPosition ?: 0).toLong()
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clickable { togglePlayback() }
            .background(bubbleColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = { togglePlayback() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            VoiceWaveform(
                isPlaying = isPlaying,
                phase = wavePhase,
                color = waveColor,
                duration = duration.coerceAtLeast(1),
                currentPosition = currentPosition.toInt()
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatDuration(if (isPlaying && mediaPlayer != null) ((mediaPlayer?.duration ?: duration * 1000) / 1000) else duration),
                fontSize = 12.sp,
                color = iconColor.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    isPlaying: Boolean,
    phase: Float,
    color: Color,
    duration: Int,
    currentPosition: Int
) {
    val barCount = 5
    
    Canvas(modifier = Modifier.width(80.dp).height(28.dp)) {
        val barWidth = size.width / (barCount * 2 + 1)
        val maxHeight = size.height * 0.85f
        
        for (i in 0 until barCount) {
            val x = barWidth * (i * 2 + 1.5f)
            
            val progress = if (duration > 0) currentPosition.toFloat() / (duration * 1000f) else 0f
            val barProgress = i.toFloat() / barCount
            
            var heightRatio = when {
                !isPlaying -> 0.35f + 0.15f * ((i % 2))
                barProgress <= progress -> 0.45f + 0.55f * kotlin.math.sin((phase * 2 * kotlin.math.PI.toFloat()) + (i * 0.7f)).toFloat()
                else -> 0.25f + 0.15f * ((i % 2))
            }.coerceIn(0.2f, 1.0f)

            val barHeight = maxHeight * heightRatio
            val y = (size.height - barHeight) / 2
            
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.75f, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f, barWidth * 0.3f)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    return if (seconds < 60) "${seconds}\"" else "${seconds / 60}'${seconds % 60}\""
}
