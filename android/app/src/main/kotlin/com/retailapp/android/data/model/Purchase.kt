package com.retailapp.android.data.model

data class Purchase(
    val ID: Int,
    val BillNumber: String,
    val FactoryID: Int,
    val FactoryName: String,
    val InvoiceNo: String?,
    val PurchaseDate: String,
    val TotalAmount: String,
    val AmountPaid: String,
    val Status: String, // "COMPLETED" | "CANCELLED"
    val CreatedAt: String,
)

data class PurchaseItem(
    val ID: Int,
    val PurchaseID: Int,
    val ProductID: Int,
    val ProductName: String,
    val ProductSku: String,
    val Unit: String,
    val Quantity: String,
    val UnitPrice: String,
    val LineTotal: String,
)

data class PurchaseItemInput(
    val product_id: Int,
    val quantity: Double,
    val unit_price: Double,
)

data class PurchaseInput(
    val factory_id: Int,
    val invoice_no: String,
    val amount_paid: Double,
    val items: List<PurchaseItemInput>,
)
