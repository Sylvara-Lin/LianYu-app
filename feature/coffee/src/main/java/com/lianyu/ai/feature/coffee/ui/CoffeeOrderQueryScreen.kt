package com.lianyu.ai.feature.coffee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianyu.ai.feature.coffee.data.model.OrderDetail

// 瑞幸品牌色（局部副本，避免跨文件可见性扩散）
private val LuckinBlue = Color(0xFF0066CC)
private val LuckinDarkBlue = Color(0xFF003D7A)
private val LuckinLightBlue = Color(0xFFE6F0FF)
private val LuckinRed = Color(0xFFE1251B)
private val LuckinGray = Color(0xFFF5F5F5)

/**
 * 瑞幸订单查询页（独立路由）。
 *
 * 路由：coffee_order（无参）/ coffee_order/{orderId}（带初始订单号）
 * 与 CoffeeScreen 的 ORDER_STATUS step 功能等价，但作为独立页面：
 * - 可从设置页订单历史点击跳入（带 orderId）
 * - 也可从顶栏历史图标直接进入（无 orderId，手动输入）
 * - 复用 CoffeeViewModel（同一 factory，但跨页面是不同实例 —— 此处按页面内自有 ViewModel 使用）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeOrderQueryScreen(
    initialOrderId: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CoffeeViewModel = viewModel(factory = CoffeeViewModel.factory(context))
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 带参进入时预填订单号并自动查询
    LaunchedEffect(initialOrderId) {
        if (!initialOrderId.isNullOrBlank()) {
            viewModel.updateQueryOrderId(initialOrderId)
            viewModel.queryOrderStatus(initialOrderId)
        }
    }

    // 错误提示
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订单查询", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 订单号输入
                OutlinedTextField(
                    value = uiState.queryOrderId,
                    onValueChange = viewModel::updateQueryOrderId,
                    label = { Text("订单号") },
                    placeholder = { Text("输入瑞幸订单号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.queryOrderStatus() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LuckinBlue),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !uiState.isLoading
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  查询订单状态", fontWeight = FontWeight.Bold)
                }

                // 订单详情
                uiState.orderDetail?.let { detail ->
                    Spacer(Modifier.height(16.dp))
                    OrderDetailCard(detail)
                    Spacer(Modifier.height(16.dp))

                    // 取消订单（仅待付款/下单成功/制作中可取消）
                    if (detail.orderStatus == OrderDetail.STATUS_UNPAID ||
                        detail.orderStatus == OrderDetail.STATUS_SUCCESS ||
                        detail.orderStatus == OrderDetail.STATUS_MAKING
                    ) {
                        OutlinedButton(
                            onClick = viewModel::cancelOrder,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LuckinRed)
                        ) {
                            Text("取消订单")
                        }
                    }

                    // 制作中/等待取餐时提供刷新按钮
                    if (detail.orderStatus == OrderDetail.STATUS_MAKING ||
                        detail.orderStatus == OrderDetail.STATUS_SUCCESS ||
                        detail.orderStatus == OrderDetail.STATUS_WAITING
                    ) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.queryOrderStatus() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("刷新状态", color = LuckinBlue)
                        }
                    }
                }

                // 空状态提示
                if (uiState.orderDetail == null && !uiState.isLoading) {
                    Spacer(Modifier.height(32.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LuckinLightBlue)
                    ) {
                        Text(
                            "输入订单号查询状态、取餐码和商品明细。\n订单号可在下单后或设置页订单历史中找到。",
                            Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            color = LuckinDarkBlue,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // 加载遮罩
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = LuckinBlue)
                            Spacer(Modifier.height(12.dp))
                            Text("查询中…", color = LuckinDarkBlue)
                        }
                    }
                }
            }
        }
    }
}
