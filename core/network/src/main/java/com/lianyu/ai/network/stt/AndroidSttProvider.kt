package com.lianyu.ai.network.stt

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.SpeechRecognizer.ERROR_NO_MATCH
import com.lianyu.ai.common.SecureLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AndroidSttProvider : SttProviderInterface {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isInit = false
    private val initLock = Any()

    override suspend fun recognize(context: Context, audioPath: String): String? {
        return withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                SecureLog.e("AndroidSttProvider", "Speech recognition not available on this device")
                return@withContext null
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context) ?: run {
                SecureLog.e("AndroidSttProvider", "Failed to create SpeechRecognizer")
                return@withContext null
            }

            val deferredResult = CompletableDeferred<String?>()
            val finalResults = StringBuilder()

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    SecureLog.d("AndroidSttProvider", "Ready for speech")
                    playAudioFile(context, audioPath)
                }

                override fun onBeginningOfSpeech() {
                    SecureLog.d("AndroidSttProvider", "Beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    SecureLog.d("AndroidSttProvider", "End of speech")
                }

                override fun onError(error: Int) {
                    SecureLog.e("AndroidSttProvider", "Recognition error: $error")
                    if (error == ERROR_NO_MATCH && finalResults.isNotEmpty()) {
                        deferredResult.complete(finalResults.toString().trim())
                    } else {
                        deferredResult.complete(null)
                    }
                    recognizer.destroy()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        SecureLog.i("AndroidSttProvider", "Recognition result: $text")
                        finalResults.append(text).append(" ")
                    }
                    deferredResult.complete(finalResults.toString().trim())
                    recognizer.destroy()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        SecureLog.d("AndroidSttProvider", "Partial result: ${matches[0]}")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            recognizer.startListening(intent)

            try {
                deferredResult.await()
            } catch (e: Exception) {
                SecureLog.e("AndroidSttProvider", "Recognition await error", e)
                recognizer.destroy()
                null
            }
        }
    }

    override suspend fun recognizeFromFile(context: Context, audioPath: String, mimeType: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val audioFile = File(audioPath)
                if (!audioFile.exists()) {
                    SecureLog.e("AndroidSttProvider", "Audio file not found: $audioPath")
                    return@withContext null
                }

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    SecureLog.w("AndroidSttProvider", "Speech recognition not available, trying file-based approach")
                    return@withContext null
                }

                withContext(Dispatchers.Main) {
                    recognize(context, audioPath)
                }
            } catch (e: Exception) {
                SecureLog.e("AndroidSttProvider", "File-based recognition failed", e)
                null
            }
        }
    }

    override suspend fun testConnection(): Boolean = true

    fun isInitialized(): Boolean = isInit

    fun initialize(context: Context): Boolean {
        synchronized(initLock) {
            if (isInit) return true
            return try {
                isInit = SpeechRecognizer.isRecognitionAvailable(context)
                if (isInit) {
                    SecureLog.i("AndroidSttProvider", "SpeechRecognizer initialized successfully")
                } else {
                    SecureLog.w("AndroidSttProvider", "SpeechRecognizer not available on this device")
                }
                isInit
            } catch (e: Exception) {
                SecureLog.e("AndroidSttProvider", "Initialize failed", e)
                false
            }
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun playAudioFile(context: Context, audioPath: String) {
        try {
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioPath)
                prepare()
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ -> release(); true }
                start()
            }
        } catch (e: Exception) {
            SecureLog.e("AndroidSttProvider", "Failed to play audio file for recognition", e)
        }
    }

    companion object {
        const val PROVIDER_ID = "android_stt"
        const val RECOGNITION_TIMEOUT_MS = 15000L
    }
}
