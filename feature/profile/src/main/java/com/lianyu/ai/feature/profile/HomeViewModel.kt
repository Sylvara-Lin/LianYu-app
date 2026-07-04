package com.lianyu.ai.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.common.ReadStatusManager
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.repository.ChatRepository
import com.lianyu.ai.database.repository.CompanionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val companionRepository: CompanionRepository
    private val chatRepository: ChatRepository
    private val prefs = application.getSharedPreferences("message_read_status", android.content.Context.MODE_PRIVATE)
    private val readTimeTriggers = mutableMapOf<Long, MutableStateFlow<Long>>()

    val chatList: Flow<List<ChatListItem>>

    init {
        val database = AppDatabase.getDatabase(application)
        companionRepository = CompanionRepository(database.companionDao())
        chatRepository = ChatRepository(database.chatMessageDao())

        viewModelScope.launch {
            ReadStatusManager.readEvents.collect { (id, timestamp) ->
                readTimeTriggers[id]?.value = timestamp
            }
        }

        chatList = companionRepository.getAllCompanions()
            .flatMapLatest { companions ->
                if (companions.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // 预热：为每个 companion 加载最近一页消息到 ChatRepository 内存缓存
                    viewModelScope.launch {
                        companions.forEach { companion ->
                            if (chatRepository.getCachedRecent(companion.id) == null) {
                                runCatching {
                                    chatRepository.getRecentMessagesSync(companion.id, 50)
                                }
                            }
                        }
                    }
                    val flows: List<Flow<ChatListItem>> = companions.map { companion ->
                        combine(
                            chatRepository.getLastMessageForCompanion(companion.id),
                            getOrCreateReadTimeFlow(companion.id)
                        ) { message: ChatMessage?, lastReadTime: Long ->
                            val hasUnread = message != null && !message.isFromUser && message.timestamp > lastReadTime
                            ChatListItem(companion = companion, lastMessage = message, hasUnread = hasUnread)
                        }
                            .catch {
                                emit(ChatListItem(companion = companion, lastMessage = null, hasUnread = false))
                            }
                    }
                    combine(flows) { items: Array<ChatListItem> ->
                        items.toList()
                    }
                }
            }
            .catch {
                emit(emptyList())
            }
    }

    fun markCompanionAsRead(companionId: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            prefs.edit().putLong("read_$companionId", now).apply()
            readTimeTriggers[companionId]?.value = now
        }
    }

    private fun getOrCreateReadTimeFlow(companionId: Long): Flow<Long> {
        return readTimeTriggers.getOrPut(companionId) {
            MutableStateFlow(prefs.getLong("read_$companionId", 0L))
        }
    }
}

data class ChatListItem(
    val companion: CompanionEntity,
    val lastMessage: ChatMessage?,
    val hasUnread: Boolean = false
)
