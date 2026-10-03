package com.retailapp.android.data.model

import com.google.gson.annotations.SerializedName

data class Factory(
    val ID: Int,
    val Name: String,
    val ContactPerson: String?,
    val PrimaryPhone: String,
    val SecondaryPhone: String?,
    val Address: String?,
    val CreatedAt: String,
    // Cycle 5 archive; see Shop.archivedAt.
    @SerializedName(value = "archived_at", alternate = ["ArchivedAt"])
    val archivedAt: String? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** Used for both create (POST) and edit (PUT) - the fields are the same. */
data class FactoryInput(
    val name: String,
    val contact_person: String,
    val primary_phone: String,
    val secondary_phone: String,
    val address: String,
)
