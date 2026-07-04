package com.lianyu.ai.common.safety

/**
 * 从关键词纯数据生成贝叶斯引导样本。
 *
 * 不依赖 database 模块（core:common 不能依赖 core:database）。
 * 调用方从 KeywordDao 读取数据后传入。
 */
object BayesianBootstrapper {

    /** 正则元字符：包含这些字符的样本是模式串，直接当字面样本会污染分类器 */
    private val REGEX_META = Regex("""[\\\\^$.*+?()|[\\]{}]""")

    /**
     * @param keywords (keyword, level) 对，如 ("违禁药", "HIGH")
     */
    fun bootstrap(keywords: List<Pair<String, String>>): List<SafetySample> {
        val samples = mutableListOf<SafetySample>()

        for ((word, level) in keywords) {
            val w = word.trim()
            if (w.isBlank()) continue
            // 跳过正则模式串本身，避免把 "child\\s*porn" 这类字符串当成正样本
            if (REGEX_META.containsMatchIn(w)) continue

            // 基础样本：原词
            samples.add(SafetySample(w, true, SampleSource.USER_INPUT, level))

            // 自然句变体
            if (w.length >= 2) {
                samples.add(SafetySample("我想要$w", true, SampleSource.USER_INPUT, level))
            }
            if (w.length >= 3) {
                samples.add(SafetySample("请问在哪里可以买到$w", true, SampleSource.USER_INPUT, level))
            }
        }

        for (text in SAFE_BASELINE) {
            samples.add(SafetySample(text, false, SampleSource.USER_INPUT, "safe"))
        }

        return samples
    }

    private val SAFE_BASELINE = listOf(
        "你好呀", "今天天气真好", "吃饭了吗", "在忙什么呢",
        "晚安好梦", "我想你了", "今天学到了很多", "谢谢你陪我聊天",
        "最近过得怎么样", "有什么好看的电影推荐吗",
        "分享一下今天的趣事", "我刚刚看到一只很可爱的小猫",
        "周末打算去哪里玩", "这本书很有意思", "音乐让人心情变好"
    )
}
