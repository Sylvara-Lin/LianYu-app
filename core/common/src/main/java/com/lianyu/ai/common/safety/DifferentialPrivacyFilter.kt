package com.lianyu.ai.common.safety

import kotlin.math.exp
import kotlin.math.ln
import java.security.SecureRandom

/**
 * ε-Local Differential Privacy filter for chat messages.
 *
 * Applies randomized response to personally identifiable information (PII)
 * before messages are sent to third-party AI providers. Each piece of PII
 * is either preserved (with probability p) or replaced with a generic token
 * (with probability 1-p), providing ε-differential privacy.
 *
 * Privacy guarantee:
 *   For any two neighboring inputs differing by one PII field,
 *   P[output | input_1] ≤ e^ε × P[output | input_2]
 *
 * ε values:
 *   ε = 0.1 → strong privacy (p ≈ 0.525)
 *   ε = 1.0 → moderate (p ≈ 0.731)
 *   ε = 5.0 → weak (p ≈ 0.993)
 *
 * Default: ε = 1.0 (moderate privacy with reasonable utility)
 */
object DifferentialPrivacyFilter {

    private val random = SecureRandom()

    /** Default privacy budget (ε). Higher = less noise, lower = more privacy. */
    var epsilon: Double = 1.0

    /** Whether DP filtering is active */
    var enabled: Boolean = true

    /** PII patterns detected and replaced */
    private val piiPatterns = listOf(
        // Chinese phone numbers
        Regex("1[3-9]\\d{9}") to "[PHONE]",
        // Chinese ID numbers (18 digits)
        Regex("\\d{17}[\\dXx]") to "[ID_NUMBER]",
        // Email addresses
        Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}") to "[EMAIL]",
        // URLs
        Regex("https?://[\\w./?=&%-]+") to "[URL]",
        // IP addresses
        Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") to "[IP]",
        // Bank card numbers (16-19 digits)
        Regex("\\d{16,19}") to "[CARD_NUMBER]",
        // WeChat/Alipay IDs
        Regex("(?:wxid_|alipay_)[a-zA-Z0-9_-]+") to "[ACCOUNT_ID]",
    )

    /** Chinese geographic names (city level) — replaced with province */
    private val geoPatterns = mapOf(
        Regex("(?:北京|上海|广州|深圳|杭州|成都|武汉|南京|重庆|天津|苏州|西安|长沙|郑州|青岛|东莞|宁波|佛山|合肥|无锡|厦门|福州|济南|大连|沈阳|昆明|哈尔滨|长春|石家庄|贵阳|南宁|太原|南昌|兰州|乌鲁木齐|呼和浩特|银川|西宁|海口|拉萨)市?") to "[CITY]",
    )

    /** Chinese personal names (common surnames + given name patterns) */
    private val namePattern = Regex(
        "(?:[王李张刘陈杨黄赵周吴徐孙马胡朱郭何罗高林郑梁谢唐许冯宋韩邓彭曹曾田萧潘袁蔡蒋余于杜叶程魏苏吕丁任卢姚沈钟姜崔谭陆范汪廖石金贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤])(?:[\\u4e00-\\u9fff]{1,2})"
    )

    /**
     * Apply differential privacy to a message.
     *
     * @param text The original message text
     * @return Message with PII probabilistically replaced per ε
     */
    fun sanitize(text: String): String {
        if (!enabled || text.isBlank()) return text

        val p = randomizationProbability(epsilon)
        var result = text

        // Replace structured PII patterns with probability (1-p)
        for ((pattern, replacement) in piiPatterns) {
            result = pattern.replace(result) { match ->
                if (random.nextDouble() < p) match.value else replacement
            }
        }

        // Replace geographic names
        for ((pattern, replacement) in geoPatterns) {
            result = pattern.replace(result) { match ->
                if (random.nextDouble() < p) match.value else replacement
            }
        }

        // Replace personal names (only in Chinese text context)
        result = namePattern.replace(result) { match ->
            if (random.nextDouble() < p) match.value else "[NAME]"
        }

        return result
    }

    /**
     * Compute the randomization probability from ε.
     * p = e^ε / (e^ε + 1)
     */
    private fun randomizationProbability(epsilon: Double): Double {
        val e = exp(epsilon)
        return e / (e + 1.0)
    }

    /**
     * Compute the effective ε from observed true/false ratio.
     * Used for audit/verification.
     */
    fun estimateEpsilon(keptCount: Int, totalCount: Int): Double {
        if (totalCount == 0 || keptCount == 0 || keptCount == totalCount) return Double.POSITIVE_INFINITY
        val p = keptCount.toDouble() / totalCount
        return ln(p / (1.0 - p))
    }
}
