package com.lianyu.ai.feature.qqbot.data.network

import com.lianyu.ai.feature.qqbot.data.model.AccessTokenResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface QQBotAuthApi {
    @POST("app/getAppAccessToken")
    suspend fun getAppAccessToken(@Body request: TokenRequest): Response<AccessTokenResponse>
}

@Serializable
data class TokenRequest(
    val appId: String,
    @SerialName("clientSecret") val clientSecret: String
)
