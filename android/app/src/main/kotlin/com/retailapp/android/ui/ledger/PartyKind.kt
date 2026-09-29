package com.retailapp.android.ui.ledger

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.Terms

/**
 * Customers and suppliers share one set of dues/ledger screens; this holds everything that
 * differs between them - wording, the backend party_type, and which permission applies.
 */
enum class PartyKind(
    val partyType: String,
    val singular: String,
    val plural: String,
    val duesTitle: String,
    val duesLabel: String,
    val billEntity: AttachmentEntity,
    val billLabel: String,
    val totalBilledLabel: String,
    val totalPaidLabel: String,
    val paymentLabel: String,
    val payAction: String,
    val newBillAction: String,
) {
    CUSTOMER(
        partyType = "shop",
        singular = Terms.CUSTOMER,
        plural = Terms.CUSTOMERS,
        duesTitle = "Customer Dues",
        duesLabel = "To receive",
        billEntity = AttachmentEntity.SALE,
        billLabel = "Sale",
        totalBilledLabel = "Total billed",
        totalPaidLabel = "Total collected",
        paymentLabel = "Payment collected",
        payAction = "Collect money",
        newBillAction = "New sale",
    ),
    SUPPLIER(
        partyType = "factory",
        singular = Terms.SUPPLIER,
        plural = Terms.SUPPLIERS,
        duesTitle = "Supplier Dues",
        duesLabel = "To pay",
        billEntity = AttachmentEntity.PURCHASE,
        billLabel = "Purchase",
        totalBilledLabel = "Total purchased",
        totalPaidLabel = "Total paid",
        paymentLabel = "Payment given",
        payAction = "Pay supplier",
        newBillAction = "New purchase",
    ),
    ;

    /** Mirrors the backend: customer dues/ledgers need sales access, supplier ones purchase access. */
    val hasAccess: Boolean get() = if (this == CUSTOMER) Session.canSell else Session.canPurchase

    companion object {
        fun fromPartyType(partyType: String) = if (partyType == "shop") CUSTOMER else SUPPLIER
    }
}

fun dialPhone(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // No dialer (e.g. a tablet) - nothing useful to do.
    }
}

/** Opens a WhatsApp chat with [message] pre-filled. A bare 10-digit number is taken as Indian. */
fun openWhatsApp(context: Context, phone: String, message: String) {
    var digits = phone.filter { it.isDigit() }
    if (digits.length == 10) digits = "91$digits"
    try {
        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Neither WhatsApp nor a browser installed.
    }
}
