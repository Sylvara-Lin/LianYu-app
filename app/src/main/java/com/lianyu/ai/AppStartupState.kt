package com.lianyu.ai

/**
 * 应用启动状态机。
 *
 * 三阶段：CRITICAL → BACKGROUND → UI_READY
 * 每阶段有明确的依赖关系和超时保护。
 */
sealed class AppStartupState {
    /** 关键路径初始化中 */
    data object CriticalInit : AppStartupState()

    /** 关键路径完成，后台任务进行中 */
    data class BackgroundInit(
        val criticalMs: Long,
        val tasksCompleted: Int = 0,
        val tasksTotal: Int = 0
    ) : AppStartupState()

    /** 所有初始化完成，应用就绪 */
    data class Ready(val totalMs: Long) : AppStartupState()

    /** 初始化失败 */
    data class Failed(val phase: String, val reason: String) : AppStartupState()
}
