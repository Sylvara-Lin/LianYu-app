package com.lianyu.ai.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.common.BanManager
import com.lianyu.ai.common.ContentFilter
import kotlinx.coroutines.delay

@Composable
fun BanScreen(
    onStartQuiz: (Int) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var countdown by remember { mutableLongStateOf(
        BanManager.getBanInfo(context).let { it.banUntil - System.currentTimeMillis() }
    ) }
    val banInfo = remember { BanManager.getBanInfo(context) }

    // 实时重新计算倒计时
    LaunchedEffect(Unit) {
        while (true) {
            val remaining = banInfo.banUntil - System.currentTimeMillis()
            if (remaining <= 0) {
                countdown = 0
                break
            }
            countdown = remaining
            delay(1000)
        }
    }

    val days = (countdown / (1000 * 60 * 60 * 24)).toInt()
    val hours = ((countdown % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
    val minutes = ((countdown % (1000 * 60 * 60)) / (1000 * 60)).toInt()
    val seconds = ((countdown % (1000 * 60)) / 1000).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF5F5F5), Color(0xFFEEEEEE), Color(0xFFF5F5F5))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFCDD2).copy(alpha = 0.6f),
                                            Color(0xFFEF9A9A).copy(alpha = 0.3f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F).copy(alpha = 0.8f),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "账号已被封禁",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = Color(0xFFC62828)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "违规次数: ${banInfo.violationCount} | 等级: ${banInfo.levelName} | 答题: ${banInfo.quizQuestionCount}题",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Countdown card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "剩余封禁时间",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (days > 0) {
                            Text(
                                text = "${days}天",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (days > 0) 26.sp else 34.sp,
                                letterSpacing = 4.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "可立即答题解封，无需等待倒计时结束",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 答题按钮（始终可见）
            item {
                Button(
                    onClick = { onStartQuiz(banInfo.quizQuestionCount) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "开始答题解封（${banInfo.quizQuestionCount}题）",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 违规说明
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF3E0).copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "违规说明",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFFBF360C)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "您因发送违规内容被系统自动封禁。封禁时间到期后，需要完成安全规范与心理健康答题（正确率需达80%）才能解封。违规次数越多，封禁时间越长、答题数量越多。请务必遵守使用规范。",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 封禁级别表
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "封禁界限参考（累犯会在基础上倍增）",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = Color(0xFF424242)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // 各级别
            items(BanManager.getViolationLevels()) { level ->
                val bgColor = when (level.level) {
                    ContentFilter.ViolationLevel.LOW -> Color(0xFFFFF8E1)
                    ContentFilter.ViolationLevel.MEDIUM -> Color(0xFFFFF3E0)
                    ContentFilter.ViolationLevel.HIGH -> Color(0xFFFFEBEE)
                    ContentFilter.ViolationLevel.SEVERE -> Color(0xFFFCE4EC)
                    ContentFilter.ViolationLevel.CRITICAL -> Color(0xFFF3E5F5)
                    ContentFilter.ViolationLevel.EXTREME -> Color(0xFFE0E0E0)
                    else -> Color.White
                }
                val accentColor = when (level.level) {
                    ContentFilter.ViolationLevel.LOW -> Color(0xFFF9A825)
                    ContentFilter.ViolationLevel.MEDIUM -> Color(0xFFEF6C00)
                    ContentFilter.ViolationLevel.HIGH -> Color(0xFFD32F2F)
                    ContentFilter.ViolationLevel.SEVERE -> Color(0xFFC2185B)
                    ContentFilter.ViolationLevel.CRITICAL -> Color(0xFF7B1FA2)
                    ContentFilter.ViolationLevel.EXTREME -> Color(0xFF424242)
                    else -> Color.Gray
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${level.name}（${level.baseDays}天）",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = accentColor
                        )
                        Text(
                            text = level.description,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            ),
                            color = Color(0xFF757575)
                        )
                        Text(
                            text = "例: ${level.examples}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE8EAF6))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "累犯倍率：第1次 x1 | 第2次 x1.5 | 第3次 x2 | 第4次及以上 x2.5",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFF3949AB)
                        )
                        Text(
                            text = "封禁天数上限：3650天",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color(0xFF5C6BC0).copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
