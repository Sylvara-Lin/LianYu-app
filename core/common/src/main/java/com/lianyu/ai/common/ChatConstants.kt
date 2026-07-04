package com.lianyu.ai.common

/**
 * 聊天业务层常量集中管理。
 *
 * 包括：
 * - 聊天消息分页参数
 * - 批量消息合并/拆分参数
 * - 消息通道背压参数
 * - 提示词/上下文截断长度
 * - 群聊上下文参数
 *
 * 这些数值原先散落在 ChatViewModel / GroupChatViewModel / AiService 中，
 * 集中后便于统一调整并保持多模块一致。
 */
object ChatConstants {

    // ── 聊天消息分页 ──
    /** 初始进入聊天时加载的消息条数 */
    const val CHAT_PAGE_SIZE = 50

    /** 点击"加载更多"时追加的历史消息条数 */
    const val CHAT_LOAD_MORE_SIZE = 30

    // ── 消息队列与批量合并 ──
    /** 发送消息 Channel 容量上限，满时拒绝新消息（不排队、不阻塞） */
    const val MESSAGE_QUEUE_CAPACITY = 100

    /** 用户快速连发时合并为一批的时间窗口（毫秒） */
    const val MESSAGE_BATCH_WINDOW_MS = 2500L

    /** 批量窗口内轮询间隔（毫秒），避免 CPU 忙等待 */
    const val MESSAGE_BATCH_POLL_INTERVAL_MS = 100L

    /** 单批次最大消息数，超出时拆分为子批次 */
    const val MESSAGE_BATCH_MAX_SIZE = 10

    /** 拆分后的子批次之间的发送间隔（毫秒），避免 API 限流 */
    const val MESSAGE_BATCH_SPLIT_DELAY_MS = 300L

    /** 消费器/观察器异常后最多自动重启次数，防止无限重试雪崩 */
    const val MAX_AUTO_RESTARTS = 5

    // ── 提示词/上下文截断 ──
    /** 表情候选列表最大条数（单聊/群聊共用） */
    const val CHAT_STICKER_CANDIDATES = 10

    /** 表情描述/名称在提示词中使用的最大长度 */
    const val CHAT_STICKER_NAME_MAX_LENGTH = 20

    /** 角色人格字段最大长度 */
    const val COMPANION_PERSONALITY_MAX_LENGTH = 300

    /** 角色说话风格字段最大长度 */
    const val COMPANION_SPEAKING_STYLE_MAX_LENGTH = 100

    /** 角色背景故事字段最大长度 */
    const val COMPANION_BACKSTORY_MAX_LENGTH = 200

    /** 追问/重生成等辅助流程使用的少量最近历史条数 */
    const val SHORT_HISTORY_LIMIT = 10

    /** AI 上下文从数据库拉取的安全上限（无论用户设置多少，不超过此值） */
    const val MAX_AI_CONTEXT_FETCH = 200

    /** UI 层内存中保留的最大消息条数 */
    const val MAX_UI_MESSAGES = 200

    /** AI 上下文解析缓存大小 */
    const val CONTEXT_CACHE_SIZE = 3

    /** 记忆上下文最大字符数 */
    const val MEMORY_CONTEXT_MAX_CHARS = 500

    /** 用户消息在提示词中的最大字符数 */
    const val USER_MESSAGE_PROMPT_MAX_CHARS = 2000

    // ── 群聊 ──
    /** 群聊自动对话默认轮数 */
    const val GROUP_CHAT_AUTO_ROUNDS = 2

    /** 群聊气泡间隔（毫秒） */
    const val GROUP_CHAT_BUBBLE_GAP_MS = 500L

    /** 群聊上下文窗口条数 */
    const val GROUP_CHAT_CONTEXT_WINDOW = 20

    /** 群聊 @ 判断阈值 */
    const val GROUP_CHAT_MENTION_JUDGE_THRESHOLD = 0.8f

    /** 群聊 @ 判断是否启用 */
    const val GROUP_CHAT_MENTION_JUDGE_ENABLED = true

    /** 群聊历史消息默认加载条数 */
    const val GROUP_CHAT_MESSAGE_LIMIT = 50

    /** 群聊 @ 上下文块最多包含的最近消息条数 */
    const val GROUP_CHAT_MENTION_CONTEXT_MAX_MESSAGES = 12

    /** 群聊 @ 上下文摘要最大长度 */
    const val GROUP_CHAT_MENTION_SUMMARY_MAX_LENGTH = 120

    /** 群聊构建上下文时取用的最近消息条数 */
    const val GROUP_CHAT_HISTORY_LOOKBACK = 10

    /** 群聊判断是否需要回复时取用的最近消息条数 */
    const val GROUP_CHAT_REPLY_HISTORY_LOOKBACK = 8

    /** 群聊关键词匹配时取用的最近消息条数 */
    const val GROUP_CHAT_KEYWORD_HISTORY_LOOKBACK = 6

    /** 群聊 @ 快照构建时取用的最近消息条数 */
    const val GROUP_CHAT_MENTION_SNAPSHOT_LOOKBACK = 6

    /** 群聊清洗句子预览最大长度 */
    const val GROUP_CHAT_SENTENCE_PREVIEW_MAX_LENGTH = 14

    /** 群聊重复回复检测时取用的最近回复条数 */
    const val GROUP_CHAT_DUPLICATE_HISTORY_LOOKBACK = 3

    /** 群聊重复回复检测时归一化内容预览最大长度 */
    const val GROUP_CHAT_DUPLICATE_PREVIEW_MAX_LENGTH = 20

    /** 群聊关键词匹配时性格字段预览最大长度 */
    const val GROUP_CHAT_PERSONALITY_PREVIEW_MAX_LENGTH = 50

    // ── 日志/调试截断 ──
    /** 普通日志中消息内容预览最大长度 */
    const val LOG_MESSAGE_PREVIEW_MAX_LENGTH = 30

    /** 长日志中消息内容预览最大长度 */
    const val LOG_LONG_MESSAGE_PREVIEW_MAX_LENGTH = 50

    /** 错误日志中异常消息预览最大长度 */
    const val LOG_ERROR_PREVIEW_MAX_LENGTH = 80

    /** 安全分类错误提示中异常消息预览最大长度 */
    const val SAFETY_ERROR_PREVIEW_MAX_LENGTH = 50

    // ── 打字/分段延迟（毫秒）──
    /** 单条 AI 回复后的短暂广播延迟 */
    const val AI_REPLY_BROADCAST_DELAY_MS = 100L

    /** 多条回复分段之间的基础延迟 */
    const val MULTI_REPLY_BASE_DELAY_MS = 800L

    /** 多条回复分段之间的随机延迟上限 */
    const val MULTI_REPLY_RANDOM_DELAY_MS = 1200L

    /** 追问触发后的基础延迟 */
    const val FOLLOW_UP_BASE_DELAY_MS = 2000L

    /** 追问触发后的随机延迟上限 */
    const val FOLLOW_UP_RANDOM_DELAY_MS = 3000L

    /** 加载更多历史时的延迟，让加载指示器可见 */
    const val LOAD_MORE_HISTORY_DELAY_MS = 200L

    /** 消息观察器崩溃后重启前的退避延迟 */
    const val OBSERVE_MESSAGES_RESTART_DELAY_MS = 500L

    /** 设置页恢复配置状态前的延迟，避免主线程阻塞 */
    const val SETTINGS_RESTORE_DELAY_MS = 500L

    // ── 文本处理器 ──
    /** 局部重复检测最小文本长度 */
    const val REPETITION_MIN_TEXT_LENGTH = 4

    /** 重复子串最小匹配长度（后缀/子串模式） */
    const val REPETITION_MIN_MATCH_LENGTH = 2

    /** 重复子串非后缀模式最小匹配长度 */
    const val REPETITION_MIN_SUB_MATCH_LENGTH = 4

    /** 句子级重复检测最小句子数 */
    const val REPETITION_MIN_SENTENCES = 2

    /** 句子级重复检测最小比较长度 */
    const val REPETITION_MIN_COMPARE_LENGTH = 4

    /** AI 回复清洗后安全最小长度 */
    const val CLEANED_MIN_LENGTH = 2

    /** AI 回复清洗后保守回退触发阈值（原长大于此值但清洗后过短时回退） */
    const val CLEANED_FALLBACK_ORIGINAL_LENGTH = 5

    /** 方括号清洗最大合理长度 */
    const val BRACKET_CLEAN_MAX_LENGTH = 50

    /** 表情概率随机范围上限 */
    const val STICKER_RANDOM_MAX = 100

    /** 表情概率随机起始值 */
    const val STICKER_RANDOM_MIN = 1

    /** 表情描述最小有效长度 */
    const val STICKER_DESC_MIN_LENGTH = 2

    /** 去除表情描述后文本最小保留长度 */
    const val STICKER_CLEAN_MIN_REMAINING_LENGTH = 2

    /** 泄露表情描述最大匹配长度 */
    const val LEAKED_STICKER_MAX_LENGTH = 20

    // ── 主动消息 Worker ──
    /** 主动消息 Worker 首次/回退最小间隔（分钟） */
    const val PROACTIVE_FALLBACK_MIN_MINUTES = 30L

    /** 主动消息 Worker 首次/回退最大间隔（分钟） */
    const val PROACTIVE_FALLBACK_MAX_MINUTES = 120L

    /** 主动消息用户输入最小间隔（分钟） */
    const val PROACTIVE_USER_MIN_INTERVAL_MINUTES = 15

    /** 主动消息用户输入最大间隔（分钟，24小时） */
    const val PROACTIVE_USER_MAX_INTERVAL_MINUTES = 1440

    // ── QQ Bot WebSocket ──
    /** QQ Bot WebSocket 心跳间隔系数（相对服务端 interval 的比例） */
    const val QQ_BOT_HEARTBEAT_FACTOR = 0.8

    /** QQ Bot WebSocket 心跳最小间隔（毫秒） */
    const val QQ_BOT_HEARTBEAT_MIN_MS = 5000L

    /** QQ Bot WebSocket 重连退避基数（毫秒） */
    const val QQ_BOT_RECONNECT_BACKOFF_BASE_MS = 2000L

    /** QQ Bot WebSocket 最大重连退避（毫秒） */
    const val QQ_BOT_RECONNECT_MAX_DELAY_MS = 30000L

    // ── WeChat Polling Service ──
    /** 微信轮询消息长轮询超时（毫秒） */
    const val WECHAT_SERVICE_POLL_TIMEOUT_MS = 20000L

    /** 微信轮询超时后的重试延迟（毫秒） */
    const val WECHAT_SERVICE_TIMEOUT_RETRY_DELAY_MS = 3000L

    /** 微信轮询连接错误后的重试延迟（毫秒） */
    const val WECHAT_SERVICE_CONNECTION_RETRY_DELAY_MS = 5000L

    /** 微信轮询通用错误后的重试延迟（毫秒） */
    const val WECHAT_SERVICE_ERROR_RETRY_DELAY_MS = 5000L

    // ── 群聊延迟 ──
    /** 群聊成员回复顺序第 0 位延迟范围（毫秒） */
    const val GROUP_CHAT_REPLY_DELAY_0_MIN_MS = 100L
    const val GROUP_CHAT_REPLY_DELAY_0_MAX_MS = 400L

    /** 群聊成员回复顺序第 1 位延迟范围（毫秒） */
    const val GROUP_CHAT_REPLY_DELAY_1_MIN_MS = 300L
    const val GROUP_CHAT_REPLY_DELAY_1_MAX_MS = 700L

    /** 群聊成员回复顺序第 2 位延迟范围（毫秒） */
    const val GROUP_CHAT_REPLY_DELAY_2_MIN_MS = 500L
    const val GROUP_CHAT_REPLY_DELAY_2_MAX_MS = 900L

    /** 群聊成员回复顺序默认延迟范围（毫秒） */
    const val GROUP_CHAT_REPLY_DELAY_DEFAULT_MIN_MS = 700L
    const val GROUP_CHAT_REPLY_DELAY_DEFAULT_MAX_MS = 1200L

    /** 群聊轮次之间延迟范围（毫秒） */
    const val GROUP_CHAT_ROUND_GAP_MIN_MS = 800L
    const val GROUP_CHAT_ROUND_GAP_MAX_MS = 2000L

    /** 群聊文字段发送延迟范围（毫秒） */
    const val GROUP_CHAT_TEXT_SEGMENT_DELAY_MIN_MS = 800L
    const val GROUP_CHAT_TEXT_SEGMENT_DELAY_MAX_MS = 1600L

    /** 群聊表情包发送延迟范围（毫秒） */
    const val GROUP_CHAT_STICKER_DELAY_MIN_MS = 600L
    const val GROUP_CHAT_STICKER_DELAY_MAX_MS = 1200L

    // ── 提示词/后处理 ──
    /** 主动消息时间阈值（分钟）：用户最后一条消息距今超过此值才触发 */
    const val PROACTIVE_TIME_THRESHOLD_MINUTES = 3

    /** 主动消息用户短消息忽略长度阈值 */
    const val PROACTIVE_SHORT_MESSAGE_LENGTH = 3

    /** 主动消息上下文最近消息条数 */
    const val PROACTIVE_CONTEXT_MESSAGE_COUNT = 8

    /** 主动消息上下文用户话题条数 */
    const val PROACTIVE_TOPIC_COUNT = 3

    /** 主动消息话题承接所需最少用户消息数 */
    const val PROACTIVE_TOPIC_MIN_MESSAGES = 2

    /** 主动消息时间流逝感提示阈值（分钟） */
    const val PROACTIVE_TIME_FLOW_THRESHOLD_MINUTES = 10

    /** 主动消息时间间隙：实时聊天阈值（分钟） */
    const val PROACTIVE_GAP_REALTIME_MINUTES = 1

    /** 主动消息时间间隙：最近阈值（分钟） */
    const val PROACTIVE_GAP_RECENT_MINUTES = 5

    /** 主动消息时间间隙：中等阈值（分钟） */
    const val PROACTIVE_GAP_MEDIUM_MINUTES = 15

    /** 主动消息时间间隙：长阈值（分钟） */
    const val PROACTIVE_GAP_LONG_MINUTES = 60

    /** AI 回复后处理：最大短句数 */
    const val POST_PROCESS_MAX_SENTENCES = 8

    /** AI 回复后处理：长截断阈值（字符） */
    const val POST_PROCESS_LONG_CUT_THRESHOLD = 150

    /** AI 回复后处理：长截断候选长度（字符） */
    const val POST_PROCESS_CUT_CANDIDATE_LENGTH = 120

    /** AI 回复后处理：截断点最小有效位置 */
    const val POST_PROCESS_CUT_MIN_POSITION = 20

    /** AI 回复后处理：回声检测用户消息最小长度 */
    const val ECHO_MIN_USER_LENGTH = 4

    /** AI 回复后处理：回声检测长度容差 */
    const val ECHO_LENGTH_TOLERANCE = 3

    /** AI 回复后处理：回声结束位置最小剩余长度 */
    const val ECHO_MIN_REMAINING_LENGTH = 2

    /** AI 回复后处理：重复称呼检测最近轮数 */
    const val REPEAT_NICKNAME_LOOKBACK_ROUNDS = 5

    /** AI 回复后处理：重复称呼最小词长度 */
    const val REPEAT_NICKNAME_MIN_WORD_LENGTH = 2

    /** 直接回复提取：引号内容最小长度 */
    const val EXTRACT_DIRECT_REPLY_MIN_QUOTED_LENGTH = 2

    /** 直接回复提取：最后一段长度阈值 */
    const val EXTRACT_DIRECT_REPLY_PARAGRAPH_THRESHOLD = 80

    /** 直接回复提取：段落数量阈值 */
    const val EXTRACT_DIRECT_REPLY_MIN_PARAGRAPHS = 2

    /** buildMessages 默认上下文限制（轻量调用） */
    const val BUILD_MESSAGES_DEFAULT_CONTEXT_LIMIT = 12

    /** 本地上下文压缩默认保留比例 */
    const val DEFAULT_COMPRESSION_KEEP_RATIO = 0.5f

    /** 本地上下文压缩默认最小保留条数 */
    const val DEFAULT_COMPRESSION_MIN_KEEP = 6

    /** 本地模型系统提示：贴纸概率默认值 */
    const val LOCAL_STICKER_PROBABILITY_DEFAULT = 30

    /** 系统提示可用表情最大数量 */
    const val SYSTEM_PROMPT_MAX_STICKERS = 50

    /** 本地模型最小回复长度阈值 */
    const val LOCAL_MIN_RESPONSE_LENGTH = 20

    /** 人格规则默认回复短句数上限 */
    const val PERSONA_MAX_SENTENCES = 5

    /** 人格规则默认回复字数下限 */
    const val PERSONA_MIN_CHARS = 15

    /** 人格规则默认回复字数上限 */
    const val PERSONA_MAX_CHARS = 50

    /** 高贴纸概率阈值（%） */
    const val STICKER_PROBABILITY_HIGH = 80

    /** 中等贴纸概率阈值（%） */
    const val STICKER_PROBABILITY_MEDIUM = 50

    /** 低贴纸概率阈值（%） */
    const val STICKER_PROBABILITY_LOW = 20
}

