package com.lianyu.ai.common

import android.util.Log
import com.lianyu.ai.common.safety.SafetySample

/**
 * Native 内容安全检查 — AC 自动机 + 贝叶斯分类器下沉到 C++。
 *
 * 当 SO 不可用或 JNI 方法缺失时（如 WorkManager 子线程 ClassLoader 隔离），
 * 自动降级为纯 Kotlin 实现，保证功能不中断。
 */
object NativeSafetyFilter {

    private const val TAG = "NativeSafetyFilter"

    /** Native JNI 方法是否可用（SO 加载成功且符号解析正常） */
    val isNativeAvailable: Boolean

    init {
        var available = false
        try {
            System.loadLibrary("lianyu_security")
            // 验证符号可解析：调用一个无副作用的轻量检测
            available = try {
                probeNativeSymbol()
                true
            } catch (_: UnsatisfiedLinkError) {
                Log.w(TAG, "Native symbols not resolved, falling back to Kotlin")
                false
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "liblianyu_security.so not loaded: ${e.message}")
            // SO 可能由 Shell 机制加载，尝试探测
            available = try { probeNativeSymbol(); true }
            catch (_: UnsatisfiedLinkError) { false }
        }
        isNativeAvailable = available
        Log.i(TAG, "Native mode=${available}")
    }

    /** AC 匹配结果 */
    data class AcMatch(
        val level: Int,        // ViolationLevel ordinal
        val keyword: String,
        val endPos: Int
    )

    // ===== AC 自动机 =====

    @JvmStatic external fun nativeAcBuild(keywords: Array<String>, levels: IntArray): Long

    @JvmStatic external fun nativeAcSearch(text: String, acPtr: Long): Array<AcMatch>?

    @JvmStatic external fun nativeAcFree(acPtr: Long)

    // ===== 贝叶斯预测 =====

    @JvmStatic external fun nativeBayesianPredict(
        features: FloatArray,
        priors: FloatArray,
        likelihoods: FloatArray
    ): Float

    // ===== 探测方法：验证 JNI 符号可解析 =====

    private fun probeNativeSymbol() {
        // 调用 nativeBayesianPredict 用空数组做零成本探测
        // 如果符号不存在会抛 UnsatisfiedLinkError，由 init 块捕获
        nativeBayesianPredict(floatArrayOf(0f), floatArrayOf(0.5f, 0.5f), floatArrayOf(0f, 0f))
    }

    // ===== 便捷封装（自动选择 Native / Kotlin 降级）=====

    private var acPtr: Long = 0
    private val acLock = Any()

    // ── Kotlin 降级实现 ──

    /** Kotlin AC 自动机（native 不可用时使用） */
    private val kotlinAcNode = mutableMapOf<Int, MutableMap<Char, Int>>()
    private val kotlinAcFail = mutableMapOf<Int, Int>()
    private val kotlinAcOutput = mutableMapOf<Int, Pair<Int, String>>() // level -> keyword
    private var kotlinAcBuilt = false

    fun initAc(keywords: Map<Int, List<String>>) {
        synchronized(acLock) {
            if (isNativeAvailable) {
                if (acPtr != 0L) nativeAcFree(acPtr)
                val flat = mutableListOf<String>()
                val levels = mutableListOf<Int>()
                for ((level, kws) in keywords) {
                    for (kw in kws) {
                        flat.add(kw)
                        levels.add(level)
                    }
                }
                acPtr = nativeAcBuild(flat.toTypedArray(), levels.toIntArray())
            } else {
                buildKotlinAc(keywords)
            }
        }
    }

    fun searchAc(text: String): List<AcMatch> {
        synchronized(acLock) {
            return if (isNativeAvailable) {
                if (acPtr == 0L) return emptyList()
                nativeAcSearch(text, acPtr)?.toList() ?: emptyList()
            } else {
                searchKotlinAc(text)
            }
        }
    }

    /** Release native AC automaton memory. */
    fun destroy() {
        synchronized(acLock) {
            if (isNativeAvailable && acPtr != 0L) {
                nativeAcFree(acPtr)
                acPtr = 0
            }
            kotlinAcNode.clear()
            kotlinAcFail.clear()
            kotlinAcOutput.clear()
            kotlinAcBuilt = false
        }
    }

    fun bayesianPredict(
        features: FloatArray,
        priors: FloatArray,
        likelihoods: FloatArray
    ): Float {
        return if (isNativeAvailable) {
            nativeBayesianPredict(features, priors, likelihoods)
        } else {
            bayesianPredictKotlin(features, priors, likelihoods)
        }
    }

    // ── Kotlin AC 自动机实现 ──

    private fun buildKotlinAc(keywords: Map<Int, List<String>>) {
        kotlinAcNode.clear()
        kotlinAcFail.clear()
        kotlinAcOutput.clear()

        val nodes = listOf(mutableMapOf<Char, Int>()) // node 0 = root
        // 使用 flat list 模拟动态增长
        val next = mutableListOf(mutableMapOf<Char, Int>())
        val fail = mutableListOf(0)
        val output = mutableListOf<Pair<Int, String>?>(null)

        for ((level, kws) in keywords) {
            for (kw in kws) {
                var state = 0
                for (ch in kw.lowercase()) {
                    if (!next[state].containsKey(ch)) {
                        next[state][ch] = next.size
                        next.add(mutableMapOf())
                        fail.add(0)
                        output.add(null)
                    }
                    state = next[state][ch]!!
                }
                output[state] = level to kw
            }
        }

        // BFS 构建 fail 指针
        val queue = ArrayDeque<Int>()
        for ((ch, s) in next[0]) {
            fail[s] = 0
            queue.addLast(s)
        }
        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            for ((ch, s) in next[r]) {
                queue.addLast(s)
                var f = fail[r]
                while (f != 0 && !next[f].containsKey(ch)) f = fail[f]
                fail[s] = next[f].getOrDefault(ch, 0)
                if (output[s] == null && output[fail[s]] != null) {
                    output[s] = output[fail[s]]
                }
            }
        }

        kotlinAcNode.clear()
        next.forEachIndexed { i, map -> kotlinAcNode[i] = map }
        kotlinAcFail.clear()
        kotlinAcFail.putAll(fail.withIndex().associate { it.index to it.value })
        kotlinAcOutput.clear()
        output.forEachIndexed { i, v -> if (v != null) kotlinAcOutput[i] = v }
        kotlinAcBuilt = true
    }

    private fun searchKotlinAc(text: String): List<AcMatch> {
        if (!kotlinAcBuilt) return emptyList()
        val results = mutableListOf<AcMatch>()
        var state = 0
        for ((i, ch) in text.withIndex()) {
            val lc = ch.lowercaseChar()
            // Kotlin 按 Unicode code point 遍历，可匹配中文关键词
            // （C++ 按 char 遍历跳过非 ASCII，但 Kotlin 降级应支持中文以弥补 C++ 的局限）
            while (state != 0 && !kotlinAcNode.getOrDefault(state, emptyMap()).containsKey(lc)) {
                state = kotlinAcFail.getOrDefault(state, 0)
            }
            state = kotlinAcNode.getOrDefault(state, emptyMap()).getOrDefault(lc, 0)
            // 沿 fail 链回溯查找所有匹配（与 C++ AC 自动机行为一致）
            var temp = state
            while (temp != 0) {
                kotlinAcOutput[temp]?.let { (level, keyword) ->
                    results.add(AcMatch(level, keyword, i))
                }
                temp = kotlinAcFail.getOrDefault(temp, 0)
            }
        }
        return results
    }

    // ── Kotlin 贝叶斯预测实现 ──

    private fun bayesianPredictKotlin(
        features: FloatArray,
        priors: FloatArray,
        likelihoods: FloatArray
    ): Float {
        val n = features.size
        var scoreSafe = Math.log((priors.getOrElse(0) { 0.5f } + 1e-10f).toDouble())
        var scoreDanger = Math.log((priors.getOrElse(1) { 0.5f } + 1e-10f).toDouble())

        for (i in 0 until n) {
            val safeLike = Math.log((likelihoods.getOrElse(i * 2) { 0.5f } + 1e-10f).toDouble())
            val dangerLike = Math.log((likelihoods.getOrElse(i * 2 + 1) { 0.5f } + 1e-10f).toDouble())
            scoreSafe += safeLike * features[i]
            scoreDanger += dangerLike * features[i]
        }

        val maxScore = maxOf(scoreSafe, scoreDanger)
        val expSafe = Math.exp(scoreSafe - maxScore)
        val expDanger = Math.exp(scoreDanger - maxScore)
        return (expDanger / (expSafe + expDanger)).toFloat()
    }
}
