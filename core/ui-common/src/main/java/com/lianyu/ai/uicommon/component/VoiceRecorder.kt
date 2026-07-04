package com.lianyu.ai.uicommon.component

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    var isRecording = false
        private set

    fun start(): String? {
        return try {
            stop()
            val outputDir = File(context.cacheDir, "voice_messages")
            outputDir.mkdirs()
            outputFile = File(outputDir, "voice_${System.currentTimeMillis()}.m4a")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            outputFile?.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun stop(): String? {
        if (!isRecording || mediaRecorder == null) return outputFile?.absolutePath
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            outputFile?.absolutePath
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            outputFile?.takeIf { it.exists() }?.delete()
            null
        }
    }

    fun cancel() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        outputFile?.let { if (it.exists()) it.delete() }
        outputFile = null
    }

    fun getDurationMs(): Int {
        val file = outputFile ?: return 0
        if (!file.exists() || file.length() == 0L) return 0
        val player = android.media.MediaPlayer()
        return try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            val duration = player.duration
            player.release()
            duration
        } catch (e: Exception) {
            try { player.release() } catch (_: Exception) {}
            0
        }
    }

    companion object {
        @Volatile
        private var instance: VoiceRecorder? = null

        fun getInstance(context: Context): VoiceRecorder {
            return instance ?: synchronized(this) {
                instance ?: VoiceRecorder(context.applicationContext).also { instance = it }
            }
        }
    }
}
