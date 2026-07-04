package com.lianyu.ai.common

/**
 * 并发/线程池常量集中管理。
 *
 * [P0 FIX] 将原先无界 CachedThreadPool 改为有界线程池，避免长对话/高频请求下
 * 线程无限增长导致 OOM 或 ANR。
 */
object ConcurrencyConstants {

    /** 有界线程池核心线程数 */
    const val INTERRUPTIBLE_EXECUTOR_CORE_POOL_SIZE = 4

    /** 有界线程池最大线程数 */
    const val INTERRUPTIBLE_EXECUTOR_MAXIMUM_POOL_SIZE = 32

    /** 非核心线程空闲保留时间（秒） */
    const val INTERRUPTIBLE_EXECUTOR_KEEP_ALIVE_SECONDS = 30L

    /** 有界线程池任务队列容量 */
    const val INTERRUPTIBLE_EXECUTOR_WORK_QUEUE_CAPACITY = 256
}
