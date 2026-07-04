package com.lianyu.ai.uicommon.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DeviceScreenSize {
    COMPACT,
    MEDIUM,
    EXPANDED
}

val LocalDeviceScreenSize = compositionLocalOf { DeviceScreenSize.COMPACT }

@Composable
fun rememberDeviceScreenSize(): DeviceScreenSize {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(configuration, density) {
        val widthDp = configuration.screenWidthDp.toFloat()
        when {
            widthDp < 600f -> DeviceScreenSize.COMPACT
            widthDp < 840f -> DeviceScreenSize.MEDIUM
            else -> DeviceScreenSize.EXPANDED
        }
    }
}

@Composable
fun isCompactDevice(): Boolean = rememberDeviceScreenSize() == DeviceScreenSize.COMPACT

@Composable
fun isMediumDevice(): Boolean = rememberDeviceScreenSize() == DeviceScreenSize.MEDIUM

@Composable
fun isExpandedDevice(): Boolean = rememberDeviceScreenSize() == DeviceScreenSize.EXPANDED

@Composable
fun responsiveDp(
    compact: Dp,
    medium: Dp = compact,
    expanded: Dp = medium
): Dp {
    val size = rememberDeviceScreenSize()
    return when (size) {
        DeviceScreenSize.COMPACT -> compact
        DeviceScreenSize.MEDIUM -> medium
        DeviceScreenSize.EXPANDED -> expanded
    }
}

@Composable
fun responsiveSp(
    compact: Int,
    medium: Int = compact,
    expanded: Int = medium
): androidx.compose.ui.unit.TextUnit {
    val size = rememberDeviceScreenSize()
    return when (size) {
        DeviceScreenSize.COMPACT -> compact.sp
        DeviceScreenSize.MEDIUM -> medium.sp
        DeviceScreenSize.EXPANDED -> expanded.sp
    }
}

@Composable
fun responsivePadding(
    compact: Dp,
    medium: Dp = compact,
    expanded: Dp = medium
): Dp = responsiveDp(compact, medium, expanded)

@Composable
fun responsiveFloat(
    compact: Float,
    medium: Float = compact,
    expanded: Float = medium
): Float {
    val size = rememberDeviceScreenSize()
    return when (size) {
        DeviceScreenSize.COMPACT -> compact
        DeviceScreenSize.MEDIUM -> medium
        DeviceScreenSize.EXPANDED -> expanded
    }
}
