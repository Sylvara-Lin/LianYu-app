package com.lianyu.ai.feature.wechat.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.feature.wechat.data.A0
import com.lianyu.ai.feature.wechat.data.WeChatMessageRepository
import com.lianyu.ai.feature.wechat.data.WeChatTokenStore
import com.lianyu.ai.feature.wechat.service.WeChatPollingService
import com.lianyu.ai.feature.wechat.service.WeChatPollingWorker
import com.lianyu.ai.feature.wechat.service.WeChatServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WeChatViewModel(
    application: Application,
    private val repository: WeChatMessageRepository,
    private val tokenStore: WeChatTokenStore
) : ViewModel() {
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(WeChatUiState())
    val uiState: StateFlow<WeChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WeChatEvent>()
    val events: SharedFlow<WeChatEvent> = _events.asSharedFlow()

    private val loginManager = WeChatLoginManager(repository)
    private val companionDao = AppDatabase.getDatabase(appContext).companionDao()

    init {
        viewModelScope.launch {
            repository.accountFlow.collect { account ->
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = account != null,
                    account = account
                )
                if (account != null) {
                    WeChatPollingService.start(appContext)
                    loadUserMappings()
                } else {
                    WeChatPollingService.stop(appContext)
                }
            }
        }

        viewModelScope.launch {
            tokenStore.autoReplyFlow.collect { autoReply ->
                _uiState.value = _uiState.value.copy(autoReply = autoReply)
            }
        }

        viewModelScope.launch {
            tokenStore.notifyEnabledFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(notifyEnabled = enabled)
            }
        }

        viewModelScope.launch {
            tokenStore.forwardEnabledFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(forwardEnabled = enabled)
            }
        }

        viewModelScope.launch {
            tokenStore.defaultCompanionIdFlow.collect { companionId ->
                _uiState.value = _uiState.value.copy(defaultCompanionId = companionId)
            }
        }

        viewModelScope.launch {
            tokenStore.customBotNameFlow.collect { name ->
                _uiState.value = _uiState.value.copy(customBotName = name)
            }
        }

        viewModelScope.launch {
            companionDao.getAllCompanions().collect { companions ->
                _uiState.value = _uiState.value.copy(availableCompanions = companions)
            }
        }

        viewModelScope.launch {
            repository.incomingMessages.collect { message ->
                val text = repository.extractText(message)
                _events.emit(WeChatEvent.MessageReceived(message.fromUserId ?: "", text))
            }
        }
    }

    private fun loadUserMappings() {
        viewModelScope.launch {
            val mappings = tokenStore.getAllWechatUserMappings()
            _uiState.value = _uiState.value.copy(userCompanionMappings = mappings)
        }
    }

    fun setUserCompanionMapping(wechatUserId: String, companionId: Long) {
        viewModelScope.launch {
            tokenStore.setCompanionIdForWechatUser(wechatUserId, companionId)
            loadUserMappings()
        }
    }

    fun removeUserCompanionMapping(wechatUserId: String) {
        viewModelScope.launch {
            tokenStore.removeWechatUserMapping(wechatUserId)
            loadUserMappings()
        }
    }

    // ==================== QR Code Login ====================

    fun startQrCodeLogin() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = loginManager.getQrCode()
            result.onSuccess { qrCode ->
                _uiState.value = _uiState.value.copy(
                    qrCodeKey = qrCode.statusToken,
                    qrCodeContent = qrCode.displayContent,
                    isLoading = false,
                    showQrCode = true
                )
                loginManager.startQrPolling(
                    qrCode = qrCode.statusToken,
                    scope = viewModelScope,
                    onSuccess = { account ->
                        _uiState.value = _uiState.value.copy(
                            isLoggedIn = true,
                            account = account,
                            showQrCode = false,
                            qrCodeKey = null,
                            qrCodeContent = null,
                            error = null
                        )
                        viewModelScope.launch {
                            _events.emit(WeChatEvent.LoginSuccess)
                            WeChatPollingWorker.schedule(appContext)
                        }
                    },
                    onExpired = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showQrCode = false,
                            qrCodeKey = null,
                            qrCodeContent = null,
                            error = "二维码已过期，请重新获取"
                        )
                    },
                    onTimeout = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showQrCode = false,
                            qrCodeKey = null,
                            qrCodeContent = null,
                            error = "扫码超时，请重试"
                        )
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        }
    }

    fun cancelQrLogin() {
        loginManager.cancelQrPolling()
        _uiState.value = _uiState.value.copy(showQrCode = false, qrCodeKey = null, qrCodeContent = null, isLoading = false)
    }

    // ==================== Settings ====================

    fun toggleAutoReply(enabled: Boolean) {
        viewModelScope.launch {
            tokenStore.setAutoReply(enabled)
        }
    }

    fun toggleNotifyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            tokenStore.setNotifyEnabled(enabled)
        }
    }

    fun toggleForwardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            tokenStore.setForwardEnabled(enabled)
        }
    }

    fun setDefaultCompanionId(companionId: Long?) {
        viewModelScope.launch {
            tokenStore.setDefaultCompanionId(companionId)
        }
    }

    fun setCustomBotName(name: String?) {
        viewModelScope.launch {
            tokenStore.setCustomBotName(name)
        }
    }

    // ==================== Send Message ====================

    fun sendMessage(toUserId: String, text: String) {
        viewModelScope.launch {
            val result = repository.sendTextMessage(toUserId, text)
            result.onFailure { error ->
                _events.emit(WeChatEvent.SendFailed(error.message ?: "发送失败"))
            }
        }
    }

    // ==================== Logout ====================

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            WeChatPollingService.stop(appContext)
            WeChatPollingWorker.cancel(appContext)
            _uiState.value = _uiState.value.copy(isLoggedIn = false, account = null)
            _events.emit(WeChatEvent.LoggedOut)
        }
    }

    override fun onCleared() {
        super.onCleared()
        loginManager.clear()
    }
}

data class WeChatUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val account: A0? = null,
    val showQrCode: Boolean = false,
    val qrCodeKey: String? = null,
    val qrCodeContent: String? = null,
    val error: String? = null,
    val autoReply: Boolean = false,
    val notifyEnabled: Boolean = true,
    val forwardEnabled: Boolean = true,
    val defaultCompanionId: Long? = null,
    val availableCompanions: List<CompanionEntity> = emptyList(),
    val userCompanionMappings: Map<String, Long> = emptyMap(),
    val customBotName: String? = null
)

sealed class WeChatEvent {
    data class MessageReceived(val fromUserId: String, val text: String) : WeChatEvent()
    data class SendFailed(val error: String) : WeChatEvent()
    data object LoginSuccess : WeChatEvent()
    data object LoggedOut : WeChatEvent()
}

class WeChatViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = WeChatServiceLocator.messageRepository(application)
        val tokenStore = WeChatServiceLocator.tokenStore(application)
        return WeChatViewModel(application, repository, tokenStore) as T
    }
}
