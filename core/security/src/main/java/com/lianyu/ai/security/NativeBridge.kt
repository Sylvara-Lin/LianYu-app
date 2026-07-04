package com.lianyu.ai.security

import android.content.Context

/** Open-source stub for the former native security bridge. */
object NativeBridge {
    var sDexClassLoader: ClassLoader? = null
    var sRealAppClass: Class<*>? = null
    fun wbAesEncrypt(data: ByteArray): ByteArray? = data
    fun encryptData(data: String): String = data
    fun decryptData(data: String): String? = data
    fun verifySignature(context: Context): Boolean = true
    fun isSafe(): Boolean = true
    fun resetGuard() = Unit
    fun getThreatScore(): Int = 0
    fun isDeviceRooted(): Boolean = false
    fun isHookDetected(): Boolean = false
    fun isEmulator(): Boolean = false
    fun isDebugged(): Boolean = false
    fun isMitmDetected(): Boolean = false
    fun isFridaDetected(): Boolean = false
    fun isHeartbeatOk(): Boolean = true
    fun antiDebugInit(): Boolean = true
    fun wbAesInit(): Boolean = true
    fun wbAesSelftest(): Boolean = true
    fun checkDexIntegrity(): Boolean = true
    fun checkSoIntegrity(): Boolean = true
    fun checkResourcesIntegrity(): Boolean = true
    fun computeIntegrityDigest(): String = "open-source"
    fun getExpectedCertSha256(): String? = null
    fun enterDeadLoop() = Unit
    fun startHeartbeat() = Unit
    fun zeroTrustEvaluate(): Int = 0
    fun zeroTrustGetState(): Int = 0
    fun wbAesObfuscateTables(seed: Long) = Unit
    fun wbAesSideChannelDefense() = Unit
    fun nativeLoadPayload(context: Context, appClassName: String): Int = 0
    fun vmpWbAesKeycheck(): Int = 1
    fun vmpTeeAttest(): Int = 1
    fun vmpApkSigVerify(): Int = 1
    fun getSecureString(index: Int): String = ""
}
