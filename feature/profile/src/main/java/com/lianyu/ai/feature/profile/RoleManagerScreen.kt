package com.lianyu.ai.feature.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.common.CompanionRole
import kotlinx.coroutines.delay

/**
 * 个人中心角色管理模块。
 *
 * 允许用户随时查看当前角色并在 AI女友 / AI男友 之间切换，
 * 切换过程保留聊天记录与个性化设置，并通过独立快照保证体验连贯。
 *
 * 本次优化：
 * - 修复旧实现中在 IO 线程回调导航导致的 setCurrentState 崩溃；
 * - 增加全屏玻璃质感加载遮罩，实时展示切换阶段；
 * - 当前角色标题与按钮文案使用 AnimatedContent/Crossfade 实现平滑过渡；
 * - 切换期间拦截点击与返回手势，避免误操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleManagerScreen(
    currentRole: CompanionRole,
    switchState: RoleSwitchState,
    onSwitchRole: (CompanionRole) -> Unit,
    onNavigateBack: () -> Unit,
    onConsumeError: () -> Unit = {}
) {
    var selectedRole by remember { mutableStateOf(currentRole) }
    var isVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isSwitching = switchState is RoleSwitchState.InProgress

    LaunchedEffect(currentRole) {
        selectedRole = currentRole
    }

    LaunchedEffect(Unit) {
        delay(80)
        isVisible = true
    }

    // 错误状态反馈：单次 Toast 后消费掉，避免重复弹出
    LaunchedEffect(switchState) {
        if (switchState is RoleSwitchState.Error) {
            Toast.makeText(context, switchState.message, Toast.LENGTH_SHORT).show()
            onConsumeError()
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.role_manager_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isSwitching
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 5 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.role_manager_current),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        AnimatedContent(
                            targetState = currentRole,
                            transitionSpec = {
                                (fadeIn(tween(350)) + scaleIn(initialScale = 0.85f, animationSpec = tween(350))) togetherWith
                                        (fadeOut(tween(250)) + scaleOut(targetScale = 1.15f, animationSpec = tween(250)))
                            },
                            label = "currentRoleTitle"
                        ) { role ->
                            val accentColor = when (role) {
                                CompanionRole.GIRLFRIEND -> Color(0xFFFF6B9D)
                                CompanionRole.BOYFRIEND -> Color(0xFF4A90E2)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when (role) {
                                        CompanionRole.GIRLFRIEND -> stringResource(R.string.role_girlfriend_title)
                                        CompanionRole.BOYFRIEND -> stringResource(R.string.role_boyfriend_title)
                                    },
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp
                                    ),
                                    color = accentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.role_manager_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(500, delayMillis = 120)) + slideInVertically(tween(500, delayMillis = 120)) { it / 5 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RoleManagerCard(
                            role = CompanionRole.GIRLFRIEND,
                            icon = Icons.Filled.Favorite,
                            title = stringResource(R.string.role_girlfriend_title),
                            name = stringResource(R.string.role_girlfriend_name),
                            description = stringResource(R.string.role_girlfriend_desc),
                            accentColor = Color(0xFFFF6B9D),
                            isSelected = selectedRole == CompanionRole.GIRLFRIEND,
                            isCurrent = currentRole == CompanionRole.GIRLFRIEND,
                            onClick = { if (!isSwitching) selectedRole = CompanionRole.GIRLFRIEND }
                        )

                        RoleManagerCard(
                            role = CompanionRole.BOYFRIEND,
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.role_boyfriend_title),
                            name = stringResource(R.string.role_boyfriend_name),
                            description = stringResource(R.string.role_boyfriend_desc),
                            accentColor = Color(0xFF4A90E2),
                            isSelected = selectedRole == CompanionRole.BOYFRIEND,
                            isCurrent = currentRole == CompanionRole.BOYFRIEND,
                            onClick = { if (!isSwitching) selectedRole = CompanionRole.BOYFRIEND }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(500, delayMillis = 240)) + slideInVertically(tween(500, delayMillis = 240)) { it / 5 }
                ) {
                    Button(
                        onClick = { onSwitchRole(selectedRole) },
                        enabled = !isSwitching && selectedRole != currentRole,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF07C160),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Crossfade(
                            targetState = isSwitching,
                            animationSpec = tween(250),
                            label = "buttonContent"
                        ) { switching ->
                            if (switching) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text(
                                        text = stringResource(R.string.role_switching),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.role_switch_confirm),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 切换中全屏加载遮罩
            val inProgressState = switchState as? RoleSwitchState.InProgress
            AnimatedVisibility(
                visible = inProgressState != null,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                inProgressState?.let { state ->
                    RoleSwitchLoadingOverlay(
                        role = selectedRole,
                        stage = state.stage
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleSwitchLoadingOverlay(
    role: CompanionRole,
    stage: SwitchStage,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = when (role) {
        CompanionRole.GIRLFRIEND -> Color(0xFFFF6B9D)
        CompanionRole.BOYFRIEND -> Color(0xFF4A90E2)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // 拦截所有点击，避免切换过程中误触返回或卡片
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(24.dp))
                .background(colorScheme.surface.copy(alpha = 0.95f))
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (role) {
                        CompanionRole.GIRLFRIEND -> Icons.Filled.Favorite
                        CompanionRole.BOYFRIEND -> Icons.Filled.Shield
                    },
                    contentDescription = null,
                    tint = accentColor.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.role_switch_loading_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 } togetherWith
                            fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 }
                },
                label = "stageText"
            ) { currentStage ->
                Text(
                    text = stageLabel(currentStage),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    color = accentColor,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchStage.entries.forEachIndexed { index, step ->
                    val isActive = step.ordinal <= stage.ordinal
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) accentColor
                                else colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun stageLabel(stage: SwitchStage): String = when (stage) {
    SwitchStage.SAVING_SNAPSHOT -> stringResource(R.string.role_switch_stage_save)
    SwitchStage.LOADING_PRESET -> stringResource(R.string.role_switch_stage_load)
    SwitchStage.APPLYING_PRESET -> stringResource(R.string.role_switch_stage_apply)
    SwitchStage.UPDATING_PREFERENCE -> stringResource(R.string.role_switch_stage_update)
}

@Composable
private fun RoleManagerCard(
    role: CompanionRole,
    icon: ImageVector,
    title: String,
    name: String,
    description: String,
    accentColor: Color,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = if (isSelected) accentColor else colorScheme.outline.copy(alpha = 0.3f)
    val backgroundColor = if (isSelected) accentColor.copy(alpha = 0.08f) else colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    color = colorScheme.onSurface
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isCurrent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.role_current),
                        tint = Color(0xFF07C160),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.role_current),
                        color = Color(0xFF07C160),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}
