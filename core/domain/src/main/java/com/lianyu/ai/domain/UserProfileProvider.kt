package com.lianyu.ai.domain

/**
 * Provides user profile information to features that need it.
 * Implemented by feature:profile (or app-level bridge).
 *
 * 注意：此接口属于 core:domain 零依赖模块，不得引入 kotlinx.coroutines 等外部依赖。
 * 头像变化监听采用回调模式，返回取消订阅的函数。
 */
interface UserProfileProvider {
    fun getUserId(): String
    fun getNickname(): String
    fun getAvatar(): String?

    /**
     * 观察头像变化。调用方在协程中订阅，[onChange] 每次头像更新时回调。
     * @return 取消订阅的函数，调用后不再收到回调
     */
    fun observeAvatar(onChange: (String?) -> Unit): () -> Unit

    fun isLoggedIn(): Boolean
}
