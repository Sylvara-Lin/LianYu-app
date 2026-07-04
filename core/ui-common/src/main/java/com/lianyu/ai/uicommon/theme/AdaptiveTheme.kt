package com.lianyu.ai.uicommon.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.uicommon.utils.DeviceScreenSize
import com.lianyu.ai.uicommon.utils.rememberDeviceScreenSize

@Immutable
data class AdaptiveSizing(
    val avatarSize: Dp,
    val inputBarHeight: Dp,
    val messageBubbleMaxWidthRatio: Float,
    val chatItemHeight: Dp,
    val fontSizeBody: Int,
    val fontSizeTitle: Int,
    val fontSizeCaption: Int,
    val fontSizeSmall: Int,
    val iconButtonSize: Dp,
    val iconSize: Dp,
    val extensionIconBoxSize: Dp,
    val extensionIconSize: Dp,
    val extensionGridSpacing: Dp,
    val cornerRadius: Dp,
    val listHorizontalPadding: Dp,
    val chatBubblePaddingHorizontal: Dp,
    val chatBubblePaddingVertical: Dp
)

@Composable
fun rememberAdaptiveSizing(): AdaptiveSizing {
    val deviceSize = rememberDeviceScreenSize()
    return remember(deviceSize) {
        when (deviceSize) {
            DeviceScreenSize.COMPACT -> AdaptiveSizing(
                avatarSize = 40.dp,
                inputBarHeight = 42.dp,
                messageBubbleMaxWidthRatio = 0.75f,
                chatItemHeight = 72.dp,
                fontSizeBody = 14,
                fontSizeTitle = 18,
                fontSizeCaption = 9,
                fontSizeSmall = 11,
                iconButtonSize = 36.dp,
                iconSize = 20.dp,
                extensionIconBoxSize = 52.dp,
                extensionIconSize = 26.dp,
                extensionGridSpacing = 16.dp,
                cornerRadius = 22.dp,
                listHorizontalPadding = 12.dp,
                chatBubblePaddingHorizontal = 14.dp,
                chatBubblePaddingVertical = 10.dp
            )
            DeviceScreenSize.MEDIUM -> AdaptiveSizing(
                avatarSize = 48.dp,
                inputBarHeight = 48.dp,
                messageBubbleMaxWidthRatio = 0.70f,
                chatItemHeight = 80.dp,
                fontSizeBody = 15,
                fontSizeTitle = 20,
                fontSizeCaption = 10,
                fontSizeSmall = 12,
                iconButtonSize = 40.dp,
                iconSize = 22.dp,
                extensionIconBoxSize = 56.dp,
                extensionIconSize = 28.dp,
                extensionGridSpacing = 20.dp,
                cornerRadius = 24.dp,
                listHorizontalPadding = 16.dp,
                chatBubblePaddingHorizontal = 16.dp,
                chatBubblePaddingVertical = 11.dp
            )
            DeviceScreenSize.EXPANDED -> AdaptiveSizing(
                avatarSize = 56.dp,
                inputBarHeight = 54.dp,
                messageBubbleMaxWidthRatio = 0.65f,
                chatItemHeight = 88.dp,
                fontSizeBody = 16,
                fontSizeTitle = 22,
                fontSizeCaption = 10,
                fontSizeSmall = 13,
                iconButtonSize = 44.dp,
                iconSize = 24.dp,
                extensionIconBoxSize = 60.dp,
                extensionIconSize = 30.dp,
                extensionGridSpacing = 24.dp,
                cornerRadius = 26.dp,
                listHorizontalPadding = 24.dp,
                chatBubblePaddingHorizontal = 18.dp,
                chatBubblePaddingVertical = 12.dp
            )
        }
    }
}
