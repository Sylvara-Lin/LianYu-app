package com.lianyu.ai.feature.wechat.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// App-facing message models. The wire protocol is handled by wechat-ilink-sdk.

@Serializable
@SerialName("M1")
data class M1(
    val type: Int,
    @SerialName("text_item") val textItem: M2? = null,
    @SerialName("image_item") val imageItem: M3? = null,
    @SerialName("voice_item") val voiceItem: M4? = null,
    @SerialName("file_item") val fileItem: M5? = null,
    @SerialName("video_item") val videoItem: M6? = null
)

@Serializable
@SerialName("M2")
data class M2(val text: String)

@Serializable
@SerialName("M3")
data class M3(
    @SerialName("cdn_img") val cdnImg: M7? = null
)

@Serializable
@SerialName("M4")
data class M4(
    @SerialName("cdn_voice") val cdnVoice: M7? = null
)

@Serializable
@SerialName("M5")
data class M5(
    @SerialName("cdn_file") val cdnFile: M7? = null,
    @SerialName("file_name") val fileName: String? = null
)

@Serializable
@SerialName("M6")
data class M6(
    @SerialName("cdn_video") val cdnVideo: M7? = null,
    @SerialName("cdn_thumb") val cdnThumb: M7? = null
)

@Serializable
@SerialName("M7")
data class M7(
    @SerialName("encrypt_query_param") val encryptQueryParam: String? = null,
    @SerialName("aes_key") val aesKey: String? = null
)

@Serializable
@SerialName("M0")
data class M0(
    val seq: Long? = null,
    @SerialName("message_id") val messageId: Long? = null,
    @SerialName("from_user_id") val fromUserId: String? = null,
    @SerialName("to_user_id") val toUserId: String? = null,
    @SerialName("create_time_ms") val createTimeMs: Long? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("message_type") val messageType: Int? = null,
    @SerialName("message_state") val messageState: Int? = null,
    @SerialName("item_list") val itemList: List<M1>? = null,
    @SerialName("context_token") val contextToken: String? = null
)

enum class M1Type(val value: Int) {
    TEXT(1),
    IMAGE(2),
    VOICE(3),
    FILE(4),
    VIDEO(5)
}

enum class MessageType(val value: Int) {
    USER(1),
    BOT(2)
}

enum class MessageState(val value: Int) {
    NEW(0),
    GENERATING(1),
    FINISH(2)
}
