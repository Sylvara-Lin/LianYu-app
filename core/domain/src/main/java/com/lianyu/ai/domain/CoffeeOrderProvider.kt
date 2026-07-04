package com.lianyu.ai.domain

/**
 * 瑞幸咖啡订单服务提供者接口。
 *
 * 由 feature:coffee 实现，通过 ServiceRegistry 注入到 feature:chat，
 * 供 AI 工具调用（[AiTool] 实现）使用。
 *
 * 设计原则（遵循 core:domain 零依赖）：
 * - 方法返回 JSON 字符串而非强类型对象，避免把 feature:coffee 的数据类下沉
 * - 参数用基本类型 + JSON 字符串，不引入 kotlinx.serialization 依赖
 * - Token 状态通过 [isAvailable] 暴露，不回显 Token 本身
 */
interface CoffeeOrderProvider {
    /** Token 是否已配置（AI 可据此判断能否调用咖啡工具） */
    suspend fun isAvailable(): Boolean

    /**
     * 查询附近门店。
     * @return JSON 字符串：[{"deptId","deptName","address","distance","workTime"},...]
     */
    suspend fun queryShops(longitude: Double, latitude: Double, deptName: String?): String

    /**
     * 搜索商品。
     * @param deptId 门店 ID
     * @param query 搜索关键词（如"生椰拿铁"）
     * @return JSON 字符串：[{"productId","productName","skuCode","pictureUrl","estimatePrice"},...]
     */
    suspend fun searchProducts(deptId: Long, query: String): String

    /**
     * 预览订单（获取真实价格 + 优惠）。
     * @param productListJson JSON 数组：[{"amount":1,"productId":123,"skuCode":"xxx"},...]
     * @return JSON 字符串：{"discountPrice","productInfoList":[...],"privilegeMoney",...}
     */
    suspend fun previewOrder(deptId: Long, productListJson: String): String

    /**
     * 创建订单（生成支付二维码）。
     * @param productListJson JSON 数组：[{"amount":1,"productId":123,"skuCode":"xxx"},...]
     * @return JSON 字符串：{"orderIdStr","payOrderUrl","payOrderQrCodeUrl","needPay","discountPrice"}
     */
    suspend fun createOrder(
        deptId: Long,
        productListJson: String,
        longitude: Double,
        latitude: Double,
        remark: String?
    ): String

    /**
     * 查询订单详情。
     * @return JSON 字符串：{"orderId","orderStatusName","takeMealCodeInfo","shopInfo",...}
     */
    suspend fun queryOrderDetail(orderId: String): String

    /**
     * 取消订单。
     * @return JSON 字符串：{"success":true/false}
     */
    suspend fun cancelOrder(orderId: String): String
}
