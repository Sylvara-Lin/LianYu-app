package com.lianyu.ai.push

/**
 * 各厂商 Push SDK 密钥配置。
 *
 * 请前往对应开放平台创建应用后填入真实值：
 * - OPPO: https://open.oppomobile.com/  → AppKey / AppSecret
 * - vivo: https://dev.vivo.com.cn/      → AppID / AppKey
 * - 华为: https://developer.huawei.com/  → app_id（在 agconnect-services.json 中）
 * - 小米: https://dev.mi.com/            → AppID / AppKey / AppSecret
 *
 * 当前使用空字符串占位，未配置时对应厂商通道会自动跳过注册，避免崩溃。
 */
object PushConfig {
    const val OPPO_APP_KEY = ""
    const val OPPO_APP_SECRET = ""

    const val VIVO_APP_ID = ""
    const val VIVO_APP_KEY = ""

    const val XIAOMI_APP_ID = ""
    const val XIAOMI_APP_KEY = ""
}
