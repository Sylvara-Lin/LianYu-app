package com.lianyu.ai.network

import com.lianyu.ai.common.SecureLog
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * NetworkLogger — 网络请求/响应日志拦截器
 *
 * 记录 API 请求的 URL、状态码、耗时和响应摘要（不含敏感信息）
 */
class NetworkLogger : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val requestId = generateRequestId()

        SecureLog.network("REQ", "[$requestId] ${request.method} ${request.url.encodedPath} host=${request.url.host}")

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            val elapsed = System.currentTimeMillis() - startTime
            SecureLog.network("ERR", "[$requestId] FAILED after ${elapsed}ms: ${e.message}")
            throw e
        }

        val elapsed = System.currentTimeMillis() - startTime
        val contentLength = response.body?.contentLength() ?: -1

        SecureLog.network("RESP", "[$requestId] HTTP ${response.code} in ${elapsed}ms, body=${contentLength}bytes")

        if (!response.isSuccessful) {
            val errorBody = response.peekBody(1024).string()
            SecureLog.network("ERR", "[$requestId] Error body: ${errorBody.take(200)}")
        }

        return response
    }

    private fun generateRequestId(): String {
        return (System.currentTimeMillis() % 10000).toString(36).uppercase()
    }
}
