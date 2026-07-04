package com.lianyu.ai.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Open-source placeholder for the former private relay key provider.
 *
 * The public build never contacts a bundled server and never returns embedded
 * credentials. Keep this object only so existing settings/network call sites can
 * compile while users migrate to their own API configuration.
 */
object RemoteKeyProvider {
    data class PartnerSession(
        val clientId: String,
        val token: String,
        val sessionKey: String?
    )

    @Volatile
    var serverUrl: String = ""

    fun openSourceHandshake(ctx: Context): JSONObject = JSONObject().apply {
        put("ok", false)
        put("error", "open_source_build_requires_user_api_config")
    }

        fun storeHandshakeResult(ctx: Context, clientId: String, secret: String) = Unit
    fun storeHandshakeResult(ctx: Context, handshake: JSONObject): Boolean = false
    fun storeSessionResult(ctx: Context, clientId: String, sessionToken: String, sessionKey: String?) = Unit
    fun getPartnerKeys(context: Context): List<String> = emptyList()

    suspend fun fetchKeysAsync(context: Context, forceRefresh: Boolean = false): List<String> =
        withContext(Dispatchers.IO) { emptyList() }

    suspend fun ensureSession(context: Context, forceRefresh: Boolean = false): PartnerSession? =
        withContext(Dispatchers.IO) { null }

    fun getPartnerSession(context: Context): PartnerSession? = null
    fun getRandomModel(context: Context): String? = null
    fun clearCache(context: Context) = Unit
}
