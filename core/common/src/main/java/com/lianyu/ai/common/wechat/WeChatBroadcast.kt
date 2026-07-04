package com.lianyu.ai.common.wechat

/**
 * feature:notification 与 feature:wechat 之间的广播约定常量。
 * 通过 core:common 共享，避免硬编码字符串导致的模块间隐式耦合。
 */
object WeChatBroadcast {
    const val ACTION_SEND_PROACTIVE = "com.lianyu.ai.wechat.SEND_PROACTIVE"
    const val EXTRA_COMPANION_ID = "companion_id"
    /** @deprecated Use EXTRA_MESSAGE_ID + DB lookup instead of passing content in Intent. */
    @Deprecated("Use EXTRA_MESSAGE_ID instead")
    const val EXTRA_CONTENT = "content"
    const val EXTRA_MESSAGE_ID = "message_id"
    /** App 处理后的最终内容，直接发给微信，避免微信端再从 DB 读取 */
    const val EXTRA_FINAL_CONTENT = "final_content"
}
