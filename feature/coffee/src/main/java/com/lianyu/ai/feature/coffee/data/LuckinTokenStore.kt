package com.lianyu.ai.feature.coffee.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lianyu.ai.feature.coffee.data.model.OrderCreated
import com.lianyu.ai.feature.coffee.data.model.OrderHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.coffeeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "luckin_coffee_prefs"
)

/**
 * 瑞幸咖啡本地存储。
 *
 * 持久化两类数据到同一个 DataStore（`luckin_coffee_prefs`）：
 * 1. MCP Bearer Token（用户从 https://open.lkcoffee.com/mcp 登录获取，有效期约 30 天）
 * 2. 订单历史（createOrder 成功后写入，最近 20 条，用于设置页展示）
 *
 * 安全说明：
 * - Token 与瑞幸账号会话绑定，严禁泄露
 * - 存储在应用私有目录，卸载后自动清除
 */
class LuckinTokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("luckin_mcp_token")
    private val tokenSaveTimeKey = stringPreferencesKey("luckin_mcp_token_save_time")
    private val orderHistoryKey = stringPreferencesKey("luckin_order_history")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** 获取已存储的 Token，空字符串表示未配置 */
    val token: Flow<String> = context.coffeeDataStore.data.map { it[tokenKey] ?: "" }

    /** Token 保存时间戳（毫秒），用于判断是否过期 */
    private val tokenSaveTime: Flow<Long> = context.coffeeDataStore.data.map {
        it[tokenSaveTimeKey]?.toLongOrNull() ?: 0L
    }

    /** 订单历史（按 createdAt 倒序，最近 20 条） */
    val orderHistory: Flow<List<OrderHistoryEntry>> = context.coffeeDataStore.data.map { prefs ->
        prefs[orderHistoryKey]?.let(::decodeHistory) ?: emptyList()
    }

    /**
     * 保存 Token（覆盖旧值，用于"替换 Token"场景）。
     * @param token 用户从瑞幸开放平台获取的完整 Bearer Token
     */
    suspend fun saveToken(token: String) {
        context.coffeeDataStore.edit { prefs ->
            prefs[tokenKey] = token.trim()
            prefs[tokenSaveTimeKey] = System.currentTimeMillis().toString()
        }
    }

    /** 清除 Token（用户撤销授权时调用） */
    suspend fun clearToken() {
        context.coffeeDataStore.edit {
            it.remove(tokenKey)
            it.remove(tokenSaveTimeKey)
        }
    }

    /**
     * 检查 Token 是否可能已过期（超过 29 天）。
     * 这是客户端预判，实际过期以服务端返回 401 为准。
     */
    suspend fun isTokenLikelyExpired(): Boolean {
        val saveTime = tokenSaveTime.first()
        if (saveTime == 0L) return true
        val elapsed = System.currentTimeMillis() - saveTime
        return elapsed > 29L * 24 * 60 * 60 * 1000
    }

    /** Token 保存距今天数（用于设置页展示），0 表示未配置 */
    suspend fun tokenSavedDaysAgo(): Int {
        val saveTime = tokenSaveTime.first()
        if (saveTime == 0L) return 0
        val elapsed = System.currentTimeMillis() - saveTime
        return (elapsed / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }

    /** 新增一条订单历史（createOrder 成功后调用），自动截断到最近 20 条 */
    suspend fun addOrderHistory(order: OrderCreated, deptName: String) {
        if (order.orderIdStr.isBlank() && order.orderId == 0L) return
        context.coffeeDataStore.edit { prefs ->
            val current = prefs[orderHistoryKey]?.let(::decodeHistory) ?: emptyList()
            val entry = OrderHistoryEntry(
                orderIdStr = order.orderIdStr.ifBlank { order.orderId.toString() },
                deptName = deptName,
                discountPrice = order.discountPrice,
                createdAt = System.currentTimeMillis()
            )
            val updated = (listOf(entry) + current).take(20)
            prefs[orderHistoryKey] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(OrderHistoryEntry.serializer()),
                updated
            )
        }
    }

    /** 清空全部订单历史 */
    suspend fun clearOrderHistory() {
        context.coffeeDataStore.edit { it.remove(orderHistoryKey) }
    }

    /** 同步读取一次订单历史（用于 ViewModel 一次性加载） */
    suspend fun snapshotOrderHistory(): List<OrderHistoryEntry> = orderHistory.first()

    private fun decodeHistory(raw: String): List<OrderHistoryEntry> =
        runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(OrderHistoryEntry.serializer()),
                raw
            )
        }.getOrElse { emptyList() }
}
