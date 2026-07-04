package com.lianyu.ai.feature.wechat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatIncomingMessagePolicyTest {

    @Test
    fun foregroundTextMessageAutoRepliesWhenEnabled() {
        val decision = WeChatIncomingMessagePolicy.evaluate(
            isAppInForeground = true,
            notifyEnabled = true,
            autoReplyEnabled = true,
            messageText = "hello"
        )

        assertTrue(decision.shouldAutoReply)
        assertFalse(decision.shouldNotify)
    }

    @Test
    fun backgroundTextMessageNotifiesAndAutoRepliesWhenEnabled() {
        val decision = WeChatIncomingMessagePolicy.evaluate(
            isAppInForeground = false,
            notifyEnabled = true,
            autoReplyEnabled = true,
            messageText = "hello"
        )

        assertTrue(decision.shouldNotify)
        assertTrue(decision.shouldAutoReply)
    }

    @Test
    fun blankMessageDoesNotAutoReply() {
        val decision = WeChatIncomingMessagePolicy.evaluate(
            isAppInForeground = false,
            notifyEnabled = true,
            autoReplyEnabled = true,
            messageText = "   "
        )

        assertTrue(decision.shouldNotify)
        assertFalse(decision.shouldAutoReply)
    }
}
