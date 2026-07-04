package com.lianyu.ai.feature.chat.ui.viewmodel

import androidx.compose.runtime.Stable

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 消息处理流水线 — 5 阶段接口。
 *
 * 控制论: S[k+1] = f(S[k], U[k])
 * 每阶段有独立超时预算 (定义在 com.lianyu.ai.common.TimeoutBudgets)
 * 上游阶段输出 = 下游阶段输入，失败短路。
 */
interface MessagePipeline {

    /** 流水线输入: 用户原始输入 */
    data class PipelineInput(
        val rawText: String,
        val companionId: Long,
        val isVision: Boolean = false,
        val imageUri: String? = null
    )

    /** 流水线状态: 每阶段完成后更新 */
    @Stable
data class PipelineState(
        val stage: Stage = Stage.IDLE,
        val stageDurationMs: Long = 0,
        val totalDurationMs: Long = 0,
        val error: String? = null
    )

    enum class Stage {
        IDLE,
        /** 阶段 1: 内容安全检查 → 贝叶斯 + AC + 向量 */
        VALIDATE,
        /** 阶段 2: 意图分类 → 文本/图片/语音 */
        CLASSIFY,
        /** 阶段 3: SM4 加密 → C++ 层 */
        ENCRYPT,
        /** 阶段 4: API 调用 → 远程 LLM */
        SEND,
        /** 阶段 5: 持久化 → Room DB 写入 */
        CONFIRM,
        DONE
    }

    /** 当前流水线状态，供监控使用 */
    val pipelineState: StateFlow<PipelineState>

    /** 队列深度信号：≥ 10 触发 UI 降级 */
    val queueDepth: StateFlow<Int>

    /**
     * 执行完整流水线。
     * @return true 如果全部 5 阶段成功完成
     */
    suspend fun execute(input: PipelineInput): Boolean
}
