package com.lianyu.ai.common

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ReadStatusManager {
    private val _readEvents = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 1)
    val readEvents: SharedFlow<Pair<Long, Long>> = _readEvents.asSharedFlow()

    fun markAsRead(context: Context, id: Long, timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences("message_read_status", Context.MODE_PRIVATE)
            .edit().putLong("read_$id", timestamp).apply()
        _readEvents.tryEmit(id to timestamp)
    }

    fun getReadTime(context: Context, id: Long): Long {
        return context.getSharedPreferences("message_read_status", Context.MODE_PRIVATE)
            .getLong("read_$id", 0L)
    }
}
