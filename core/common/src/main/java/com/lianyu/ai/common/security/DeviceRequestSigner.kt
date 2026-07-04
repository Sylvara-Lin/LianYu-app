package com.lianyu.ai.common.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

object DeviceRequestSigner {
    private const val KEY_ALIAS = "lianyu_device_signing_p256_v1"
    const val SIGNATURE_ALGORITHM = "ES256"

    data class SignedRequest(
        val signature: String,
        val keyId: String,
        val deviceId: String
    )

    fun deviceId(): String {
        val material = listOf(
            Build.FINGERPRINT,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.BRAND,
        ).joinToString("|")
        return sha256Hex(material.toByteArray(Charsets.UTF_8)).take(32)
    }

    fun publicKeyBase64(): String {
        val publicKey = getOrCreateKeyPair().public
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun keyId(): String = sha256Hex(getOrCreateKeyPair().public.encoded).take(32)

    fun sign(payload: ByteArray): SignedRequest? {
        return try {
            val privateKey = getPrivateKey()
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(payload)
            SignedRequest(
                signature = Base64.encodeToString(signature.sign(), Base64.NO_WRAP),
                keyId = keyId(),
                deviceId = deviceId()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getPrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey
    }

    private fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            return generateKeyPair()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}