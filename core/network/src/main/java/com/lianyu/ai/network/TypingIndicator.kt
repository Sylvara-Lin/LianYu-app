package com.lianyu.ai.network

import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * TypingIndicator — 对方正在输入中状态管理
 *
 * 管理 AI 回复时的"正在输入"状态，支持：
 * - 协议级别的 typing 参数（部分 API 支持）
 * - UI 状态同步
 * - 超时自动清除
 */
object TypingIndicator {

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private var typingStartTime = AtomicLong(0L)
    private const val TYPING_TIMEOUT_MS = 60000L // 60秒超时

    /**
     * 开始输入状态
     */
    fun startTyping(source: String = "unknown") {
        _isTyping.value = true
        typingStartTime.set(System.currentTimeMillis())
        SecureLog.typing("START", "Typing started from $source")
    }

    /**
     * 结束输入状态
     */
    fun stopTyping(source: String = "unknown") {
        if (_isTyping.value) {
            val elapsed = System.currentTimeMillis() - typingStartTime.get()
            _isTyping.value = false
            SecureLog.typing("STOP", "Typing stopped from $source, duration=${elapsed}ms")
        }
    }

    /**
     * 检查是否超时，超时时自动停止
     */
    fun checkTimeout(): Boolean {
        if (!_isTyping.value) return false
        val elapsed = System.currentTimeMillis() - typingStartTime.get()
        if (elapsed > TYPING_TIMEOUT_MS) {
            SecureLog.typing("TIMEOUT", "Typing timed out after ${elapsed}ms")
            _isTyping.value = false
            return true
        }
        return false
    }

    /**
     * 获取当前 typing 持续时间
     */
    fun getTypingDuration(): Long {
        return if (_isTyping.value) System.currentTimeMillis() - typingStartTime.get() else 0
    }

    /**
     * 生成协议级别的 typing 参数
     * 部分 API 支持在请求中包含 typing 指示
     */
    fun createTypingParameter(enabled: Boolean = true): Map<String, Any> {
        return mapOf(
            "stream" to enabled,
            "stream_options" to mapOf("include_usage" to true)
        )
    }
}

/**
 * ChatTypingState — 每个对话的独立 typing 状态
 */
class ChatTypingState {
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _typingText = MutableStateFlow("")
    val typingText: StateFlow<String> = _typingText.asStateFlow()

    private var typingJob: kotlinx.coroutines.Job? = null
    private val textBuffer = StringBuffer()
    private var lastFlushTime = 0L
    private val flushIntervalMs = 80L

    fun startTyping() {
        _isTyping.value = true
        _typingText.value = ""
        textBuffer.setLength(0)
        lastFlushTime = System.currentTimeMillis()
    }

    fun appendText(text: String) {
        textBuffer.append(text)
        val now = System.currentTimeMillis()
        if (now - lastFlushTime >= flushIntervalMs || textBuffer.length >= 15) {
            _typingText.value += textBuffer.toString()
            textBuffer.setLength(0)
            lastFlushTime = now
        }
    }

    fun stopTyping() {
        // 先清空文本再置标志位，避免 UI 观察到 typingText 非空但 isTyping=false 的中间态
        textBuffer.setLength(0)
        _typingText.value = ""
        _isTyping.value = false
        typingJob?.cancel()
        typingJob = null
    }

    fun setTypingJob(job: kotlinx.coroutines.Job) {
        typingJob = job
    }
}
