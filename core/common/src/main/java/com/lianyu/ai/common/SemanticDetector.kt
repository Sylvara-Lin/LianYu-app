package com.lianyu.ai.common

import android.util.Log
import java.util.regex.Pattern

object SemanticDetector {

    private const val TAG = "SemanticDetector"

    data class DetectionResult(
        val isViolating: Boolean,
        val level: ContentFilter.ViolationLevel,
        val reason: String,
        val matchedTerms: List<String>
    )

    // P2-15: 收紧绕过模式 — 只保留明确的绕过关键词，去掉常见词汇
    private val evasionPatterns = mapOf(
        "CHAR_SUBSTITUTION" to arrayOf(
            """(?i)(s${'$'}x|f${'$'}ck|sh\!t|@ss|b\!tch|d\!ck)""",
            """(?i)(p0rn|pr0n|s3x|n00d|f4gg0t|r3t4rd)""",
            """(?i)(c\.h\.i\.l\.d|p\.e\.d\.o|j\.i\.h\.a\.d)""",
            """(?i)(\*sex\*|\*porn\*|\*fuck\*|\*rape\*)"""
        ),
        "SPACE_INSERTION" to arrayOf(
            """(?i)(s\s+e\s+x|p\s+o\s+r\s+n|f\s+u\s+c\s+k|k\s+i\s+l\s+l)""",
            """(?i)(j\s+a\s+i\s+l\s+b\s+r\s+e\s+a\s+k)"""
        )
    )

    // 上下文意图检测 - 只保留明确有害意图
    private val intentPatterns = mapOf(
        "SEXUAL_INTENT" to arrayOf(
            "(?i)(write\\s+(a|me)\\s+(erotic|porn|nude|sex\\s*ual)\\s*(story|scene))",
            "(?i)(describe\\s+(explicitly|graphically)\\s+(sex|nude|porn))",
            "(?i)(make\\s+it\\s+(more\\s+)?(explicit|erotic|sexual))"
        ),
        "VIOLENT_INTENT" to arrayOf(
            "(?i)(how\\s+to\\s+(kill|hurt|murder|torture|poison|bomb))",
            "(?i)(help\\s+me\\s+(die|kill\\s+myself|end\\s+my\\s+life|suicide))",
            "(?i)(want\\s+to\\s+(die|kill\\s+myself|hurt\\s+someone))"
        ),
        "ILLEGAL_INTENT" to arrayOf(
            "(?i)(how\\s+can\\s+I\\s+(get\\s+away\\s+with|avoid\\s+detection|hide\\s+evidence))",
            "(?i)(black\\s+market|dark\\s+web|deep\\s+web)",
            "(?i)(money\\s+laundering|tax\\s+evasion|fraud\\s+scheme|identity\\s+theft)"
        )
    )

    // 多语言混合检测 — 只保留明确有害的中英混用
    private val multilingualPatterns = mapOf(
        "CHINESE_ENGLISH_MIX" to arrayOf(
            "(?i)(写\\s*(write|generate|create).{0,10}(色情|porn|hentai|性爱))",
            "(?i)(忽略\\s*(ignore|disregard).{0,10}(安全规则|限制))",
            "(?i)((儿童|幼女|loli|child).{0,10}(色情|porn|性爱|sex))",
            "(?i)((炸弹|bomb|炸药).{0,10}(制造|make|build).{0,10}(教程|guide))"
        ),
        "PINYIN_DETECTION" to arrayOf(
            "(se\\s+qing|huang\\s+pian|lian\\s+tong|xing\\s+ai)",
            "(yue\\s+yu|po\\s+xian|jie\\s+jia)"
        )
    )

    /** 预编译的绕过模式: category -> List<Pattern> */
    private val compiledEvasionPatterns: Map<String, List<Pattern>> by lazy {
        evasionPatterns.mapValues { (_, patterns) ->
            patterns.mapNotNull { runCatching { Pattern.compile(it) }.getOrNull() }
        }
    }

    /** 预编译的意图模式: category -> List<Pattern> */
    private val compiledIntentPatterns: Map<String, List<Pattern>> by lazy {
        intentPatterns.mapValues { (_, patterns) ->
            patterns.mapNotNull { runCatching { Pattern.compile(it) }.getOrNull() }
        }
    }

    /** 预编译的多语言模式: category -> List<Pattern> */
    private val compiledMultilingualPatterns: Map<String, List<Pattern>> by lazy {
        multilingualPatterns.mapValues { (_, patterns) ->
            patterns.mapNotNull { runCatching { Pattern.compile(it) }.getOrNull() }
        }
    }

    fun detectSemanticViolations(text: String): DetectionResult {
        if (text.isBlank()) return DetectionResult(false, ContentFilter.ViolationLevel.NONE, "", emptyList())

        val lowerText = text.lowercase()
        val allMatches = mutableListOf<String>()

        // 1. 检测绕过模式
        for ((category, patterns) in compiledEvasionPatterns) {
            for (pattern in patterns) {
                try {
                    val matcher = pattern.matcher(lowerText)
                    while (matcher.find()) {
                        allMatches.add("[EVASION:$category] ${matcher.group()}")
                        Log.w(TAG, "检测到绕过尝试: $category -> ${matcher.group()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "正则匹配失败: ${pattern.pattern()}", e)
                }
            }
        }

        // 2. 检测恶意意图
        for ((category, patterns) in compiledIntentPatterns) {
            var matchCount = 0
            for (pattern in patterns) {
                try {
                    if (pattern.matcher(lowerText).find()) {
                        matchCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "正则匹配失败: ${pattern.pattern()}", e)
                }
            }
            if (matchCount >= 2) {
                allMatches.add("[INTENT:$category] 检测到${matchCount}个意图匹配")
                Log.w(TAG, "检测到可疑意图: $category ($matchCount matches)")
            }
        }

        // 3. 检测多语言混合
        for ((category, patterns) in compiledMultilingualPatterns) {
            for (pattern in patterns) {
                try {
                    val matcher = pattern.matcher(lowerText)
                    while (matcher.find()) {
                        allMatches.add("[MULTILINGUAL:$category] ${matcher.group()}")
                        Log.w(TAG, "检测到多语言混合: $category -> ${matcher.group()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "正则匹配失败: ${pattern.pattern()}", e)
                }
            }
        }

        // P2-15: 等级判定 — 只有明确有害内容才 HIGH+，普通绕过仅 LOW
        return when {
            allMatches.isEmpty() ->
                DetectionResult(false, ContentFilter.ViolationLevel.NONE, "", emptyList())

            allMatches.any { it.contains("CHILD", ignoreCase = true) || it.contains("PORNO", ignoreCase = true) || it.contains("PEDO", ignoreCase = true) } ->
                DetectionResult(true, ContentFilter.ViolationLevel.EXTREME, "检测到极端违规内容", allMatches.distinct())

            allMatches.any { it.contains("BOMB", ignoreCase = true) || it.contains("TERROR", ignoreCase = true) || it.contains("ENCODING") } ->
                DetectionResult(true, ContentFilter.ViolationLevel.CRITICAL, "检测到极严重违规内容", allMatches.distinct())

            allMatches.any { it.contains("INTENT") && (it.contains("VIOLENT") || it.contains("ILLEGAL")) } ->
                DetectionResult(true, ContentFilter.ViolationLevel.SEVERE, "检测到严重违规意图", allMatches.distinct())

            allMatches.any { it.contains("MULTILINGUAL") } ->
                DetectionResult(true, ContentFilter.ViolationLevel.MEDIUM, "检测到可疑多语言混合", allMatches.distinct())

            allMatches.any { it.contains("INTENT") } ->
                DetectionResult(true, ContentFilter.ViolationLevel.LOW, "检测到不明确意图", allMatches.distinct())

            else ->
                DetectionResult(true, ContentFilter.ViolationLevel.LOW, "检测到轻微绕过尝试", allMatches.distinct())
        }
    }

    fun preprocessAndDetect(text: String): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        results.add(detectSemanticViolations(text))
        val noSpaces = text.replace("\\s+".toRegex(), "")
        if (noSpaces != text) {
            results.add(detectSemanticViolations(noSpaces))
        }
        return results.filter { it.isViolating }
    }

    fun generateDetectionReport(text: String): String {
        val violations = preprocessAndDetect(text)
        if (violations.isEmpty()) return "✅ 未检测到违规"
        
        val report = StringBuilder()
        report.appendLine("⚠️ 检测到 ${violations.size} 个潜在违规:")
        
        for (violation in violations) {
            report.appendLine("  [${violation.level}] ${violation.reason}")
            violation.matchedTerms.forEach { term ->
                report.appendLine("    - $term")
            }
        }
        
        return report.toString()
    }
}
