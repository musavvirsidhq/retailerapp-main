package com.retailapp.android.data.model

import com.google.gson.annotations.SerializedName

data class Shop(
    val ID: Int,
    val Name: String,
    val OwnerName: String?,
    val PrimaryPhone: String,
    val SecondaryPhone: String?,
    val Area: String?,
    val OpeningBalance: String,
    val CreatedAt: String,
    // Cycle 5 archive. Only sent with ?include_archived=true; accepts either casing so it works
    // whether the backend tags the column or leaves sqlc's PascalCase.
    @SerializedName(value = "archived_at", alternate = ["ArchivedAt"])
    val archivedAt: String? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class ShopInput(
    val name: String,
    val owner_name: String,
    val primary_phone: String,
    val secondary_phone: String,
    val area: String,
    val opening_balance: Double,
)

/** PUT /api/shops/{id}. No opening_balance: it is financial history and the backend ignores it. */
data class ShopUpdate(
    val name: String,
    val owner_name: String,
    val primary_phone: String,
    val secondary_phone: String,
    val area: String,
)
