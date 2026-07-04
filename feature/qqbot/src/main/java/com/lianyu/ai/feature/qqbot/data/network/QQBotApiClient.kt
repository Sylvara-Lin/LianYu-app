package com.lianyu.ai.feature.qqbot.data.network

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lianyu.ai.feature.qqbot.data.QQBotTokenStore
import com.lianyu.ai.feature.qqbot.data.model.AccessTokenResponse
import com.lianyu.ai.feature.qqbot.data.model.QQBotAccount
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class QQBotApiClient(private val tokenStore: QQBotTokenStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val tokenMutex = Mutex()

    private val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // [PERF] 移除 BODY 日志，避免高频消息时大量 I/O 拖慢请求
            .build()
    }

    val authApi: QQBotAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(baseOkHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(QQBotAuthApi::class.java)
    }

    // [PERF] 缓存已认证的 RestApi，避免每条消息都重建 Retrofit/OkHttp
    @Volatile
    private var cachedRestApi: QQBotRestApi? = null
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var cachedAccount: QQBotAccount? = null
    @Volatile
    private var cachedTokenExpireAt: Long = 0L

    suspend fun getOrRefreshToken(account: QQBotAccount): String {
        val cached = tokenStore.getAccessToken()
        val expireAt = tokenStore.getTokenExpireAt()
        if (!cached.isNullOrBlank() && System.currentTimeMillis() < expireAt - TOKEN_REFRESH_MARGIN_MS) {
            return cached
        }
        return tokenMutex.withLock {
            val doubleCheck = tokenStore.getAccessToken()
            val doubleCheckExpire = tokenStore.getTokenExpireAt()
            if (!doubleCheck.isNullOrBlank() && System.currentTimeMillis() < doubleCheckExpire - TOKEN_REFRESH_MARGIN_MS) {
                return@withLock doubleCheck
            }
            val response = authApi.getAppAccessToken(
                TokenRequest(appId = account.appId, clientSecret = account.clientSecret)
            )
            if (!response.isSuccessful || response.body() == null) {
                throw IllegalStateException("获取 QQ Bot AccessToken 失败: ${response.code()} ${response.errorBody()?.string()}")
            }
            val body = response.body()!!
            tokenStore.setAccessToken(body.accessToken)
            tokenStore.setTokenExpireAt(System.currentTimeMillis() + body.expiresIn * 1000)
            // token 刷新后清除 API 缓存，让下一次调用重建
            clearApiCache()
            body.accessToken
        }
    }

    suspend fun createAuthenticatedRestApi(): QQBotRestApi {
        val account = tokenStore.getAccount() ?: throw IllegalStateException("未配置 QQ Bot 账号")
        val cachedAccountLocal = cachedAccount
        val cachedTokenLocal = cachedToken
        val cachedApiLocal = cachedRestApi
        if (cachedAccountLocal == account && cachedTokenLocal != null && cachedApiLocal != null) {
            if (System.currentTimeMillis() < cachedTokenExpireAt - TOKEN_REFRESH_MARGIN_MS) {
                return cachedApiLocal
            }
        }
        val token = getOrRefreshToken(account)
        val expireAt = tokenStore.getTokenExpireAt()
        val api = buildRestApi(account, token)
        cachedAccount = account
        cachedToken = token
        cachedTokenExpireAt = expireAt
        cachedRestApi = api
        return api
    }

    fun clearApiCache() {
        cachedRestApi = null
        cachedToken = null
        cachedAccount = null
        cachedTokenExpireAt = 0L
    }

    /**
     * 返回当前缓存的 access token（不含 QQBot 前缀）。
     * 仅用于 WebSocket identify/resume，避免在回调线程读 DataStore。
     */
    fun getCachedToken(): String? {
        val token = cachedToken ?: return null
        if (token.isBlank() || System.currentTimeMillis() >= cachedTokenExpireAt - TOKEN_REFRESH_MARGIN_MS) return null
        return token
    }

    private fun buildRestApi(account: QQBotAccount, token: String): QQBotRestApi {
        val client = baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "QQBot $token")
                    .header("X-Union-Appid", account.appId)
                    .build()
                chain.proceed(request)
            }
            // 不设置 authenticator：token 过期由 createAuthenticatedRestApi() 预判刷新兜底，
            // 避免 runBlocking 阻塞 OkHttp dispatcher 线程（见 Bug #3 次因）。
            .build()

        return Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(QQBotRestApi::class.java)
    }

    private suspend fun refreshToken(account: QQBotAccount): String {
        val response = authApi.getAppAccessToken(
            TokenRequest(appId = account.appId, clientSecret = account.clientSecret)
        )
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("刷新 QQ Bot AccessToken 失败: ${response.code()}")
        }
        val body = response.body()!!
        tokenStore.setAccessToken(body.accessToken)
        tokenStore.setTokenExpireAt(System.currentTimeMillis() + body.expiresIn * 1000)
        clearApiCache()
        return body.accessToken
    }

    private val okhttp3.Response.responseCount: Int
        get() {
            var count = 1
            var prior = this.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }

    companion object {
        private const val AUTH_BASE_URL = "https://bots.qq.com/"
        private const val API_BASE_URL = "https://api.sgroup.qq.com/"
        private const val TOKEN_REFRESH_MARGIN_MS = 60_000L
    }
}
