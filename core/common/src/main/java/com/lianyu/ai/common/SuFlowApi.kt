package com.lianyu.ai.common

/**
 * Open-source API placeholders.
 *
 * The public edition does not ship with a built-in relay, private server,
 * provision endpoint, or embedded API credentials. Users should configure an
 * OpenAI-compatible endpoint in Settings.
 */
object SuFlowApi {
    const val BASE_URL = ""
    const val AUTH_BASE_URL = ""
    const val CHAT_BASE_URL = ""
    const val CHAT_PATH = "/chat/completions"
    const val MODELS_PATH = "/models"
    const val USAGE_PATH = "/usage"
    const val HANDSHAKE_PATH = ""
    const val KEYS_FETCH_PATH = ""
    const val OPEN_SOURCE_PROVISION = ""
    const val OPEN_SOURCE_KEY_FETCH = ""
    const val TIMEOUT_SECONDS = 30L
    const val TEST_MODEL = "gpt-4o-mini"

    fun deviceFingerprint(): String {
        val fp = android.os.Build.FINGERPRINT + "|" +
            android.os.Build.MODEL + "|" +
            android.os.Build.SERIAL
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(fp.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}
