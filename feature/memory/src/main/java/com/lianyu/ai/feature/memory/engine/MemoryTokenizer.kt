package com.lianyu.ai.feature.memory.engine

/**
 * 中文分词器
 * 基于标点符号 + 停用词的简易分词，不引入外部依赖
 * 解决现有 MemoryRepository 使用空格分词对中文无效的问题
 */
object MemoryTokenizer {
    private val delimiters = Regex(
        "[，。！？、；：\"'（）【】《》「」『』 \t\n\r,.!?;:\"'()<>\\[\\]@#￥%…&*+=|/\\\\-]"
    )

    private val stopWords = setOf(
        "的", "了", "是", "我", "你", "他", "她", "它", "们", "这", "那", "有", "不", "在",
        "也", "都", "就", "要", "会", "能", "和", "与", "或", "但", "而", "如", "因", "为",
        "所", "以", "于", "把", "被", "让", "使", "给", "对", "向", "从", "到", "由", "用",
        "按", "照", "这个", "那个", "什么", "怎么", "为什么", "哪里", "哪个", "哪些", "一些",
        "一点", "一下", "一直", "一定", "一样", "这种", "那种", "这样", "那样", "这里", "那里",
        "他们", "她们", "它们", "我们", "你们", "自己", "别人", "大家", "现在", "以前", "以后",
        "已经", "正在", "将要", "可以", "应该", "需要", "必须", "可能", "也许", "大概", "或许",
        "确实", "真的", "其实", "只是", "只有", "只要", "只能", "只好", "不仅", "而且", "并且",
        "或者", "还是", "虽然", "但是", "然而", "不过", "如果", "即使", "尽管", "无论", "除非",
        "一旦", "一边", "由于", "所以", "因此", "于是", "然后", "接着", "最后", "首先", "其次",
        "另外", "此外", "不但", "不光", "只不过", "而不是", "而非", "以免", "以便", "从而",
        "进而", "况且", "何况", "甚至", "纵然", "哪怕", "即便", "就算", "假如", "假使", "倘若",
        "要是", "若是", "万一", "一时", "一向", "一阵", "一次", "一切", "所有", "整个", "全部",
        "完全", "充分", "足够", "十分", "非常", "特别", "尤其", "格外", "分外", "异常", "相当",
        "觉得", "认为", "感觉", "知道", "明白", "理解", "的话", "的话", "的话"
    )

    /**
     * 分词：按标点分割，过滤停用词和短词
     * @param text 待分词文本
     * @return 分词结果（去重）
     */
    fun tokenize(text: String): List<String> {
        return text.split(delimiters)
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
    }

    /**
     * 计算 Jaccard 相似度
     * 用于记忆去重，解决现有空格分词对中文无效的问题
     * @param a 文本A
     * @param b 文本B
     * @return 相似度 [0.0, 1.0]
     */
    fun similarity(a: String, b: String): Float {
        val tokensA = tokenize(a).toSet()
        val tokensB = tokenize(b).toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return intersection.toFloat() / union.toFloat()
    }

    /**
     * 提取关键词（用于记忆标签）
     * 取出现频率最高的前N个词
     */
    fun extractKeywords(text: String, topN: Int = 3): List<String> {
        val tokens = tokenize(text)
        return tokens.groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }
}
