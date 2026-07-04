package com.lianyu.ai.feature.coffee

import android.app.Application
import com.lianyu.ai.domain.CoffeeOrderProvider
import com.lianyu.ai.feature.coffee.data.LuckinMcpClient
import com.lianyu.ai.feature.coffee.data.LuckinTokenStore
import com.lianyu.ai.feature.coffee.data.model.ProductListItem
import com.lianyu.ai.feature.coffee.domain.CoffeeOrderRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * [CoffeeOrderProvider] 的 feature:coffee 实现。
 *
 * 包装 [CoffeeOrderRepository] 的 MCP 工具调用，对外暴露为 JSON 字符串接口。
 * 由 app 装配时通过 ServiceRegistry 注册，供 feature:chat 的 AI 工具调用使用。
 *
 * @param app Application（用于初始化 LuckinTokenStore）
 */
class CoffeeOrderProviderImpl(app: Application) : CoffeeOrderProvider {

    private val tokenStore = LuckinTokenStore(app)
    private val client = LuckinMcpClient()
    private val repository = CoffeeOrderRepository(client, tokenStore)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    override suspend fun isAvailable(): Boolean = try {
        tokenStore.token.first().isNotBlank()
    } catch (e: Exception) {
        false
    }

    override suspend fun queryShops(longitude: Double, latitude: Double, deptName: String?): String {
        val shops = repository.queryShopList(longitude, latitude, deptName)
        val arr = buildJsonArray {
            for (shop in shops) {
                add(buildJsonObject {
                    put("deptId", shop.deptId)
                    put("deptName", shop.deptName)
                    put("address", shop.address)
                    put("distance", shop.distance)
                    put("workTime", "${shop.workTimeStart}-${shop.workTimeEnd}")
                })
            }
        }
        return json.encodeToString(JsonArray.serializer(), arr)
    }

    override suspend fun searchProducts(deptId: Long, query: String): String {
        val products = repository.searchProduct(deptId, query)
        val arr = buildJsonArray {
            for (p in products) {
                add(buildJsonObject {
                    put("productId", p.productId)
                    put("productName", p.productName)
                    put("skuCode", p.skuCode)
                    put("pictureUrl", p.pictureUrl)
                    put("initialPrice", p.initialPrice)
                    put("estimatePrice", p.estimatePrice)
                })
            }
        }
        return json.encodeToString(JsonArray.serializer(), arr)
    }

    override suspend fun previewOrder(deptId: Long, productListJson: String): String {
        val productList = parseProductList(productListJson)
        val preview = repository.previewOrder(deptId, productList)
        val obj = buildJsonObject {
            put("discountPrice", preview.discountPrice)
            put("totalInitialPrice", preview.totalInitialPrice)
            put("privilegeMoney", preview.privilegeMoney)
            putJsonArray("productInfoList") {
                for (p in preview.productInfoList) {
                    add(buildJsonObject {
                        put("name", p.name)
                        put("amount", p.amount)
                        put("additionDesc", p.additionDesc)
                        put("estimateTotalPrice", p.estimateTotalPrice)
                    })
                }
            }
            putJsonArray("couponCodeList") {
                for (c in preview.couponCodeList) { add(JsonPrimitive(c)) }
            }
        }
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    override suspend fun createOrder(
        deptId: Long,
        productListJson: String,
        longitude: Double,
        latitude: Double,
        remark: String?
    ): String {
        val productList = parseProductList(productListJson)
        val order = repository.createOrder(deptId, productList, longitude, latitude, null, remark)
        val obj = buildJsonObject {
            put("orderIdStr", order.orderIdStr)
            put("payOrderUrl", order.payOrderUrl)
            put("payOrderQrCodeUrl", order.payOrderQrCodeUrl)
            put("needPay", order.needPay)
            put("discountPrice", order.discountPrice)
        }
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    override suspend fun queryOrderDetail(orderId: String): String {
        val detail = repository.queryOrderDetail(orderId)
        val obj = buildJsonObject {
            put("orderId", detail.orderId)
            put("orderStatus", detail.orderStatus)
            put("orderStatusName", detail.orderStatusName)
            put("takeMealCode", detail.takeMealCodeInfo?.code ?: "")
            put("shopName", detail.shopInfo?.deptName ?: "")
            put("shopAddress", detail.shopInfo?.address ?: "")
            put("orderPayAmount", detail.orderPayAmount)
            putJsonArray("productInfoList") {
                detail.productInfoList?.forEach { p ->
                    add(buildJsonObject {
                        put("name", p.name)
                        put("amount", p.amount)
                        put("additionDesc", p.additionDesc)
                    })
                }
            }
            putJsonArray("orderCommodityList") {
                for (c in detail.orderCommodityList) {
                    add(buildJsonObject {
                        put("commodityName", c.commodityName)
                        put("payMoney", c.payMoney)
                    })
                }
            }
        }
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    override suspend fun cancelOrder(orderId: String): String {
        val success = repository.cancelOrder(orderId)
        val obj = buildJsonObject { put("success", success) }
        return json.encodeToString(JsonElement.serializer(), obj)
    }

    /** 解析 productList JSON 字符串为 [ProductListItem] 列表 */
    private fun parseProductList(jsonStr: String): List<ProductListItem> {
        val array = json.parseToJsonElement(jsonStr).jsonArray
        return array.map { element ->
            val obj = element.jsonObject
            ProductListItem(
                amount = obj["amount"]?.jsonPrimitive?.intOrNull ?: 1,
                productId = obj["productId"]?.jsonPrimitive?.longOrNull ?: 0L,
                skuCode = obj["skuCode"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        }
    }
}
