package com.lianyu.ai.common.safety

private val URL_REGEX = Regex("https?://|www\\.")
private val PHONE_REGEX = Regex("1[3-9]\\d{9}")
private val SCORE_REGEX = Regex("\\(([0-9.]+)\\)")

/**
 * L1+L2 融合特征提取器。
 *
 * 将关键词层和向量层的输出转化为结构化特征 token，
 * 直接喂给 BayesianClassifier（替代原始 n-gram）。
 *
 * 特征 token 格式：
 *   kw_hit:N      — 关键词命中数（0, 1, 2, 3+）
 *   kw_max:LEVEL  — 最高违规等级
 *   vec_score:B   — 向量相似度分桶（0-4）
 *   vec_gray:Y/N  — 是否在灰区
 *   len_bucket:B  — 文本长度分桶（0-3）
 *   has_url:Y/N
 *   has_phone:Y/N
 */
object SafetyFeatureExtractor {

    fun extract(
        keywordResult: com.lianyu.ai.common.ContentFilter.CheckResult,
        vectorResult: com.lianyu.ai.common.ContentFilter.CheckResult?,
        rawText: String
    ): List<String> {
        val features = mutableListOf<String>()

        // ── L1 关键词特征 ──
        val hitCount = keywordResult.matchedKeywords.size
        features.add("kw_hit:" + when {
            hitCount == 0 -> "0"
            hitCount == 1 -> "1"
            hitCount == 2 -> "2"
            else -> "3p"
        })

        features.add("kw_max:" + keywordResult.level.name)

        // ── L2 向量特征 ──
        if (vectorResult != null) {
            // 从 reason 中提取分数（格式: "语义匹配(0.85)" 或 "语义灰区(0.45)"）
            val score = extractScore(vectorResult.reason)
            features.add("vec_score:" + when {
                score < 0.3f -> "0"
                score < 0.5f -> "1"
                score < 0.7f -> "2"
                score < 0.85f -> "3"
                else -> "4"
            })
            features.add("vec_gray:" + if (vectorResult.reason.contains("灰区")) "Y" else "N")
        } else {
            features.add("vec_score:na")
            features.add("vec_gray:na")
        }

        // ── 文本结构特征 ──
        val len = rawText.length
        features.add("len_bucket:" + when {
            len < 10 -> "0"
            len < 50 -> "1"
            len < 200 -> "2"
            else -> "3"
        })

        features.add("has_url:" + if (rawText.contains(URL_REGEX)) "Y" else "N")
        features.add("has_phone:" + if (rawText.contains(PHONE_REGEX)) "Y" else "N")

        return features
    }

    private fun extractScore(reason: String): Float {
        // "语义匹配(0.85)" → 0.85
        val match = SCORE_REGEX.find(reason)
        return match?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    }

    // ===== 8 特征 float 向量 (C++ Bayesian 接口) =====

    /**
     * 提取 8 维 float 特征向量，供 nativeBayesianPredict 使用。
     *
     * [0] l1_ac_score    — AC 命中数/总关键词 (normalized)
     * [1] char_entropy   — 字符熵 (simplified)
     * [2] token_density  — 命中密度
     * [3] repetition     — 重复度
     * [4] mixed_script   — 混合脚本 (0/1)
     * [5] total_chars    — 总字符 (normalized)
     * [6] flagged_count  — 标记数
     * [7] clean_count    — 干净数
     */
    fun extractFloatVector(
        keywordResult: com.lianyu.ai.common.ContentFilter.CheckResult,
        vectorResult: com.lianyu.ai.common.ContentFilter.CheckResult?,
        rawText: String
    ): FloatArray {
        val vec = FloatArray(8)
        val len = rawText.length.coerceAtLeast(1)

        // [0] l1_ac_score: normalized hit count
        vec[0] = (keywordResult.matchedKeywords.size / 4f).coerceAtMost(1f)

        // [1] char_entropy: unique chars / total chars
        vec[1] = rawText.toSet().size.toFloat() / len

        // [2] token_density: hits per char
        vec[2] = keywordResult.matchedKeywords.size.toFloat() / len

        // [3] repetition: 1 - unique_ratio (高重复度→接近1)
        vec[3] = 1f - rawText.toSet().size.toFloat() / rawText.length.coerceAtLeast(1)

        // [4] mixed_script: contains non-CJK + CJK
        vec[4] = if (rawText.any { it in '\u4e00'..'\u9fff' } && rawText.any { it in 'a'..'z' || it in 'A'..'Z' }) 1f else 0f

        // [5] total_chars: log-normalized length
        vec[5] = (kotlin.math.ln(len.toDouble() + 1) / 10.0).toFloat()

        // [6] flagged_count
        vec[6] = keywordResult.matchedKeywords.size.toFloat()

        // [7] clean_count
        vec[7] = (len - keywordResult.matchedKeywords.size).coerceAtLeast(0).toFloat()

        return vec
    }
}
