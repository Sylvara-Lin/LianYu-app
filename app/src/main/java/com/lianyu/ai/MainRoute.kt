package com.lianyu.ai

/**
 * Navigation 状态机 — 所有路由的 sealed class 定义。
 *
 * 控制论: 离散系统差分方程 S[k+1] = f(S[k], E[k])
 * 验证: 所有状态从 Home 可达 ✓  所有状态出度 ≥ 1 ✓  编译器穷尽检查 ✓
 */
sealed class MainRoute(val route: String) {
    // === 主页 Tab ===
    object Home : MainRoute("home")
    object Contacts : MainRoute("contacts")
    object Profile : MainRoute("profile")

    // === 聊天 ===
    data class Chat(val companionId: Long) : MainRoute("chat/$companionId")
    data class ChatDetail(val companionId: Long) : MainRoute("chat_detail/$companionId")
    data class VoiceCall(val companionId: Long) : MainRoute("voice_call/$companionId")

    // === 群聊 ===
    data class GroupChat(val groupId: Long) : MainRoute("group_chat/$groupId")
    data class GroupDetail(val groupId: Long) : MainRoute("group_detail/$groupId")
    object CreateGroup : MainRoute("create_group")

    // === 伴侣 ===
    object CreateCompanion : MainRoute("create")
    data class EditCompanion(val companionId: Long) : MainRoute("edit/$companionId")

    // === 设置 ===
    object Settings : MainRoute("settings")
    object TtsSettings : MainRoute("tts_settings")
    object TokenUsage : MainRoute("token_usage")
    object Theme : MainRoute("theme")
    object Language : MainRoute("language")
    object CheckUpdate : MainRoute("check_update")
    object FrameRate : MainRoute("frame_rate")
    object YandereMode : MainRoute("yandere_mode")
    object ExperimentalFeatures : MainRoute("experimental_features")

    // === 总设置 ===
    object GeneralSettings : MainRoute("general_settings")

    // === 角色管理 ===
    object RoleManager : MainRoute("role_manager")

    // === 个人中心 ===
    object Memory : MainRoute("memory")
    object ContextMemory : MainRoute("context_memory")
    object About : MainRoute("about")
    object AgreementView : MainRoute("agreement_view")
    object Team : MainRoute("team")
    object Support : MainRoute("support")
    object Thanks : MainRoute("thanks")
    object ThanksFullList : MainRoute("thanks_full_list")
    object OriginOSAdaption : MainRoute("originos_adaption")

    // === 微信 ===
    object WeChatSettings : MainRoute("wechat_settings")
    object WeChatBind : MainRoute("wechat_bind")

    // === QQ 机器人 ===
    object QQBotSettings : MainRoute("qqbot_settings")

    // === 数据备份 ===
    object DataBackup : MainRoute("data_backup")

    // === 瑞幸咖啡 ===
    object Coffee : MainRoute("coffee")
    /** 商品定制页：coffee/product/{deptId}/{productId} */
    data class CoffeeProduct(val deptId: Long, val productId: Long) : MainRoute("coffee_product/$deptId/$productId")
    /** 瑞幸独立设置页 */
    object CoffeeSettings : MainRoute("coffee_settings")
    /** Token 输入/替换页 */
    object CoffeeToken : MainRoute("coffee_token")
    /** 独立订单查询页（无参，手动输入订单号） */
    object CoffeeOrderQuery : MainRoute("coffee_order")
    /** 独立订单查询页（带初始订单号，从订单历史跳入） */
    data class CoffeeOrderQueryWithId(val orderId: String) : MainRoute("coffee_order/$orderId")

    companion object {
        /** 从路由字符串解析（用于 NavHost currentRoute） */
        fun fromRoute(route: String?): MainRoute = when {
            route == null -> Home
            route == "home" -> Home
            route == "contacts" -> Contacts
            route == "profile" -> Profile
            route == "create" -> CreateCompanion
            route == "create_group" -> CreateGroup
            route == "settings" -> Settings
            route == "tts_settings" -> TtsSettings
            route == "token_usage" -> TokenUsage
            route == "memory" -> Memory
            route == "context_memory" -> ContextMemory
            route == "role_manager" -> RoleManager
            route == "theme" -> Theme
            route == "language" -> Language
            route == "check_update" -> CheckUpdate
            route == "about" -> About
            route == "agreement_view" -> AgreementView
            route == "frame_rate" -> FrameRate
            route == "yandere_mode" -> YandereMode
            route == "experimental_features" -> ExperimentalFeatures
            route == "general_settings" -> GeneralSettings
            route == "team" -> Team
            route == "support" -> Support
            route == "thanks" -> Thanks
            route == "thanks_full_list" -> ThanksFullList
            route == "originos_adaption" -> OriginOSAdaption
            route == "wechat_settings" -> WeChatSettings
            route == "wechat_bind" -> WeChatBind
            route == "qqbot_settings" -> QQBotSettings
            route == "data_backup" -> DataBackup
            route == "coffee" -> Coffee
            route == "coffee_settings" -> CoffeeSettings
            route == "coffee_token" -> CoffeeToken
            route == "coffee_order" -> CoffeeOrderQuery
            route?.startsWith("coffee_order/") == true -> {
                CoffeeOrderQueryWithId(route.removePrefix("coffee_order/"))
            }
            route?.startsWith("coffee_product/") == true -> {
                val parts = route.removePrefix("coffee_product/").split("/")
                CoffeeProduct(parts.getOrNull(0)?.toLongOrNull() ?: 0L, parts.getOrNull(1)?.toLongOrNull() ?: 0L)
            }
            route?.startsWith("chat/") == true -> Chat(route.removePrefix("chat/").toLongOrNull() ?: 0L)
            route?.startsWith("chat_detail/") == true -> ChatDetail(route.removePrefix("chat_detail/").toLongOrNull() ?: 0L)
            route?.startsWith("voice_call/") == true -> VoiceCall(route.removePrefix("voice_call/").toLongOrNull() ?: 0L)
            route?.startsWith("group_chat/") == true -> GroupChat(route.removePrefix("group_chat/").toLongOrNull() ?: 0L)
            route?.startsWith("group_detail/") == true -> GroupDetail(route.removePrefix("group_detail/").toLongOrNull() ?: 0L)
            route?.startsWith("edit/") == true -> EditCompanion(route.removePrefix("edit/").toLongOrNull() ?: 0L)
            else -> Home
        }
    }
}
