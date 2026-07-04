package com.lianyu.ai.network

import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Open-source request interceptor.
 *
 * Private request signing, native white-box crypto, and server-bound integrity
 * checks are not included in the public edition. The class remains as a small
 * compatibility hook and TLS helper for forks that want to add their own policy.
 */
class RequestSecurityInterceptor(
    private val shouldSignRequest: (okhttp3.Request) -> Boolean = { false }
) : Interceptor {
    companion object {
        fun enforceTls(builder: OkHttpClient.Builder) {
            try {
                val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                    .tlsVersions(okhttp3.TlsVersion.TLS_1_2, okhttp3.TlsVersion.TLS_1_3)
                    .build()
                builder.connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
            } catch (_: Exception) {
                // Keep platform defaults if restricted TLS is unavailable.
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
