package com.lianyu.ai.feature.localmodel

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalApi::class)
class LocalAiService private constructor(private val context: Context) {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var engineModelId: String? = null
    private var refCount = 0

    @Volatile
    private var _activeModel: LocalModel = LocalModelCatalog.default
    val activeModel: LocalModel get() = _activeModel
    val applicationContext: Context get() = context.applicationContext

    companion object {
        private const val TAG = "LocalAiService"
        private const val PREFS_NAME = "local_ai_engine_state"
        private const val KEY_ENGINE_DISABLED = "engine_disabled"

        @Volatile
        private var instance: LocalAiService? = null
        private val INSTANCE_LOCK = Any()

        /**
         * 原生库是否在当前设备上受支持。
         * false = x86 模拟器，或之前初始化崩溃过。
         */
        @Volatile
        var isNativeLibrarySupported: Boolean = true
            private set

        fun getInstance(context: Context): LocalAiService {
            return instance ?: synchronized(INSTANCE_LOCK) {
                instance ?: LocalAiService(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 持久化禁用本地模型引擎（崩溃后调用，防止反复闪退）。
         * 使用 commit() 确保同步写入磁盘，在进程被杀前落盘。
         */
        private fun persistDisable(context: Context) {
            isNativeLibrarySupported = false
            runCatching {
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ENGINE_DISABLED, true)
                    .commit()
            }
            Log.w(TAG, "Local model engine permanently disabled due to previous crash")
        }
    }

    init {
        checkNativeLibrarySupport()
    }

    private fun checkNativeLibrarySupport() {
        // 1. 检查 ABI：x86 模拟器不支持
        val abis = runCatching { Build.SUPPORTED_ABIS }.getOrNull()
        val primaryAbi = abis?.firstOrNull()?.lowercase()
        if (primaryAbi != null && primaryAbi.startsWith("x86")) {
            isNativeLibrarySupported = false
            Log.w(TAG, "Local model disabled: x86 ABI ($primaryAbi) not supported")
            return
        }

        // 2. 检查持久化标记：上次启动时引擎初始化是否崩溃过
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ENGINE_DISABLED, false)) {
            isNativeLibrarySupported = false
            Log.w(TAG, "Local model disabled: engine previously crashed on this device")
            return
        }
    }

    private fun tryCreateEngine(modelFile: java.io.File, backend: Backend): Engine? {
        return try {
            Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = backend,
                    cacheDir = context.cacheDir.absolutePath,
                    maxNumTokens = 1024
                )
            ).also { it.initialize() }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Engine init failed (native lib)", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Engine init failed: ${e.message}", e)
            null
        }
    }

    fun setActiveModel(model: LocalModel) {
        _activeModel = model
    }

    fun acquire() {
        synchronized(this) {
            refCount++
        }
    }

    /**
     * [M12 FIX] close 内的 engine 关闭操作改为走 mutex.withLock：
     * 原实现用 synchronized(this) 保护 refCount，但 engine?.close() 在 synchronized 块外执行，
     * 而 generate() 用 mutex.withLock 保护 engine 访问。两把不同的锁。
     * 时序：close 将 refCount 减到 0 → 在 synchronized 外执行 engine.close()；
     * 同时 generate 持有 mutex 正在使用 engine → 用到已关闭的 native 句柄，崩溃。
     * 现在 engine 的关闭也走 mutex，与 generate 的读取/初始化互斥。
     */
    suspend fun close() {
        val shouldShutdown = synchronized(this) {
            if (refCount <= 0) {
                false
            } else {
                refCount--
                refCount == 0
            }
        }
        if (shouldShutdown) {
            mutex.withLock {
                engine?.close()
                engine = null
                engineModelId = null
            }
        }
    }

    suspend fun shutdownEngine() {
        mutex.withLock {
            engine?.close()
            engine = null
            engineModelId = null
        }
    }

    suspend fun generate(
        systemPrompt: String,
        historyPrompt: String,
        userPrompt: String,
        model: LocalModel = _activeModel
    ): String = withContext(Dispatchers.IO) {
        if (!isNativeLibrarySupported) {
            throw IllegalStateException(
                "Local model is not supported on this device (${Build.SUPPORTED_ABIS.firstOrNull()})"
            )
        }

        val modelFile = model.modelFile(context)
        require(modelFile.exists()) { "${model.displayName} has not been downloaded." }

        val activeEngine = mutex.withLock {
            if (engine != null && engineModelId == model.id) {
                return@withLock engine!!
            }

            engine?.close()
            engine = null
            engineModelId = null

            val eng = tryCreateEngine(modelFile, Backend.GPU())
            if (eng != null) {
                engine = eng
                engineModelId = model.id
                eng
            } else {
                val fallback = tryCreateEngine(modelFile, Backend.CPU(2))
                if (fallback != null) {
                    engine = fallback
                    engineModelId = model.id
                    fallback
                } else {
                    persistDisable(context)
                    throw IllegalStateException(
                        "Local model engine failed to initialize."
                    )
                }
            }
        }

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(
                topK = 1,
                topP = 0.95,
                temperature = 0.3
            )
        )

        activeEngine.createConversation(config).use { conversation ->
            val prompt = buildString {
                if (historyPrompt.isNotBlank()) {
                    appendLine(historyPrompt)
                    appendLine()
                }
                append(userPrompt)
            }.trim()

            val response = conversation.sendMessage(prompt)
            conversation.renderMessageIntoString(response).trim()
        }
    }
}
