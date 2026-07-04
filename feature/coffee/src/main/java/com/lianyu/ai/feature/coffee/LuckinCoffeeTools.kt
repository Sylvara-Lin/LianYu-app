package com.lianyu.ai.feature.coffee

import com.lianyu.ai.domain.AiTool
import com.lianyu.ai.domain.CoffeeOrderProvider
import com.lianyu.ai.domain.ToolRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 瑞幸咖啡 MCP 工具集 —— 注册为 [AiTool]，供 AI 对话调用。
 *
 * 注册 6 个工具（对齐官方 MCP 文档字段名）：
 * - luckin_query_shops    查门店
 * - luckin_search_products 搜商品
 * - luckin_preview_order  预览订单（真实价格）
 * - luckin_create_order   下单（生成支付二维码）
 * - luckin_query_order    查订单详情/取餐码
 * - luckin_cancel_order   取消订单
 *
 * 注：queryProductDetail/switchProduct 是 UI 定制专用，不开放给 AI。
 *
 * 安全约束：
 * - createOrder 涉及支付，由 ChatViewModel 侧做用户确认，工具本身直接执行
 * - Token 不回显给 AI，AI 只通过工具返回结果感知
 */
object LuckinCoffeeTools {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * 注册全部瑞幸工具到 [ToolRegistry]。
     * 应在 app 启动时（LianYuApplication）调用一次。
     */
    fun registerAll(provider: CoffeeOrderProvider) {
        ToolRegistry.register(QueryShopsTool(provider))
        ToolRegistry.register(SearchProductsTool(provider))
        ToolRegistry.register(PreviewOrderTool(provider))
        ToolRegistry.register(CreateOrderTool(provider))
        ToolRegistry.register(QueryOrderTool(provider))
        ToolRegistry.register(CancelOrderTool(provider))
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 1: 查门店
    // ════════════════════════════════════════════════════════════════

    private class QueryShopsTool(private val provider: CoffeeOrderProvider) : AiTool {
        override val name = "luckin_query_shops"
        override val description = "查询附近的瑞幸咖啡门店。需要经纬度坐标。可按门店名筛选。"
        override val parametersJsonSchema = """
            {"type":"object","properties":{"longitude":{"type":"number","description":"经度"},"latitude":{"type":"number","description":"纬度"},"deptName":{"type":"string","description":"门店名筛选词（可选）"}},"required":["longitude","latitude"]}
        """.trimIndent()

        override suspend fun execute(argumentsJson: String): String {
            if (!provider.isAvailable()) return """{"error":"瑞幸 Token 未配置，请在设置中配置"}"""
            val obj = json.parseToJsonElement(argumentsJson).jsonObject
            val longitude = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val latitude = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val deptName = obj["deptName"]?.jsonPrimitive?.contentOrNull
            return provider.queryShops(longitude, latitude, deptName)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 2: 搜商品
    // ════════════════════════════════════════════════════════════════

    private class SearchProductsTool(private val provider: CoffeeOrderProvider) : AiTool {
        override val name = "luckin_search_products"
        override val description = "在指定瑞幸门店搜索商品。返回商品名、SKU编码、到手价等。"
        override val parametersJsonSchema = """
            {"type":"object","properties":{"deptId":{"type":"integer","description":"门店ID"},"query":{"type":"string","description":"搜索关键词，如 生椰拿铁"}},"required":["deptId","query"]}
        """.trimIndent()

        override suspend fun execute(argumentsJson: String): String {
            if (!provider.isAvailable()) return """{"error":"瑞幸 Token 未配置"}"""
            val obj = json.parseToJsonElement(argumentsJson).jsonObject
            val deptId = obj["deptId"]?.jsonPrimitive?.longOrNull ?: 0L
            val query = obj["query"]?.jsonPrimitive?.contentOrNull ?: ""
            return provider.searchProducts(deptId, query)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 3: 预览订单
    // ════════════════════════════════════════════════════════════════

    private class PreviewOrderTool(private val provider: CoffeeOrderProvider) : AiTool {
        override val name = "luckin_preview_order"
        override val description = "预览瑞幸订单，获取真实到手价、优惠金额、商品明细。下单前必先预览。"
        override val parametersJsonSchema = """
            {"type":"object","properties":{"deptId":{"type":"integer","description":"门店ID"},"productList":{"type":"array","items":{"type":"object","properties":{"amount":{"type":"integer"},"productId":{"type":"integer"},"skuCode":{"type":"string"}},"required":["amount","productId","skuCode"]}}},"required":["deptId","productList"]}
        """.trimIndent()

        override suspend fun execute(argumentsJson: String): String {
            if (!provider.isAvailable()) return """{"error":"瑞幸 Token 未配置"}"""
            val obj = json.parseToJsonElement(argumentsJson).jsonObject
            val deptId = obj["deptId"]?.jsonPrimitive?.longOrNull ?: 0L
            val productListJson = obj["productList"]?.toString() ?: "[]"
            return provider.previewOrder(deptId, productListJson)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 4: 下单（涉及支付，ChatViewModel 侧做用户确认）
    // ════════════════════════════════════════════════════════════════

    private class CreateOrderTool(private val provider: CoffeeOrderProvider) : AiTool {
        override val name = "luckin_create_order"
        override val description = "创建瑞幸订单并生成支付二维码。注意：此操作会产生真实订单，请先预览并经用户确认后再调用。返回支付链接和二维码URL。"
        override val parametersJsonSchema = """
            {"type":"object","properties":{"deptId":{"type":"integer","description":"门店ID"},"productList":{"type":"array","items":{"type":"object","properties":{"amount":{"type":"integer"},"productId":{"type":"integer"},"skuCode":{"type":"string"}},"required":["amount","productId","skuCode"]}},"longitude":{"type":"number","description":"经度"},"latitude":{"type":"number","description":"纬度"},"remark":{"type":"string","description":"订单备注（可选）"}},"required":["deptId","productList","longitude","latitude"]}
        """.trimIndent()

        override suspend fun execute(argumentsJson: String): String {
            if (!provider.isAvailable()) return """{"error":"瑞幸 Token 未配置"}"""
            val obj = json.parseToJsonElement(argumentsJson).jsonObject
            val deptId = obj["deptId"]?.jsonPrimitive?.longOrNull ?: 0L
            val productListJson = obj["productList"]?.toString() ?: "[]"
            val longitude = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val latitude = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val remark = obj["remark"]?.jsonPrimitive?.contentOrNull
            return provider.createOrder(deptId, productListJson, longitude, latitude, remark)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 5: 查订单详情
    // ════════════════════════════════════════════════════════════════

    private class QueryOrderTool(private val provider: CoffeeOrderProvider) : AiTool {
        override val name = "luckin_query_order"
        override val description = "查询瑞幸订单详情，包括状态、取餐码、门店、商品明细、支付金额。"
        override val parametersJsonSchema = """
            {"type":"object","properties":{"orderId":{"type":"string","description":"订单号"}},"required":["orderId"]}
        """.trimIndent()

        override suspend fun execute(argumentsJson: String): String {
            if (!provider.isAvailable()) return """{"error":"瑞幸 Token 未配置"}"""
            val obj = json.parseToJsonElement(argumentsJson).jsonObject
            val orderId = obj["orderId"]?.jsonPrimitive?.contentOrNull ?: ""
            return provider.queryOrderDetail(orderId)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 6: 取消订单
    // ════════════════════════════════════════════════════════════════

    private class CancelOrderTool(private val provider: CoffeeOrderProvider) : AiTool {
        override val name = "luckin_cancel_order"
        override val description = "取消瑞幸订单。仅未支付或制作中的订单可取消。"
        override val parametersJsonSchema = """
            {"type":"object","properties":{"orderId":{"type":"string","description":"订单号"}},"required":["orderId"]}
        """.trimIndent()

        override suspend fun execute(argumentsJson: String): String {
            if (!provider.isAvailable()) return """{"error":"瑞幸 Token 未配置"}"""
            val obj = json.parseToJsonElement(argumentsJson).jsonObject
            val orderId = obj["orderId"]?.jsonPrimitive?.contentOrNull ?: ""
            return provider.cancelOrder(orderId)
        }
    }
}
