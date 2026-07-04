package com.lianyu.ai.feature.wechat.data

import com.github.wechat.ilink.sdk.core.model.MessageItem as SdkMessageItem
import com.github.wechat.ilink.sdk.core.model.WeixinMessage as SdkWeixinMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatSdkMessageMapperTest {

    @Test
    fun mapsSdkTextMessageToAppMessageModel() {
        val sdkMessage = SdkWeixinMessage().apply {
            message_id = 42L
            message_type = 1
            from_user_id = "user@im.wechat"
            to_user_id = "bot@im.bot"
            create_time_ms = 123456789L
            context_token = "context-token"
            item_list = listOf(SdkMessageItem.text("hello from sdk"))
        }

        val message = WeChatSdkMessageMapper.toAppMessage(sdkMessage)

        assertEquals(42L, message.messageId)
        assertEquals(1, message.messageType)
        assertEquals("user@im.wechat", message.fromUserId)
        assertEquals("bot@im.bot", message.toUserId)
        assertEquals(123456789L, message.createTimeMs)
        assertEquals("context-token", message.contextToken)
        assertEquals("hello from sdk", message.itemList?.single()?.textItem?.text)
    }
}
