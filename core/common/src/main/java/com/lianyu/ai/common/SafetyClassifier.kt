package com.lianyu.ai.common

/**
 * 安全分类器接口 —— L3 语义模型层。
 *
 * 实现可用本地 Gemma、远程 API、或其他模型。
 * 在 L1(AC) + L2(向量) 之后，对灰区/可疑内容做最终判定。
 */
interface SafetyClassifier {
    /**
     * 对文本做安全分类。
     * @return 违规等级，NONE = 安全
     */
    suspend fun classify(text: String): ContentFilter.ViolationLevel
}
