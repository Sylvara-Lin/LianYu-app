package com.lianyu.ai.feature.qqbot.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 加解密工具，用于 QQ Bot 扫码绑定流程。
 *
 * 密钥在本地生成，仅 base64 形式传给 QQ 服务器用于加密返回的 client_secret。
 * 服务器无法解密，secret 不在网络明文传输。
 */
object AESGCMHelper {

    private const val AES_KEY_SIZE = 32          // 256 bit
    private const val GCM_IV_LENGTH = 12         // 96 bit
    private const val GCM_TAG_LENGTH = 128       // 128 bit

    /**
     * 生成 32 字节随机密钥，并返回 base64 编码字符串。
     */
    fun generateKey(): String {
        val key = ByteArray(AES_KEY_SIZE)
        SecureRandom().nextBytes(key)
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /**
     * 解密 QQ 服务器返回的加密 secret。
     *
     * @param encryptedBase64 base64 编码的密文（前 12 字节为 IV，剩余为 ciphertext + auth tag）
     * @param keyBase64 本地生成的 AES 密钥（base64 编码）
     * @return 解密后的明文 client_secret
     */
    fun decrypt(encryptedBase64: String, keyBase64: String): String {
        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        val raw = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        require(raw.size >= GCM_IV_LENGTH) { "密文太短，无法提取 IV" }

        val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = raw.copyOfRange(GCM_IV_LENGTH, raw.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}
