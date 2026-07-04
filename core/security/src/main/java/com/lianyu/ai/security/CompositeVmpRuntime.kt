package com.lianyu.ai.security

import android.content.Context

/** Open-source no-op replacement for the private VMP runtime. */
object CompositeVmpRuntime {
    const val OP_API_SECRET_ENCRYPT = 1
    const val OP_API_SECRET_DECRYPT = 2
    const val OP_SHELL_RECORD_STARTUP_PREFLIGHT = 3
    const val OP_SHELL_VERIFY_BEFORE_PAYLOAD = 4
    const val OP_SHELL_CREATE_PAYLOAD_LOADER = 5
    val debugMode: Boolean = true
    fun execute(op: Int, vararg args: Any?): Any? = when (op) {
        OP_API_SECRET_ENCRYPT, OP_API_SECRET_DECRYPT -> args.firstOrNull()
        else -> null
    }
    fun initialize(context: Context) = Unit
    fun verifyTrustAnchors(): Boolean = true
    fun verifyWbAesIntegrity(): Boolean = true
    fun verifyTeeAttestation(): Boolean = true
    fun verifyApkSignature(): Boolean = true
}
