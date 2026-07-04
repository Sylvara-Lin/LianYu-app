package com.lianyu.ai.feature.wechat.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lianyu.ai.common.TimeoutBudgets
import com.lianyu.ai.feature.wechat.data.WeChatMessageRepository
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class WeChatPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = WeChatServiceLocator.messageRepository(applicationContext)

        if (!repo.isLoggedIn()) {
            return Result.success()
        }

        return try {
            // Poll messages with long-polling (统一用 WECHAT_POLL_TIMEOUT_MS)
            val result = repo.pollMessages(timeoutMs = TimeoutBudgets.WECHAT_POLL_TIMEOUT_MS)
            if (result.isSuccess) {
                Result.success()
            } else {
                // If failed, retry after a short delay
                delay(5000)
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "wechat_polling"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeChatPollingWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("wechat_keepalive")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
