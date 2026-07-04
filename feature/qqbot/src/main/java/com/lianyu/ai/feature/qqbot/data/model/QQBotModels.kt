package com.lianyu.ai.feature.qqbot.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * QQ 官方 Bot API v2 数据模型。
 *
 * 参考 Hermes gateway/platforms/qqbot/adapter.py 中的字段命名，
 * 与 QQ 开放平台文档保持一致。
 */

@Serializable
data class QQBotAccount(
    val appId: String,
    val clientSecret: String,
    val customName: String? = null
)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long
)

@Serializable
data class GatewayResponse(
    val url: String
)

@Serializable
data class QQGatewayPayload(
    val op: Int,
    val d: kotlinx.serialization.json.JsonElement? = null,
    val s: Int? = null,
    val t: String? = null
)

@Serializable
data class QQHelloData(
    @SerialName("heartbeat_interval") val heartbeatInterval: Long
)

@Serializable
data class QQReadyData(
    val version: Int? = null,
    @SerialName("session_id") val sessionId: String,
    val user: QQUser? = null,
    val shard: List<Int>? = null
)

@Serializable
data class QQUser(
    val id: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    @SerialName("bot_status") val botStatus: Int? = null
)

@Serializable
data class QQMessageAuthor(
    @SerialName("user_openid") val userOpenid: String? = null,
    @SerialName("member_openid") val memberOpenid: String? = null,
    val id: String? = null,
    val username: String? = null
)

@Serializable
data class QQMessageAttachment(
    @SerialName("content_type") val contentType: String? = null,
    val filename: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    val url: String? = null,
    @SerialName("size") val sizeBytes: Long? = null
)

@Serializable
data class QQMessageEvent(
    val id: String,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("guild_id") val guildId: String? = null,
    @SerialName("group_openid") val groupOpenid: String? = null,
    val author: QQMessageAuthor? = null,
    val content: String? = null,
    val timestamp: String? = null,
    @SerialName("message_type") val messageType: Int? = null,
    val attachments: List<QQMessageAttachment>? = null,
    val member: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class SendTextRequest(
    val content: String? = null,
    val markdown: QQMarkdown? = null,
    @SerialName("msg_type") val msgType: Int = 0,
    @SerialName("msg_id") val msgId: String? = null,
    @SerialName("msg_seq") val msgSeq: Int = 0,
    @SerialName("message_reference") val messageReference: QQMessageReference? = null
)

@Serializable
data class QQMarkdown(
    val content: String
)

@Serializable
data class QQMessageReference(
    @SerialName("message_id") val messageId: String
)

@Serializable
data class SendMessageResponse(
    val id: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("guild_id") val guildId: String? = null,
    @SerialName("group_openid") val groupOpenid: String? = null,
    val content: String? = null,
    val timestamp: String? = null
)

@Serializable
data class SendMediaRequest(
    @SerialName("msg_type") val msgType: Int = 7,
    @SerialName("msg_id") val msgId: String? = null,
    @SerialName("msg_seq") val msgSeq: Int = 0,
    val content: String? = null,
    val media: QQMediaInfo? = null
)

@Serializable
data class QQMediaInfo(
    @SerialName("file_info") val fileInfo: String
)

@Serializable
data class UploadFileRequest(
    @SerialName("file_type") val fileType: Int,
    val url: String? = null,
    @SerialName("file_data") val fileData: String? = null,
    @SerialName("srv_send_msg") val srvSendMsg: Boolean = false,
    @SerialName("file_name") val fileName: String? = null
)

@Serializable
data class UploadFileResponse(
    @SerialName("file_info") val fileInfo: String? = null
)

enum class QQMessageType(val value: Int) {
    TEXT(0),
    MARKDOWN(2),
    INPUT_NOTIFY(6),
    MEDIA(7)
}

enum class QQMediaFileType(val value: Int) {
    IMAGE(1),
    VIDEO(2),
    VOICE(3),
    FILE(4)
}

sealed class QQInboundEvent {
    abstract val raw: QQMessageEvent

    data class C2CMessage(
        val userOpenid: String,
        override val raw: QQMessageEvent
    ) : QQInboundEvent()

    data class GroupAtMessage(
        val groupOpenid: String,
        val memberOpenid: String,
        override val raw: QQMessageEvent
    ) : QQInboundEvent()

    data class GuildMessage(
        val channelId: String,
        val guildId: String?,
        val authorId: String,
        override val raw: QQMessageEvent
    ) : QQInboundEvent()

    data class DirectMessage(
        val guildId: String,
        val authorId: String,
        override val raw: QQMessageEvent
    ) : QQInboundEvent()
}
