package com.lianyu.ai.feature.chat.voice

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 语音通话管理器 - 基于 sherpa-onnx 的实时流式语音识别
 *
 * 技术栈：
 * - sherpa-onnx OnlineRecognizer (Zipformer Transducer 模型, v1.13.3)
 * - AudioRecord (16000Hz, MONO, PCM_16BIT, VOICE_COMMUNICATION)
 * - AcousticEchoCanceler + NoiseSuppressor 音频增强
 * - 端点检测：尾部静音 2.4s / 静默 1.2s / 最长 20s
 * - 4 线程并行解码
 *
 * 用法：
 * ```kotlin
 * val manager = VoiceCallManager(context)
 * manager.init()                          // 加载模型
 * manager.onPartialResult = { text -> }   // 实时部分结果
 * manager.onFinalResult = { text -> }     // 端点检测触发，一句话完成
 * manager.startListening()
 * manager.stopListening()
 * manager.destroy()
 * ```
 */
class VoiceCallManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceCallManager"
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80
        private const val NUM_THREADS = 4

        // 端点检测规则
        private const val RULE1_TRAILING_SILENCE = 2.4f
        private const val RULE2_UTTERANCE_LENGTH = 1.2f
        private const val RULE3_MAX_UTTERANCE = 20.0f
    }

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var initialized = false

    /** 实时部分识别结果回调（每次 decode 后触发） */
    var onPartialResult: ((String) -> Unit)? = null

    /** 一句话最终识别结果回调（端点检测触发） */
    var onFinalResult: ((String) -> Unit)? = null

    /** 是否正在录音 */
    val isListening: Boolean get() = recordingJob?.isActive == true

    /**
     * 初始化 sherpa-onnx OnlineRecognizer
     * 首次运行时从 assets 复制模型到 filesDir（约需一次，后续跳过）
     */
    fun init() {
        if (initialized) return
        try {
            val encoderPath = copyAsset("encoder-epoch-20-avg-1-chunk-16-left-128.int8.onnx")
            val decoderPath = copyAsset("decoder-epoch-20-avg-1-chunk-16-left-128.onnx")
            val joinerPath = copyAsset("joiner-epoch-20-avg-1-chunk-16-left-128.int8.onnx")
            val tokensPath = copyAsset("tokens.txt")

            val featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = FEATURE_DIM
            )

            val transducerConfig = OnlineTransducerModelConfig(
                encoder = encoderPath,
                decoder = decoderPath,
                joiner = joinerPath
            )

            val modelConfig = OnlineModelConfig(
                transducer = transducerConfig,
                tokens = tokensPath,
                numThreads = NUM_THREADS
            )

            val endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, RULE1_TRAILING_SILENCE, 0.0f),
                rule2 = EndpointRule(true, RULE2_UTTERANCE_LENGTH, 0.0f),
                rule3 = EndpointRule(false, 0.0f, RULE3_MAX_UTTERANCE)
            )

            val config = OnlineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                endpointConfig = endpointConfig,
                enableEndpoint = true
            )

            recognizer = OnlineRecognizer(context.assets, config)
            initialized = true
            SecureLog.i(TAG, "✅ sherpa-onnx OnlineRecognizer 初始化成功 (v1.13.3)")
        } catch (e: Exception) {
            SecureLog.e(TAG, "❌ sherpa-onnx 模型初始化失败，请确保 assets 中有模型文件", e)
        }
    }

    /**
     * 开始录音和流式识别
     */
    fun startListening() {
        if (recordingJob?.isActive == true) {
            SecureLog.d(TAG, "录音已在进行中，忽略重复启动")
            return
        }

        val rec = recognizer
        if (rec == null) {
            SecureLog.e(TAG, "识别器未初始化，请先调用 init()")
            return
        }

        stream = rec.createStream()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuf > 0) minBuf else 1024

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                SecureLog.e(TAG, "AudioRecord 初始化失败")
                return
            }
            audioRecord = record
            record.startRecording()

            // 音频增强
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)
                    echoCanceler?.setEnabled(true)
                }
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(record.audioSessionId).setEnabled(true)
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "音频增强失败: ${e.message}")
            }

            recordingJob = scope.launch {
                recordLoop(record, bufferSize)
            }
            SecureLog.i(TAG, "🎤 开始流式录音 (${SAMPLE_RATE}Hz)")
        } catch (e: SecurityException) {
            SecureLog.e(TAG, "录音权限被拒绝", e)
        }
    }

    /**
     * 录音循环：PCM → float → acceptWaveform → decode → 端点检测
     */
    private suspend fun recordLoop(record: AudioRecord, bufferSize: Int) {
        val shorts = ShortArray(bufferSize / 2)

        while (scope.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val n = record.read(shorts, 0, shorts.size)
            if (n <= 0) continue

            val floats = FloatArray(n) { i -> shorts[i] / 32768.0f }
            val s = stream ?: continue
            s.acceptWaveform(floats, SAMPLE_RATE)

            val rec = recognizer ?: continue
            while (rec.isReady(s)) rec.decode(s)

            // 部分结果
            val result = rec.getResult(s)
            if (result != null && result.text.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    onPartialResult?.invoke(result.text)
                }
            }

            // 端点检测 → 最终结果
            if (rec.isEndpoint(s)) {
                val final = rec.getResult(s)
                val text = final?.text ?: ""
                if (text.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onFinalResult?.invoke(text)
                    }
                }
                stream = rec.createStream()
            }
        }
    }

    /**
     * 停止录音
     */
    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        stream = null
        echoCanceler?.release()
        echoCanceler = null
        SecureLog.i(TAG, "⏹️ 停止录音")
    }

    /**
     * 销毁所有资源
     */
    fun destroy() {
        stopListening()
        recognizer = null
        initialized = false
        scope.cancel()
        SecureLog.i(TAG, "💥 已销毁")
    }

    /**
     * 从 assets 复制文件到 filesDir（首次），返回绝对路径
     */
    private fun copyAsset(name: String): String {
        val target = File(context.filesDir, name)
        if (target.exists() && target.length() > 0) return target.absolutePath
        try {
            context.assets.open(name).use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            }
        } catch (_: Exception) {}
        return target.absolutePath
    }
}
