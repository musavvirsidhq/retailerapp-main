package com.retailapp.android.data.model

import com.google.gson.annotations.SerializedName

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
    // Cycle 5 archive; see Shop.archivedAt.
    @SerializedName(value = "archived_at", alternate = ["ArchivedAt"])
    val archivedAt: String? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** Used for both create (POST) and edit (PUT). Stock is never sent: it only moves through bills. */
data class ProductInput(
    val name: String,
    val sku: String,
    val unit: String,
    val category_id: Int,
    val subcategory_id: Int?,
    val selling_price: Double,
)
