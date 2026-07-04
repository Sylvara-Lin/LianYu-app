package com.lianyu.ai.database.repository

import com.lianyu.ai.database.dao.ChatGroupDao
import com.lianyu.ai.database.model.ChatGroup
import kotlinx.coroutines.flow.Flow

class ChatGroupRepository(private val chatGroupDao: ChatGroupDao) {
    fun getAllGroups(): Flow<List<ChatGroup>> = chatGroupDao.getAllGroups()

    suspend fun getGroupById(id: Long): ChatGroup? = chatGroupDao.getGroupById(id)

    fun getGroupByIdFlow(id: Long): Flow<ChatGroup?> = chatGroupDao.getGroupByIdFlow(id)

    suspend fun insertGroup(group: ChatGroup): Long = chatGroupDao.insertGroup(group)

    suspend fun updateGroup(group: ChatGroup) = chatGroupDao.updateGroup(group)

    suspend fun deleteGroup(group: ChatGroup) = chatGroupDao.deleteGroup(group)

    suspend fun updateTimestamp(id: Long) = chatGroupDao.updateTimestamp(id)
}
