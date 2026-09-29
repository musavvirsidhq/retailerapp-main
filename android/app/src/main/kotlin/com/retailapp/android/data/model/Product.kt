package com.retailapp.android.data.model

data class Product(
    val ID: Int,
    val CompanyID: Int,
    val Name: String,
    val Sku: String,
    val Unit: String,
    val CategoryID: Int,
    val SubcategoryID: Int?,
    val CurrentSellingPrice: String, // decimal serialized as string by the backend - parse with toDoubleOrNull()
    val CurrentStock: String,
    val CreatedAt: String,
    val Pinned: Boolean = false,
)

data class ProductInput(
    val name: String,
    val sku: String,
    val unit: String,
    val category_id: Int,
    val subcategory_id: Int?,
    val selling_price: Double,
)
