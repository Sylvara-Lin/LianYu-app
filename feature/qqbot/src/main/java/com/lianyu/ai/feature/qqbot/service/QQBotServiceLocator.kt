package com.lianyu.ai.feature.qqbot.service

import android.content.Context
import com.lianyu.ai.feature.qqbot.data.QQBotChatBridge
import com.lianyu.ai.feature.qqbot.data.QQBotMessageRepository
import com.lianyu.ai.feature.qqbot.data.QQBotTokenStore
import com.lianyu.ai.feature.qqbot.data.network.QQBotApiClient

object QQBotServiceLocator {

    @Volatile
    private var tokenStore: QQBotTokenStore? = null

    @Volatile
    private var apiClient: QQBotApiClient? = null

    @Volatile
    private var messageRepository: QQBotMessageRepository? = null

    @Volatile
    private var chatBridge: QQBotChatBridge? = null

    fun tokenStore(context: Context): QQBotTokenStore {
        return tokenStore ?: synchronized(this) {
            tokenStore ?: QQBotTokenStore(context.applicationContext).also {
                tokenStore = it
            }
        }
    }

    fun apiClient(context: Context): QQBotApiClient {
        return apiClient ?: synchronized(this) {
            apiClient ?: QQBotApiClient(tokenStore(context)).also {
                apiClient = it
            }
        }
    }

    fun messageRepository(context: Context): QQBotMessageRepository {
        return messageRepository ?: synchronized(this) {
            messageRepository ?: run {
                val store = tokenStore(context)
                val client = apiClient(context)
                QQBotMessageRepository(context.applicationContext, store, client).also {
                    messageRepository = it
                }
            }
        }
    }

    fun chatBridge(context: Context): QQBotChatBridge {
        return chatBridge ?: synchronized(this) {
            chatBridge ?: run {
                val repo = messageRepository(context)
                QQBotChatBridge(context.applicationContext, repo, tokenStore(context)).also {
                    chatBridge = it
                }
            }
        }
    }
}
