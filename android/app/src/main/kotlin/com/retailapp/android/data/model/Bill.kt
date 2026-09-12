package com.retailapp.android.data.model

data class BillItem(
    val ProductName: String,
    val ProductSKU: String,
    val Unit: String,
    val Quantity: Double,
    val UnitPrice: Double,
    val LineTotal: Double,
    val BelowCost: Boolean,
)

data class BillData(
    val DocumentTitle: String,
    val BillNumber: String,
    val BillDate: String,
    val CompanyName: String,
    val CompanyCode: String,
    val CounterpartyName: String,
    val CounterpartyPhone: String,
    val CounterpartyArea: String,
    val Items: List<BillItem>,
    val TotalAmount: Double,
    val AmountPaid: Double,
    val Status: String, // "COMPLETED" | "CANCELLED"
    val CancelledReason: String?,
)
