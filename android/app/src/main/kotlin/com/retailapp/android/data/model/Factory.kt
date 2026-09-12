package com.retailapp.android.data.model

data class Factory(
    val ID: Int,
    val Name: String,
    val ContactPerson: String?,
    val PrimaryPhone: String,
    val SecondaryPhone: String?,
    val Address: String?,
    val CreatedAt: String,
)

data class FactoryInput(
    val name: String,
    val contact_person: String,
    val primary_phone: String,
    val secondary_phone: String,
    val address: String,
)
