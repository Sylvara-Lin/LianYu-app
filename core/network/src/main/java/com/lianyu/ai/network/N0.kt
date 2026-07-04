package com.lianyu.ai.network

import okhttp3.Interceptor
import okhttp3.Response

/** Open-source compatibility alias for the former native request signer. */
class N0 : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
