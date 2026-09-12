package com.retailapp.android.data.model

data class Payment(
    val ID: Int,
    val PartyType: String, // "shop" | "factory"
    val PartyID: Int,
    val Amount: String,
    val PaymentMode: String,
    val PaymentDate: String,
    val Notes: String?,
    val CreatedAt: String,
)

data class PaymentInput(
    val party_type: String,
    val party_id: Int,
    val amount: Double,
    val payment_mode: String,
    val notes: String,
)

data class BalanceResponse(val balance: String)
