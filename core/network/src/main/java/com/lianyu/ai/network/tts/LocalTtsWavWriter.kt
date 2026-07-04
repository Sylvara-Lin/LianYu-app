package com.lianyu.ai.network.tts

import java.io.File
import java.io.RandomAccessFile

/**
 * WAV 编码器：将 sherpa-onnx [GeneratedAudio.samples] (FloatArray, PCM -1..1)
 * 量化为 16-bit PCM 并写入标准 WAV 文件。
 *
 * 纯函数 object，无外部依赖，无状态。
 * 手写 44 字节 WAV 头 + 小端 PCM 数据。
 *
 * @param samples 归一化浮点样本（-1.0..1.0）
 * @param sampleRate 采样率（如 22050）
 * @param out 输出 .wav 文件
 */
object LocalTtsWavWriter {

    fun writePcmToWav(samples: FloatArray, sampleRate: Int, out: File) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = samples.size * 2
        val chunkSize = 36 + dataSize

        out.parentFile?.mkdirs()
        val raf = RandomAccessFile(out, "rw")
        try {
            raf.setLength(0)
            // RIFF header
            raf.writeBytes("RIFF")
            raf.writeLittleEndianInt(chunkSize)
            raf.writeBytes("WAVE")
            // fmt chunk
            raf.writeBytes("fmt ")
            raf.writeLittleEndianInt(16)           // PCM
            raf.writeLittleEndianShort(1)          // audioFormat = PCM
            raf.writeLittleEndianShort(numChannels.toShort())
            raf.writeLittleEndianInt(sampleRate)
            raf.writeLittleEndianInt(byteRate)
            raf.writeLittleEndianShort(blockAlign.toShort())
            raf.writeLittleEndianShort(bitsPerSample.toShort())
            // data chunk
            raf.writeBytes("data")
            raf.writeLittleEndianInt(dataSize)
            // PCM samples (float → int16)
            val buffer = ByteArray(8192)
            var bufferPos = 0
            for (sample in samples) {
                // Clamp + quantize to int16
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                val intSample = (clamped * 32767.0f).toInt()
                if (bufferPos + 2 > buffer.size) {
                    raf.write(buffer, 0, bufferPos)
                    bufferPos = 0
                }
                buffer[bufferPos++] = (intSample and 0xFF).toByte()
                buffer[bufferPos++] = ((intSample shr 8) and 0xFF).toByte()
            }
            if (bufferPos > 0) raf.write(buffer, 0, bufferPos)
        } finally {
            raf.close()
        }
    }

    private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun RandomAccessFile.writeLittleEndianShort(value: Short) {
        write(value.toInt() and 0xFF)
        write((value.toInt() shr 8) and 0xFF)
    }
}
