package com.retailapp.android.data.model

data class Company(
    val ID: Int,
    val CompanyName: String,
    val CompanyCode: String,
    val Status: String,
    val JoiningDate: String,
    val CreatedOn: String,
    val subscription_type: String?,
    val expiry_date: String?,
    val subscription_status: String?,
)

data class Subscription(
    val ID: Int,
    val CompanyID: Int,
    val SubscriptionType: String,
    val StartDate: String,
    val ExpiryDate: String,
    val Amount: String,
    val PurchaseDate: String,
    val Status: String,
)

data class CreateCompanyInput(
    val company_name: String,
    val company_code: String,
    val admin_name: String,
    val admin_username: String,
    val admin_password: String,
)

data class GrantSubscriptionRequest(
    val subscription_type: String,
    val amount: Double,
    val months: Int,
)

data class ExtendSubscriptionRequest(
    val months: Int,
    val days: Int,
    val amount: Double,
)
