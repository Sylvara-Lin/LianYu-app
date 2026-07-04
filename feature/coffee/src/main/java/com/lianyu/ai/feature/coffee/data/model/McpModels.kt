package com.lianyu.ai.feature.coffee.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ════════════════════════════════════════════════════════════════
// 工具参数模型（按瑞幸官方文档定义，字段名严格对齐）
// 文档来源：https://open.lkcoffee.com/docs （SPA JS bundle 提取的工具目录）
// ════════════════════════════════════════════════════════════════

/** queryShopList 参数 */
@Serializable
internal data class QueryShopArgs(
    val longitude: Double,
    val latitude: Double,
    @SerialName("deptName") val deptName: String? = null
)

/** searchProductForMcp 参数 */
@Serializable
internal data class SearchProductArgs(
    @SerialName("deptId") val deptId: Long,
    val query: String
)

/** queryProductDetailInfo 参数（官方只定义 deptId + productId，无 delivery） */
@Serializable
internal data class ProductDetailArgs(
    @SerialName("deptId") val deptId: Long,
    @SerialName("productId") val productId: Long
)

/** switchProduct 参数 */
@Serializable
internal data class SwitchProductArgs(
    @SerialName("deptId") val deptId: Long,
    @SerialName("productId") val productId: Long,
    @SerialName("skuCode") val skuCode: String,
    @SerialName("attrOperationParam") val attrOperationParam: AttrOperationParam,
    val amount: Int
)

@Serializable
internal data class AttrOperationParam(
    @SerialName("attributeId") val attributeId: Long,
    @SerialName("subAttr") val subAttr: SubAttr
)

@Serializable
internal data class SubAttr(
    @SerialName("attributeId") val attributeId: Long,
    val operation: Int
)

/** previewOrder / createOrder 商品项 */
@Serializable
data class ProductListItem(
    val amount: Int,
    @SerialName("productId") val productId: Long,
    @SerialName("skuCode") val skuCode: String
)

/** previewOrder 参数 */
@Serializable
internal data class PreviewOrderArgs(
    @SerialName("deptId") val deptId: Long,
    @SerialName("productList") val productList: List<ProductListItem>
)

/** createOrder 参数 */
@Serializable
internal data class CreateOrderArgs(
    @SerialName("deptId") val deptId: Long,
    @SerialName("productList") val productList: List<ProductListItem>,
    val longitude: Double,
    val latitude: Double,
    @SerialName("couponCodeList") val couponCodeList: List<String>? = null,
    val remark: String? = null
)

/** queryOrderDetailInfo / cancelOrder 参数 */
@Serializable
internal data class OrderIdArgs(
    @SerialName("orderId") val orderId: String
)

// ════════════════════════════════════════════════════════════════
// 工具返回业务模型（字段名严格对齐官方文档）
// ════════════════════════════════════════════════════════════════

/** 门店信息（queryShopList 输出项、previewOrder/queryOrderDetailInfo 的 shopInfo） */
@Serializable
data class ShopInfo(
    @SerialName("deptId") val deptId: Long = 0,
    @SerialName("deptName") val deptName: String = "",
    val address: String = "",
    @SerialName("deptTags") val deptTags: List<String> = emptyList(),
    val longitude: Double = 0.0,
    val latitude: Double = 0.0,
    @SerialName("workTimeStart") val workTimeStart: String = "",
    @SerialName("workTimeEnd") val workTimeEnd: String = "",
    val distance: Double = 0.0,
    val number: String = ""
)

/** queryShopList 返回的 data 是 List<ShopInfo> */

/**
 * 商品属性值（productSubAttrs 子项）。
 * searchProductForMcp / queryProductDetailInfo / switchProduct 共用。
 */
@Serializable
data class ProductSubAttr(
    @SerialName("attributeId") val attributeId: Long = 0,
    @SerialName("attributeName") val attributeName: String = "",
    /** 是否选中（null/true/false，null 表示未指定） */
    val selected: Boolean? = null,
    /** 属性加价 */
    val price: Double = 0.0,
    /** 是否可选（null/0/1，null 表示未指定） */
    @SerialName("canSelected") val canSelected: Int? = null
)

/**
 * 商品属性组（productAttrs 项）。
 */
@Serializable
data class ProductAttrGroup(
    @SerialName("attributeId") val attributeId: Long = 0,
    @SerialName("attributeName") val attributeName: String = "",
    @SerialName("productSubAttrs") val productSubAttrs: List<ProductSubAttr> = emptyList()
)

/**
 * 商品信息（searchProductForMcp 输出项、queryProductDetailInfo/switchProduct 输出对象）。
 * 三个工具返回结构完全一致，统一用一个模型。
 */
@Serializable
data class ProductInfo(
    @SerialName("productId") val productId: Long = 0,
    @SerialName("productName") val productName: String = "",
    @SerialName("skuCode") val skuCode: String = "",
    @SerialName("pictureUrl") val pictureUrl: String = "",
    @SerialName("productAttrs") val productAttrs: List<ProductAttrGroup> = emptyList(),
    val tags: List<String>? = null,
    @SerialName("initialPrice") val initialPrice: Double = 0.0,
    @SerialName("estimatePrice") val estimatePrice: Double = 0.0
)

/** queryProductDetailInfo / switchProduct 返回的 data 是单个 ProductInfo（对象，非数组） */

/**
 * previewOrder / queryOrderDetailInfo 的 productInfoList 子项。
 */
@Serializable
data class OrderProduct(
    @SerialName("productId") val productId: Long = 0,
    @SerialName("skuCode") val skuCode: String = "",
    val name: String = "",
    val amount: Int = 0,
    @SerialName("additionDesc") val additionDesc: String = "",
    @SerialName("bigPicUrl") val bigPicUrl: String? = null,
    @SerialName("breviaryPicUrl") val breviaryPicUrl: String? = null,
    @SerialName("initPrice") val initPrice: Double = 0.0,
    @SerialName("estimatePrice") val estimatePrice: Double = 0.0,
    @SerialName("estimateTotalPrice") val estimateTotalPrice: Double = 0.0
)

/** 订单商品粒度价格信息（previewOrder.orderGranularCommodityList / queryOrderDetailInfo.orderCommodityList） */
@Serializable
data class OrderCommodity(
    @SerialName("commodityId") val commodityId: Long = 0,
    @SerialName("commodityCode") val commodityCode: String = "",
    @SerialName("commodityName") val commodityName: String = "",
    @SerialName("payableMoney") val payableMoney: Double = 0.0,
    @SerialName("payMoney") val payMoney: Double = 0.0
)

/** 取餐码信息 */
@Serializable
data class TakeMealCodeInfo(
    val code: String = "",
    @SerialName("takeOrderId") val takeOrderId: String = ""
)

/** 配送信息（queryOrderDetailInfo.dispatchInfo） */
@Serializable
data class DispatchInfo(
    @SerialName("dispatcherName") val dispatcherName: String = "",
    @SerialName("dispatcherMobile") val dispatcherMobile: String = "",
    @SerialName("dispatchAboutTime") val dispatchAboutTime: String = "",
    @SerialName("destinationDistance") val destinationDistance: Double = 0.0
)

/** previewOrder 返回 */
@Serializable
data class OrderPreview(
    @SerialName("aboutTime") val aboutTime: Long = 0,
    @SerialName("discountPrice") val discountPrice: Double = 0.0,
    @SerialName("shopInfo") val shopInfo: ShopInfo? = null,
    @SerialName("productInfoList") val productInfoList: List<OrderProduct> = emptyList(),
    @SerialName("couponCodeList") val couponCodeList: List<String> = emptyList(),
    @SerialName("orderGranularCommodityList") val orderGranularCommodityList: List<OrderCommodity> = emptyList(),
    @SerialName("expressExpectTime") val expressExpectTime: Long? = null,
    @SerialName("privilegeMoney") val privilegeMoney: Double = 0.0,
    @SerialName("totalInitialPrice") val totalInitialPrice: Double = 0.0
)

/** createOrder 返回 */
@Serializable
data class OrderCreated(
    @SerialName("orderId") val orderId: Long = 0,
    @SerialName("orderIdStr") val orderIdStr: String = "",
    @SerialName("payOrderUrl") val payOrderUrl: String = "",
    @SerialName("payOrderQrCodeUrl") val payOrderQrCodeUrl: String = "",
    @SerialName("discountPrice") val discountPrice: Double = 0.0,
    @SerialName("needPay") val needPay: Boolean = true,
    @SerialName("tradeNo") val tradeNo: String? = null,
    val description: String? = null,
    @SerialName("businessNotifyUrl") val businessNotifyUrl: String? = null,
    @SerialName("subMchid") val subMchid: String? = null
)

/** 订单状态（queryOrderDetailInfo 返回） */
@Serializable
data class OrderDetail(
    @SerialName("orderId") val orderId: String = "",
    @SerialName("orderStatus") val orderStatus: Int = 0,
    @SerialName("orderStatusName") val orderStatusName: String = "",
    @SerialName("aboutTime") val aboutTime: Long = 0,
    @SerialName("takeMealTime") val takeMealTime: String = "",
    @SerialName("takeMealCodeInfo") val takeMealCodeInfo: TakeMealCodeInfo? = null,
    @SerialName("shopInfo") val shopInfo: ShopInfo? = null,
    @SerialName("productInfoList") val productInfoList: List<OrderProduct>? = null,
    @SerialName("orderPayAmount") val orderPayAmount: Double = 0.0,
    @SerialName("dispatchInfo") val dispatchInfo: DispatchInfo? = null,
    @SerialName("orderCommodityList") val orderCommodityList: List<OrderCommodity> = emptyList(),
    @SerialName("orderType") val orderType: String = "",
    @SerialName("customerParams") val customerParams: String? = null
) {
    companion object {
        // 订单状态码（来自瑞幸文档）
        const val STATUS_UNPAID = 10
        const val STATUS_SUCCESS = 20
        const val STATUS_MAKING = 30
        const val STATUS_WAITING = 60
        const val STATUS_DONE = 80
        const val STATUS_CANCELED = 100
    }
}

/** cancelOrder 返回 data 是 boolean */

// ════════════════════════════════════════════════════════════════
// 订单历史（本地持久化，非 MCP 工具）
// ════════════════════════════════════════════════════════════════

/** 订单历史条目，createOrder 成功后写入本地 */
@Serializable
data class OrderHistoryEntry(
    @SerialName("orderIdStr") val orderIdStr: String,
    @SerialName("deptName") val deptName: String = "",
    @SerialName("discountPrice") val discountPrice: Double = 0.0,
    @SerialName("createdAt") val createdAt: Long
)

/**
 * 已选商品（UI 层领域模型，融合搜索结果 + 定制后的 skuCode）。
 * 替代旧的 Pair<ProductInfo, Int>，携带定制后的真实 skuCode 和到手价。
 */
data class SelectedProduct(
    val productId: Long,
    val productName: String,
    val pictureUrl: String,
    val skuCode: String,
    val amount: Int,
    val estimatePrice: Double
)
