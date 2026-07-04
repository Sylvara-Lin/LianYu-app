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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.common.BatteryOptimizationHelper
import com.lianyu.ai.common.OppoVivoAdaptationHelper
import com.lianyu.ai.common.RomUtils
import com.lianyu.ai.uicommon.theme.PetalPrimary
import com.lianyu.ai.uicommon.theme.PetalPrimaryContainer

/**
 * 设置页 - 权限与后台运行卡片。
 *
 * 针对 OPPO / vivo / 其他国产 ROM 提供统一的电池优化、自启动、后台高耗电、
 * 通知权限等设置入口，引导用户完成保活所需的系统授权。
 */
@Composable
fun BackgroundPermissionsCard(
    isVisible: Boolean,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val needsDeepGuide = RomUtils.isOppoOrVivo()
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400, delayMillis = 180)) +
                expandVertically(tween(400, delayMillis = 180)),
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cardBackground)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
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
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = PetalPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "权限与后台运行",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimaryColor
                        )
                        Text(
                            text = if (needsDeepGuide) {
                                "${RomUtils.getRomDisplayName()} 需手动授权以保持消息推送"
                            } else {
                                "确保消息提醒和后台服务稳定运行"
                            },
                            fontSize = 12.sp,
                            color = textSecondaryColor
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = textSecondaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (needsDeepGuide) {
                        Text(
                            text = "使用本机（${RomUtils.getRomDisplayName()}）时，建议依次完成以下设置，否则虚拟恋人可能无法主动发消息或后台被杀：",
                            fontSize = 13.sp,
                            color = textSecondaryColor,
                            lineHeight = 18.sp
                        )

                        OppoVivoAdaptationHelper.getGuideSteps().forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                fontSize = 13.sp,
                                color = textSecondaryColor,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    PermissionActionItem(
                        icon = Icons.Default.Sync,
                        title = "自启动 / 应用启动管理",
                        subtitle = "允许应用自动启动",
                        onClick = { BatteryOptimizationHelper.openAutoStartSettings(context) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor
                    )

                    if (needsDeepGuide) {
                        PermissionActionItem(
                            icon = Icons.Default.BatteryAlert,
                            title = "后台高耗电 / 耗电保护",
                            subtitle = "避免系统清理后台进程",
                            onClick = { BatteryOptimizationHelper.openBackgroundPowerSettings(context) },
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                    }

                    PermissionActionItem(
                        icon = Icons.Default.Notifications,
                        title = "通知权限",
                        subtitle = "接收虚拟恋人消息提醒",
                        onClick = { BatteryOptimizationHelper.openNotificationSettings(context) },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor
                    )

                    PermissionActionItem(
                        icon = Icons.Default.PowerSettingsNew,
                        title = "电池优化",
                        subtitle = if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                            "已设为不优化"
                        } else {
                            "点击设为“不优化”"
                        },
                        onClick = {
                            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                            } else {
                                BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
                            }
                        },
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor
                    )

                    if (RomUtils.isVivo) {
                        PermissionActionItem(
                            icon = Icons.Default.BatteryAlert,
                            title = "vivo 神隐模式 / 后台管理",
                            subtitle = "关闭对本应用的后台限制",
                            onClick = { OppoVivoAdaptationHelper.openVivoGodModeSettings(context) },
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PetalPrimary,
            modifier = Modifier.size(22.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimaryColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = textSecondaryColor
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textSecondaryColor,
            modifier = Modifier.size(18.dp)
        )
    }
}
