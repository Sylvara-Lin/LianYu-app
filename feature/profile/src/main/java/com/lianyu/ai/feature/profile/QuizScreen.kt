package com.lianyu.ai.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.lianyu.ai.common.QuizBank
import com.lianyu.ai.common.QuizQuestion

@Composable
fun QuizScreen(
    questionCount: Int,
    onPass: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val questions = remember(questionCount) { QuizBank.getRandomQuestions(questionCount) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableIntStateOf(-1) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    val wrongIndices = remember { mutableStateListOf<Int>() }
    var quizFinished by remember { mutableStateOf(false) }
    var quizPassed by remember { mutableStateOf(false) }

    if (quizFinished) {
        QuizResultScreen(
            totalQuestions = questions.size,
            correctCount = correctCount,
            passed = quizPassed,
            onPass = {
                BanManager.passQuiz(context)
                onPass()
            },
            onRetry = {
                val newQuestions = QuizBank.getRandomQuestions(questionCount)
                // Restart
                currentIndex = 0
                selectedOption = -1
                showResult = false
                isCorrect = false
                correctCount = 0
                wrongIndices.clear()
                quizFinished = false
                quizPassed = false
                BanManager.recordQuizAttempt(context)
            },
            onExit = onExit
        )
        return
    }

    val currentQuestion = questions.getOrNull(currentIndex)
    if (currentQuestion == null) {
        // All questions answered
        val requiredCorrect = (questions.size * 0.8).toInt().coerceAtLeast(1)
        quizPassed = correctCount >= requiredCorrect
        quizFinished = true
        return
    }

    val progress = (currentIndex).toFloat() / questions.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "违规教育答题",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color(0xFF5C6BC0),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "第 ${currentIndex + 1} / ${questions.size} 题 · 答对 ${correctCount} 题 · 需答对 ${(questions.size * 0.8).toInt().coerceAtLeast(1)} 题",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF5C6BC0),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5C6BC0).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = null,
                        tint = Color(0xFF5C6BC0),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentQuestion.category,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF5C6BC0)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Question card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(20.dp)
            ) {
                Text(
                    text = currentQuestion.question,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options
            currentQuestion.options.forEachIndexed { index, option ->
                val isSelected = selectedOption == index
                val bgColor = when {
                    !showResult -> if (isSelected) Color(0xFFE8EAF6) else Color.White
                    showResult && index == currentQuestion.correctIndex -> Color(0xFFE8F5E9)
                    showResult && isSelected && !isCorrect -> Color(0xFFFFEBEE)
                    else -> Color.White
                }
                val borderColor = when {
                    showResult && index == currentQuestion.correctIndex -> Color(0xFF4CAF50)
                    showResult && isSelected && !isCorrect -> Color(0xFFF44336)
                    isSelected && !showResult -> Color(0xFF5C6BC0)
                    else -> Color(0xFFE0E0E0)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable(enabled = !showResult) {
                            selectedOption = index
                            isCorrect = (index == currentQuestion.correctIndex)
                            showResult = true
                            if (isCorrect) correctCount++ else wrongIndices.add(currentIndex)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    showResult && index == currentQuestion.correctIndex -> Color(0xFF4CAF50)
                                    showResult && isSelected && !isCorrect -> Color(0xFFF44336)
                                    isSelected && !showResult -> Color(0xFF5C6BC0)
                                    else -> Color(0xFFF5F5F5)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ('A' + index).toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = when {
                                showResult && index == currentQuestion.correctIndex -> Color.White
                                showResult && isSelected && !isCorrect -> Color.White
                                isSelected && !showResult -> Color.White
                                else -> Color(0xFF757575)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = option,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showResult && index == currentQuestion.correctIndex) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (showResult && isSelected && !isCorrect) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Explanation after answer
            AnimatedVisibility(visible = showResult) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (isCorrect) "✓ 回答正确！" else "✗ 回答错误，请记住正确答案。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (currentIndex < questions.size - 1) {
                                currentIndex++
                                selectedOption = -1
                                showResult = false
                            } else {
                                val requiredCorrect = (questions.size * 0.8).toInt().coerceAtLeast(1)
                                quizPassed = correctCount >= requiredCorrect
                                quizFinished = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                    ) {
                        Text(
                            text = if (currentIndex < questions.size - 1) "下一题" else "提交",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuizResultScreen(
    totalQuestions: Int,
    correctCount: Int,
    passed: Boolean,
    onPass: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val requiredCorrect = (totalQuestions * 0.8).toInt().coerceAtLeast(1)
    val percentage = (correctCount * 100) / totalQuestions

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                if (passed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (passed) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            tint = if (passed) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (passed) "答题通过！" else "未通过",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = if (passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "答对 $correctCount / $totalQuestions 题（正确率 ${percentage}%）",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "需答对 $requiredCorrect 题（正确率80%以上）才能通过",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    if (passed) {
                        Button(
                            onClick = onPass,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text(
                                text = "确认并返回",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5C6BC0)
                                )
                            ) {
                                Text(
                                    text = "重新答题",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = onExit,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF757575)
                                )
                            ) {
                                Text(
                                    text = "返回等待",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "请认真学习安全规范和心理健康知识，\n遵守平台使用规则。",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
