package com.retailapp.android.data.model

data class CountTotal(val Count: Int, val Total: String)
data class LowStockItem(val ID: Int, val Name: String, val Unit: String, val CurrentStock: String)
data class DueItem(val ID: Int, val Name: String, val Balance: String)

data class DashboardData(
    val today_sales: CountTotal,
    val today_purchases: CountTotal,
    val total_profit: String,
    val low_stock: List<LowStockItem>,
    val shop_dues: List<DueItem>,
    val factory_payables: List<DueItem>,
)
