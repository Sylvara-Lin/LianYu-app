package com.lianyu.ai

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import com.lianyu.ai.common.RomUtils
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.feature.notification.CompanionKeepAliveService
import com.lianyu.ai.feature.notification.CompanionMessageWorker

/**
 * IQOO/OriginOS 专用保活 JobService。
 *
 * [FIX] 2026-06-22: OriginOS 对前台服务和 WorkManager 都有严格限制，
 * 使用 JobScheduler 作为第三层兜底保活机制。
 *
 * 职责：
 * 1. 检查前台服务是否存活，若已死则尝试重启
 * 2. 确保 WorkManager 中有待处理的 CompanionMessageWorker
 * 3. 记录设备级诊断日志（用于排查 IQOO 特定问题）
 */
class IqooKeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        SecureLog.i("IqooKeepAliveJobService", "Job started on ${RomUtils.getRomDisplayName()}")

        // JobService 标准异步模式：返回 true 表示工作异步执行，完成后必须调 jobFinished。
        // 裸 Thread 在此场景符合契约（JobScheduler 管理进程生命周期，无 CoroutineJobService 基类）。
        Thread({
            try {
                performKeepAliveCheck()
            } catch (e: Exception) {
                SecureLog.e("IqooKeepAliveJobService", "Keep-alive check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }, "iqoo-keepalive").start()

        return true // 表示工作正在异步执行
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        SecureLog.w("IqooKeepAliveJobService", "Job stopped prematurely by system")
        // 返回 true 表示希望系统重新调度此任务
        return true
    }

    private fun performKeepAliveCheck() {
        val context = applicationContext

        // 1. 检查并尝试重启前台服务
        try {
            CompanionKeepAliveService.safeStart(context)
            SecureLog.d("IqooKeepAliveJobService", "Keep-alive service check passed")
        } catch (e: Exception) {
            SecureLog.w("IqooKeepAliveJobService", "Failed to restart keep-alive service: ${e.message}")
        }

        // 2. 确保 WorkManager 中有待处理任务
        try {
            CompanionMessageWorker.schedule(context)
            SecureLog.d("IqooKeepAliveJobService", "WorkManager check passed")
        } catch (e: Exception) {
            SecureLog.w("IqooKeepAliveJobService", "Failed to schedule WorkManager: ${e.message}")
        }

        // 3. 记录设备级诊断信息
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            SecureLog.i("IqooKeepAliveJobService", "Battery optimization ignored: $isIgnoring")
        }
    }
}
