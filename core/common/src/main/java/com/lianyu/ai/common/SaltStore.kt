package com.lianyu.ai.common

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * SaltStore — Runtime-derived cryptographic salts for data integrity verification.
 *
 * Replaces compile-time hardcoded salts with per-namespace salts that are:
 * - Generated on first use from SHA-256(packageName + namespace + SecureRandom.nextBytes(16))
 * - Persisted to SharedPreferences (runtime-only, not in DEX)
 * - Unique per device install (SecureRandom seed)
 *
 * Usage:
 *   // In Application.onCreate():
 *   SaltStore.init(applicationContext)
 *
 *   // In repository code:
 *   val salt = SaltStore.getSalt("quiz")  // or "keyword"
 */
object SaltStore {

    private const val PREFS_NAME = "lianyu_salt_store"
    private const val KEY_PREFIX = "runtimesalt_"

    @Volatile
    private var context: Context? = null

    private val saltCache = ConcurrentHashMap<String, String>()

    /**
     * Initialize the SaltStore with an application context.
     * Must be called before any salt retrieval.
     */
    @JvmStatic
    fun init(appContext: Context) {
        context = appContext.applicationContext
    }

    /**
     * Get the runtime-derived salt for a given namespace.
     * Generated on first access, cached in memory and persisted to SharedPreferences.
     */
    @JvmStatic
    fun getSalt(namespace: String): String {
        val ctx = context
            ?: throw IllegalStateException("SaltStore.init(context) must be called before getSalt()")

        return saltCache.getOrPut(namespace) {
            val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_PREFIX + namespace, null) ?: run {
                val random = SecureRandom()
                val randomBytes = ByteArray(16)
                random.nextBytes(randomBytes)
                val seed = "${ctx.packageName}:${namespace}:${randomBytes.joinToString("") { "%02x".format(it) }}"
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(seed.toByteArray(Charsets.UTF_8))
                val hash = hashBytes.joinToString("") { "%02x".format(it) }
                prefs.edit().putString(KEY_PREFIX + namespace, hash).apply()
                hash
            }
        }
    }

    fun shutdown() {
        saltCache.clear()
        context = null
    }
}
