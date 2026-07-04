package com.lianyu.ai.feature.coffee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyu.ai.feature.coffee.data.model.OrderDetail

// 瑞幸品牌色（与 CoffeeScreen 一致，供复用组件共享）
internal val LuckinBlueC = Color(0xFF0066CC)
internal val LuckinDarkBlueC = Color(0xFF003D7A)
internal val LuckinLightBlueC = Color(0xFFE6F0FF)
internal val LuckinRedC = Color(0xFFE1251B)
internal val LuckinGrayC = Color(0xFFF5F5F5)

/**
 * 订单详情卡片（从 CoffeeScreen 抽离，供 CoffeeScreen 和 CoffeeOrderQueryScreen 共用）。
 *
 * 展示官方 queryOrderDetailInfo 返回的完整字段：
 * 状态、订单号、shopInfo、取餐码、productInfoList（可能 null）、
 * orderCommodityList（待付款时的明细）、支付金额。
 */
@Composable
fun OrderDetailCard(detail: OrderDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            // 状态标签
            val statusColor = when (detail.orderStatus) {
                OrderDetail.STATUS_UNPAID -> Color(0xFFFF9800)
                OrderDetail.STATUS_MAKING -> LuckinBlueC
                OrderDetail.STATUS_WAITING -> Color(0xFF4CAF50)
                OrderDetail.STATUS_DONE -> Color(0xFF4CAF50)
                OrderDetail.STATUS_CANCELED -> Color.Gray
                else -> LuckinDarkBlueC
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(detail.orderStatusName, fontWeight = FontWeight.Bold, color = statusColor, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))
            Text("订单号: ${detail.orderId}", fontSize = 13.sp, color = Color.Gray)

            // 门店信息（官方返回 shopInfo）
            detail.shopInfo?.let { shop ->
                if (shop.deptName.isNotBlank()) {
                    Text("门店: ${shop.deptName}", fontSize = 13.sp, color = Color.Gray)
                }
                if (shop.address.isNotBlank()) {
                    Text("地址: ${shop.address}", fontSize = 13.sp, color = Color.Gray)
                }
            }

            // 取餐码（仅已支付时展示）
            detail.takeMealCodeInfo?.let { codeInfo ->
                if (codeInfo.code.isNotBlank() && detail.orderStatus >= OrderDetail.STATUS_SUCCESS) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LuckinLightBlueC)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("取餐码", fontSize = 13.sp, color = LuckinDarkBlueC)
                            Text(codeInfo.code, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = LuckinRedC)
                            if (detail.aboutTime > 0) {
                                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                                    .format(java.util.Date(detail.aboutTime * 1000))
                                Text("预计取餐: $time", fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // 商品列表（productInfoList 可能为 null，待付款订单）
            detail.productInfoList?.let { products ->
                if (products.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("商品", fontWeight = FontWeight.Bold, color = LuckinDarkBlueC)
                    products.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${p.name} x${p.amount}", fontSize = 13.sp)
                            if (p.additionDesc.isNotBlank()) {
                                Text(p.additionDesc, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // 订单商品粒度价格（orderCommodityList，待付款时 productInfoList 为 null，用这个看明细）
            if (detail.orderCommodityList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("明细", fontWeight = FontWeight.Bold, color = LuckinDarkBlueC)
                detail.orderCommodityList.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${c.commodityName}", fontSize = 13.sp)
                        Text("应付 ¥${"%.2f".format(c.payableMoney)} / 实付 ¥${"%.2f".format(c.payMoney)}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("支付金额", fontWeight = FontWeight.Bold)
                Text("¥${"%.2f".format(detail.orderPayAmount)}", fontWeight = FontWeight.Bold, color = LuckinRedC)
            }
        }
    }
}
