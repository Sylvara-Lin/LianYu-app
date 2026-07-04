package com.lianyu.ai.feature.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.messageReadDataStore: DataStore<Preferences> by preferencesDataStore(name = "message_read_status")

class MessageReadStore(context: Context) {

    private val dataStore = context.applicationContext.messageReadDataStore

    companion object {
        private fun readTimeKey(companionId: Long) = longPreferencesKey("read_${companionId}")
    }

    fun lastReadTimeFlow(companionId: Long): Flow<Long> =
        dataStore.data.map { prefs -> prefs[readTimeKey(companionId)] ?: 0L }

    suspend fun getLastReadTime(companionId: Long): Long =
        lastReadTimeFlow(companionId).first()

    suspend fun markAsRead(companionId: Long, timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[readTimeKey(companionId)] = timestamp
        }
    }
}
