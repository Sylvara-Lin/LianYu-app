package com.lianyu.ai.network

import com.lianyu.ai.common.SecureLog

/**
 * ImageHelper — 图片处理纯函数集合。
 * 从 AiService 解耦, 无状态依赖。
 */
object ImageHelper {

    /**
     * 将图片文件编码为 Base64 字符串。
     * @throws Exception 文件不存在或为空时抛出
     */
    fun encodeImageToBase64(imagePath: String): String {
        return try {
            val file = java.io.File(imagePath)
            if (!file.exists()) throw Exception("图片文件不存在: $imagePath")

            val bytes = file.readBytes()
            if (bytes.isEmpty()) throw Exception("图片文件为空")

            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            SecureLog.e("ImageHelper", "encodeImageToBase64 failed", e)
            throw Exception("图片编码失败: ${e.message}")
        }
    }

    /**
     * 根据文件扩展名获取 MIME 类型。
     */
    fun getImageMimeType(imagePath: String): String {
        return when (imagePath.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
