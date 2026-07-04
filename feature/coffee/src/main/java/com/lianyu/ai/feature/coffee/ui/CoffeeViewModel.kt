package com.lianyu.ai.feature.coffee.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lianyu.ai.feature.coffee.data.LuckinMcpClient
import com.lianyu.ai.feature.coffee.data.LuckinTokenStore
import com.lianyu.ai.feature.coffee.data.McpException
import com.lianyu.ai.feature.coffee.data.model.OrderCreated
import com.lianyu.ai.feature.coffee.data.model.OrderDetail
import com.lianyu.ai.feature.coffee.data.model.OrderHistoryEntry
import com.lianyu.ai.feature.coffee.data.model.OrderPreview
import com.lianyu.ai.feature.coffee.data.model.ProductInfo
import com.lianyu.ai.feature.coffee.data.model.ProductListItem
import com.lianyu.ai.feature.coffee.data.model.SelectedProduct
import com.lianyu.ai.feature.coffee.data.model.ShopInfo
import com.lianyu.ai.feature.coffee.domain.CoffeeOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 下单流程步骤（严格约束：不可跳步）
 * TOKEN_CONFIG 已迁移到独立设置页，不再作为下单步骤
 */
enum class OrderStep {
    /** 选择门店 */
    SHOP_SELECT,
    /** 搜索并选择商品 */
    PRODUCT_SELECT,
    /** 确认订单（展示明细+预估价） */
    ORDER_CONFIRM,
    /** 订单预览（previewOrder 返回真实价格+优惠） */
    ORDER_PREVIEW,
    /** 支付（展示二维码） */
    PAYMENT,
    /** 订单状态查询 */
    ORDER_STATUS
}

/**
 * UI 状态
 */
data class CoffeeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isTokenConfigured: Boolean = false,
    val currentStep: OrderStep = OrderStep.SHOP_SELECT,
    val shops: List<ShopInfo> = emptyList(),
    val selectedShop: ShopInfo? = null,
    val searchQuery: String = "",
    val products: List<ProductInfo> = emptyList(),
    val selectedProducts: List<SelectedProduct> = emptyList(),
    val orderPreview: OrderPreview? = null,
    val createdOrder: OrderCreated? = null,
    val orderDetail: OrderDetail? = null,
    val queryOrderId: String = "",
    val longitude: Double = 0.0,
    val latitude: Double = 0.0,
    val hasPreciseLocation: Boolean = false,
    // === 商品定制状态 ===
    val productCustomizing: ProductInfo? = null,
    val customizingDeptId: Long = 0,
    val customizingAmount: Int = 1,
    val orderHistory: List<OrderHistoryEntry> = emptyList(),
    /** 订单备注（createOrder 可选参数 remark） */
    val remark: String = ""
)

class CoffeeViewModel(
    private val repository: CoffeeOrderRepository,
    private val tokenStore: LuckinTokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoffeeUiState())
    val uiState: StateFlow<CoffeeUiState> = _uiState.asStateFlow()

    init {
        checkTokenStatus()
        loadOrderHistory()
    }

    private fun checkTokenStatus() {
        viewModelScope.launch {
            val token = tokenStore.token.first()
            _uiState.value = _uiState.value.copy(
                isTokenConfigured = token.isNotBlank()
            )
        }
    }

    private fun loadOrderHistory() {
        viewModelScope.launch {
            val history = tokenStore.snapshotOrderHistory()
            _uiState.value = _uiState.value.copy(orderHistory = history)
        }
    }

    fun refreshTokenStatus() = checkTokenStatus()

    // ════════════════════════════════════════════════════════════════
    // Token 管理（仅做保存/清除，UI 在独立设置页）
    // ════════════════════════════════════════════════════════════════

    suspend fun saveToken(token: String) {
        tokenStore.saveToken(token)
        _uiState.value = _uiState.value.copy(isTokenConfigured = token.isNotBlank())
    }

    suspend fun clearToken() {
        tokenStore.clearToken()
        _uiState.value = _uiState.value.copy(isTokenConfigured = false)
    }

    suspend fun tokenSavedDaysAgo(): Int = tokenStore.tokenSavedDaysAgo()

    // ════════════════════════════════════════════════════════════════
    // 门店查询
    // ════════════════════════════════════════════════════════════════

    fun updateLocation(longitude: Double, latitude: Double, precise: Boolean) {
        _uiState.value = _uiState.value.copy(
            longitude = longitude,
            latitude = latitude,
            hasPreciseLocation = precise
        )
    }

    fun queryShops(deptName: String? = null) {
        val state = _uiState.value
        if (state.longitude == 0.0 || state.latitude == 0.0) {
            _uiState.value = state.copy(errorMessage = "需要定位来查找附近门店")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val shops = repository.queryShopList(
                    longitude = state.longitude,
                    latitude = state.latitude,
                    deptName = deptName
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    shops = shops,
                    errorMessage = if (shops.isEmpty()) "未找到门店，请更换位置重试" else null
                )
            } catch (e: McpException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (e.isAuthError) "Token 无效或已过期，请前往设置重新获取" else e.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "网络错误: ${e.message}"
                )
            }
        }
    }

    fun selectShop(shop: ShopInfo) {
        _uiState.value = _uiState.value.copy(
            selectedShop = shop,
            currentStep = OrderStep.PRODUCT_SELECT,
            products = emptyList(),
            searchQuery = ""
        )
    }

    // ════════════════════════════════════════════════════════════════
    // 商品搜索
    // ════════════════════════════════════════════════════════════════

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun searchProducts() {
        val state = _uiState.value
        val shop = state.selectedShop
        if (shop == null || state.searchQuery.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val products = repository.searchProduct(shop.deptId, state.searchQuery)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    products = products,
                    errorMessage = if (products.isEmpty()) "未找到相关商品" else null
                )
            } catch (e: McpException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (e.isAuthError) "Token 无效或已过期" else e.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "搜索失败: ${e.message}"
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 商品定制（queryProductDetailInfo + switchProduct）
    // ════════════════════════════════════════════════════════════════

    /** 加载商品详情，进入定制页前调用 */
    fun loadProductDetail(deptId: Long, productId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val detail = repository.queryProductDetail(deptId, productId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    productCustomizing = detail,
                    customizingDeptId = deptId,
                    customizingAmount = 1
                )
            } catch (e: McpException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (e.isAuthError) "Token 无效或已过期" else "加载商品详情失败: ${e.message}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载商品详情失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 切换属性选项（温度/杯型等）。
     * 调用 switchProduct 拿到新的 skuCode + estimatePrice，更新定制状态。
     */
    fun switchAttribute(attributeId: Long, subAttributeId: Long) {
        val state = _uiState.value
        val customizing = state.productCustomizing ?: return
        val deptId = state.customizingDeptId
        if (deptId == 0L) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val updated = repository.switchProduct(
                    deptId = deptId,
                    productId = customizing.productId,
                    skuCode = customizing.skuCode,
                    attributeId = attributeId,
                    subAttributeId = subAttributeId,
                    operation = 3,
                    amount = state.customizingAmount
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    productCustomizing = updated
                )
            } catch (e: McpException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (e.isAuthError) "Token 无效或已过期" else "属性切换失败: ${e.message}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "属性切换失败: ${e.message}"
                )
            }
        }
    }

    fun changeCustomizingAmount(delta: Int) {
        val newAmount = (_uiState.value.customizingAmount + delta).coerceAtLeast(1)
        _uiState.value = _uiState.value.copy(customizingAmount = newAmount)
    }

    fun clearCustomizing() {
        _uiState.value = _uiState.value.copy(
            productCustomizing = null,
            customizingAmount = 1
        )
    }

    /**
     * 把当前定制结果合并进 selectedProducts。
     * 若同 productId 已存在则累加数量（取新 skuCode 和到手价）。
     */
    fun confirmCustomizedProduct() {
        val state = _uiState.value
        val customizing = state.productCustomizing ?: return
        val amount = state.customizingAmount

        val selected = SelectedProduct(
            productId = customizing.productId,
            productName = customizing.productName,
            pictureUrl = customizing.pictureUrl,
            skuCode = customizing.skuCode,
            amount = amount,
            estimatePrice = customizing.estimatePrice
        )

        val current = state.selectedProducts.toMutableList()
        val existing = current.indexOfFirst { it.productId == selected.productId }
        if (existing >= 0) {
            // 同商品不同 SKU：以最新定制结果覆盖（瑞幸一个商品通常只有一个有效 SKU）
            current[existing] = selected.copy(amount = current[existing].amount + amount)
        } else {
            current.add(selected)
        }
        _uiState.value = _uiState.value.copy(
            selectedProducts = current,
            productCustomizing = null,
            customizingAmount = 1
        )
    }

    /** 快捷添加（用搜索返回的默认 SKU，不走进定制页） */
    fun addProductQuick(product: ProductInfo, amount: Int = 1) {
        if (product.skuCode.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "该商品暂无可下单规格")
            return
        }
        val selected = SelectedProduct(
            productId = product.productId,
            productName = product.productName,
            pictureUrl = product.pictureUrl,
            skuCode = product.skuCode,
            amount = amount,
            estimatePrice = product.estimatePrice
        )
        val current = _uiState.value.selectedProducts.toMutableList()
        val existing = current.indexOfFirst { it.productId == selected.productId }
        if (existing >= 0) {
            current[existing] = current[existing].copy(amount = current[existing].amount + amount)
        } else {
            current.add(selected)
        }
        _uiState.value = _uiState.value.copy(selectedProducts = current)
    }

    fun removeProduct(productId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedProducts = _uiState.value.selectedProducts.filterNot { it.productId == productId }
        )
    }

    fun goToOrderConfirm() {
        if (_uiState.value.selectedProducts.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先选择商品")
            return
        }
        _uiState.value = _uiState.value.copy(currentStep = OrderStep.ORDER_CONFIRM)
    }

    // ════════════════════════════════════════════════════════════════
    // 订单预览 + 创建（强约束：previewOrder → createOrder 不可跳步）
    // ════════════════════════════════════════════════════════════════

    fun previewOrder() {
        val state = _uiState.value
        val shop = state.selectedShop
        if (shop == null || state.selectedProducts.isEmpty()) return

        val productList = state.selectedProducts.map { it.toListItem() }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null, currentStep = OrderStep.ORDER_PREVIEW)
            try {
                val preview = repository.previewOrder(shop.deptId, productList)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    orderPreview = preview
                )
            } catch (e: McpException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (e.isAuthError) "Token 无效或已过期" else e.message,
                    currentStep = OrderStep.ORDER_CONFIRM
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "预览失败: ${e.message}",
                    currentStep = OrderStep.ORDER_CONFIRM
                )
            }
        }
    }

    fun createOrder(remark: String? = null) {
        val state = _uiState.value
        val shop = state.selectedShop
        val preview = state.orderPreview
        if (shop == null || preview == null || state.selectedProducts.isEmpty()) return

        val productList = state.selectedProducts.map { it.toListItem() }
        val actualRemark = remark ?: state.remark.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val order = repository.createOrder(
                    deptId = shop.deptId,
                    productList = productList,
                    longitude = shop.longitude,
                    latitude = shop.latitude,
                    couponCodeList = preview.couponCodeList.ifEmpty { null },
                    remark = actualRemark
                )
                // 写入订单历史
                tokenStore.addOrderHistory(order, shop.deptName)
                loadOrderHistory()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    createdOrder = order,
                    currentStep = OrderStep.PAYMENT
                )
            } catch (e: McpException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (e.isAuthError) "Token 无效或已过期" else e.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "下单失败: ${e.message}"
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 订单查询
    // ════════════════════════════════════════════════════════════════

    fun updateQueryOrderId(orderId: String) {
        _uiState.value = _uiState.value.copy(queryOrderId = orderId)
    }

    fun updateRemark(remark: String) {
        _uiState.value = _uiState.value.copy(remark = remark)
    }

    /**
     * 查询订单状态。可传 orderId（来自订单历史跳转），不传则用输入框的 queryOrderId。
     * 成功后同时写入 queryOrderId，便于后续取消订单复用。
     */
    fun queryOrderStatus(orderId: String? = null) {
        val target = orderId?.takeIf { it.isNotBlank() } ?: _uiState.value.queryOrderId.trim()
        if (target.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请输入订单号")
            return
        }
        _uiState.value = _uiState.value.copy(queryOrderId = target)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val detail = repository.queryOrderDetail(target)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    orderDetail = detail
                )
            } catch (e: McpException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (e.isAuthError) "Token 无效或已过期" else e.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "查询失败: ${e.message}"
                )
            }
        }
    }

    /** 支付完成后查询取餐码（用 orderIdStr，fallback 到 orderId 数字） */
    fun queryCurrentOrderStatus() {
        val orderId = _uiState.value.createdOrder?.orderIdStr
            ?.takeIf { it.isNotBlank() }
            ?: _uiState.value.createdOrder?.orderId?.takeIf { it != 0L }?.toString()
        if (orderId.isNullOrBlank()) return
        _uiState.value = _uiState.value.copy(currentStep = OrderStep.ORDER_STATUS)
        queryOrderStatus(orderId)
    }

    fun cancelOrder() {
        val orderId = _uiState.value.createdOrder?.orderIdStr
            ?.takeIf { it.isNotBlank() }
            ?: _uiState.value.queryOrderId
        if (orderId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val success = repository.cancelOrder(orderId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (success) "订单已取消" else "取消失败，请重试"
                )
                if (success) {
                    queryOrderStatus()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "取消失败: ${e.message}"
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 导航
    // ════════════════════════════════════════════════════════════════

    fun navigateToStep(step: OrderStep) {
        _uiState.value = _uiState.value.copy(currentStep = step, errorMessage = null)
    }

    fun goBack() {
        val current = _uiState.value.currentStep
        val previous = when (current) {
            OrderStep.PRODUCT_SELECT -> OrderStep.SHOP_SELECT
            OrderStep.ORDER_CONFIRM -> OrderStep.PRODUCT_SELECT
            OrderStep.ORDER_PREVIEW -> OrderStep.ORDER_CONFIRM
            OrderStep.PAYMENT -> OrderStep.ORDER_CONFIRM
            OrderStep.ORDER_STATUS -> OrderStep.SHOP_SELECT
            else -> null
        }
        if (previous != null) {
            _uiState.value = _uiState.value.copy(currentStep = previous, errorMessage = null)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetOrder() {
        _uiState.value = _uiState.value.copy(
            selectedProducts = emptyList(),
            orderPreview = null,
            createdOrder = null,
            orderDetail = null,
            currentStep = OrderStep.SHOP_SELECT
        )
    }

    /** 从订单历史跳转查询（独立订单查询页用，同 ViewModel scope） */
    fun queryFromHistory(entry: OrderHistoryEntry) {
        queryOrderStatus(entry.orderIdStr)
    }

    /** 清空订单历史 */
    fun clearOrderHistory() {
        viewModelScope.launch {
            tokenStore.clearOrderHistory()
            loadOrderHistory()
        }
    }

    companion object {
        fun factory(context: android.content.Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = LuckinTokenStore(context)
                    val client = LuckinMcpClient()
                    val repository = CoffeeOrderRepository(client, tokenStore)
                    return CoffeeViewModel(repository, tokenStore) as T
                }
            }
    }
}

/** SelectedProduct → MCP 下单用的 ProductListItem */
private fun SelectedProduct.toListItem() = ProductListItem(
    amount = amount,
    productId = productId,
    skuCode = skuCode
)
