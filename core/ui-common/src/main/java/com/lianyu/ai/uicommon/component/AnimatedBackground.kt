package com.lianyu.ai.uicommon.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GradientOrbBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")

    val orb1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb1"
    )

    val orb2Offset by infiniteTransition.animateFloat(
        initialValue = PI.toFloat(),
        targetValue = 3f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb2"
    )

    val background = MaterialTheme.colorScheme.background
    val isDark = background.luminance() < 0.5f
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxSize()) {
        // Background gradient: 深色/浅色变体
        drawRect(
            brush = Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        background,
                        background,
                        primary.copy(alpha = 0.08f)
                    )
                } else {
                    listOf(
                        Color(0xFFFFF8FA),
                        Color(0xFFFFF0F3),
                        Color(0xFFFCE4EC).copy(alpha = 0.3f)
                    )
                }
            )
        )

        // Animated orb 1
        val orb1X = size.width * 0.3f + cos(orb1Offset) * size.width * 0.15f
        val orb1Y = size.height * 0.4f + sin(orb1Offset * 0.7f) * size.height * 0.1f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = if (isDark) 0.10f else 0.15f),
                    primary.copy(alpha = if (isDark) 0.04f else 0.05f),
                    Color.Transparent
                ),
                center = Offset(orb1X, orb1Y),
                radius = size.width * 0.4f
            ),
            radius = size.width * 0.4f,
            center = Offset(orb1X, orb1Y)
        )

        // Animated orb 2
        val orb2X = size.width * 0.7f + cos(orb2Offset * 0.8f) * size.width * 0.12f
        val orb2Y = size.height * 0.6f + sin(orb2Offset) * size.height * 0.08f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = if (isDark) 0.08f else 0.12f),
                    primary.copy(alpha = if (isDark) 0.03f else 0.04f),
                    Color.Transparent
                ),
                center = Offset(orb2X, orb2Y),
                radius = size.width * 0.35f
            ),
            radius = size.width * 0.35f,
            center = Offset(orb2X, orb2Y)
        )
    }
}
