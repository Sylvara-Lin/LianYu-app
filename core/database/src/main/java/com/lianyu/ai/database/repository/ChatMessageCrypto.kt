package com.lianyu.ai.database.repository

import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.GroupMessage
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Application-layer encryption for chat message payload fields.
 *
 * Query-critical metadata stays queryable in SQLite:
 * - timestamp for millisecond cursor reads
 * - searchContent for fuzzy search
 * - fileFormat for file category filtering
 *
 * Sensitive payload fields are encrypted at rest:
 * - content
 * - linkString (supports one or more resources encoded as a single link string)
 */
object ChatMessageCrypto {
    private const val KEYSTORE_ALIAS = "lianyu_chat_message_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val PREFIX = "enc:v1:"

    /** Primary key from AndroidKeyStore (TEE-backed, AES-256-GCM) */
    private val keyStoreKey: SecretKey? by lazy {
        runCatching { getOrCreateAndroidKeyStoreKey() }.getOrNull()
    }

    /** Current encryption key. Refuse storage when AndroidKeyStore is unavailable. */
    private val encryptionKey: SecretKey
        get() = keyStoreKey ?: error("AndroidKeyStore chat message key unavailable")

    private val legacyFallbackKey: SecretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("lianyu-chat-message-storage-v1".toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    /** KeyStore first, legacy static key only for pre-existing encrypted rows. */
    private val decryptionKeys: List<SecretKey>
        get() = listOfNotNull(keyStoreKey) + legacyFallbackKey

    // --- Public API (mirrors C0 interface for drop-in replacement) ---

    fun encryptForStorage(message: ChatMessage): ChatMessage {
        return try {
            message.copy(
                content = encrypt(message.content),
                searchContent = message.searchContent.ifBlank { message.content },
                linkString = encrypt(message.linkString)
            )
        } catch (e: Exception) {
            android.util.Log.e("ChatMessageCrypto", "encryptForStorage failed: ${e.message}", e)
            throw e
        }
    }

    fun decryptFromStorage(message: ChatMessage): ChatMessage {
        return try {
            message.copy(
                content = decrypt(message.content),
                linkString = decrypt(message.linkString)
            )
        } catch (e: Exception) {
            message.copy(
                content = DECRYPT_FAILED_PLACEHOLDER,
                linkString = ""
            )
        }
    }

    fun encryptForStorage(message: GroupMessage): GroupMessage {
        return message.copy(
            content = encrypt(message.content),
            searchContent = message.searchContent.ifBlank { message.content },
            linkString = encrypt(message.linkString)
        )
    }

    fun decryptFromStorage(message: GroupMessage): GroupMessage {
        return message.copy(
            content = decrypt(message.content),
            linkString = decrypt(message.linkString)
        )
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return plaintext
        if (plaintext.startsWith(PREFIX)) return plaintext
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()
        return PREFIX + Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(value: String): String {
        if (value.isEmpty() || !value.startsWith(PREFIX)) return value
        val combined = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        // Try each key: KeyStore first, then legacy fallback (backward compat)
        for (key in decryptionKeys) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (_: Exception) {
                // Try next key
            }
        }
        // If all keys fail, throw so callers detect failure instead of passing garbage
        throw javax.crypto.AEADBadTagException("ChatMessage decrypt failed: no key matched")
    }

    // --- KeyStore helpers ---

    private fun getOrCreateAndroidKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        generator.generateKey()
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    const val DECRYPT_FAILED_PLACEHOLDER = "[消息解密失败]"
}

@kotlin.jvm.JvmName("filterDecryptedChat")
fun List<com.lianyu.ai.database.model.ChatMessage>.filterDecrypted(): List<com.lianyu.ai.database.model.ChatMessage> =
    filter { it.content != ChatMessageCrypto.DECRYPT_FAILED_PLACEHOLDER }

@kotlin.jvm.JvmName("filterDecryptedGroup")
fun List<com.lianyu.ai.database.model.GroupMessage>.filterDecrypted(): List<com.lianyu.ai.database.model.GroupMessage> =
    filter { it.content != ChatMessageCrypto.DECRYPT_FAILED_PLACEHOLDER }
