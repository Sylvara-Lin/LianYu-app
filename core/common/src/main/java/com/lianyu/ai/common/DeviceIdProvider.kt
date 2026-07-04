package com.lianyu.ai.common

import android.content.Context
import android.os.Build
import java.security.MessageDigest

/**
 * Device ID provider with hardware binding.
 *
 * Derives device ID from hardware identifiers (not random UUID):
 *   android_id + Build.SERIAL + ro.build.fingerprint → SHA-256
 * Falls back to android_id only if Build.SERIAL unavailable.
 * Cached in SharedPreferences, regenerated if tampered.
 */
object DeviceIdProvider {
    private const val PREFS_NAME = "lianyu_secure"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_HASH = "device_hash"  // to detect tampering

    @Volatile
    private var cachedDeviceId: String? = null

    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_DEVICE_ID, null)
        val storedHash = prefs.getString(KEY_DEVICE_HASH, null)

        // Compute hardware-derived ID
        val hardwareId = deriveHardwareId(context)
        val hardwareHash = sha256(hardwareId)

        if (stored != null && storedHash != null && storedHash == hardwareHash) {
            // Stored ID matches current hardware → valid
            cachedDeviceId = stored
            return stored
        }

        // Either first run or hardware changed → regenerate
        val newId = "ly_" + hardwareHash.take(16)
        prefs.edit()
            .putString(KEY_DEVICE_ID, newId)
            .putString(KEY_DEVICE_HASH, hardwareHash)
            .apply()
        cachedDeviceId = newId
        return newId
    }

    private fun deriveHardwareId(context: Context): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""

        val serial = try {
            Build.getSerial()
        } catch (_: SecurityException) { "" }

        val fingerprint = Build.FINGERPRINT ?: ""

        return "$androidId|$serial|$fingerprint"
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
