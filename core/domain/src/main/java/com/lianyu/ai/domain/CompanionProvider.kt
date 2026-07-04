package com.lianyu.ai.domain

/**
 * Provides companion metadata to features that need it.
 * Implemented by feature:companion (or app-level bridge).
 */
interface CompanionProvider {
    suspend fun getCompanionName(id: Long): String
    suspend fun getCompanionAvatar(id: Long): String
    suspend fun isCompanionAvailable(id: Long): Boolean
}
