package com.lianyu.ai.common.safety

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 三层融合内容安全校验器。
 *
 * 管道：
 *   L1 AC关键词（ContentFilter.checkInput）→ 快速特征
 *   L2 向量语义（ContentFilter.checkVector）→ 相似度特征
 *   L1+L2 特征 → 贝叶斯概率评分 → 风险判定
 *
 * 贝叶斯不再做原始文本 n-gram，而是基于 L1/L2 的结构化特征 token。
 * 每条消息计算 ≤10 个特征，O(1) 贝叶斯。
 */
object ContentSafetyVerifier {

    private const val PREFS_NAME = "safety_bayesian"
    private const val KEY_USER_STATE = "user_classifier_state"
    private const val KEY_MODEL_STATE = "model_classifier_state"

    val userClassifier = BayesianClassifier()
    val modelClassifier = BayesianClassifier()

    private var prefs: SharedPreferences? = null
    @Volatile
    private var bootstrapped = false

    // ============ 初始化 ============

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadUserState()
        loadModelState()
    }

    @Synchronized
    fun bootstrap(keywords: List<Pair<String, String>>) {
        if (bootstrapped || userClassifier.totalSamples > 0) return
        if (keywords.isEmpty()) return

        val samples = BayesianBootstrapper.bootstrap(keywords)
        // 缓存特征提取结果，避免重复计算 checkFull/checkVector
        val featureCache = samples.map { sample ->
            val kwResult = com.lianyu.ai.common.ContentFilter.checkFull(sample.text)
            val vecResult = com.lianyu.ai.common.ContentFilter.checkVector(sample.text)
            val features = SafetyFeatureExtractor.extract(kwResult, vecResult, sample.text)
            features to sample
        }
        // 训练用户输入分类器
        featureCache.forEach { (features, sample) ->
            if (sample.source == SampleSource.USER_INPUT) {
                userClassifier.addSampleDirect(features, sample.isViolation)
            }
        }
        // 训练模型输出分类器（仅用安全基线样本，违规特征不应标记为安全）
        val safeFeatures = featureCache.filter { (_, sample) -> !sample.isViolation }
        safeFeatures.forEach { (features, _) ->
            modelClassifier.addSampleDirect(features, false)
        }
        bootstrapped = true
        android.util.Log.i("SafetyVerifier", "Bootstrapped ${samples.size} samples → ${userClassifier.totalSamples} feature-tokens")
    }

    // ============ 训练 ============

    /** 用 L1+L2 特征训练用户输入分类器 */
    fun trainUserInput(
        rawText: String, isViolation: Boolean, label: String = "",
        kwResult: com.lianyu.ai.common.ContentFilter.CheckResult? = null,
        vecResult: com.lianyu.ai.common.ContentFilter.CheckResult? = null
    ) {
        val kw = kwResult ?: com.lianyu.ai.common.ContentFilter.checkFull(rawText)
        val vec = vecResult ?: com.lianyu.ai.common.ContentFilter.checkVector(rawText)
        val features = SafetyFeatureExtractor.extract(kw, vec, rawText)
        // P2-11: 批量添加 token
        userClassifier.addSampleDirect(features, isViolation)
        saveUserState()
    }

    /** 用 L1+L2 特征训练模型输出分类器 */
    fun trainModelOutput(
        rawText: String, isViolation: Boolean, label: String = "",
        kwResult: com.lianyu.ai.common.ContentFilter.CheckResult? = null,
        vecResult: com.lianyu.ai.common.ContentFilter.CheckResult? = null
    ) {
        val kw = kwResult ?: com.lianyu.ai.common.ContentFilter.checkFull(rawText)
        val vec = vecResult ?: com.lianyu.ai.common.ContentFilter.checkVector(rawText)
        val features = SafetyFeatureExtractor.extract(kw, vec, rawText)
        // P2-11: 批量添加 token
        modelClassifier.addSampleDirect(features, isViolation)
        saveModelState()
    }

    fun trainBatch(samples: List<SafetySample>) {
        for (s in samples) {
            if (s.source == SampleSource.USER_INPUT) trainUserInput(s.text, s.isViolation, s.label)
            else trainModelOutput(s.text, s.isViolation, s.label)
        }
    }

    // ============ 校验 ============

    /**
     * 同步校验 — L1+L2 特征 → 贝叶斯评分。
     * 调用方应已获取 ContentFilter 结果（避免重复检查）。
     */
    fun verifyUserInput(
        rawText: String,
        kwResult: com.lianyu.ai.common.ContentFilter.CheckResult,
        vecResult: com.lianyu.ai.common.ContentFilter.CheckResult?
    ): SafetyScore {
        if (rawText.isBlank()) return SafetyScore(0.0, ScoreSource.USER_INPUT, explanation = "空文本")
        if (userClassifier.totalSamples == 0) return SafetyScore.neutral(ScoreSource.USER_INPUT)

        val features = SafetyFeatureExtractor.extract(kwResult, vecResult, rawText)
        val score = userClassifier.classifyTokens(features)
        val topTokens = userClassifier.topContributingTokens(features)

        return SafetyScore(
            score = score,
            source = ScoreSource.USER_INPUT,
            topTerms = topTokens,
            explanation = buildExplanation(score, topTokens, "用户输入")
        )
    }

    /**
     * Native C++ 预测 — 使用 8 特征 float 向量 + nativeBayesianPredict。
     * 比 token-based classifyTokens() 快 ~5x。
     */
    fun verifyUserInputNative(
        rawText: String,
        kwResult: com.lianyu.ai.common.ContentFilter.CheckResult,
        vecResult: com.lianyu.ai.common.ContentFilter.CheckResult?
    ): SafetyScore {
        if (rawText.isBlank()) return SafetyScore(0.0, ScoreSource.USER_INPUT, explanation = "空文本")
        if (userClassifier.totalSamples == 0) return SafetyScore.neutral(ScoreSource.USER_INPUT)

        val floatVec = SafetyFeatureExtractor.extractFloatVector(kwResult, vecResult, rawText)
        val score = userClassifier.classifyTokensNative(floatVec).toDouble()

        return SafetyScore(
            score = score,
            source = ScoreSource.USER_INPUT,
            topTerms = emptyList(), // native 路径暂不支持可解释性
            explanation = "Native(${"%.3f".format(score)})"
        )
    }

    suspend fun verifyUserInputAsync(
        rawText: String,
        kwResult: com.lianyu.ai.common.ContentFilter.CheckResult,
        vecResult: com.lianyu.ai.common.ContentFilter.CheckResult?
    ): SafetyScore = withContext(Dispatchers.Default) {
        verifyUserInput(rawText, kwResult, vecResult)
    }

    fun verifyModelOutput(
        rawText: String,
        kwResult: com.lianyu.ai.common.ContentFilter.CheckResult,
        vecResult: com.lianyu.ai.common.ContentFilter.CheckResult?,
        userContext: String = ""
    ): SafetyScore {
        if (rawText.isBlank()) return SafetyScore(0.0, ScoreSource.MODEL_OUTPUT, explanation = "空文本")
        if (modelClassifier.totalSamples == 0) return SafetyScore.neutral(ScoreSource.MODEL_OUTPUT)

        val features = SafetyFeatureExtractor.extract(kwResult, vecResult, rawText)
        val score = modelClassifier.classifyTokens(features)
        val topTokens = modelClassifier.topContributingTokens(features)

        return SafetyScore(
            score = score,
            source = ScoreSource.MODEL_OUTPUT,
            topTerms = topTokens,
            explanation = buildExplanation(score, topTokens, "模型输出")
        )
    }

    suspend fun verifyModelOutputAsync(
        rawText: String,
        kwResult: com.lianyu.ai.common.ContentFilter.CheckResult,
        vecResult: com.lianyu.ai.common.ContentFilter.CheckResult?,
        userContext: String = ""
    ): SafetyScore = withContext(Dispatchers.Default) {
        verifyModelOutput(rawText, kwResult, vecResult, userContext)
    }

    fun verifyRoundTrip(
        userInput: String,
        modelOutput: String,
        userKw: com.lianyu.ai.common.ContentFilter.CheckResult,
        userVec: com.lianyu.ai.common.ContentFilter.CheckResult?,
        modelKw: com.lianyu.ai.common.ContentFilter.CheckResult,
        modelVec: com.lianyu.ai.common.ContentFilter.CheckResult?
    ): RoundTripVerdict {
        val userScore = verifyUserInput(userInput, userKw, userVec)
        val modelScore = verifyModelOutput(modelOutput, modelKw, modelVec, userInput)
        return RoundTripVerdict(userScore, modelScore)
    }

    // ============ 统计 ============

    fun userStats(): String = "用户输入: ${userClassifier.totalSamples} 样本, 先验=${"%.3f".format(userClassifier.priorViolation())}"
    fun modelStats(): String = "模型输出: ${modelClassifier.totalSamples} 样本, 先验=${"%.3f".format(modelClassifier.priorViolation())}"

    // ============ 私有 ============

    private fun buildExplanation(score: Double, terms: List<Pair<String, Double>>, target: String): String {
        if (terms.isEmpty()) return "$target 无显著特征"
        val top = terms.take(3).joinToString("、") { (term, contrib) ->
            val dir = if (contrib > 0) "推高" else "拉低"
            "$term($dir${"%.2f".format(Math.abs(contrib))})"
        }
        return "$target 评分${"%.2f".format(score)}: $top"
    }

    // ---- 持久化 ----

    private fun saveUserState() {
        prefs?.edit()?.putString(KEY_USER_STATE, serializeState(userClassifier.exportState()))?.apply()
    }

    private fun saveModelState() {
        prefs?.edit()?.putString(KEY_MODEL_STATE, serializeState(modelClassifier.exportState()))?.apply()
    }

    private fun loadUserState() {
        prefs?.getString(KEY_USER_STATE, null)?.let { deserializeState(it)?.let { userClassifier.importState(it) } }
    }

    private fun loadModelState() {
        prefs?.getString(KEY_MODEL_STATE, null)?.let { deserializeState(it)?.let { modelClassifier.importState(it) } }
    }

    private fun serializeState(state: BayesianState): String = JSONObject().apply {
        put("totalSamples", state.totalSamples)
        put("vocabSize", state.vocabSize)
        put("totalTermsViolation", state.totalTermsViolation)
        put("totalTermsSafe", state.totalTermsSafe)
        put("sampleCountViolation", state.sampleCountViolation)
        put("sampleCountSafe", state.sampleCountSafe)
        put("termFreqViolation", JSONObject(state.termFreqViolation))
        put("termFreqSafe", JSONObject(state.termFreqSafe))
    }.toString()

    private fun deserializeState(json: String): BayesianState? = try {
        val obj = JSONObject(json)
        BayesianState(
            totalSamples = obj.getInt("totalSamples"), vocabSize = obj.getInt("vocabSize"),
            totalTermsViolation = obj.getInt("totalTermsViolation"), totalTermsSafe = obj.getInt("totalTermsSafe"),
            sampleCountViolation = obj.getInt("sampleCountViolation"), sampleCountSafe = obj.getInt("sampleCountSafe"),
            termFreqViolation = jsonObjectToMap(obj.getJSONObject("termFreqViolation")),
            termFreqSafe = jsonObjectToMap(obj.getJSONObject("termFreqSafe"))
        )
    } catch (e: Exception) { null }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        json.keys().forEach { map[it] = json.getInt(it) }
        return map
    }
}
