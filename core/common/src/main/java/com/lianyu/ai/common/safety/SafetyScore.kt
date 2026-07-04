package com.lianyu.ai.common.safety

/**
 * 安全评分结果 — 概率值 + 风险等级 + 可解释性。
 *
 * 阈值定义（可配置）：
 *   score < 0.3  → SAFE（放行）
 *   score 0.3-0.85 → SUSPICIOUS（限频/标记但不拦截）
 *   score > 0.85  → DANGEROUS（拦截）
 *
 * [FIX] 阈值从0.7提高到0.85：0.7太敏感，先验0.3时正常消息也可能超0.7
 */
data class SafetyScore(
    /** 0.0 ~ 1.0，P(违规 | 文本) */
    val score: Double,

    /** 评分来源 */
    val source: ScoreSource,

    /** 贡献最大的特征词及其贡献度（正 = 推高违规概率，负 = 拉低） */
    val topTerms: List<Pair<String, Double>> = emptyList(),

    /** 可读解释 */
    val explanation: String = ""
) {
    val riskLevel: RiskLevel
        get() = when {
            score < LOW_THRESHOLD  -> RiskLevel.SAFE
            score < HIGH_THRESHOLD -> RiskLevel.SUSPICIOUS
            else                    -> RiskLevel.DANGEROUS
        }

    val isSafe: Boolean get() = riskLevel == RiskLevel.SAFE
    val isDangerous: Boolean get() = riskLevel == RiskLevel.DANGEROUS

    companion object {
        const val LOW_THRESHOLD = 0.3
        const val HIGH_THRESHOLD = 0.85

        fun neutral(source: ScoreSource) = SafetyScore(
            score = 0.5,
            source = source,
            explanation = "无训练数据，返回中性分数"
        )
    }
}

enum class RiskLevel { SAFE, SUSPICIOUS, DANGEROUS }

enum class ScoreSource { USER_INPUT, MODEL_OUTPUT, COMBINED }

/**
 * 双向校验结果 — 用户输入 + 模型输出联合判定。
 */
data class RoundTripVerdict(
    val userScore: SafetyScore,
    val modelScore: SafetyScore
) {
    /** 联合风险等级：取两端最高 */
    val overallLevel: RiskLevel
        get() = when {
            userScore.isDangerous || modelScore.isDangerous -> RiskLevel.DANGEROUS
            userScore.riskLevel == RiskLevel.SUSPICIOUS || modelScore.riskLevel == RiskLevel.SUSPICIOUS -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }

    val summary: String
        get() {
            val u = userScore
            val m = modelScore
            val lvl = overallLevel
            val sb = StringBuilder()
            sb.appendLine("=== 双向安全校验 ===")
            sb.appendLine("用户输入: ${u.riskLevel} (${"%.3f".format(u.score)})")
            if (u.topTerms.isNotEmpty()) {
                sb.append("  关键词: ")
                sb.appendLine(u.topTerms.take(3).joinToString(", ") { (term, contrib) ->
                    val dir = if (contrib > 0) "+" else ""
                    "$term($dir${"%.3f".format(Math.abs(contrib))})"
                })
            }
            sb.appendLine("模型输出: ${m.riskLevel} (${"%.3f".format(m.score)})")
            if (m.topTerms.isNotEmpty()) {
                sb.append("  关键词: ")
                sb.appendLine(m.topTerms.take(3).joinToString(", ") { (term, contrib) ->
                    val dir = if (contrib > 0) "+" else ""
                    "$term($dir${"%.3f".format(Math.abs(contrib))})"
                })
            }
            sb.append("联合判定: $lvl")
            return sb.toString()
        }
}
