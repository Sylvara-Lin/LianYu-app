package com.lianyu.ai.common.safety

/**
 * 标注训练样本。
 * 双通道独立收集：用户输入样本 + 模型输出样本。
 */
data class SafetySample(
    val text: String,
    val isViolation: Boolean,
    val source: SampleSource,
    val label: String = "",           // 违规类别标签，如 "广告"/"色情"/"暴力"
    val timestamp: Long = System.currentTimeMillis()
)

enum class SampleSource { USER_INPUT, MODEL_OUTPUT }
