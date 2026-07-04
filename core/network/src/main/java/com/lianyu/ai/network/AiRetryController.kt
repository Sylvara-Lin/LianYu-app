package com.lianyu.ai.network

import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 熔断器 — 滞环三态控制 (Closed → Open → HalfOpen → Closed)。
 *
 * 控制论映射:
 *   非线性滞环: 上升阈值 ≠ 下降阈值 → 防止边界抖动
 *   饱和保护: Open 状态直接拒绝所有请求 (不排队)
 */
class AiRetryController(
    private val failureThreshold: Int = 3,       // Closed → Open 的上升阈值
    private val successThreshold: Int = 2,        // HalfOpen → Closed 的下降阈值
    private val cooldownMs: Long = 5_000,         // Open → HalfOpen 的冷却时间
    private val maxCooldownMs: Long = 60_000      // 指数退避上限
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val state = AtomicReference(State.CLOSED)
    private val consecutiveFailures = AtomicInteger(0)
    private val consecutiveSuccesses = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val currentCooldown = AtomicLong(0)

    val isOpen: Boolean get() = state.get() == State.OPEN

    /** 记录一次成功 — 仅在 HalfOpen 状态有意义 */
    fun recordSuccess() {
        if (state.get() == State.HALF_OPEN) {
            val successes = consecutiveSuccesses.incrementAndGet()
            if (successes >= successThreshold) {
                reset()
                SecureLog.i("AiRetryController", "Circuit breaker -> CLOSED after $successes successes")
            }
        }
    }

    /** 记录一次失败 — 驱动状态转移 */
    fun recordFailure() {
        val now = System.currentTimeMillis()
        lastFailureTime.set(now)
        val failures = consecutiveFailures.incrementAndGet()

        when (state.get()) {
            State.CLOSED -> {
                if (failures >= failureThreshold) {
                    val cd = currentCooldown.get()
                    val newCooldown = if (cd == 0L) cooldownMs else (cd * 2).coerceAtMost(maxCooldownMs)
                    currentCooldown.set(newCooldown)
                    state.set(State.OPEN)
                    SecureLog.w("AiRetryController",
                        "Circuit breaker -> OPEN after $failures failures, cooldown=${newCooldown}ms")
                }
            }
            State.HALF_OPEN -> {
                state.set(State.OPEN)
                val newCd = (currentCooldown.get() * 2).coerceAtMost(maxCooldownMs)
                currentCooldown.set(newCd)
                SecureLog.w("AiRetryController",
                    "Half-open probe failed -> OPEN, cooldown=${newCd}ms")
            }
            State.OPEN -> { /* 已在 Open 状态 */ }
        }
    }

    /** 检查是否允许请求通过，必要时等待冷却 */
    suspend fun tryAcquire(): Boolean {
        while (true) {
            when (state.get()) {
                State.CLOSED -> return true
                State.HALF_OPEN -> {
                    // 只允许 1 个探测请求
                    return consecutiveSuccesses.get() < successThreshold
                }
                State.OPEN -> {
                    val elapsed = System.currentTimeMillis() - lastFailureTime.get()
                    val cd = currentCooldown.get()
                    if (elapsed >= cd) {
                        state.set(State.HALF_OPEN)
                        consecutiveSuccesses.set(0)
                        SecureLog.i("AiRetryController", "Cooldown elapsed -> HALF_OPEN")
                        return true
                    }
                    // 还在冷却中,拒绝
                    SecureLog.w("AiRetryController",
                        "Request blocked: circuit OPEN, remaining=${cd - elapsed}ms")
                    return false
                }
            }
        }
    }

    private fun reset() {
        state.set(State.CLOSED)
        consecutiveFailures.set(0)
        consecutiveSuccesses.set(0)
        lastFailureTime.set(0)
    }
}

/**
 * Token Bucket 速率限流器。
 *
 * 控制论映射:
 *   饱和非线性: 桶满时直接拒绝,不排队不做 FIFO
 *   填充速率: 稳态下保证 maxTokensPerPeriod 个 token/period
 */
class AiRateLimiter(
    private val maxTokens: Int = 60,          // 桶容量
    private val refillTokens: Int = 1,         // 每次填充量
    private val refillIntervalMs: Long = 1000  // 填充间隔
) {
    private val tokens = AtomicInteger(maxTokens)
    private val lastRefill = AtomicLong(System.currentTimeMillis())
    private val mutex = Mutex()

    suspend fun tryAcquire(): Boolean = mutex.withLock {
        refill()
        if (tokens.get() > 0) {
            tokens.decrementAndGet()
            true
        } else {
            SecureLog.w("AiRateLimiter", "Rate limit hit: bucket empty")
            false
        }
    }

    val availableTokens: Int
        get() {
            refillSync()
            return tokens.get()
        }

    private fun refill() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefill.get()
        val refillCount = (elapsed / refillIntervalMs).toInt()
        if (refillCount > 0) {
            val newTokens = (tokens.get() + refillCount * refillTokens).coerceAtMost(maxTokens)
            tokens.set(newTokens)
            lastRefill.set(now)
        }
    }

    private fun refillSync() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefill.get()
        val refillCount = (elapsed / refillIntervalMs).toInt()
        if (refillCount > 0) {
            val newTokens = (tokens.get() + refillCount * refillTokens).coerceAtMost(maxTokens)
            tokens.set(newTokens)
            lastRefill.set(now)
        }
    }
}
