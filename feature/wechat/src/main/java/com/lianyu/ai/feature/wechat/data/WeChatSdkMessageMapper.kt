package com.lianyu.ai.feature.wechat.data

import com.github.wechat.ilink.sdk.core.model.CDNMedia as SdkCdnMedia
import com.github.wechat.ilink.sdk.core.model.MessageItem as SdkMessageItem
import com.github.wechat.ilink.sdk.core.model.WeixinMessage as SdkWeixinMessage
import com.lianyu.ai.feature.wechat.data.model.M7
import com.lianyu.ai.feature.wechat.data.model.M5
import com.lianyu.ai.feature.wechat.data.model.M3
import com.lianyu.ai.feature.wechat.data.model.M1
import com.lianyu.ai.feature.wechat.data.model.M2
import com.lianyu.ai.feature.wechat.data.model.M6
import com.lianyu.ai.feature.wechat.data.model.M4
import com.lianyu.ai.feature.wechat.data.model.M0

object WeChatSdkMessageMapper {
    fun toAppMessage(message: SdkWeixinMessage): M0 {
        return M0(
            seq = message.message_id,
            messageId = message.message_id,
            fromUserId = message.from_user_id,
            toUserId = message.to_user_id,
            createTimeMs = message.create_time_ms,
            messageType = message.message_type,
            itemList = message.item_list?.map { it.toAppItem() },
            contextToken = message.context_token
        )
    }

    private fun SdkMessageItem.toAppItem(): M1 {
        return M1(
            type = type,
            textItem = text_item?.let { M2(text = it.text.orEmpty()) },
            imageItem = image_item?.let { M3(cdnImg = it.media?.toAppMedia()) },
            voiceItem = voice_item?.let { M4(cdnVoice = it.media?.toAppMedia()) },
            fileItem = file_item?.let {
                M5(
                    cdnFile = it.media?.toAppMedia(),
                    fileName = it.file_name
                )
            },
            videoItem = video_item?.let {
                M6(
                    cdnVideo = it.media?.toAppMedia(),
                    cdnThumb = it.thumb_media?.toAppMedia()
                )
            }
        )
    }

    private fun SdkCdnMedia.toAppMedia(): M7 {
        return M7(
            encryptQueryParam = encrypt_query_param,
            aesKey = aes_key
        )
    }
}
