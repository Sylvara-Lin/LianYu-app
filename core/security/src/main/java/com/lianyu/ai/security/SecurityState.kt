package com.lianyu.ai.security

/** Open-source no-op security state. */
object SecurityState {
    data class Snapshot(
        val tampered: Boolean = false,
        val isTrustedForSensitiveOps: Boolean = true
    )

    fun snapshot(): Snapshot = Snapshot()
    fun resetForTest() = Unit
    fun markTampered(reason: String) = Unit
    fun markPreflightPassed(vararg ignored: Any?) = Unit
    fun markRuntimeReady(vararg ignored: Any?) = Unit
}
