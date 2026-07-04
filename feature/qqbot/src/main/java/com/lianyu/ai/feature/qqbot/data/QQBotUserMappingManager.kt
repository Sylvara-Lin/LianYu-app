package com.lianyu.ai.feature.qqbot.data

import com.lianyu.ai.database.repository.CompanionRepository
import kotlinx.coroutines.flow.first

class QQBotUserMappingManager(
    private val tokenStore: QQBotTokenStore,
    private val companionRepository: CompanionRepository
) {

    suspend fun getOrCreateMapping(qqUserId: String): Long? {
        val existingId = tokenStore.getCompanionIdForQQUser(qqUserId)
        if (existingId != null && existingId > 0) {
            val companion = companionRepository.getCompanionById(existingId)
            if (companion != null) return existingId
            tokenStore.setCompanionIdForQQUser(qqUserId, 0L)
        }
        return createDefaultMapping(qqUserId)
    }

    private suspend fun createDefaultMapping(qqUserId: String): Long? {
        val defaultCompanionId = tokenStore.getDefaultCompanionId()
        val companionId = if (defaultCompanionId != null && defaultCompanionId > 0) {
            defaultCompanionId
        } else {
            companionRepository.getAllCompanions().first().firstOrNull()?.id
        } ?: return null

        tokenStore.setCompanionIdForQQUser(qqUserId, companionId)
        return companionId
    }

    suspend fun updateMapping(qqUserId: String, companionId: Long): Boolean {
        val companion = companionRepository.getCompanionById(companionId)
        if (companion == null) return false
        tokenStore.setCompanionIdForQQUser(qqUserId, companionId)
        return true
    }

    suspend fun removeMapping(qqUserId: String) {
        tokenStore.removeQQUserMapping(qqUserId)
    }

    suspend fun getAllMappings(): Map<String, Long> {
        return tokenStore.getAllQQUserMappings()
    }

    suspend fun getMappingForQQUser(qqUserId: String): Long? {
        val existingId = tokenStore.getCompanionIdForQQUser(qqUserId)
        if (existingId != null && existingId > 0) {
            val companion = companionRepository.getCompanionById(existingId)
            if (companion != null) return existingId
            tokenStore.removeQQUserMapping(qqUserId)
        }
        return null
    }
}
