package com.lianyu.ai.network

/**
 * 集中管理网络层常量，避免超时、连接池、URL 路径等数值散落在各个文件中。
 *
 * 说明：
 * - 通用超时尽量收敛到此处；特殊场景（如流式 60s、调试日志 3s）保留独立常量。
 * - 第三方官方 API 的固定 endpoint 也放在这里，便于后续统一维护。
 */
object NetworkConstants {

    // ── 通用 OkHttp 超时（秒） ──
    const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10
    const val DEFAULT_READ_TIMEOUT_SECONDS = 30
    const val DEFAULT_WRITE_TIMEOUT_SECONDS = 10
    const val DEFAULT_CALL_TIMEOUT_SECONDS = 30
    const val DEFAULT_PING_INTERVAL_SECONDS = 30

    // ── 特殊场景超时（秒） ──
    const val SHORT_READ_TIMEOUT_SECONDS = 20
    const val STREAMING_READ_TIMEOUT_SECONDS = 60
    const val VISION_READ_TIMEOUT_SECONDS = 25
    const val DEBUG_LOG_TIMEOUT_SECONDS = 3
    const val DOWNLOAD_WRITE_TIMEOUT_SECONDS = 15
    const val MODEL_FETCH_TIMEOUT_SECONDS = 25

    // ── 连接池 ──
    const val CONNECTION_POOL_MAX_IDLE = 5
    const val CONNECTION_POOL_KEEP_ALIVE_MINUTES = 5L
    const val PARTNER_CONNECTION_POOL_MAX_IDLE = 3

    // ── OpenAI 兼容 API ──
    const val OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/"
    const val OPENAI_CHAT_COMPLETIONS_PATH = "/chat/completions"
    const val OPENAI_DEFAULT_API_VERSION = "/v1"
    val OPENAI_BILLING_SUBSCRIPTION_ENDPOINTS = listOf(
        "/dashboard/billing/subscription",
        "/v1/dashboard/billing/subscription"
    )
    val OPENAI_BILLING_USAGE_ENDPOINTS = listOf(
        "/dashboard/billing/usage",
        "/v1/dashboard/billing/usage"
    )

    // ── 合作方/自托管提供商标配超时 ──
    const val PARTNER_CONNECT_TIMEOUT_SECONDS = 15
    const val PARTNER_READ_TIMEOUT_SECONDS = 25

    // ── TTS 服务固定 endpoint ──
    const val BAIDU_TTS_URL = "https://tsn.baidu.com/text2audio"

    // ── QQ Bot 官方 API ──
    const val QQ_BOT_AUTH_BASE_URL = "https://bots.qq.com/"
    const val QQ_BOT_API_BASE_URL = "https://api.sgroup.qq.com/"

    // ── QQ Bot 开放平台扫码绑定（Hermes 协议） ──
    const val QQ_BOT_LITE_CREATE_TASK_URL = "https://q.qq.com/lite/create_bind_task"
    const val QQ_BOT_LITE_POLL_RESULT_URL = "https://q.qq.com/lite/poll_bind_result"
    const val QQ_BOT_LITE_QR_CONNECT_URL = "https://q.qq.com/qqbot/openclaw/connect.html"

    // ── QQ Bot 开放平台（WebView 扫码创建机器人） ──
    const val QQ_BOT_OPEN_PLATFORM_URL = "https://q.qq.com/qqbot/"

    // ── QQ Bot API 超时（秒） ──
    const val QQ_BOT_API_CONNECT_TIMEOUT_SECONDS = 15
    const val QQ_BOT_API_READ_TIMEOUT_SECONDS = 15
    const val QQ_BOT_API_WRITE_TIMEOUT_SECONDS = 15
    const val QQ_BOT_TOKEN_REFRESH_MARGIN_MS = 60_000L

    // ── QQ Bot WebSocket 超时（秒） ──
    const val QQ_BOT_WS_CONNECT_TIMEOUT_SECONDS = 15
    const val QQ_BOT_WS_READ_TIMEOUT_SECONDS = 0
    const val QQ_BOT_WS_WRITE_TIMEOUT_SECONDS = 15
    const val QQ_BOT_WS_PING_INTERVAL_SECONDS = 30

    // ── WeChat iLink SDK ──
    const val WECHAT_BASE_URL = "https://ilinkai.weixin.qq.com"
    const val WECHAT_POLL_INTERVAL_MINUTES = 15L
    const val WECHAT_POLL_FLEX_MINUTES = 5L
    const val WECHAT_POLL_TIMEOUT_MS = 15_000L
    const val WECHAT_POLL_RETRY_DELAY_MS = 5_000L
    const val WECHAT_SDK_CONNECT_TIMEOUT_MS = 10_000L
    const val WECHAT_SDK_READ_TIMEOUT_MS = 15_000L
    const val WECHAT_SDK_WRITE_TIMEOUT_MS = 10_000L
    const val WECHAT_SDK_LOGIN_TIMEOUT_MS = 5 * 60 * 1000L
    const val WECHAT_SDK_CHANNEL_VERSION = "1.0.3"

    // ── TTS 超时（秒） ──
    const val TTS_CONNECT_TIMEOUT_SECONDS = 15
    const val TTS_READ_TIMEOUT_SECONDS = 30

    // ── TTS 服务端点 ──
    const val XUNFEI_TTS_WEBSOCKET_URL = "wss://tts-api.xfyun.cn/v2/tts"
    const val XUNFEI_TTS_HOST = "tts-api.xfyun.cn"
    const val VOLCENGINE_TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
    const val MICROSOFT_TTS_SPEECH_HOST = "tts.speech.microsoft.com"
    const val MICROSOFT_TTS_SPEECH_PATH = "/cognitiveservices/v1"
    const val MICROSOFT_TTS_TOKEN_HOST = "api.cognitive.microsoft.com"
    const val MICROSOFT_TTS_TOKEN_PATH = "/sts/v1.0/issueToken"
    const val BAIDU_TTS_TOKEN_URL_TEMPLATE =
        "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s"
    const val ALIYUN_TTS_ENDPOINT = "nls-gateway-cn-shanghai.aliyuncs.com"
    const val ALIYUN_TTS_PATH = "/stream/v1/tts"
    const val ALIYUN_TTS_TOKEN_ENDPOINT = "nls-meta.cn-shanghai.aliyuncs.com"
    const val ALIYUN_TTS_TOKEN_PATH = "/pop/v2018-05-18/tokens"

    // ── OpenAI 兼容轻量客户端超时（秒） ──
    const val OPENAI_LIGHT_CONNECT_TIMEOUT_SECONDS = 15
    const val OPENAI_LIGHT_READ_TIMEOUT_SECONDS = 45
    const val OPENAI_LIGHT_WRITE_TIMEOUT_SECONDS = 15

    // ── 应用层 API 调用超时（毫秒） ──
    // [FIX] 2026-06-22: IQOO/OriginOS 设备上网络可能被系统限制，增加超时时间
    const val API_CALL_TIMEOUT_MS = 30_000L
    const val VISION_API_CALL_TIMEOUT_MS = 60_000L
    const val SAFETY_CLASSIFY_TIMEOUT_MS = 30_000L
    const val MEMORY_EXTRACT_TIMEOUT_MS = 5_000L
    const val TTS_SYNTH_TIMEOUT_MS = 10_000L
    
    // IQOO/OriginOS 专用：更长的 API 超时，应对系统级网络限制
    const val API_CALL_TIMEOUT_MS_IQOO = 45_000L
    const val VISION_API_CALL_TIMEOUT_MS_IQOO = 90_000L

    // ── 聊天内部流程超时（毫秒） ──
    const val API_AVAILABILITY_TIMEOUT_MS = 1500L
    const val PIPELINE_EXECUTION_TIMEOUT_MS = 8000L
    const val CONTENT_FILTER_FULL_TIMEOUT_MS = 3000L
    const val CONTENT_FILTER_VECTOR_TIMEOUT_MS = 3000L
    const val BAYESIAN_VERIFICATION_TIMEOUT_MS = 5000L

    // ── 重试/退避 ──
    const val DEFAULT_RETRY_MAX_RETRIES = 2
    const val DEFAULT_RETRY_INITIAL_DELAY_MS = 300L
    const val KEY_FAILURE_COOLDOWN_MS = 5_000L

    // ── 流式响应缓冲 ──
    const val STREAM_BUFFER_INTERVAL_MS = 50L
    const val STREAM_BUFFER_TEXT_LENGTH_THRESHOLD = 20
    const val STREAM_TYPING_BASE_DELAY_MS = 30L
    const val STREAM_TYPING_PER_CHAR_DELAY_MS = 15L

    // ── DNS / 网络基础设施 ──
    const val DNS_RESOLUTION_TIMEOUT_SECONDS = 5

    // ── GitHub Releases ──
    const val GITHUB_RELEASES_API_URL =
        "https://api.github.com/repos/linruoxi666/LianYu/releases/latest"

    // ── Debug Log Server（默认地址，可被 DEBUG_LOG_SERVER_URL 环境变量覆盖） ──
    const val DEBUG_LOG_SERVER_URL = "http://10.188.248.127:8765/log"

    // ── 第三方平台帮助页 ──
    const val DEEPSEEK_API_KEYS_URL = "https://platform.deepseek.com/api_keys"

    // ── 应用全局 DNS（仅作常量，便于后续改为可配置） ──
    const val DEFAULT_DNS_SERVER = "8.8.8.8"
}
