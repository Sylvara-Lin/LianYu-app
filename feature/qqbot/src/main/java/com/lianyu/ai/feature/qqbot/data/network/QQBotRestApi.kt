package com.lianyu.ai.feature.qqbot.data.network

import com.lianyu.ai.feature.qqbot.data.model.GatewayResponse
import com.lianyu.ai.feature.qqbot.data.model.SendMediaRequest
import com.lianyu.ai.feature.qqbot.data.model.SendMessageResponse
import com.lianyu.ai.feature.qqbot.data.model.SendTextRequest
import com.lianyu.ai.feature.qqbot.data.model.UploadFileRequest
import com.lianyu.ai.feature.qqbot.data.model.UploadFileResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface QQBotRestApi {
    @GET("gateway")
    suspend fun getGateway(): Response<GatewayResponse>

    @POST("v2/users/{openid}/messages")
    suspend fun sendC2CMessage(
        @Path("openid") openid: String,
        @Body request: SendTextRequest
    ): Response<SendMessageResponse>

    @POST("v2/groups/{group_openid}/messages")
    suspend fun sendGroupMessage(
        @Path("group_openid") groupOpenid: String,
        @Body request: SendTextRequest
    ): Response<SendMessageResponse>

    @POST("v2/channels/{channel_id}/messages")
    suspend fun sendChannelMessage(
        @Path("channel_id") channelId: String,
        @Body request: SendTextRequest
    ): Response<SendMessageResponse>

    @POST("v2/dms/{guild_id}/messages")
    suspend fun sendDirectMessage(
        @Path("guild_id") guildId: String,
        @Body request: SendTextRequest
    ): Response<SendMessageResponse>

    @POST("v2/users/{openid}/files")
    suspend fun uploadC2CFile(
        @Path("openid") openid: String,
        @Body request: UploadFileRequest
    ): Response<UploadFileResponse>

    @POST("v2/groups/{group_openid}/files")
    suspend fun uploadGroupFile(
        @Path("group_openid") groupOpenid: String,
        @Body request: UploadFileRequest
    ): Response<UploadFileResponse>

    @POST("v2/users/{openid}/messages")
    suspend fun sendC2CMediaMessage(
        @Path("openid") openid: String,
        @Body request: SendMediaRequest
    ): Response<SendMessageResponse>

    @POST("v2/groups/{group_openid}/messages")
    suspend fun sendGroupMediaMessage(
        @Path("group_openid") groupOpenid: String,
        @Body request: SendMediaRequest
    ): Response<SendMessageResponse>
}
