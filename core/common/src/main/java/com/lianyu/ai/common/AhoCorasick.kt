package com.lianyu.ai.common

/**
 * Aho-Corasick 确定性自动机 — 预计算所有 goto/fail 转换，搜索 O(n) 无内循环。
 *
 * 构建: O(总关键词长度 × 字母表) ≈ O(L × 128)
 * 搜索: O(n) 每条字符一次数组查表，无 while 循环
 * 内存: stateCount × 128 × 4B = ~50KB (1000 状态)
 */
class AhoCorasick private constructor(
    /** 确定性转移表: next[state][char] = 下一状态 (永不 -1) */
    private val next: Array<IntArray>,
    /** 合并输出: output[state] = 最高违规等级 (已合并 fail 链) */
    private val output: Array<ContentFilter.ViolationLevel?>,
    /** 关键词长度表: len[state] = 该状态对应关键词的最短长度 */
    private val keywordLen: IntArray,
    /** 关键词文本: 用于提取匹配文本 */
    private val keywordText: Array<String?>,

    val stateCount: Int
) {
    companion object {
        fun build(
            keywords: Map<ContentFilter.ViolationLevel, List<String>>
        ): AhoCorasick {
            var totalChars = 0
            for ((_, words) in keywords) {
                for (w in words) totalChars += w.length
            }
            val maxStates = (totalChars * 2) + 1
            val goto = Array(maxStates) { IntArray(128) { -1 } }
            val output = arrayOfNulls<ContentFilter.ViolationLevel>(maxStates)
            val wordLen = IntArray(maxStates) { 0 }
            val wordText = arrayOfNulls<String>(maxStates)

            // 1. 构建 trie
            var stateCount = 1
            for ((level, words) in keywords) {
                for (word in words) {
                    var state = 0
                    for (ch in word.lowercase()) {
                        val ci = ch.code
                        if (ci >= 128) continue
                        if (goto[state][ci] == -1) {
                            goto[state][ci] = stateCount++
                        }
                        state = goto[state][ci]
                    }
                    if (output[state] == null || level.ordinal > output[state]!!.ordinal) {
                        output[state] = level
                        wordLen[state] = word.length
                        wordText[state] = word
                    }
                }
            }

            // 2. BFS 构建 fail + 合并输出
            val fail = IntArray(maxStates) { 0 }
            val queue = ArrayDeque<Int>()

            for (c in 0 until 128) {
                if (goto[0][c] != -1) {
                    fail[goto[0][c]] = 0
                    queue.addLast(goto[0][c])
                } else {
                    goto[0][c] = 0
                }
            }

            while (queue.isNotEmpty()) {
                val r = queue.removeFirst()
                for (c in 0 until 128) {
                    if (goto[r][c] != -1) {
                        val s = goto[r][c]
                        queue.addLast(s)
                        fail[s] = goto[fail[r]][c]
                        // 合并 fail 链输出
                        if (output[fail[s]] != null) {
                            val inherited = output[fail[s]]!!
                            if (output[s] == null || inherited.ordinal > output[s]!!.ordinal) {
                                output[s] = inherited
                                // 取较短的关键词文本（更精确的回溯）
                                if (wordText[s] == null || wordLen[fail[s]] < wordLen[s]) {
                                    wordLen[s] = wordLen[fail[s]]
                                    wordText[s] = wordText[fail[s]]
                                }
                            }
                        }
                    }
                }
            }

            // 3. 确定化: 预计算所有 goto 转换，消除搜索中的 while 循环
            val next = Array(stateCount) { IntArray(128) }
            for (state in 0 until stateCount) {
                for (c in 0 until 128) {
                    next[state][c] = goto[state][c]
                }
            }

            return AhoCorasick(next, output, wordLen, wordText, stateCount)
        }
    }

    data class Match(
        val level: ContentFilter.ViolationLevel,
        val keyword: String,
        val endPos: Int
    )

    /**
     * 搜索 — O(n)，每条字符一次查表，无 while 循环。
     */
    fun search(text: String): List<Match> {
        val matches = mutableListOf<Match>()
        val chars = text.toCharArray()
        var state = 0

        for (i in chars.indices) {
            val ci = chars[i].lowercaseChar().code
            if (ci >= 128 || ci < 0) {
                state = 0
                continue
            }
            state = next[state][ci]

            // 直接查合并后的 output，无需 fail 链遍历
            output[state]?.let { level ->
                val kw = keywordText[state]
                val len = keywordLen[state]
                if (kw != null && len > 0) {
                    val start = i - len + 1
                    if (start >= 0) {
                        matches.add(Match(level, kw, i))
                    }
                }
            }
        }

        return matches
    }
}
