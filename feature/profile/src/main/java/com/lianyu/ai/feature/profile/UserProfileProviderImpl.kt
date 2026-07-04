package com.lianyu.ai.feature.profile

import android.content.Context
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.domain.UserProfileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Adapter that bridges domain UserProfileProvider to UserRepository.
 * Registered in LianYuApplication via ServiceRegistry.
 */
class UserProfileProviderImpl(context: Context) : UserProfileProvider {

    private val appContext = context.applicationContext
    private val repository = UserRepository(appContext)

    // 用于订阅 StateFlow 的内部作用域，生命周期与 Application 一致
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun getUserId(): String = "default_user"

    override fun getNickname(): String = repository.userName.value

    override fun getAvatar(): String? = repository.userAvatar.value

    override fun observeAvatar(onChange: (String?) -> Unit): () -> Unit {
        val job = observerScope.launch {
            repository.userAvatar.collect { avatar ->
                onChange(avatar)
            }
        }
        return { job.cancel() }
    }

    override fun isLoggedIn(): Boolean = true
}
