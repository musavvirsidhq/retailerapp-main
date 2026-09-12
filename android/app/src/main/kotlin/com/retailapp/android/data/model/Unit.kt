package com.retailapp.android.data.model

// Named UnitDto (not Unit) to avoid clashing with kotlin.Unit.
data class UnitDto(
    val Code: String,
    val Label: String,
)
