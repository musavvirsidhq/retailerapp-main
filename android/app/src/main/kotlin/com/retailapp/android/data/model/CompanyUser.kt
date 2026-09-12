package com.retailapp.android.data.model

data class CompanyUser(
    val id: Int,
    val name: String,
    val username: String,
    val user_type: String,
    val purchase_access: Boolean,
    val sales_access: Boolean,
    val sales_below_cost_approve: Boolean,
    val status: String,
)

data class CreateStaffInput(
    val name: String,
    val username: String,
    val password: String,
    val purchase_access: Boolean,
    val sales_access: Boolean,
    val sales_below_cost_approve: Boolean,
)

// Note the camelCase keys here - this one endpoint (PUT .../permissions) uses camelCase JSON,
// unlike the snake_case bodies everywhere else. Matches frontend/src/api/companyUsers.ts exactly.
data class UpdatePermissionsRequest(
    val purchaseAccess: Boolean,
    val salesAccess: Boolean,
    val salesBelowCostApprove: Boolean,
)

data class SubscriptionStatus(
    val status: String,
    val expiry_date: String?,
    val days_remaining: Int?,
    val warning_level: String?,
    val subscription_type: String?,
)
