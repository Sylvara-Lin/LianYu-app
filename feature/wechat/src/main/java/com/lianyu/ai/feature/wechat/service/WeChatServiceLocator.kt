package com.lianyu.ai.feature.wechat.service

import android.content.Context
import com.lianyu.ai.feature.wechat.data.WeChatChatBridge
import com.lianyu.ai.feature.wechat.data.WeChatMessageRepository
import com.lianyu.ai.feature.wechat.data.WeChatSdkClientManager
import com.lianyu.ai.feature.wechat.data.WeChatTokenStore

object WeChatServiceLocator {

    @Volatile
    private var tokenStore: WeChatTokenStore? = null

    @Volatile
    private var messageRepository: WeChatMessageRepository? = null

    @Volatile
    private var sdkClientManager: WeChatSdkClientManager? = null

    // [R8 FIX] 缓存 chatBridge 单例：原每次 chatBridge() 返回新实例（各带 bridgeScope），
    // 用完不 close，每条消息泄漏一个 CoroutineScope。
    @Volatile
    private var chatBridge: WeChatChatBridge? = null

    fun tokenStore(context: Context): WeChatTokenStore {
        return tokenStore ?: synchronized(this) {
            tokenStore ?: WeChatTokenStore(context.applicationContext).also {
                tokenStore = it
            }
        }
    }

    fun messageRepository(context: Context): WeChatMessageRepository {
        return messageRepository ?: synchronized(this) {
            messageRepository ?: run {
                val store = tokenStore(context)
                val manager = sdkClientManager(context)
                WeChatMessageRepository(context.applicationContext, manager, store).also {
                    messageRepository = it
                }
            }
        }
    }

    fun chatBridge(context: Context): WeChatChatBridge {
        // [R8 FIX] 双重检查锁定返回单例，不再每次新建
        return chatBridge ?: synchronized(this) {
            chatBridge ?: run {
                val repo = messageRepository(context)
                WeChatChatBridge(context.applicationContext, repo).also {
                    chatBridge = it
                }
            }
        }
    }

    /**
     * [R8 FIX] 释放缓存的 bridge（关闭其内部 CoroutineScope）。
     * 在微信服务停止时调用，避免 scope 泄漏。
     */
    fun shutdown() {
        synchronized(this) {
            chatBridge?.close()
            chatBridge = null
            messageRepository = null
            sdkClientManager = null
            tokenStore = null
        }
    }

    fun sdkClientManager(context: Context): WeChatSdkClientManager {
        return sdkClientManager ?: synchronized(this) {
            sdkClientManager ?: WeChatSdkClientManager(tokenStore(context)).also {
                sdkClientManager = it
            }
        }
    }
}
