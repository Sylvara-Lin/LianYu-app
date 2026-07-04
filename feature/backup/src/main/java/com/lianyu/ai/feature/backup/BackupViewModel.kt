package com.lianyu.ai.feature.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.feature.backup.model.BackupData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 数据备份/恢复 ViewModel。
 *
 * 状态空间 S ∈ {Idle, Exporting, Importing, Success(message), Error(message)}
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val exportService = BackupExportService(application)
    private val importService = BackupImportService(application)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // --- UI State ---
    sealed class UiState {
        data object Idle : UiState()
        data object Exporting : UiState()
        data object Importing : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _exportResult = MutableSharedFlow<ByteArray>()
    val exportResult = _exportResult.asSharedFlow()

    private val _importRequest = MutableSharedFlow<Unit>()
    val importRequest = _importRequest.asSharedFlow()

    // --- Actions ---

    fun export(password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Exporting
            try {
                val data = exportService.export()
                val jsonBytes = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)
                val encrypted = encrypt(jsonBytes, password)
                _exportResult.emit(encrypted)
                _uiState.value = UiState.Success("导出成功")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("导出失败: ${e.localizedMessage ?: "未知错误"}")
            }
        }
    }

    fun import(uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Importing
            try {
                // 读取文件
                val fileBytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取文件")
                }

                // 解密
                val jsonBytes = decrypt(fileBytes, password)
                val jsonString = String(jsonBytes, Charsets.UTF_8)

                // 反序列化
                val data = json.decodeFromString(BackupData.serializer(), jsonString)

                // 版本检查
                if (data.version > 1) {
                    _uiState.value = UiState.Error("备份文件版本过高 (v${data.version})，请升级应用后重试")
                    return@launch
                }

                // 导入
                importService.import(data).fold(
                    onSuccess = {
                        _uiState.value = UiState.Success("导入成功，请重启应用以加载数据")
                    },
                    onFailure = { e ->
                        _uiState.value = UiState.Error("导入失败: ${e.localizedMessage ?: "未知错误"}")
                    }
                )
            } catch (e: Exception) {
                val msg = when {
                    e is javax.crypto.AEADBadTagException -> "密码错误，请重试"
                    e.message?.contains("json", ignoreCase = true) == true -> "文件格式不正确"
                    else -> "导入失败: ${e.localizedMessage ?: "未知错误"}"
                }
                _uiState.value = UiState.Error(msg)
            }
        }
    }

    fun requestImport() {
        _importRequest.tryEmit(Unit)
    }

    fun onExportComplete() {
        _uiState.value = UiState.Success("导出成功")
    }

    fun onError(message: String) {
        _uiState.value = UiState.Error(message)
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }

    // --- 加密/解密 ---

    companion object Crypto {
        private const val MAGIC = "LYBK"
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 16
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256

        fun encrypt(plaintext: ByteArray, password: String): ByteArray {
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv  // GCM 自动生成 12-byte IV
            val ciphertext = cipher.doFinal(plaintext)  // 包含 16-byte auth tag

            val out = ByteArrayOutputStream()
            out.write(MAGIC.toByteArray(Charsets.UTF_8))
            out.write(salt)
            out.write(iv)
            out.write(ciphertext)
            return out.toByteArray()
        }

        fun decrypt(data: ByteArray, password: String): ByteArray {
            val headerLen = 4 + SALT_LENGTH + IV_LENGTH
            require(data.size > headerLen) { "文件格式不正确（文件太小）" }

            val magic = String(data, 0, 4, Charsets.UTF_8)
            require(magic == MAGIC) { "文件格式不正确（非 .lybk 备份文件）" }

            val salt = data.copyOfRange(4, 4 + SALT_LENGTH)
            val iv = data.copyOfRange(4 + SALT_LENGTH, headerLen)
            val ciphertext = data.copyOfRange(headerLen, data.size)
            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH * 8, iv))
            return cipher.doFinal(ciphertext)
        }

        private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
            val tmpKey = factory.generateSecret(spec)
            return SecretKeySpec(tmpKey.encoded, "AES")
        }
    }
}
