package com.lianyu.ai.common

import android.content.Context
import org.json.JSONObject

/**
 * Open-source placeholder for the former private relay integration.
 *
 * The public build never contacts a bundled server and never returns embedded
 * credentials. Users must configure their own API keys in Settings.
 */
object RemoteKeyProvider {
    @Volatile
    var serverUrl: String = ""

    fun openSourceHandshake(ctx: Context): JSONObject = JSONObject().apply {
        put("ok", false)
        put("error", "open_source_build_requires_user_api_config")
    }

    fun getRandomModel(context: Context): String? = null
    fun clearCache(context: Context) = Unit
}
