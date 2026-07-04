package com.lianyu.ai.common.safety

/**
 * 多项式朴素贝叶斯分类器 + Laplace 平滑。
 *
 * 每个通道（用户输入 / 模型输出）各自持有一个独立实例，
 * 分别在自己的样本集上训练，保持概率分布独立。
 *
 * 算法：
 *   P(term | class) = (count(term, class) + alpha) / (total_terms_class + alpha * vocab_size)
 *   P(class | text)  ∝ P(class) × Π P(term_i | class)
 *
 * 平滑参数 alpha = 1.0（Laplace 平滑），防止零概率。
 */
class BayesianClassifier(
    private val alpha: Double = 1.0
) {
    // 每个类的词频: class -> (term -> count)
    private val termFreqByClass = mutableMapOf<Boolean, MutableMap<String, Int>>()
    // 每个类的总词数
    private val totalTermsByClass = mutableMapOf<Boolean, Int>()
    // 每个类的样本数
    private val sampleCountByClass = mutableMapOf<Boolean, Int>()

    /** 样本总数 */
    @Volatile
    var totalSamples: Int = 0
        private set

    /** 全词表大小（用于 Laplace 平滑归一化） */
    private var vocabSize: Int = 0

    init {
        termFreqByClass[true] = mutableMapOf()
        termFreqByClass[false] = mutableMapOf()
        totalTermsByClass[true] = 0
        totalTermsByClass[false] = 0
        sampleCountByClass[true] = 0
        sampleCountByClass[false] = 0
    }

    // ============ 训练 ============

    /** 批量训练 */
    @Synchronized
    fun train(samples: List<SafetySample>) {
        for (sample in samples) {
            addSample(sample)
        }
        rebuildVocab()
    }

    /** 增量添加单个样本 */
    @Synchronized
    fun addSample(sample: SafetySample) {
        val grams = NGramExtractor.extract(sample.text)
        addTokensInternal(grams, sample.isViolation)
    }

    /**
     * 直接添加 token 列表作为一个完整样本（跳过 n-gram 提取，用于结构化特征训练）。
     *
     * P2-11 修复：按样本计数，不按 token 计数。
     * 原实现每添加一个 token 就递增 sampleCountByClass 和 totalSamples，
     * 导致先验概率严重偏向违规。现在只递增一次样本计数。
     */
    @Synchronized
    fun addSampleDirect(tokens: List<String>, isViolation: Boolean) {
        val freq = termFreqByClass[isViolation]!!
        for (t in tokens) {
            freq[t] = (freq[t] ?: 0) + 1
        }
        totalTermsByClass[isViolation] = totalTermsByClass[isViolation]!! + tokens.size
        // 按样本计数，每个样本只递增一次
        sampleCountByClass[isViolation] = sampleCountByClass[isViolation]!! + 1
        totalSamples++
        vocabSize = -1
    }

    /**
     * 添加单个 token（兼容旧调用）。
     * 注意：单个 token 仍视为一个样本，但建议使用 [addSampleDirect] 批量添加。
     */
    @Synchronized
    fun addSampleDirect(token: String, isViolation: Boolean) {
        addSampleDirect(listOf(token), isViolation)
    }

    private fun addTokensInternal(tokens: List<String>, isViolation: Boolean) {
        val freq = termFreqByClass[isViolation]!!
        for (t in tokens) {
            freq[t] = (freq[t] ?: 0) + 1
        }
        totalTermsByClass[isViolation] = totalTermsByClass[isViolation]!! + tokens.size
        sampleCountByClass[isViolation] = sampleCountByClass[isViolation]!! + 1
        totalSamples++
        vocabSize = -1
    }

    private fun rebuildVocab() {
        val allTerms = mutableSetOf<String>()
        termFreqByClass.values.forEach { allTerms.addAll(it.keys) }
        vocabSize = allTerms.size
    }

    // ============ 分类 ============

    /**
     * 对文本评分，返回 P(违规 | text)。
     *
     * 使用对数空间计算避免浮点下溢。
     */
    fun classify(text: String): Double {
        return classifyTokens(NGramExtractor.extract(text))
    }

    /**
     * 基于预提取 token 分类 — 用于结构化特征 token（SafetyFeatureExtractor 产物）。
     * 跳过 n-gram 提取步骤，直接做概率计算。
     */
    @Synchronized
    fun classifyTokens(tokens: List<String>): Double {
        if (totalSamples == 0) return 0.5
        if (vocabSize <= 0) rebuildVocab()
        if (tokens.isEmpty()) return priorViolation()

        val logProbViolation = Math.log(priorViolation()) +
            tokens.sumOf { Math.log(termProbability(it, true)) }
        val logProbSafe = Math.log(priorSafe()) +
            tokens.sumOf { Math.log(termProbability(it, false)) }

        val maxLog = Math.max(logProbViolation, logProbSafe)
        val probViolation = Math.exp(logProbViolation - maxLog)
        val probSafe = Math.exp(logProbSafe - maxLog)

        return probViolation / (probViolation + probSafe)
    }

    /** 基于预提取 token 的贡献度查询 */
    
    /** 导出先验概率 [P(safe), P(violation)] */
    @Synchronized
    fun exportPriors(): FloatArray = floatArrayOf(
        priorSafe().toFloat(), priorViolation().toFloat()
    )

    /** 导出所有已知 token 的似然 [P(t|safe), P(t|violation)]，展平为一维数组 */
    @Synchronized
    fun exportLikelihoods(): Pair<FloatArray, List<String>> {
        val tokens = mutableListOf<String>()
        val values = mutableListOf<Float>()
        val allTokens = mutableSetOf<String>()
        termFreqByClass.values.forEach { allTokens.addAll(it.keys) }
        for (t in allTokens) {
            tokens.add(t)
            values.add(termProbability(t, false).toFloat())
            values.add(termProbability(t, true).toFloat())
        }
        return values.toFloatArray() to tokens
    }

    /**
     * Native C++ 预测 — 使用特征维度的似然矩阵。
     *
     * P1-6 修复：原实现将 token-based 的 exportLikelihoods() 传入 native，
     * 但 native 接收 8 维特征向量，维度与词表大小的似然数组不匹配。
     * 现改用 computeFeatureLikelihoods() 生成与特征维度对应的似然矩阵。
     */
    @Synchronized
    fun classifyTokensNative(features: FloatArray): Float {
        return com.lianyu.ai.common.NativeSafetyFilter.bayesianPredict(
            features, exportPriors(), computeFeatureLikelihoods(features.size)
        )
    }

    /**
     * 计算特征维度的似然矩阵（均匀先验 + 小拉普拉斯平滑）。
     * TODO: 当有足够的特征级训练数据后替换为真实似然。
     */
    private fun computeFeatureLikelihoods(numFeatures: Int): FloatArray {
        val likelihoods = FloatArray(numFeatures * 2)
        for (i in 0 until numFeatures) {
            likelihoods[i * 2] = 0.5f + 1e-4f      // P(feature_i | safe)
            likelihoods[i * 2 + 1] = 0.5f + 1e-4f  // P(feature_i | dangerous)
        }
        return likelihoods
    }

    @Synchronized
    fun topContributingTokens(tokens: List<String>, topN: Int = 5): List<Pair<String, Double>> {
        if (tokens.isEmpty()) return emptyList()
        return tokens
            .map { t ->
                val pV = termProbability(t, true)
                val pS = termProbability(t, false)
                val contrib = if (pV > pS) pV / (pV + pS) - 0.5 else -(pS / (pV + pS) - 0.5)
                t to contrib
            }
            .filter { Math.abs(it.second) > 0.01 }
            .sortedByDescending { Math.abs(it.second) }
            .take(topN)
    }

    /**
     * 返回对评分贡献最大的 n-gram 列表（用于可解释性）
     */
    @Synchronized
    fun topContributingTerms(text: String, topN: Int = 5): List<Pair<String, Double>> {
        val grams = NGramExtractor.extract(text)
        if (grams.isEmpty()) return emptyList()

        return grams
            .map { g ->
                val pViolation = termProbability(g, true)
                val pSafe = termProbability(g, false)
                val contribution = if (pViolation > pSafe) {
                    pViolation / (pViolation + pSafe) - 0.5
                } else {
                    -(pSafe / (pViolation + pSafe) - 0.5)
                }
                g to contribution
            }
            .filter { Math.abs(it.second) > 0.01 }
            .sortedByDescending { Math.abs(it.second) }
            .take(topN)
    }

    // ============ 概率查询 ============

    /** P(违规) 先验 — 钳制上限0.3，防止被污染后先验膨胀导致误杀 */
    @Synchronized
    fun priorViolation(): Double {
        val total = sampleCountByClass[true]!! + sampleCountByClass[false]!!
        if (total == 0) return 0.5
        val raw = sampleCountByClass[true]!!.toDouble() / total
        return raw.coerceAtMost(0.3)  // 先验上限0.3：正常用户违规比例不应超过30%
    }

    /** P(安全) 先验 */
    @Synchronized
    fun priorSafe(): Double = 1.0 - priorViolation()

    /**
     * P(term | class) 条件概率（Laplace 平滑）
     */
    @Synchronized
    fun termProbability(term: String, isViolation: Boolean): Double {
        if (vocabSize <= 0) rebuildVocab()
        val count = termFreqByClass[isViolation]?.get(term) ?: 0
        val total = totalTermsByClass[isViolation] ?: 0
        val vSize = if (vocabSize > 0) vocabSize else 1
        return (count + alpha) / (total + alpha * vSize)
    }

    /** 获取某类中的词频（调试用） */
    fun termCount(term: String, isViolation: Boolean): Int =
        termFreqByClass[isViolation]?.get(term) ?: 0

    // ============ 序列化 ============

    /** 导出训练状态（用于持久化） */
    @Synchronized
    fun exportState(): BayesianState {
        if (vocabSize <= 0) rebuildVocab()
        return BayesianState(
            termFreqViolation = termFreqByClass[true]!!.toMap(),
            termFreqSafe = termFreqByClass[false]!!.toMap(),
            totalTermsViolation = totalTermsByClass[true]!!,
            totalTermsSafe = totalTermsByClass[false]!!,
            sampleCountViolation = sampleCountByClass[true]!!,
            sampleCountSafe = sampleCountByClass[false]!!,
            vocabSize = vocabSize,
            totalSamples = totalSamples
        )
    }

    /** 导入训练状态 */
    @Synchronized
    fun importState(state: BayesianState) {
        termFreqByClass[true] = state.termFreqViolation.toMutableMap()
        termFreqByClass[false] = state.termFreqSafe.toMutableMap()
        totalTermsByClass[true] = state.totalTermsViolation
        totalTermsByClass[false] = state.totalTermsSafe
        sampleCountByClass[true] = state.sampleCountViolation
        sampleCountByClass[false] = state.sampleCountSafe
        vocabSize = state.vocabSize
        totalSamples = state.totalSamples
    }
}

/**
 * 分类器状态的序列化快照。
 */
data class BayesianState(
    val termFreqViolation: Map<String, Int>,
    val termFreqSafe: Map<String, Int>,
    val totalTermsViolation: Int,
    val totalTermsSafe: Int,
    val sampleCountViolation: Int,
    val sampleCountSafe: Int,
    val vocabSize: Int,
    val totalSamples: Int
)
