@file:OptIn(ExperimentalMaterial3Api::class)

package com.lianyu.ai.feature.settings.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.database.repository.TokenUsageRepository
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.model.TokenUsage
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.dao.TokenUsageDao
import com.lianyu.ai.uicommon.theme.PetalPrimary
import com.lianyu.ai.uicommon.theme.PetalPrimaryContainer
import com.lianyu.ai.uicommon.theme.PetalOnPrimaryContainer
import com.lianyu.ai.uicommon.theme.PetalSurface
import com.lianyu.ai.uicommon.theme.PetalGreen
import com.lianyu.ai.uicommon.theme.PetalError

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TokenUsageScreen(
    onNavigateBack: () -> Unit,
    isDarkTheme: Boolean = false,
    tokenUsageRepository: TokenUsageRepository? = null,
    companions: List<CompanionEntity> = emptyList()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isVisible by remember { mutableStateOf(false) }
    var todayUsage: TokenUsage? by remember { mutableStateOf(null) }
    var weekStats: TokenUsageDao.TotalStats? by remember { mutableStateOf(null) }
    var monthStats: TokenUsageDao.TotalStats? by remember { mutableStateOf(null) }
    var historyList: List<TokenUsage> by remember { mutableStateOf(emptyList()) }
    var companionsList: List<CompanionEntity> by remember { mutableStateOf(companions) }
    var isLoading: Boolean by remember { mutableStateOf(true) }

    val repository = tokenUsageRepository ?: TokenUsageRepository.getInstance(context)
    val companionRepository = remember { CompanionRepository(com.lianyu.ai.database.AppDatabase.getDatabase(context).companionDao()) }

    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                todayUsage = repository.getTodayUsage(-1L)
                weekStats = repository.getWeekUsage(-1L)
                monthStats = repository.getMonthUsage(-1L)
                historyList = repository.getAllUsageHistory(30)
                val companionsResult = companionRepository.getAllCompanions().first()
                companionsList = companionsResult
            } catch (e: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
        delay(100)
        isVisible = true
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.background
    val textPrimaryColor = colorScheme.onSurface
    val textSecondaryColor = colorScheme.onSurfaceVariant
    val cardBg = colorScheme.surfaceVariant

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = PetalPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Token 使用统计",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor
                )
                IconButton(onClick = { loadData() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = PetalPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = PetalPrimary
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatsOverviewCard(
                        todayUsage = todayUsage,
                        weekStats = weekStats,
                        monthStats = monthStats,
                        isDarkTheme = isDarkTheme,
                        cardBg = cardBg,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor
                    )

                    Text(
                        text = "最近30天使用记录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimaryColor
                    )

                    historyList.forEachIndexed { index, usage ->
                        UsageHistoryItem(
                            usage = usage,
                            isDarkTheme = isDarkTheme,
                            cardBg = cardBg,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            companions = companionsList
                        )
                        
                        if (index < historyList.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "暂无使用记录",
                                    color = textSecondaryColor,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "开始与AI对话后，这里将显示Token使用情况",
                                    color = textSecondaryColor.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewCard(
    todayUsage: TokenUsage?,
    weekStats: TokenUsageDao.TotalStats?,
    monthStats: TokenUsageDao.TotalStats?,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "使用概览",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimaryColor
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                title = "今日使用",
                value = formatTokenCount(todayUsage?.totalTokens ?: 0L),
                subtitle = "${todayUsage?.requestCount ?: 0} 次请求",
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme,
                bgColor = PetalPrimaryContainer.copy(alpha = 0.15f),
                textColor = PetalOnPrimaryContainer
            )
            
            StatBox(
                title = "本周使用",
                value = formatTokenCount(weekStats?.total ?: 0L),
                subtitle = formatTokenCount(weekStats?.totalInput ?: 0L) + " 输入",
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme,
                bgColor = PetalGreen.copy(alpha = 0.15f),
                textColor = PetalGreen
            )
            
            StatBox(
                title = "本月使用",
                value = formatTokenCount(monthStats?.total ?: 0L),
                subtitle = formatTokenCount(monthStats?.requests?.toLong() ?: 0L) + " 次",
                modifier = Modifier.weight(1f),
                isDarkTheme = isDarkTheme,
                bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                textColor = MaterialTheme.colorScheme.primary
            )
        }

        if (todayUsage != null && todayUsage!!.totalTokens > 0) {
            Column {
                Text(
                    text = "今日详情",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondaryColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    UsageDetailItem(label = "输入Token", value = formatTokenCount(todayUsage!!.inputTokens), color = PetalGreen, textPrimaryColor = textPrimaryColor)
                    UsageDetailItem(label = "输出Token", value = formatTokenCount(todayUsage!!.outputTokens), color = PetalPrimary, textPrimaryColor = textPrimaryColor)
                    UsageDetailItem(label = "请求次数", value = "${todayUsage!!.requestCount}", color = MaterialTheme.colorScheme.primary, textPrimaryColor = textPrimaryColor)
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    bgColor: Color,
    textColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtitle, fontSize = 10.sp, color = textColor.copy(alpha = 0.6f), maxLines = 1)
    }
}

@Composable
private fun UsageDetailItem(
    label: String,
    value: String,
    color: Color,
    textPrimaryColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = textPrimaryColor.copy(alpha = 0.7f))
    }
}

@Composable
private fun UsageHistoryItem(
    usage: TokenUsage,
    isDarkTheme: Boolean,
    cardBg: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    companions: List<CompanionEntity> = emptyList()
) {
    val companionLabel = when {
        usage.companionId == -1L -> "全局"
        usage.companionId > 0 -> companions.find { it.id == usage.companionId }?.name ?: "已删除角色"
        else -> "未知"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = usage.date,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimaryColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = companionLabel,
                fontSize = 12.sp,
                color = textSecondaryColor
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatTokenCount(usage.totalTokens),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PetalPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${usage.requestCount}次 · in:${formatTokenCount(usage.inputTokens)} out:${formatTokenCount(usage.outputTokens)}",
                fontSize = 10.sp,
                color = textSecondaryColor
            )
        }
    }
}

private fun formatTokenCount(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
    }
}
