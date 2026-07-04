package com.lianyu.ai.feature.chat.ui.viewmodel

import com.lianyu.ai.common.ContentFilter
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.common.safety.ContentSafetyVerifier
import com.lianyu.ai.common.safety.RiskLevel
import com.lianyu.ai.common.safety.SafetyScore
import com.lianyu.ai.common.safety.ScoreSource
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Safety check logic for AI-generated output.
 *
 * Extracted from ChatViewModel.finalizeResponse() to reduce class size.
 * Performs L1+L2 keyword/vector checks and Bayesian model output verification.
 *
 * @return A [SafetyDecision] containing the verdict and optional fallback message.
 */
internal object ChatSafetyChecker {

    private const val KEYWORD_CHECK_TIMEOUT_MS = 3000L
    private const val BAYESIAN_CHECK_TIMEOUT_MS = 5000L

    data class SafetyDecision(
        val isBlocked: Boolean,
        val fallbackMessage: String? = null,
        val modelKw: ContentFilter.CheckResult,
        val modelVec: ContentFilter.CheckResult?,
        val modelBayesian: SafetyScore
    )

    /**
     * Run all safety checks on AI-generated content.
     *
     * - L1+L2 keyword/vector extraction → Bayesian model output verification
     * - fail-closed: timeout = HIGH violation
     * - AI output does NOT record user violations
     */
    suspend fun checkAiOutput(
        aiContent: String,
        userContentForMemory: String?
    ): SafetyDecision {
        // L1+L2特征提取 → 贝叶斯模型输出校验
        val modelKw = try {
            withTimeoutOrNull(KEYWORD_CHECK_TIMEOUT_MS) { ContentFilter.checkFull(aiContent) }
        } catch (_: Exception) { null }
            ?: ContentFilter.CheckResult(true, ContentFilter.ViolationLevel.HIGH, "安全检查超时", emptyList())

        val modelVec = try {
            withTimeoutOrNull(KEYWORD_CHECK_TIMEOUT_MS) { ContentFilter.checkVector(aiContent) }
        } catch (_: Exception) { null }
            ?: ContentFilter.CheckResult(true, ContentFilter.ViolationLevel.HIGH, "向量检查超时", emptyList())

        // 关键词级拦截：HIGH 及以上违规直接拦截
        if (modelKw.isViolating && modelKw.level >= ContentFilter.ViolationLevel.HIGH) {
            SecureLog.w("ChatViewModel", "Output keyword violation: ${modelKw.level} - ${modelKw.reason}")
            ChatDebugLog.log("[ChatVM] AI output blocked by keyword check: ${modelKw.level} - ${modelKw.reason}")
            return SafetyDecision(
                isBlocked = true,
                fallbackMessage = "抱歉，我无法继续这个话题。",
                modelKw = modelKw,
                modelVec = modelVec,
                modelBayesian = SafetyScore(
                    score = 0.0,
                    source = ScoreSource.MODEL_OUTPUT,
                    explanation = "Keyword check blocked"
                )
            )
        }

        // 贝叶斯模型输出校验
        val modelBayesian = try {
            withTimeoutOrNull(BAYESIAN_CHECK_TIMEOUT_MS) {
                ContentSafetyVerifier.verifyModelOutputAsync(
                    aiContent, modelKw,
                    modelVec ?: ContentFilter.CheckResult(false, ContentFilter.ViolationLevel.NONE, "timeout", emptyList()),
                    userContentForMemory ?: ""
                )
            } ?: SafetyScore(
                score = 0.0,
                source = ScoreSource.MODEL_OUTPUT,
                explanation = "模型输出校验超时"
            )
        } catch (e: Exception) {
            SecureLog.e("ChatViewModel", "Model output verification failed", e)
            SafetyScore(
                score = 0.0,
                source = ScoreSource.MODEL_OUTPUT,
                explanation = "模型输出校验异常"
            )
        }

        if (modelBayesian.isDangerous) {
            SecureLog.w("ChatViewModel", "Bayesian model output blocked (" + "%.3f".format(modelBayesian.score) + "): " + modelBayesian.explanation)
            ChatDebugLog.log("[ChatVM] AI output blocked by Bayesian: score=${"%.3f".format(modelBayesian.score)}, reason=${modelBayesian.explanation}")
            return SafetyDecision(
                isBlocked = true,
                fallbackMessage = "抱歉，我无法继续这个话题。",
                modelKw = modelKw,
                modelVec = modelVec,
                modelBayesian = modelBayesian
            )
        }

        if (modelBayesian.riskLevel == RiskLevel.SUSPICIOUS) {
            SecureLog.w("ChatViewModel", "Bayesian model output suspicious (" + "%.3f".format(modelBayesian.score) + "): " + modelBayesian.explanation)
        }

        // 利用验证结果训练模型输出分类器
        if (modelBayesian.isDangerous || modelKw.isViolating) {
            ContentSafetyVerifier.trainModelOutput(
                aiContent, modelBayesian.isDangerous,
                kwResult = modelKw, vecResult = modelVec
            )
        }

        return SafetyDecision(
            isBlocked = false,
            fallbackMessage = null,
            modelKw = modelKw,
            modelVec = modelVec,
            modelBayesian = modelBayesian
        )
    }
}
