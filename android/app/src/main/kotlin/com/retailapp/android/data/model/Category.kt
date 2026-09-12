package com.retailapp.android.data.model

// Note the PascalCase field names here (ID, Name, ...) - this endpoint serializes the Go struct
// with no json tags, unlike Auth.kt's snake_case. Match whatever the corresponding frontend/src/api/*.ts
// file shows; don't assume casing is consistent across endpoints.

data class Category(
    val ID: Int,
    val CompanyID: Int,
    val Name: String,
    val CreatedAt: String,
)

data class Subcategory(
    val ID: Int,
    val CompanyID: Int,
    val CategoryID: Int,
    val Name: String,
    val CreatedAt: String,
)

data class CreateCategoryRequest(val name: String)
data class CreateSubcategoryRequest(val name: String)
