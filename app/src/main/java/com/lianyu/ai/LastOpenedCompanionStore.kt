package com.lianyu.ai

import android.content.Context
import androidx.core.content.edit
import com.lianyu.ai.database.repository.CompanionRepository

object LastOpenedCompanionStore {
    private const val PREFS_NAME = "last_opened_companion"
    private const val KEY_COMPANION_ID = "companion_id"
    private const val UNSET_COMPANION_ID = -1L

    fun save(context: Context, companionId: Long) {
        if (companionId <= 0L) return
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putLong(KEY_COMPANION_ID, companionId) }
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_COMPANION_ID) }
    }

    fun get(context: Context): Long {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_COMPANION_ID, UNSET_COMPANION_ID)
    }

    fun resolveInitialCompanionId(
        lastOpenedId: Long,
        availableCompanionIds: Collection<Long>
    ): Long? {
        return lastOpenedId.takeIf { it > 0L && it in availableCompanionIds }
    }

    suspend fun resolveInitialCompanionId(
        context: Context,
        companionRepository: CompanionRepository
    ): Long? {
        val lastOpenedId = get(context)
        if (lastOpenedId <= 0L) return null

        return if (companionRepository.getCompanionById(lastOpenedId) != null) {
            lastOpenedId
        } else {
            clear(context)
            null
        }
    }
}
