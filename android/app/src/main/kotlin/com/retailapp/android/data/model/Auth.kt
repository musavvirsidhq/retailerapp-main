package com.retailapp.android.data.model

// Field names match the backend JSON exactly (see frontend/src/api/auth.ts) so Gson can
// deserialize with no @SerializedName mapping needed.

data class LoginRequest(
    val username: String,
    val password: String,
)

data class User(
    val id: Int,
    val name: String,
    val username: String,
    val user_type: String, // "SUPER_ADMIN" | "COMPANY_ADMIN" | "STAFF"
    val company_id: Int?,
    val purchase_access: Boolean,
    val sales_access: Boolean,
    val sales_below_cost_approve: Boolean,
)
