package com.lianyu.ai.common

import android.util.Log
import java.util.regex.Pattern

object LanguageRestrictor {

    private const val TAG = "LanguageRestrictor"

    enum class LanguageType {
        CHINESE_SIMPLIFIED,    // 简体中文
        CHINESE_TRADITIONAL,   // 繁体中文
        JAPANESE,              // 日语
        ENGLISH,               // 英文
        KOREAN,                // 韩语
        MIXED,                 // 混合语言
        UNKNOWN                 // 未知/无法识别
    }

    data class LanguageCheckResult(
        val primaryLanguage: LanguageType,
        val confidence: Double,
        val chineseRatio: Double,
        val nonChineseDetected: Boolean,
        val detectedLanguages: List<LanguageType>,
        val warningMessage: String?
    )

    // 语言特征模式
    private val languagePatterns = mapOf(
        // 简体中文字符范围
        "CHINESE_SIMPLIFIED" to Pattern.compile("[\\u4e00-\\u9fff]"),
        // 日文假名（平假名 + 片假名）
        "JAPANESE" to Pattern.compile("[\\u3040-\\u309f\\u30a0-\\u30ff]"),
        // 韩文字符
        "KOREAN" to Pattern.compile("[\\uac00-\\ud7af]"),
        // 英文字母
        "ENGLISH" to Pattern.compile("[a-zA-Z]"),
        // 繁体中文特有字符（部分示例）
        "CHINESE_TRADITIONAL" to Pattern.compile("[\\u9577國語學開後會來這個與們|個們]|(為|義|從|進|區|醫|並|專|業|參|發|葉|處|備|複|與|據|擔|應|雙|關|開|際|電|號|預|頻|頭|實|賣|藝|見|車|輪|聲|響|護|證|讓|識|講|購|費|達|過|還|進|運|動|邊|選|適|題|館|體|魚|鳥|塊|條|幾|廠|廣|庫|廳|廢|廼|彙|徵|德|徵|應|戀|愛|爺|爽|糧|網|罰|羅|義|舊|艱|藝|蘇|誌|親|認|識|課|調|談|請|諸|謎|講|購|費|貝|貨|質|趙|趕|趣|躍|軀|輕|較|載|辦|違|遞|鄉|醒|銷|開|間|陽|隊|靜|面|韋|風|飛|食|首|香|馬|高|魯|鴿)")
    )

    /**
     * 检测文本的主要语言
     */
    fun detectLanguage(text: String): LanguageCheckResult {
        if (text.isBlank()) {
            return LanguageCheckResult(
                primaryLanguage = LanguageType.UNKNOWN,
                confidence = 0.0,
                chineseRatio = 0.0,
                nonChineseDetected = false,
                detectedLanguages = emptyList(),
                warningMessage = null
            )
        }

        val totalChars = text.length.toDouble()
        
        // 统计各语言字符数
        var chineseSimplifiedCount = 0
        var japaneseCount = 0
        var koreanCount = 0
        var englishCount = 0
        var chineseTraditionalCount = 0

        text.forEach { char ->
            when {
                isJapaneseChar(char) -> japaneseCount++
                isKoreanChar(char) -> koreanCount++
                isEnglishChar(char) -> englishCount++
                isChineseTraditionalChar(char) -> chineseTraditionalCount++
                isChineseSimplifiedChar(char) -> chineseSimplifiedCount++
            }
        }

        // 计算比例
        val chineseTotal = chineseSimplifiedCount + chineseTraditionalCount
        val chineseRatio = chineseTotal / totalChars
        
        val detectedLanguages = mutableListOf<LanguageType>()
        
        if (chineseSimplifiedCount > 0) detectedLanguages.add(LanguageType.CHINESE_SIMPLIFIED)
        if (chineseTraditionalCount > 0) detectedLanguages.add(LanguageType.CHINESE_TRADITIONAL)
        if (japaneseCount > 0) detectedLanguages.add(LanguageType.JAPANESE)
        if (koreanCount > 0) detectedLanguages.add(LanguageType.KOREAN)
        if (englishCount > 0) detectedLanguages.add(LanguageType.ENGLISH)

        // 判断主要语言
        val counts = mapOf(
            LanguageType.CHINESE_SIMPLIFIED to chineseSimplifiedCount,
            LanguageType.CHINESE_TRADITIONAL to chineseTraditionalCount,
            LanguageType.JAPANESE to japaneseCount,
            LanguageType.KOREAN to koreanCount,
            LanguageType.ENGLISH to englishCount
        )

        val maxEntry = counts.maxByOrNull { it.value }!!
        val primaryLanguage = if (maxEntry.value == 0) LanguageType.UNKNOWN else maxEntry.key
        val confidence = if (totalChars > 0) maxEntry.value / totalChars else 0.0

        // 检测是否包含非中文内容
        val nonChineseDetected = japaneseCount > 0 || koreanCount > 0 || 
                                (englishCount > text.length * 0.5 && chineseRatio < 0.5)

        // 生成警告信息
        val warningMessage = generateWarning(
            primaryLanguage, 
            chineseRatio, 
            nonChineseDetected,
            detectedLanguages
        )

        return LanguageCheckResult(
            primaryLanguage = primaryLanguage,
            confidence = confidence,
            chineseRatio = chineseRatio,
            nonChineseDetected = nonChineseDetected,
            detectedLanguages = detectedLanguages,
            warningMessage = warningMessage
        )
    }

    /**
     * 检查是否符合中文人设要求
     * @return true 如果符合要求，false 如果不符合
     */
    fun checkChineseOnlyRequirement(text: String): Boolean {
        if (text.length <= 3) return true

        val result = detectLanguage(text)

        return when {
            result.primaryLanguage == LanguageType.CHINESE_SIMPLIFIED ||
            result.primaryLanguage == LanguageType.CHINESE_TRADITIONAL -> {
                result.chineseRatio >= 0.5 || !result.nonChineseDetected
            }

            result.chineseRatio >= 0.6 -> true

            else -> false
        }
    }

    /**
     * 获取语言检测报告
     */
    fun getLanguageReport(text: String): String {
        val result = detectLanguage(text)
        
        return buildString {
            appendLine("=== 语言检测报告 ===")
            appendLine("文本长度: ${text.length} 字符")
            appendLine("主要语言: ${getLanguageName(result.primaryLanguage)}")
            appendLine("置信度: ${(result.confidence * 100).toInt()}%")
            appendLine("中文占比: ${(result.chineseRatio * 100).toInt()}%")
            appendLine("检测到的语言: ${result.detectedLanguages.joinToString(", ") { getLanguageName(it) }}")
            appendLine("非中文内容: ${if (result.nonChineseDetected) "⚠️ 检测到" else "✅ 未检测到"}")
            
            if (result.warningMessage != null) {
                appendLine()
                appendLine("⚠️ 警告: ${result.warningMessage}")
            }
            
            appendLine()
            appendLine("符合中文人设要求: ${if (checkChineseOnlyRequirement(text)) "✅ 是" else "❌ 否"}")
        }
    }

    private fun generateWarning(
        primaryLanguage: LanguageType,
        chineseRatio: Double,
        nonChineseDetected: Boolean,
        detectedLanguages: List<LanguageType>
    ): String? {
        val warnings = mutableListOf<String>()

        when {
            chineseRatio < 0.3 -> {
                warnings.add("内容几乎不包含中文，请使用简体中文或繁体中文交流")
            }
            chineseRatio < 0.5 -> {
                warnings.add("中文内容占比较低（${(chineseRatio * 100).toInt()}%），建议增加中文使用")
            }
            nonChineseDetected && detectedLanguages.contains(LanguageType.JAPANESE) -> {
                warnings.add("检测到日文内容，请使用中文交流")
            }
            nonChineseDetected && detectedLanguages.contains(LanguageType.KOREAN) -> {
                warnings.add("检测到韩文内容，请使用中文交流")
            }
            detectedLanguages.size > 2 -> {
                warnings.add("检测到多种语言混合使用，建议统一使用中文")
            }
        }

        return if (warnings.isNotEmpty()) warnings.joinToString("; ") else null
    }

    private fun isChineseSimplifiedChar(char: Char): Boolean {
        val code = char.code
        if (code in 0x4e00..0x9fff && !isChineseTraditionalChar(char)) return true
        if (code in 0xff01..0xff5e) return true
        if (code in 0x3000..0x303f) return true
        if (char in "，。！？、；：\"\"''（）【】《》—…·～￥") return true
        val asciiPunct = setOf('?', '!', '~', '.', ',', ';', ':', '(', ')', '[', ']', '{', '}', '+', '-', '=', '_', '@', '#', '%', '^', '&', '*', '|', '\\', '/', '`', '"', '\'', '<', '>')
        if (char in asciiPunct) return true
        if (char == ' ' || char == '\n' || char == '\r' || char == '\t') return true
        if (code == 0x2026) return true
        return false
    }

    private fun isChineseTraditionalChar(char: Char): Boolean {
        // 繁体中文常用字（简化版判断）
        val traditionalChars = setOf(
            '長', '國', '語', '學', '開', '後', '會', '來', '這', '個', '與', '們',
            '為', '義', '從', '進', '區', '醫', '並', '專', '業', '參', '發', '葉',
            '處', '備', '複', '據', '擔', '應', '雙', '關', '際', '電', '號', '預',
            '頻', '頭', '實', '賣', '藝', '見', '車', '輪', '聲', '響', '護', '證',
            '讓', '識', '講', '購', '費', '達', '過', '還', '運', '動', '邊', '選',
            '適', '題', '館', '體', '魚', '鳥', '塊', '條', '幾', '廠', '廣', '庫',
            '廳', '廢', '廼', '彙', '徵', '德', '戀', '愛', '爺', '爽', '糧', '網',
            '罰', '羅', '舊', '艱', '蘇', '誌', '親', '認', '課', '調', '談', '請',
            '諸', '謎', '韋', '風', '飛', '食', '首', '香', '馬', '高', '魯', '鴿'
        )
        return traditionalChars.contains(char)
    }

    private fun isJapaneseChar(char: Char): Boolean {
        val code = char.code
        // 平假名: 3040-309F, 片假名: 30A0-30FF
        return code in 0x3040..0x30ff
    }

    private fun isKoreanChar(char: Char): Boolean {
        val code = char.code
        // 韩文音节: AC00-D7AF
        return code in 0xac00..0xd7af
    }

    private fun isEnglishChar(char: Char): Boolean {
        return char.code in 0x41..0x5a || char.code in 0x61..0x7a // A-Z, a-z
    }

    private fun getLanguageName(language: LanguageType): String {
        return when (language) {
            LanguageType.CHINESE_SIMPLIFIED -> "简体中文"
            LanguageType.CHINESE_TRADITIONAL -> "繁体中文"
            LanguageType.JAPANESE -> "日语"
            LanguageType.ENGLISH -> "英文"
            LanguageType.KOREAN -> "韩语"
            LanguageType.MIXED -> "混合语言"
            LanguageType.UNKNOWN -> "未知"
        }
    }
}
