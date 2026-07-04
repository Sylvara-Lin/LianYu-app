package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.feature.chat.ui.viewmodel.ChatDebugLog

import com.lianyu.ai.common.ContentFilter
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.TimeoutBudgets
import com.lianyu.ai.common.safety.ContentSafetyVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ChatViewModel 消息流水线实现 — 5 阶段结构。
 *
 * 控制论: 每个阶段的时滞符合 TimeoutBudgets 约束。
 * 阶段输出 = 下一阶段输入，失败短路返回。
 * 流水线状态可观测，供 UI 监控使用。
 */
class MessagePipelineRunner(
    private val onViolation: ((ContentFilter.ViolationLevel) -> Unit)? = null
) : MessagePipeline {

    private val _pipelineState = MutableStateFlow(MessagePipeline.PipelineState())
    override val pipelineState: StateFlow<MessagePipeline.PipelineState> = _pipelineState

    private val _queueDepth = MutableStateFlow(0)
    override val queueDepth: StateFlow<Int> = _queueDepth

    override suspend fun execute(input: MessagePipeline.PipelineInput): Boolean {
        val startTime = System.currentTimeMillis()
        _pipelineState.value = MessagePipeline.PipelineState(stage = MessagePipeline.Stage.VALIDATE)
        _queueDepth.value = maxOf(0, _queueDepth.value + 1)

        return try {
            ChatDebugLog.log("[Pipeline] calling ContentFilter.checkInput...")
            ChatDebugLog.log("[Pipeline] STEP1: checkInput start")
            val filterResult = ContentFilter.checkInput(input.rawText)
            ChatDebugLog.log("[Pipeline] STEP1: checkInput done, violating=${filterResult.isViolating}")
            ChatDebugLog.log("[Pipeline] ContentFilter returned: violating=${filterResult.isViolating}")
            if (filterResult.isViolating) {
                onViolation?.invoke(filterResult.level)
                _pipelineState.value = MessagePipeline.PipelineState(
                    stage = MessagePipeline.Stage.VALIDATE,
                    error = "内容违规: ${filterResult.reason}"
                )
                _queueDepth.value = maxOf(0, _queueDepth.value - 1)
                return false
            }

            _pipelineState.value = MessagePipeline.PipelineState(stage = MessagePipeline.Stage.CLASSIFY)
            ChatDebugLog.log("[Pipeline] STEP2: checkVector start")
            val vectorResult = ContentFilter.checkVector(input.rawText)
            ChatDebugLog.log("[Pipeline] STEP2: checkVector done")

            _pipelineState.value = MessagePipeline.PipelineState(stage = MessagePipeline.Stage.CLASSIFY)
            ChatDebugLog.log("[Pipeline] STEP3: Bayesian start (timeout=${TimeoutBudgets.SAFETY_CLASSIFY_MS}ms)")
            val bayesianScore = withTimeoutOrNull(TimeoutBudgets.SAFETY_CLASSIFY_MS) {
                ContentSafetyVerifier.verifyUserInputAsync(input.rawText, filterResult, vectorResult)
            } ?: com.lianyu.ai.common.safety.SafetyScore(
                score = 1.0,
                source = com.lianyu.ai.common.safety.ScoreSource.USER_INPUT,
                explanation = "Safety check timed out, fail-closed"
            )
            ChatDebugLog.log("[Pipeline] STEP3: Bayesian done, dangerous=${bayesianScore.isDangerous}")

            if (bayesianScore.isDangerous) {
                onViolation?.invoke(ContentFilter.ViolationLevel.HIGH)
                _pipelineState.value = MessagePipeline.PipelineState(
                    stage = MessagePipeline.Stage.CLASSIFY,
                    error = "贝叶斯判定危险: ${bayesianScore.explanation}"
                )
                _queueDepth.value = maxOf(0, _queueDepth.value - 1)
                return false
            }

            // 阶段 3-5 由 ChatViewModel.doStartApiCall 负责
            _pipelineState.value = MessagePipeline.PipelineState(
                stage = MessagePipeline.Stage.SEND,
                totalDurationMs = System.currentTimeMillis() - startTime
            )
            _queueDepth.value = maxOf(0, _queueDepth.value - 1)
            true

        } catch (e: kotlinx.coroutines.CancellationException) {
            // [CRITICAL] 必须重新抛出 CancellationException，否则 withTimeoutOrNull 失效
            _queueDepth.value = maxOf(0, _queueDepth.value - 1)
            throw e
        } catch (e: Throwable) {
            SecureLog.e("MessagePipeline", "[${_pipelineState.value.stage}] 失败", e)
            ChatDebugLog.log("[MessagePipeline] error at stage=${_pipelineState.value.stage}: ${e.javaClass.simpleName}: ${e.message}")
            _pipelineState.value = MessagePipeline.PipelineState(
                stage = _pipelineState.value.stage,
                error = e.message
            )
            _queueDepth.value = maxOf(0, _queueDepth.value - 1)
            false
        }
    }
}
