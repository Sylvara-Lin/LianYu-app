package com.lianyu.ai.feature.coffee.domain

import com.lianyu.ai.feature.coffee.data.LuckinMcpClient
import com.lianyu.ai.feature.coffee.data.LuckinTokenStore
import com.lianyu.ai.feature.coffee.data.McpException
import com.lianyu.ai.feature.coffee.data.model.OrderCreated
import com.lianyu.ai.feature.coffee.data.model.OrderDetail
import com.lianyu.ai.feature.coffee.data.model.OrderPreview
import com.lianyu.ai.feature.coffee.data.model.ProductInfo
import com.lianyu.ai.feature.coffee.data.model.ProductListItem
import com.lianyu.ai.feature.coffee.data.model.ShopInfo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 瑞幸咖啡订单仓库。
 *
 * 封装 8 个 MCP 工具调用，对上层提供领域语义 API。
 * 严格遵循下单流程约束：
 *   确认门店 → 确认商品（含属性定制） → previewOrder → createOrder（不可跳步）
 *
 * @throws McpException 当 Token 无效或 MCP 调用失败 / 瑞幸业务 code != 0 时抛出
 */
class CoffeeOrderRepository(
    private val client: LuckinMcpClient,
    private val tokenStore: LuckinTokenStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** 获取已存储的 Token，未配置时抛出 IllegalStateException */
    private suspend fun requireToken(): String {
        val token = tokenStore.token.first()
        if (token.isBlank()) {
            throw IllegalStateException("请先配置瑞幸 MCP Token")
        }
        return token
    }

    /**
     * 通用解码：callTool 返回的是业务 data 节点（JsonElement），
     * 直接 decode 为目标类型。data 可能是对象、数组或基本类型。
     */
    private inline fun <reified T> JsonElement.decodeAsList(): List<T> =
        json.decodeFromJsonElement(this)

    // ════════════════════════════════════════════════════════════════
    // 工具 1: queryShopList — 查询门店
    // ════════════════════════════════════════════════════════════════

    suspend fun queryShopList(
        longitude: Double,
        latitude: Double,
        deptName: String? = null
    ): List<ShopInfo> {
        val token = requireToken()
        val args = buildJsonObject {
            put("longitude", longitude)
            put("latitude", latitude)
            if (!deptName.isNullOrBlank()) {
                put("deptName", deptName)
            }
        }
        val data = client.callTool(token, "queryShopList", args)
        // data 是 List<ShopInfo>
        return data.decodeAsList()
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 2: searchProductForMcp — 搜索商品
    // ════════════════════════════════════════════════════════════════

    suspend fun searchProduct(deptId: Long, query: String): List<ProductInfo> {
        val token = requireToken()
        val args = buildJsonObject {
            put("deptId", deptId)
            put("query", query)
        }
        val data = client.callTool(token, "searchProductForMcp", args)
        // data 是 List<ProductInfo>
        return data.decodeAsList()
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 3: queryProductDetailInfo — 商品详情（属性定制入口）
    // ════════════════════════════════════════════════════════════════

    suspend fun queryProductDetail(deptId: Long, productId: Long): ProductInfo {
        val token = requireToken()
        val args = buildJsonObject {
            put("deptId", deptId)
            put("productId", productId)
        }
        val data = client.callTool(token, "queryProductDetailInfo", args)
        // data 是单个 ProductInfo
        return json.decodeFromJsonElement(data)
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 4: switchProduct — 切换 SKU（属性定制）
    // operation=3 表示选中
    // ════════════════════════════════════════════════════════════════

    suspend fun switchProduct(
        deptId: Long,
        productId: Long,
        skuCode: String,
        attributeId: Long,
        subAttributeId: Long,
        operation: Int = 3,
        amount: Int
    ): ProductInfo {
        val token = requireToken()
        val args = buildJsonObject {
            put("deptId", deptId)
            put("productId", productId)
            put("skuCode", skuCode)
            put("amount", amount)
            putJsonObject("attrOperationParam") {
                put("attributeId", attributeId)
                putJsonObject("subAttr") {
                    put("attributeId", subAttributeId)
                    put("operation", operation)
                }
            }
        }
        val data = client.callTool(token, "switchProduct", args)
        // data 是切换后的 ProductInfo
        return json.decodeFromJsonElement(data)
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 5: previewOrder — 订单预览（获取真实价格+优惠券）
    // ════════════════════════════════════════════════════════════════

    suspend fun previewOrder(
        deptId: Long,
        productList: List<ProductListItem>
    ): OrderPreview {
        val token = requireToken()
        val args = buildJsonObject {
            put("deptId", deptId)
            putJsonArray("productList") {
                productList.forEach { item ->
                    add(buildJsonObject {
                        put("amount", item.amount)
                        put("productId", item.productId)
                        put("skuCode", item.skuCode)
                    })
                }
            }
        }
        val data = client.callTool(token, "previewOrder", args)
        return json.decodeFromJsonElement(data)
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 6: createOrder — 创建订单（生成支付二维码）
    // ════════════════════════════════════════════════════════════════

    suspend fun createOrder(
        deptId: Long,
        productList: List<ProductListItem>,
        longitude: Double,
        latitude: Double,
        couponCodeList: List<String>? = null,
        remark: String? = null
    ): OrderCreated {
        val token = requireToken()
        val args = buildJsonObject {
            put("deptId", deptId)
            put("longitude", longitude)
            put("latitude", latitude)
            putJsonArray("productList") {
                productList.forEach { item ->
                    add(buildJsonObject {
                        put("amount", item.amount)
                        put("productId", item.productId)
                        put("skuCode", item.skuCode)
                    })
                }
            }
            if (!couponCodeList.isNullOrEmpty()) {
                putJsonArray("couponCodeList") {
                    couponCodeList.forEach { add(JsonPrimitive(it)) }
                }
            }
            if (!remark.isNullOrBlank()) {
                put("remark", remark)
            }
        }
        val data = client.callTool(token, "createOrder", args)
        return json.decodeFromJsonElement(data)
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 7: queryOrderDetailInfo — 查询订单详情
    // ════════════════════════════════════════════════════════════════

    suspend fun queryOrderDetail(orderId: String): OrderDetail {
        val token = requireToken()
        val args = buildJsonObject {
            put("orderId", orderId)
        }
        val data = client.callTool(token, "queryOrderDetailInfo", args)
        return json.decodeFromJsonElement(data)
    }

    // ════════════════════════════════════════════════════════════════
    // 工具 8: cancelOrder — 取消订单
    // 返回 data 是 boolean（是否取消成功）
    // ════════════════════════════════════════════════════════════════

    suspend fun cancelOrder(orderId: String): Boolean {
        val token = requireToken()
        val args = buildJsonObject {
            put("orderId", orderId)
        }
        return try {
            val data = client.callTool(token, "cancelOrder", args)
            // data 是 boolean，兼容 JsonPrimitive / 字符串
            when (data) {
                is JsonPrimitive -> data.content.toBooleanStrictOrNull() ?: false
                else -> data.toString().trim('"').toBooleanStrictOrNull() ?: false
            }
        } catch (e: McpException) {
            false
        }
    }
}
