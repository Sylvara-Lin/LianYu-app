package com.lianyu.ai.feature.qqbot.ui

/**
 * QQBot 设置页字符串集中管理。
 *
 * 提取到独立 object 是为了规避 GBK 编码环境对 Kotlin 源文件中
 * 中文直接书写的限制；此处通过统一入口便于后续替换为 strings.xml 资源。
 */
object QQBotStrings {
    const val SETTINGS_TITLE = "QQ 机器人设置"
    const val BACK = "返回"
    const val QR_BIND = "扫码绑定"
    const val MANUAL_BIND = "手动绑定"

    const val BIND_SUCCESS = "QQ Bot 绑定成功"
    const val UNBOUND = "已解除绑定"
    fun bindFailed(error: String) = "绑定失败: $error"

    const val FEATURE_SETTINGS = "功能设置"
    const val MSG_NOTIFY = "消息通知"
    const val MSG_NOTIFY_SUB = "QQ 消息到达时推送通知"
    const val AUTO_REPLY = "自动回复"
    const val AUTO_REPLY_SUB = "收到 QQ 消息后自动调用 AI 回复"
    const val MSG_FORWARD = "消息转发"
    const val MSG_FORWARD_SUB = "将 AI 消息同步发送到 QQ"
    const val DEFAULT_COMPANION = "默认 AI 伴侣"
    const val NOT_SELECTED = "未选择（使用第一个）"
    const val SELECT = "选择"

    const val USER_MAPPING_TITLE = "QQ 用户人设分配"
    const val DELETE_MAPPING = "删除映射"
    const val SWITCH = "切换"
    const val DELETE_LABEL = "删除"
    fun userLabel(qqUserId: String) = "用户 $qqUserId"
    fun personaLabel(name: String) = "人设: $name"
    fun unknownCompanion(id: Long) = "未知 (ID: $id)"
    fun selectCompanionFor(qqUserId: String) = "为用户 $qqUserId 选择 AI 伴侣"
    fun deleteMappingConfirm(qqUserId: String) = "确定要删除用户 $qqUserId 的人设映射吗？删除后将使用默认 AI 伴侣。"

    const val NOTES_TITLE = "说明"
    const val NOTES_BODY =
        "• 基于 QQ 官方 Bot 平台（小龙虾/Hermes 同协议）\n" +
        "• 支持扫码绑定和手动填入两种方式\n" +
        "• 支持 C2C 私聊、群聊 @、频道消息\n" +
        "• 扫码后自动获取 AppID 和 ClientSecret，或手动填写\n" +
        "• AccessToken 会自动刷新，无需手动维护"

    const val UNBIND = "解除绑定"
    const val UNBIND_CONFIRM = "确定要解除 QQ Bot 绑定吗？解除后将无法通过 QQ 接收消息。"
    const val CONFIRM = "确定"
    const val CANCEL = "取消"

    const val SELECT_DEFAULT_COMPANION = "选择默认 AI 伴侣"
    const val AUTO_ASSIGN = "自动分配（使用第一个）"

    const val SET_BOT_NAME = "设置机器人名字"
    const val NAME = "名字"
    const val SAVE = "保存"

    // 二维码绑定
    const val QR_CONTENT_DESC = "QQ 扫码绑定二维码"
    const val WAITING_SCAN = "等待扫码..."
    const val BIND_OK = "✓ 绑定成功"
    const val QR_EXPIRED = "二维码已过期，正在刷新..."
    const val GENERATING_QR = "正在生成二维码..."
    const val USE_QQ_SCAN = "使用手机 QQ 扫码绑定"
    const val SCAN_HINT = "扫码后将在 QQ 开放平台创建机器人，\n自动获取 AppID 和 Secret"
    const val GENERATE_QR = "生成二维码"
    const val QR_FOOTER = "使用手机 QQ 扫描上方二维码，授权后即可自动完成绑定。"

    // 手动绑定
    const val BIND_QQBOT = "绑定 QQ 机器人"
    const val BOT_NAME_OPTIONAL = "机器人名字（可选）"
    const val BINDING = "绑定中..."
    const val BIND = "绑定"

    // 状态卡
    const val NOT_BOUND = "未绑定"
    const val ONLINE = "● 在线"
    const val CONNECTING = "◌ 连接中..."
    const val RECONNECTING = "◌ 重连中..."
    const val AUTH_FAILED = "⚠ 鉴权失败"
    const val OFFLINE = "● 离线"
    const val BOUND = "QQ 机器人已绑定"
    const val NOT_BOUND_QQBOT = "未绑定 QQ 机器人"
    const val BIND_HINT = "绑定后可通过 QQ 收发消息"
    const val RENAME = "改名"
    const val BIND_NOW = "立即绑定"
}
