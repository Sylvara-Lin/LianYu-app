package com.lianyu.ai.feature.coffee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lianyu.ai.feature.coffee.data.model.ProductAttrGroup
import com.lianyu.ai.feature.coffee.data.model.ProductSubAttr

// 瑞幸品牌色（与 CoffeeScreen 一致）
private val LuckinBlue = Color(0xFF0066CC)
private val LuckinDarkBlue = Color(0xFF003D7A)
private val LuckinLightBlue = Color(0xFFE6F0FF)
private val LuckinRed = Color(0xFFE1251B)
private val LuckinGray = Color(0xFFF5F5F5)

/**
 * 瑞幸商品定制页（queryProductDetailInfo + switchProduct）。
 *
 * 路由：coffee/product/{deptId}/{productId}
 * 流程：加载商品详情 → 用户切换属性 → 实时拿到新 skuCode + 到手价 → 加入订单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    deptId: Long,
    productId: Long,
    onBack: () -> Unit,
    onAddedToCart: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CoffeeViewModel = viewModel(factory = CoffeeViewModel.factory(context))
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 进入页面加载商品详情
    LaunchedEffect(deptId, productId) {
        viewModel.loadProductDetail(deptId, productId)
    }

    // 错误提示
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 加入订单后退出
    LaunchedEffect(uiState.productCustomizing) {
        // productCustomizing 变 null 表示已确认加入，由 onAddedToCart 回调退出
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("商品定制", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearCustomizing()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LuckinBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LuckinGray)
                .padding(padding)
        ) {
            val product = uiState.productCustomizing

            if (product == null && uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LuckinBlue)
                }
            } else if (product != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // 商品图 + 名称 + 价格
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                if (product.pictureUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(product.pictureUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = product.productName,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .background(LuckinGray, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                                Text(product.productName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = LuckinDarkBlue)
                                product.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(tags.joinToString(" · "), fontSize = 12.sp, color = LuckinBlue)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    if (product.initialPrice > product.estimatePrice && product.initialPrice > 0) {
                                        Text(
                                            "¥${"%.0f".format(product.initialPrice)}",
                                            fontSize = 14.sp,
                                            color = Color.Gray,
                                            textDecoration = TextDecoration.LineThrough
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        "¥${"%.2f".format(product.estimatePrice)}",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LuckinRed
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("到手价", fontSize = 12.sp, color = LuckinRed)
                                }
                            }
                        }
                    }

                    // 属性定制区
                    items(product.productAttrs) { attrGroup ->
                        AttributeGroupCard(
                            attrGroup = attrGroup,
                            onSwitch = { subAttrId ->
                                viewModel.switchAttribute(attrGroup.attributeId, subAttrId)
                            }
                        )
                    }

                    // 数量选择
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("数量", fontWeight = FontWeight.Bold, color = LuckinDarkBlue)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.changeCustomizingAmount(-1) },
                                        enabled = uiState.customizingAmount > 1
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "减少")
                                    }
                                    Text(
                                        "${uiState.customizingAmount}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(40.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    IconButton(onClick = { viewModel.changeCustomizingAmount(1) }) {
                                        Icon(Icons.Default.Add, contentDescription = "增加")
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部加入订单按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.confirmCustomizedProduct()
                            onAddedToCart()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LuckinBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "加入订单 · ¥${"%.2f".format(product.estimatePrice * uiState.customizingAmount)}",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // 全局加载遮罩（属性切换时）
            if (uiState.isLoading && uiState.productCustomizing != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LuckinBlue)
                }
            }
        }
    }
}

@Composable
private fun AttributeGroupCard(
    attrGroup: ProductAttrGroup,
    onSwitch: (subAttributeId: Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(attrGroup.attributeName, fontWeight = FontWeight.Bold, color = LuckinDarkBlue, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attrGroup.productSubAttrs) { subAttr ->
                    AttributeChip(subAttr = subAttr, onClick = { onSwitch(subAttr.attributeId) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttributeChip(subAttr: ProductSubAttr, onClick: () -> Unit) {
    val isSelected = subAttr.selected == true
    val enabled = subAttr.canSelected != 0
    FilterChip(
        selected = isSelected,
        onClick = { if (enabled && !isSelected) onClick() },
        enabled = enabled,
        label = {
            Column {
                Text(subAttr.attributeName, fontSize = 13.sp)
                if (subAttr.price > 0) {
                    Text("+¥${"%.0f".format(subAttr.price)}", fontSize = 11.sp, color = LuckinRed)
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LuckinBlue,
            selectedLabelColor = Color.White
        )
    )
}
