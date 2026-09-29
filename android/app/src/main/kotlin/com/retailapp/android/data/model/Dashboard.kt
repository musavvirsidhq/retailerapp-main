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
    // Cycle 4 dashboard cards. Nullable so an older backend without them still parses.
    val customer_due_total: String?,
    val customer_due_count: Int?,
    val supplier_due_total: String?,
    val supplier_due_count: Int?,
)
