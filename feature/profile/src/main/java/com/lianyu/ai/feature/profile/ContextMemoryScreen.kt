package com.lianyu.ai.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianyu.ai.common.AppSettingsStore

import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val PetalPrimary = Color(0xFF894C5C)
private val PetalSurfaceContainer = Color(0xFFEFEDED)

class ContextMemoryViewModel(context: android.content.Context) : androidx.lifecycle.ViewModel() {
    private val appSettingsStore = AppSettingsStore(context)
    val contextLimit: StateFlow<Int> = MutableStateFlow(50)
    val innerThoughtEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    val contextCompressionMode: StateFlow<String> = MutableStateFlow(AppSettingsStore.CompressionMode.OFF)
    val compressionKeepRatio: StateFlow<Float> = MutableStateFlow(0.5f)
    val compressionMinKeep: StateFlow<Int> = MutableStateFlow(6)

    init {
        viewModelScope.launch {
            appSettingsStore.contextLimitFlow.collect { limit ->
                (contextLimit as MutableStateFlow).value = limit
            }
        }

        viewModelScope.launch {
            appSettingsStore.innerThoughtEnabledFlow.collect { enabled ->
                (innerThoughtEnabled as MutableStateFlow).value = enabled
            }
        }

        viewModelScope.launch {
            appSettingsStore.contextCompressionModeFlow.collect { mode ->
                (contextCompressionMode as MutableStateFlow).value = mode
            }
        }

        viewModelScope.launch {
            appSettingsStore.compressionKeepRatioFlow.collect { ratio ->
                (compressionKeepRatio as MutableStateFlow).value = ratio
            }
        }

        viewModelScope.launch {
            appSettingsStore.compressionMinKeepFlow.collect { minKeep ->
                (compressionMinKeep as MutableStateFlow).value = minKeep
            }
        }
    }

    fun setContextLimit(limit: Int) {
        viewModelScope.launch {
            appSettingsStore.setContextLimit(limit)
        }
    }

    fun setInnerThoughtEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.setInnerThoughtEnabled(enabled)
        }
    }

    fun setContextCompressionMode(mode: String) {
        viewModelScope.launch {
            appSettingsStore.setContextCompressionMode(mode)
        }
    }

    fun setCompressionKeepRatio(ratio: Float) {
        viewModelScope.launch {
            appSettingsStore.setCompressionKeepRatio(ratio)
        }
    }

    fun setCompressionMinKeep(minKeep: Int) {
        viewModelScope.launch {
            appSettingsStore.setCompressionMinKeep(minKeep)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMemoryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ContextMemoryViewModel = viewModel(factory = ContextMemoryViewModelFactory(context))
    val contextLimit by viewModel.contextLimit.collectAsState()
    val innerThoughtEnabled by viewModel.innerThoughtEnabled.collectAsState()
    val compressionMode by viewModel.contextCompressionMode.collectAsState()
    val compressionKeepRatio by viewModel.compressionKeepRatio.collectAsState()
    val compressionMinKeep by viewModel.compressionMinKeep.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "上下文记忆",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 说明卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "AI 记忆能力",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "设置 AI 能记住的最近对话轮数。数值越大，AI 越能记住之前的对话内容，但也会消耗更多 token。",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            // 设置卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "记忆消息条数",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "当前: $contextLimit 条",
                                fontSize = 13.sp,
                                color = PetalPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = contextLimit.toString(),
                        onValueChange = {
                            val num = it.toIntOrNull()
                            if (num != null) {
                                viewModel.setContextLimit(num)
                            }
                        },
                        label = { Text("条数 (1-10000)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PetalPrimary,
                            unfocusedBorderColor = PetalSurfaceContainer,
                            focusedLabelColor = PetalPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = contextLimit.toFloat().coerceIn(1f, 500f),
                        onValueChange = { viewModel.setContextLimit(it.toInt()) },
                        valueRange = 1f..500f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = PetalPrimary,
                            activeTrackColor = PetalPrimary,
                            inactiveTrackColor = PetalSurfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        Text("100", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "滑块快速调节 1-100，输入框可精确设置 1-10000",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            // 心理活动描写开关
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "心理活动描写",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (innerThoughtEnabled) "已开启 - AI 回复中可能包含括号内的心理活动" else "已关闭 - AI 不会输出括号内的心理活动",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = innerThoughtEnabled,
                        onCheckedChange = { viewModel.setInnerThoughtEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PetalPrimary,
                            checkedTrackColor = PetalPrimary.copy(alpha = 0.4f),
                            uncheckedThumbColor = colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // 上下文压缩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "上下文压缩",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当聊天记录超过记忆条数时，旧消息不再直接丢弃而是压缩摘要",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            AppSettingsStore.CompressionMode.OFF to "关闭",
                            AppSettingsStore.CompressionMode.LOCAL to "本地压缩",
                            AppSettingsStore.CompressionMode.AI to "AI压缩"
                        )
                        modes.forEach { (mode, label) ->
                            val isSelected = compressionMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PetalPrimary.copy(alpha = 0.15f) else colorScheme.surfaceVariant)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) PetalPrimary else colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setContextCompressionMode(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) PetalPrimary else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (compressionMode) {
                            AppSettingsStore.CompressionMode.LOCAL -> "本地压缩：自动提取关键情感、话题和事实信息，免费无延迟"
                            AppSettingsStore.CompressionMode.AI -> "AI压缩：调用模型生成高质量摘要，更智能但消耗额外token"
                            else -> "关闭：超出条数的消息将被直接丢弃"
                        },
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            if (compressionMode != AppSettingsStore.CompressionMode.OFF) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.surfaceVariant)
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "压缩细节调整",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "微调压缩行为以适应不同场景",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "保留比例 ${(compressionKeepRatio * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = PetalPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "压缩时保留最近消息的比例，越高保留越多但压缩效果越弱",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = compressionKeepRatio.coerceIn(0.1f, 0.9f),
                            onValueChange = { viewModel.setCompressionKeepRatio(it) },
                            valueRange = 0.1f..0.9f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = PetalPrimary,
                                activeTrackColor = PetalPrimary,
                                inactiveTrackColor = PetalSurfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("10%", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                            Text("90%", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "最少保留 $compressionMinKeep 条",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = PetalPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "即使按比例计算更少，也至少保留这么多条最近消息",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = compressionMinKeep.toFloat().coerceIn(2f, 20f),
                            onValueChange = { viewModel.setCompressionMinKeep(it.toInt()) },
                            valueRange = 2f..20f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = PetalPrimary,
                                activeTrackColor = PetalPrimary,
                                inactiveTrackColor = PetalSurfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("2", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                            Text("20", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 提示卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "建议设置",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 普通对话: 12-50 条\n• 长文创作: 100-200 条\n• 超长上下文模型 (如 DeepSeek-V4 Pro): 可达 10000 条",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

class ContextMemoryViewModelFactory(
    private val context: android.content.Context
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ContextMemoryViewModel(context) as T
    }
}
