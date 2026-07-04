package com.lianyu.ai.uicommon.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.uicommon.model.ApiProviderInfo
import com.lianyu.ai.uicommon.theme.AdaptiveSizing
import com.lianyu.ai.uicommon.theme.rememberAdaptiveSizing
import kotlinx.coroutines.launch

@Composable
fun ChatInputExtensionPanel(
    isVisible: Boolean,
    availableApis: List<ApiProviderInfo> = emptyList(),
    currentApi: ApiProviderInfo? = null,
    onSwitchApi: ((ApiProviderInfo) -> Unit)? = null,
    onAlbumClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {},
    onLocationClick: () -> Unit = {},
    onVoiceInputClick: () -> Unit = {},
    onStickerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(200)),
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        val pagerState = rememberPagerState(pageCount = { 1 })
        val coroutineScope = rememberCoroutineScope()
        val adaptiveSizing = rememberAdaptiveSizing()
        val bgColor = MaterialTheme.colorScheme.surfaceVariant
        val iconBgColor = MaterialTheme.colorScheme.surface
        val textColor = MaterialTheme.colorScheme.onSurfaceVariant
        val iconTintColor = MaterialTheme.colorScheme.onSurface

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(vertical = 16.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) { _ ->
                ExtensionGridPage(
                    iconBgColor = iconBgColor,
                    textColor = textColor,
                    iconTintColor = iconTintColor,
                    availableApis = availableApis,
                    currentApi = currentApi,
                    onSwitchApi = onSwitchApi,
                    onAlbumClick = onAlbumClick,
                    onCameraClick = onCameraClick,
                    onVideoCallClick = onVideoCallClick,
                    onLocationClick = onLocationClick,
                    onStickerClick = onStickerClick,
                    onVoiceInputClick = onVoiceInputClick,
                    adaptiveSizing = adaptiveSizing
                )
            }
        }
    }
}

@Composable
private fun ExtensionGridPage(
    iconBgColor: Color,
    textColor: Color,
    iconTintColor: Color,
    availableApis: List<ApiProviderInfo>,
    currentApi: ApiProviderInfo?,
    onSwitchApi: ((ApiProviderInfo) -> Unit)?,
    onAlbumClick: () -> Unit,
    onCameraClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onLocationClick: () -> Unit,
    onStickerClick: () -> Unit,
    onVoiceInputClick: () -> Unit,
    adaptiveSizing: AdaptiveSizing
) {
    val hasMultipleApis = availableApis.size > 1 && onSwitchApi != null

    val items = mutableListOf<ExtensionItem>()
    items.add(ExtensionItem("相册", Icons.Filled.Image, onAlbumClick))
    items.add(ExtensionItem("拍摄", Icons.Filled.CameraAlt, onCameraClick))
    items.add(ExtensionItem("视频通话", Icons.Filled.Videocam, onVideoCallClick))
    items.add(ExtensionItem("位置", Icons.Filled.LocationOn, onLocationClick))
    items.add(ExtensionItem("表情包", Icons.Filled.Mood, onStickerClick))
    items.add(ExtensionItem("语音输入", Icons.Filled.Mic, onVoiceInputClick))
    if (hasMultipleApis) {
        items.add(ExtensionItem("切换模型", Icons.Filled.SwapHoriz) { onSwitchApi!!((currentApi?.let { c ->
            val idx = availableApis.indexOfFirst { it.name == c.name }
            availableApis.getOrNull((idx + 1) % availableApis.size)
        } ?: availableApis.first())) })
    }

    ExtensionGrid(items = items, iconBgColor = iconBgColor, textColor = textColor, iconTintColor = iconTintColor, adaptiveSizing = adaptiveSizing)
}

@Composable
private fun ExtensionGrid(
    items: List<ExtensionItem>,
    iconBgColor: Color,
    textColor: Color,
    iconTintColor: Color,
    adaptiveSizing: AdaptiveSizing
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(adaptiveSizing.extensionGridSpacing)
    ) {
        val rows = items.chunked(4)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowItems.forEach { item ->
                    ExtensionIconButton(
                        label = item.label,
                        icon = item.icon,
                        iconBgColor = iconBgColor,
                        textColor = textColor,
                        iconTintColor = iconTintColor,
                        onClick = item.onClick,
                        adaptiveSizing = adaptiveSizing
                    )
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.width(adaptiveSizing.extensionIconBoxSize))
                }
            }
        }
    }
}

@Composable
private fun ExtensionIconButton(
    label: String,
    icon: ImageVector,
    iconBgColor: Color,
    textColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit,
    adaptiveSizing: AdaptiveSizing
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(adaptiveSizing.extensionIconBoxSize)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(adaptiveSizing.extensionIconBoxSize)
                .clip(RoundedCornerShape(16.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(adaptiveSizing.extensionIconSize),
                tint = iconTintColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = adaptiveSizing.fontSizeSmall.sp,
            color = textColor,
            fontWeight = FontWeight.Normal
        )
    }
}

private data class ExtensionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
