package com.lianyu.ai.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.common.CompanionRole
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.database.RolePresetStore
import com.lianyu.ai.database.repository.CompanionRepository
import com.lianyu.ai.database.repository.UserRepository
import com.lianyu.ai.common.ImageUtils
import com.lianyu.ai.domain.ServiceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 角色切换过程中的 UI 状态。
 */
sealed class RoleSwitchState {
    data object Idle : RoleSwitchState()
    data class InProgress(val stage: SwitchStage) : RoleSwitchState()
    data class Error(val message: String) : RoleSwitchState()
}

/**
 * 角色切换的细粒度阶段，用于向用户展示当前进度。
 */
enum class SwitchStage {
    SAVING_SNAPSHOT,
    LOADING_PRESET,
    APPLYING_PRESET,
    UPDATING_PREFERENCE
}

/**
 * [R13 FIX] 一次性事件，通过 SharedFlow 发送，不再用 StateFlow 存。
 */
sealed class RoleSwitchEvent {
    data class Error(val message: String) : RoleSwitchEvent()
    data class StageUpdate(val stage: SwitchStage) : RoleSwitchEvent()
    object Success : RoleSwitchEvent()
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    // [R12 FIX] 改为 by lazy 延迟获取，消除启动竞态闪退（与 CreateCompanionViewModel 修复一致）
    private val repository by lazy { ServiceRegistry.getOrThrow(UserRepository::class.java) }
    private val companionRepository by lazy { ServiceRegistry.getOrThrow(CompanionRepository::class.java) }
    private val rolePresetStore = RolePresetStore(application)

    val userName: StateFlow<String> by lazy { repository.userName }
    val userAvatar: StateFlow<String?> by lazy { repository.userAvatar }
    val selectedRole: StateFlow<CompanionRole> by lazy { repository.selectedRole }

    // [R13 FIX] 一次性事件改用 SharedFlow（非 sticky）：原 StateFlow<RoleSwitchState.Error>
    // 靠 UI 手动 consumeSwitchError() 清除，是教科书级反模式。
    private val _switchState = MutableStateFlow<RoleSwitchState>(RoleSwitchState.Idle)
    val switchState: StateFlow<RoleSwitchState> = _switchState.asStateFlow()

    private val _switchEvent = MutableSharedFlow<RoleSwitchEvent>(extraBufferCapacity = 4)
    val switchEvent: SharedFlow<RoleSwitchEvent> = _switchEvent

    /** 兼容旧 UI：只要处于 InProgress 状态即视为切换中。 */
    val isSwitchingRole: StateFlow<Boolean> = _switchState
        .map { it is RoleSwitchState.InProgress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun updateUserName(name: String) {
        viewModelScope.launch {
            repository.updateUserName(name)
        }
    }

    fun updateUserAvatar(avatarUri: String?) {
        viewModelScope.launch {
            val savedUri = if (avatarUri != null) {
                ImageUtils.saveUriToInternalStorage(getApplication(), avatarUri)
            } else null
            repository.updateUserAvatar(savedUri)
        }
    }

    /**
     * 切换当前角色类型。
     *
     * 逻辑：
     * 1. 将当前默认伴侣的字段快照保存到当前角色的 RolePresetStore。
     * 2. 读取目标角色的预设（用户自定义过则取自定义，否则取默认）。
     * 3. 将目标预设应用到默认体验伴侣，保留 companionId、聊天记录与亲密度。
     * 4. 更新用户当前选中角色。
     *
     * 所有 UI 状态与完成回调都在主线程分发；数据库/SharedPreferences 操作在 Dispatchers.IO 执行，
     * 避免阻塞主线程，同时彻底消除因 IO 线程回调导航/Compose 状态导致的 "setCurrentState must be
     * called on the main thread" 崩溃。
     */
    fun switchRole(targetRole: CompanionRole, onComplete: (() -> Unit)? = null) {
        if (targetRole == repository.selectedRole.value) {
            onComplete?.invoke()
            return
        }

        viewModelScope.launch {
            _switchState.value = RoleSwitchState.InProgress(SwitchStage.SAVING_SNAPSHOT)
            val success = try {
                val currentRole = repository.selectedRole.value

                // 1) 查询默认体验伴侣（IO）
                val defaultCompanion = withContext(Dispatchers.IO) {
                    companionRepository.getDefaultExperienceCompanion()
                }

                if (defaultCompanion != null) {
                    // 2) 保存当前角色快照（IO）
                    withContext(Dispatchers.IO) {
                        rolePresetStore.snapshotFromCompanion(currentRole, defaultCompanion)
                    }

                    // 3) 加载目标角色预设（IO）
                    _switchState.value = RoleSwitchState.InProgress(SwitchStage.LOADING_PRESET)
                    val targetPreset = withContext(Dispatchers.IO) {
                        rolePresetStore.getPreset(targetRole)
                    }

                    // 4) 应用预设并更新数据库（IO）
                    _switchState.value = RoleSwitchState.InProgress(SwitchStage.APPLYING_PRESET)
                    val updatedCompanion = targetPreset.applyTo(defaultCompanion)
                    withContext(Dispatchers.IO) {
                        companionRepository.updateCompanion(updatedCompanion)
                    }
                } else {
                    // 没有默认伴侣时直接插入目标预设的新实体
                    _switchState.value = RoleSwitchState.InProgress(SwitchStage.LOADING_PRESET)
                    val targetPreset = withContext(Dispatchers.IO) {
                        rolePresetStore.getPreset(targetRole)
                    }
                    _switchState.value = RoleSwitchState.InProgress(SwitchStage.APPLYING_PRESET)
                    withContext(Dispatchers.IO) {
                        companionRepository.insertCompanion(targetPreset.createCompanion())
                    }
                }

                // 5) 更新用户偏好（IO 写 SharedPreferences，线程安全）
                _switchState.value = RoleSwitchState.InProgress(SwitchStage.UPDATING_PREFERENCE)
                withContext(Dispatchers.IO) {
                    repository.updateSelectedRole(targetRole)
                }

                SecureLog.d("ProfileViewModel", "switchRole success: $targetRole")
                true
            } catch (e: CancellationException) {
                _switchState.value = RoleSwitchState.Idle
                throw e
            } catch (e: Exception) {
                SecureLog.e("ProfileViewModel", "switchRole failed", e)
                _switchState.value = RoleSwitchState.Idle
                // [R13 FIX] 错误通过 SharedFlow 发送一次性事件，不再用 sticky StateFlow
                _switchEvent.tryEmit(RoleSwitchEvent.Error(e.message ?: "切换失败"))
                false
            }

            if (success) {
                _switchState.value = RoleSwitchState.Idle
                _switchEvent.tryEmit(RoleSwitchEvent.Success)
                // 保证所有导航/Compose 状态更新都在主线程执行
                onComplete?.invoke()
            }
        }
    }

    /**
     * [R13 FIX] 保留向后兼容：原 consumeSwitchError 现在是 no-op，
     * 因为错误已通过 SharedFlow 发送，不再需要手动清除 sticky 状态。
     */
    fun consumeSwitchError() {
        // no-op: errors now flow through _switchEvent SharedFlow
    }
}
