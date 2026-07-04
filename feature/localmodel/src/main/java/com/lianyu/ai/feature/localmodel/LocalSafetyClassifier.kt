package com.lianyu.ai.feature.localmodel

import android.util.Log
import com.lianyu.ai.common.ContentFilter
import com.lianyu.ai.common.SafetyClassifier

class LocalSafetyClassifier(
    private val localAi: LocalAiService
) : SafetyClassifier {

    companion object {
        private const val TAG = "SafetyGemma"

        private val SYS = """
安全审核。判断文本违规等级，只输出一个词:
NONE LOW MEDIUM HIGH SEVERE CRITICAL EXTREME
        """.trimIndent()
    }

    override suspend fun classify(text: String): ContentFilter.ViolationLevel {
        try {
            val model = localAi.activeModel
            val context = localAi.applicationContext
            val modelFile = model.modelFile(context)
            if (!modelFile.exists()) return ContentFilter.ViolationLevel.NONE

            val response = localAi.generate(
                systemPrompt = SYS,
                historyPrompt = "",
                userPrompt = text.take(300),
                model = model
            )
            val r = response.trim().uppercase()
            Log.d(TAG, "$r ← ${text.take(40)}")

            return when {
                "EXTREME" in r -> ContentFilter.ViolationLevel.EXTREME
                "CRITICAL" in r -> ContentFilter.ViolationLevel.CRITICAL
                "SEVERE" in r -> ContentFilter.ViolationLevel.SEVERE
                "HIGH" in r -> ContentFilter.ViolationLevel.HIGH
                "MEDIUM" in r -> ContentFilter.ViolationLevel.MEDIUM
                "LOW" in r -> ContentFilter.ViolationLevel.LOW
                else -> ContentFilter.ViolationLevel.NONE
            }
        } catch (e: Exception) {
            Log.w(TAG, "不可用", e)
            return ContentFilter.ViolationLevel.NONE
        }
    }
}
