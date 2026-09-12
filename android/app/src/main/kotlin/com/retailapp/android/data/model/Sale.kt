package com.retailapp.android.data.model

data class Sale(
    val ID: Int,
    val BillNumber: String,
    val ShopID: Int,
    val ShopName: String,
    val SaleDate: String,
    val TotalAmount: String,
    val AmountPaid: String,
    val PaymentType: String,
    val Status: String, // "COMPLETED" | "CANCELLED"
    val CreatedAt: String,
)

data class SaleItem(
    val ID: Int,
    val SaleID: Int,
    val ProductID: Int,
    val ProductName: String,
    val ProductSku: String,
    val Unit: String,
    val Quantity: String,
    val UnitPrice: String,
    val LineTotal: String,
    val BelowCost: Boolean,
)

data class SaleItemInput(
    val product_id: Int,
    val quantity: Double,
    val unit_price: Double,
)

data class SaleInput(
    val shop_id: Int,
    val amount_paid: Double,
    val payment_type: String, // "cash" | "credit"
    val items: List<SaleItemInput>,
)

data class CancelRequest(val reason: String)
