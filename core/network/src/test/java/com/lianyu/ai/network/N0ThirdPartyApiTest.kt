package com.lianyu.ai.network

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class N0ThirdPartyApiTest {

    @Test
    fun intercept_doesNotAttachInternalSecurityHeadersToThirdPartyModelApi() {
        val interceptor = N0(
            signer = N0.Signer { "abc123" }
        )
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .get()
            .build()
        val chain = RecordingChain(request)

        interceptor.intercept(chain)

        assertTrue(chain.proceeded)
        val proceeded = chain.proceededRequest
        assertNull(proceeded?.header("X-LianYu-Sig"))
        assertNull(proceeded?.header("X-LianYu-Ts"))
        assertNull(proceeded?.header("X-LianYu-Nonce"))
        assertNull(proceeded?.header("X-LianYu-Client"))
    }

    @Test
    fun intercept_keepsInternalSecurityHeadersForLianYuBackend() {
        val interceptor = N0(
            signer = N0.Signer { "abc123" }
        )
        val request = Request.Builder()
            .url("https://api.lianyu.app/v1/sync")
            .get()
            .build()
        val chain = RecordingChain(request)

        interceptor.intercept(chain)

        assertTrue(chain.proceeded)
        assertEquals("abc123", chain.proceededRequest?.header("X-LianYu-Sig"))
    }

    private class RecordingChain(
        private val request: Request
    ) : Interceptor.Chain {
        var proceeded: Boolean = false
            private set
        var proceededRequest: Request? = null
            private set

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            proceeded = true
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        override fun connection(): okhttp3.Connection? = null
        override fun call(): okhttp3.Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 0
        override fun readTimeoutMillis(): Int = 0
        override fun writeTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
    }
}
