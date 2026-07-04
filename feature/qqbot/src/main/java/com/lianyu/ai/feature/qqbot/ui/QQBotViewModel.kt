package com.lianyu.ai.feature.qqbot.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.database.AppDatabase
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.feature.qqbot.data.AESGCMHelper
import com.lianyu.ai.feature.qqbot.data.QRCodeGenerator
import com.lianyu.ai.feature.qqbot.data.QQBotMessageRepository
import com.lianyu.ai.feature.qqbot.data.QQBotTokenStore
import com.lianyu.ai.feature.qqbot.data.model.QQBotAccount
import com.lianyu.ai.feature.qqbot.data.network.BindStatus
import com.lianyu.ai.feature.qqbot.data.network.CreateBindTaskRequest
import com.lianyu.ai.feature.qqbot.data.network.PollBindResultRequest
import com.lianyu.ai.feature.qqbot.data.network.QQBotLiteBindApi
import com.lianyu.ai.feature.qqbot.data.network.QQBotWebSocketClient
import com.lianyu.ai.feature.qqbot.service.QQBotForegroundService
import com.lianyu.ai.feature.qqbot.service.QQBotServiceLocator
import com.lianyu.ai.network.NetworkConstants
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

import java.util.concurrent.TimeUnit

class QQBotViewModel(
    application: Application,
    private val repository: QQBotMessageRepository,
    private val tokenStore: QQBotTokenStore
) : ViewModel() {

    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(QQBotUiState())
    val uiState: StateFlow<QQBotUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<QQBotEvent>()
    val events: SharedFlow<QQBotEvent> = _events.asSharedFlow()

    private val companionDao = AppDatabase.getDatabase(appContext).companionDao()

    // ── 扫码绑定相关 ──
    private var bindJob: Job? = null
    private var currentBindKey: String? = null
    private var currentTaskId: String? = null

    private val liteBindApi: QQBotLiteBindApi by lazy {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://q.qq.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(QQBotLiteBindApi::class.java)
    }

    init {
        viewModelScope.launch {
            repository.accountFlow.collect { account ->
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = account != null,
                    account = account
                )
                if (account != null) {
                    QQBotForegroundService.start(appContext)
                    loadUserMappings()
                } else {
                    QQBotForegroundService.stop(appContext)
                }
            }
        }

        viewModelScope.launch {
            tokenStore.autoReplyFlow.collect { _uiState.value = _uiState.value.copy(autoReply = it) }
        }
        viewModelScope.launch {
            tokenStore.notifyEnabledFlow.collect { _uiState.value = _uiState.value.copy(notifyEnabled = it) }
        }
        viewModelScope.launch {
            tokenStore.forwardEnabledFlow.collect { _uiState.value = _uiState.value.copy(forwardEnabled = it) }
        }
        viewModelScope.launch {
            tokenStore.defaultCompanionIdFlow.collect { _uiState.value = _uiState.value.copy(defaultCompanionId = it) }
        }
        viewModelScope.launch {
            tokenStore.customBotNameFlow.collect { _uiState.value = _uiState.value.copy(customBotName = it) }
        }
        viewModelScope.launch {
            companionDao.getAllCompanions().collect { _uiState.value = _uiState.value.copy(availableCompanions = it) }
        }
        viewModelScope.launch {
            repository.incomingEvents.collect { event ->
                val text = repository.extractText(event)
                _events.emit(QQBotEvent.MessageReceived(repository.getReplyKey(event), text))
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
    }

    // ── 手动绑定 ──

    fun saveAccount(appId: String, clientSecret: String, customName: String?) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = repository.saveAccount(appId.trim(), clientSecret.trim(), customName?.trim())
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true, error = null)
                _events.emit(QQBotEvent.LoginSuccess)
                QQBotForegroundService.start(appContext)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _events.emit(QQBotEvent.LoginFailed(e.message ?: "绑定失败"))
            }
        }
    }

    // ── 扫码绑定（Hermes 协议） ──

    fun startQrBind() {
        if (bindJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(
            qrBitmap = null,
            bindStatus = BindStatus.NONE,
            bindError = null,
            isLoading = true
        )
        bindJob = viewModelScope.launch {
            try {
                val key = AESGCMHelper.generateKey()
                currentBindKey = key

                val createResponse = liteBindApi.createBindTask(
                    CreateBindTaskRequest(key = key)
                )
                if (!createResponse.isSuccessful || createResponse.body() == null) {
                    throw IllegalStateException("创建绑定任务失败: ${createResponse.code()}")
                }
                val body = createResponse.body()!!
                if (body.retcode != 0 || body.data == null) {
                    throw IllegalStateException(body.msg ?: "创建绑定任务失败")
                }
                val taskId = body.data.taskId
                currentTaskId = taskId

                val qrUrl = "${NetworkConstants.QQ_BOT_LITE_QR_CONNECT_URL}?task_id=$taskId&_wv=2"
                val qrBitmap = QRCodeGenerator.generate(qrUrl, sizePx = 512)
                    ?: throw IllegalStateException("二维码生成失败")

                _uiState.value = _uiState.value.copy(
                    qrBitmap = qrBitmap,
                    bindStatus = BindStatus.PENDING,
                    isLoading = false
                )

                // 轮询
                val deadline = System.currentTimeMillis() + 600_000L // 10 分钟
                var refreshCount = 0
                val maxRefresh = 3

                while (System.currentTimeMillis() < deadline && refreshCount < maxRefresh) {
                    delay(2_000L)

                    val pollResponse = liteBindApi.pollBindResult(
                        PollBindResultRequest(taskId = taskId)
                    )
                    if (!pollResponse.isSuccessful || pollResponse.body() == null) continue

                    val pollBody = pollResponse.body()!!
                    if (pollBody.retcode != 0 || pollBody.data == null) continue

                    val status = BindStatus.values().find { it.value == pollBody.data.status } ?: BindStatus.NONE
                    _uiState.value = _uiState.value.copy(bindStatus = status)

                    when (status) {
                        BindStatus.COMPLETED -> {
                            val appId = pollBody.data.botAppId
                                ?: throw IllegalStateException("绑定完成但未返回 AppID")
                            val encryptedSecret = pollBody.data.botEncryptSecret
                                ?: throw IllegalStateException("绑定完成但未返回加密 Secret")
                            val secret = AESGCMHelper.decrypt(encryptedSecret, key)
                            val openid = pollBody.data.userOpenid

                            // 保存账号
                            tokenStore.saveAccount(QQBotAccount(appId, secret))
                            // 触发 accountFlow 更新
                            _uiState.value = _uiState.value.copy(
                                isLoggedIn = true,
                                qrBitmap = null,
                                bindStatus = BindStatus.COMPLETED,
                                bindError = null
                            )
                            _events.emit(QQBotEvent.LoginSuccess)
                            QQBotForegroundService.start(appContext)
                            return@launch
                        }
                        BindStatus.EXPIRED -> {
                            refreshCount++
                            if (refreshCount >= maxRefresh) {
                                _uiState.value = _uiState.value.copy(
                                    bindError = "二维码已过期，请重新扫码",
                                    bindStatus = BindStatus.EXPIRED
                                )
                                return@launch
                            }
                            // 重新创建任务
                            val newKey = AESGCMHelper.generateKey()
                            currentBindKey = newKey
                            val newResponse = liteBindApi.createBindTask(
                                CreateBindTaskRequest(key = newKey)
                            )
                            if (newResponse.isSuccessful && newResponse.body()?.retcode == 0) {
                                val newTaskId = newResponse.body()!!.data!!.taskId
                                currentTaskId = newTaskId
                                val newQrUrl = "${NetworkConstants.QQ_BOT_LITE_QR_CONNECT_URL}?task_id=$newTaskId&_wv=2"
                                val newQrBitmap = QRCodeGenerator.generate(newQrUrl, sizePx = 512)
                                if (newQrBitmap != null) {
                                    _uiState.value = _uiState.value.copy(
                                        qrBitmap = newQrBitmap,
                                        bindStatus = BindStatus.PENDING
                                    )
                                }
                            }
                        }
                        else -> { /* 继续轮询 */ }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    bindError = "绑定超时，请重试",
                    bindStatus = BindStatus.EXPIRED
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    bindError = e.message ?: "绑定失败",
                    isLoading = false
                )
            }
        }
    }

    fun cancelQrBind() {
        bindJob?.cancel()
        bindJob = null
        currentBindKey = null
        currentTaskId = null
        _uiState.value = _uiState.value.copy(
            qrBitmap = null,
            bindStatus = BindStatus.NONE,
            bindError = null,
            isLoading = false
        )
    }

    fun logout() {
        cancelQrBind()
        viewModelScope.launch {
            repository.logout()
            QQBotForegroundService.stop(appContext)
            _uiState.value = _uiState.value.copy(isLoggedIn = false, account = null)
            _events.emit(QQBotEvent.LoggedOut)
        }
    }

    fun toggleAutoReply(enabled: Boolean) {
        viewModelScope.launch { tokenStore.setAutoReply(enabled) }
    }

    fun toggleNotifyEnabled(enabled: Boolean) {
        viewModelScope.launch { tokenStore.setNotifyEnabled(enabled) }
    }

    fun toggleForwardEnabled(enabled: Boolean) {
        viewModelScope.launch { tokenStore.setForwardEnabled(enabled) }
    }

    fun setDefaultCompanionId(companionId: Long?) {
        viewModelScope.launch { tokenStore.setDefaultCompanionId(companionId) }
    }

    fun setCustomBotName(name: String?) {
        viewModelScope.launch { tokenStore.setCustomBotName(name?.takeIf { it.isNotBlank() }) }
    }

    fun setUserCompanionMapping(qqUserId: String, companionId: Long) {
        viewModelScope.launch {
            tokenStore.setCompanionIdForQQUser(qqUserId, companionId)
            loadUserMappings()
        }
    }

    fun removeUserCompanionMapping(qqUserId: String) {
        viewModelScope.launch {
            tokenStore.removeQQUserMapping(qqUserId)
            loadUserMappings()
        }
    }

    private fun loadUserMappings() {
        viewModelScope.launch {
            val mappings = tokenStore.getAllQQUserMappings()
            _uiState.value = _uiState.value.copy(userCompanionMappings = mappings)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bindJob?.cancel()
    }
}

data class QQBotUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val account: QQBotAccount? = null,
    val error: String? = null,
    val autoReply: Boolean = false,
    val notifyEnabled: Boolean = true,
    val forwardEnabled: Boolean = true,
    val defaultCompanionId: Long? = null,
    val availableCompanions: List<CompanionEntity> = emptyList(),
    val userCompanionMappings: Map<String, Long> = emptyMap(),
    val customBotName: String? = null,
    val connectionState: QQBotWebSocketClient.ConnectionState = QQBotWebSocketClient.ConnectionState.DISCONNECTED,
    // 扫码绑定状态
    val qrBitmap: Bitmap? = null,
    val bindStatus: BindStatus = BindStatus.NONE,
    val bindError: String? = null
)

sealed class QQBotEvent {
    data class MessageReceived(val key: String, val text: String) : QQBotEvent()
    data object LoginSuccess : QQBotEvent()
    data object LoggedOut : QQBotEvent()
    data class LoginFailed(val error: String) : QQBotEvent()
}

class QQBotViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = QQBotServiceLocator.messageRepository(application)
        val tokenStore = QQBotServiceLocator.tokenStore(application)
        return QQBotViewModel(application, repository, tokenStore) as T
    }
}
