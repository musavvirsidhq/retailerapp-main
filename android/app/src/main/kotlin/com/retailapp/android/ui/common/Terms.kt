package com.retailapp.android.ui.common

/**
 * The one place the app's party wording lives. Cycle 4 renamed Shop -> Customer and
 * Factory -> Supplier in the app only: the backend still says "shop"/"factory" in its paths
 * and party_type values, so never send these labels to the API.
 */
object Terms {
    const val CUSTOMER = "Customer"
    const val CUSTOMERS = "Customers"
    const val SUPPLIER = "Supplier"
    const val SUPPLIERS = "Suppliers"

    /** Label for a backend party_type ("shop" | "factory"). */
    fun partyLabel(partyType: String): String = if (partyType == "shop") CUSTOMER else SUPPLIER
}
