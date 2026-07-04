package com.lianyu.ai.common

/**
 * 时滞预算常量 — 每个异步操作的期望和最大时滞。
 * 
 * 控制论原则: 时滞 × 请求速率 = 在途请求数。
 * 时滞 > 波动周期时系统必然振荡。
 */
object TimeoutBudgets {
    // === Native C++ 操作 ===
    const val SM4_DECRYPT_MS = 50L          // SM4解密
    const val BAYESIAN_CLASSIFY_MS = 30L     // 贝叶斯分类
    const val AC_SCAN_MS = 10L               // AC关键词扫描
    const val PROTO_CODEC_MS = 20L           // Protobuf编解码
    const val IMAGE_PROCESS_MS = 50L         // 图片处理

    // === 网络操作 ===
    // [P0 FIX] API_CHAT_MS 从25s降至15s：原值导致消息队列串行堵塞，用户连续发消息时延迟=15s x N条
    const val API_CHAT_MS = 15_000L          // 普通AI对话 (15s)
    const val API_VISION_MS = 30_000L        // 视觉识别 (30s，需编码+传输)
    const val API_STREAM_MS = 20_000L        // 流式对话 (20s)
    const val TTS_SYNTH_MS = 10_000L         // TTS合成 (10s)
    const val STT_RECOGNIZE_MS = 15_000L     // 语音识别 (15s)
    // [M11 FIX] ChatViewModel 使用的实际超时值（OkHttp callTimeout 对齐，网络慢时需更长）
    const val CHAT_VM_API_TIMEOUT_MS = 30_000L      // ChatViewModel AI 调用超时
    const val CHAT_VM_VISION_TIMEOUT_MS = 60_000L   // ChatViewModel 视觉调用超时
    const val CHAT_VM_SAFETY_CLASSIFY_MS = 30_000L  // ChatViewModel 安全分类超时
    const val CHAT_VM_MEMORY_EXTRACT_MS = 5_000L    // ChatViewModel 记忆提取超时
    const val CHAT_VM_TTS_SYNTH_MS = 10_000L        // ChatViewModel TTS 超时
    const val CHAT_VM_BATCH_WINDOW_MS = 2_500L      // ChatViewModel 批量合并窗口
    // [P1 FIX] 散落在 ChatViewModel 的硬编码超时归一至此
    const val MODEL_OUTPUT_VERIFY_MS = 5_000L  // 贝叶斯模型输出校验（语义不同于 MEMORY_EXTRACT）
    const val API_CONFIG_WAIT_MS = 1_500L      // 冷启动等待 API 配置加载（竞态窗口）
    const val PIPELINE_EXECUTE_MS = 8_000L     // 内容安全管道执行总预算

    // === HTTP 客户端通用超时（OkHttp connectTimeout/readTimeout/writeTimeout/pingInterval） ===
    const val HTTP_CONNECT_MS = 10_000L        // TCP 连接建立
    const val HTTP_READ_MS = 30_000L           // 读响应（对齐 API_CHAT_MS 的 2x）
    const val HTTP_WRITE_MS = 10_000L          // 写请求体
    const val HTTP_PING_MS = 30_000L           // HTTP/2 ping 间隔（保活）

    // === 微信 ===
    const val WECHAT_POLL_TIMEOUT_MS = 15_000L // 长轮询超时（统一 Service 20s 与 Worker 15s 不一致）
    const val BROADCAST_GOASYNC_MS = 9_500L    // BroadcastReceiver goAsync() 10s 限制预留 500ms

    // === 瑞幸 MCP (JSON-RPC over Streamable HTTP) ===
    // 集中管理：原先硬编码在 LuckinMcpClient companion，现统一至此处
    const val MCP_CONNECT_MS = 15_000L       // MCP 连接超时
    const val MCP_READ_MS = 30_000L          // MCP 读取超时（普通 JSON 响应）
    const val MCP_WRITE_MS = 15_000L         // MCP 写入超时
    const val MCP_SSE_READ_MS = 30_000L      // SSE 流无数据超时（防协程永久阻塞）

    // === 数据库操作 ===
    const val ROOM_WRITE_MS = 5_000L         // Room写入
    const val ROOM_QUERY_MS = 3_000L         // Room查询
    const val MEMORY_EXTRACT_MS = 5_000L     // 记忆提取

    // === 安全检测 ===
    const val CONTENT_FILTER_MS = 3_000L     // 内容过滤
    const val SAFETY_CLASSIFY_MS = 30_000L   // 安全分类

    // === 分岔点硬限制 ===
    const val CHANNEL_CAPACITY = 100         // 消息队列容量
    const val MAX_CONCURRENT_API = 3         // 最大并发API请求
    const val LAZY_COLUMN_MAX_ITEMS = 200    // LazyColumn视口上限
    const val IMAGE_CACHE_MB = 128           // 图片缓存上限(MB)
    const val MAX_TTS_TASKS = 3              // 同时TTS任务数
    const val MEMORY_ALERT_RATIO = 0.85f     // 内存告警阈值
    const val MEMORY_RECOVER_RATIO = 0.60f   // 内存恢复阈值(滞环)
}
