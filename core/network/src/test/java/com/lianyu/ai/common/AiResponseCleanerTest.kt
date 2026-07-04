package com.lianyu.ai.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AiResponseCleanerTest {

    @Test
    fun `stripResponsePrefix removes lowercase response prefix`() {
        assertEquals("怎么啦，我脸上有东西吗。", AiResponseCleaner.stripResponsePrefix("response 怎么啦，我脸上有东西吗。"))
    }

    @Test
    fun `stripResponsePrefix removes capitalized Response prefix`() {
        assertEquals("你好呀~", AiResponseCleaner.stripResponsePrefix("Response: 你好呀~"))
    }

    @Test
    fun `stripResponsePrefix removes uppercase RESPONSE prefix with chinese colon`() {
        assertEquals("测试一下", AiResponseCleaner.stripResponsePrefix("RESPONSE：测试一下"))
    }

    @Test
    fun `stripResponsePrefix handles leading whitespace`() {
        assertEquals("在干嘛呢", AiResponseCleaner.stripResponsePrefix("  response   在干嘛呢"))
    }

    @Test
    fun `stripResponsePrefix keeps normal text unchanged`() {
        assertEquals("今天天气真好呀", AiResponseCleaner.stripResponsePrefix("今天天气真好呀"))
    }

    @Test
    fun `stripResponsePrefix preserves response when it is the only content`() {
        // 避免把合法短回复误删成空字符串
        assertEquals("response", AiResponseCleaner.stripResponsePrefix("response"))
    }

    @Test
    fun `stripResponsePrefix removes prefix and keeps trailing punctuation`() {
        assertEquals("（忽然想到什么似的，眼睛一亮）。", AiResponseCleaner.stripResponsePrefix("response （忽然想到什么似的，眼睛一亮）。"))
    }

    @Test
    fun `stripResponsePrefix handles repeated historical contamination`() {
        // 模拟历史消息中已存在 response 前缀，再次进入清洗后应被移除
        assertEquals("要不...", AiResponseCleaner.stripResponsePrefix("response 要不..."))
    }

    @Test
    fun `stripResponsePrefix removes response prefix directly followed by Chinese`() {
        // response 后直接跟中文（无空格）也应被识别为元前缀
        assertEquals("怎么啦", AiResponseCleaner.stripResponsePrefix("response怎么啦"))
    }

    @Test
    fun `stripResponsePrefix preserves response as substring of English word`() {
        // 负向前瞻应保护正常英文单词不被误删
        assertEquals("responses are valid", AiResponseCleaner.stripResponsePrefix("responses are valid"))
        assertEquals("responseable word", AiResponseCleaner.stripResponsePrefix("responseable word"))
    }

    @Test
    fun `stripResponsePrefix handles tab and multiple spaces`() {
        assertEquals("你好", AiResponseCleaner.stripResponsePrefix("\tresponse\t\t  你好"))
    }

    @Test
    fun `stripResponsePrefix handles multiline prefix`() {
        assertEquals("正文内容", AiResponseCleaner.stripResponsePrefix("response\n正文内容"))
    }

    @Test
    fun `stripResponsePrefix preserves response in middle of sentence`() {
        // response 不在开头时不应被删除
        assertEquals("他说 response 不好", AiResponseCleaner.stripResponsePrefix("他说 response 不好"))
    }

    @Test
    fun `stripResponsePrefix handles empty and blank input`() {
        assertEquals("", AiResponseCleaner.stripResponsePrefix(""))
        assertEquals("   ", AiResponseCleaner.stripResponsePrefix("   "))
    }

    @Test
    fun `stripResponsePrefix removes mixed case prefix`() {
        assertEquals("测试", AiResponseCleaner.stripResponsePrefix("ReSpOnSe：测试"))
    }
}
