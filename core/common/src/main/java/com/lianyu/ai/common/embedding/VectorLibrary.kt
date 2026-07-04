package com.lianyu.ai.common.embedding

import com.lianyu.ai.common.ContentFilter
import kotlin.math.sqrt

/**
 * 词向量违规范例库 —— VLB4 格式
 *
 * 随机索引法: 每个 char n-gram → 确定性稀疏向量，词向量 = n-gram 向量之和。
 * 输入文本 → n-gram 向量和 → 与各类质心余弦匹配。
 *
 * VLB4 二进制格式:
 *   [magic:4 "VLB4"] [dim:2 LE] [numWords:4 LE] [numClasses:2 LE=6]
 *   [words: wordLen:1, word:UTF8, levelId:1, vec: dim*1 int8] × numWords
 *   [centroids: dim*4 float32 LE] × 6
 */
class VectorLibrary private constructor(
    private val dim: Int,
    /** 各类质心 (L2归一化) */
    private val centroids: Map<ContentFilter.ViolationLevel, FloatArray>
) {
    companion object {
        const val HIT_THRESHOLD = 0.35f
        const val GRAY_LOWER = 0.20f
    }

    data class MatchResult(
        val level: ContentFilter.ViolationLevel,
        val score: Float,
        val isGrayZone: Boolean
    )

    /**
     * 检查文本与各类质心的余弦相似度。
     * @return 最高匹配的违规等级，或 null
     */
    fun check(text: String): MatchResult? {
        val vec = textToVector(text)
        var nonZero = 0
        for (v in vec) if (v != 0f) nonZero++
        if (nonZero < 2) return null

        var bestLevel: ContentFilter.ViolationLevel? = null
        var bestScore = 0f
        var isGray = false

        for ((level, centroid) in centroids) {
            var dot = 0f
            for (i in vec.indices) { dot += vec[i] * centroid[i] }
            if (dot > bestScore) {
                bestScore = dot
                when {
                    dot >= HIT_THRESHOLD -> { bestLevel = level; isGray = false }
                    dot >= GRAY_LOWER -> { bestLevel = level; isGray = true }
                }
            }
        }
        return bestLevel?.let { MatchResult(it, bestScore, isGray) }
    }

    /** 文本 → 256维向量 */
    private fun textToVector(text: String): FloatArray {
        val vec = FloatArray(dim)
        val chars = text.toCharArray()
        var count = 0
        for (n in 2..3) {
            for (i in 0..chars.size - n) {
                val gram = String(chars, i, n)
                val gv = gramVector(gram)
                for (j in 0 until dim) { vec[j] += gv[j] }
                count++
            }
        }
        if (count > 0) {
            var l2 = 0f
            for (v in vec) l2 += v * v
            l2 = sqrt(l2)
            if (l2 > 1e-9f) {
                for (i in vec.indices) vec[i] /= l2
            }
        }
        return vec
    }

    /**
     * 确定性 gram 向量 (与 Python 端一致)。
     * SHA-256 + 8 个稀疏位，固定 seed。
     */
    private fun gramVector(gram: String): FloatArray {
        val vec = FloatArray(dim)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(gram.toByteArray())
        val used = mutableSetOf<Int>()
        for (i in 0 until 8) {
            var pos = ((hash[i * 2].toInt() and 0xFF) shl 8 or (hash[i * 2 + 1].toInt() and 0xFF)) % dim
            val sign = if ((hash[i * 2 + 1].toInt() and 1) != 0) 1f else -1f
            var attempts = 0
            while (pos in used && attempts < 20) { pos = (pos + 7) % dim; attempts++ }
            used.add(pos)
            vec[pos] = sign
        }
        return vec
    }

    /** 从 assets/safety/violation_vectors.bin 加载 */
    class Loader {
        fun load(bytes: ByteArray): VectorLibrary? {
            try {
                var pos = 0
                fun rShort(): Int = ((bytes[pos++].toInt() and 0xFF) or ((bytes[pos++].toInt() and 0xFF) shl 8))
                fun rInt(): Int = ((bytes[pos++].toInt() and 0xFF) or ((bytes[pos++].toInt() and 0xFF) shl 8) or ((bytes[pos++].toInt() and 0xFF) shl 16) or ((bytes[pos++].toInt() and 0xFF) shl 24))
                fun rFloat(): Float = java.lang.Float.intBitsToFloat(rInt())

                val magic = String(bytes, pos, 4); pos += 4
                if (magic != "VLB4") return null
                val dim = rShort()
                val numWords = rInt()
                val numClasses = rShort()

                // Skip word vectors (only need centroids)
                for (i in 0 until numWords) {
                    val wlen = bytes[pos++].toInt() and 0xFF
                    pos += wlen  // word bytes
                    pos += 1      // levelId
                    pos += dim    // int8 vector
                }

                // Read centroids
                val levelOrder = arrayOf(
                    ContentFilter.ViolationLevel.LOW, ContentFilter.ViolationLevel.MEDIUM,
                    ContentFilter.ViolationLevel.HIGH, ContentFilter.ViolationLevel.SEVERE,
                    ContentFilter.ViolationLevel.CRITICAL, ContentFilter.ViolationLevel.EXTREME
                )
                val centroids = mutableMapOf<ContentFilter.ViolationLevel, FloatArray>()
                for (ci in 0 until numClasses) {
                    val centroid = FloatArray(dim)
                    for (i in 0 until dim) { centroid[i] = rFloat() }
                    centroids[levelOrder[ci]] = centroid
                }

                return VectorLibrary(dim, centroids)
            } catch (e: Exception) {
                return null
            }
        }
    }
}
