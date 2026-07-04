package com.lianyu.ai.feature.qqbot.data.network

import com.lianyu.ai.network.NetworkConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * QQ 开放平台 Lite 扫码绑定 API（Hermes 协议）。
 *
 * 流程：
 * 1. [createBindTask] 生成 AES key → POST 创建一次性 task → 拿到 task_id
 * 2. 将 task_id 拼成扫码 URL，生成二维码展示给用户
 * 3. [pollBindResult] 每 2 秒轮询一次，直到 status=2（完成）或 status=3（过期）
 * 4. 拿到 bot_encrypt_secret 后用本地 AES key 解密，得到 client_secret
 *
 * 参考：E:\hermes-agent-new\docs\qqbot-bind.md
 */
interface QQBotLiteBindApi {

    @POST("lite/create_bind_task")
    @Headers("Accept: application/json")
    suspend fun createBindTask(@Body request: CreateBindTaskRequest): Response<CreateBindTaskResponse>

    @POST("lite/poll_bind_result")
    @Headers("Accept: application/json")
    suspend fun pollBindResult(@Body request: PollBindResultRequest): Response<PollBindResultResponse>
}

@Serializable
data class CreateBindTaskRequest(
    val key: String
)

@Serializable
data class CreateBindTaskResponse(
    val retcode: Int = 0,
    val msg: String? = null,
    val data: CreateBindTaskData? = null
)

@Serializable
data class CreateBindTaskData(
    @SerialName("task_id") val taskId: String
)

@Serializable
data class PollBindResultRequest(
    @SerialName("task_id") val taskId: String
)

@Serializable
data class PollBindResultResponse(
    val retcode: Int = 0,
    val msg: String? = null,
    val data: PollBindResultData? = null
)

@Serializable
data class PollBindResultData(
    val status: Int = 0,
    @SerialName("bot_appid") val botAppId: String? = null,
    @SerialName("bot_encrypt_secret") val botEncryptSecret: String? = null,
    @SerialName("user_openid") val userOpenid: String? = null
)

enum class BindStatus(val value: Int) {
    NONE(0),
    PENDING(1),
    COMPLETED(2),
    EXPIRED(3)
}
