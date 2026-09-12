package com.retailapp.android.data.model

data class Shop(
    val ID: Int,
    val Name: String,
    val OwnerName: String?,
    val PrimaryPhone: String,
    val SecondaryPhone: String?,
    val Area: String?,
    val OpeningBalance: String,
    val CreatedAt: String,
)

data class ShopInput(
    val name: String,
    val owner_name: String,
    val primary_phone: String,
    val secondary_phone: String,
    val area: String,
    val opening_balance: Double,
)
