package com.retailapp.android.data.model

// Cycle 4 customer/supplier dues and ledgers. These endpoints use snake_case JSON (see
// internal/handlers/ledger.go), unlike the older sqlc-shaped PascalCase models.

data class DuesRow(
    val id: Int,
    val name: String,
    val phone: String,
    val area: String?,
    val balance: String,
    val last_bill_at: String?,
    val last_payment_at: String?,
    val last_activity_at: String?,
)

data class DuesResponse(
    val total_pending: String,
    val pending_count: Int,
    val rows: List<DuesRow>,
)

data class LedgerParty(
    val id: Int,
    val name: String,
    val contact_name: String?,
    val phone: String,
    val secondary_phone: String?,
    val area: String?,
    // Cycle 5: set once the customer/supplier is archived (null on older backends).
    val archived_at: String? = null,
)

data class LedgerSummary(
    val total_billed: String,
    val total_collected: String,
    val pending: String,
    val last_payment_at: String?,
    val last_payment_amount: String?,
)

data class RangeSummary(val billed: String, val collected: String)

data class LedgerEntry(
    val type: String, // "opening" | "sale" | "purchase" | "payment"
    val id: Int,
    val date: String,
    val ref: String?,
    val invoice_no: String?,
    val amount: String?,
    val paid: String?,
    val mode: String?,
    val notes: String?,
    val balance_after: String,
    val cancelled: Boolean,
    val photo_count: Int,
    val item_count: Int,
)

data class LedgerResponse(
    val party: LedgerParty,
    val summary: LedgerSummary,
    val range_summary: RangeSummary,
    val entries: List<LedgerEntry>,
)

/** One uploaded proof photo. [url] is a server path that needs the session cookie to load. */
data class Attachment(
    val id: Int,
    val entity_type: String,
    val entity_id: Int,
    val url: String,
    val content_type: String,
    val size_bytes: Int,
    val created_at: String,
    val uploaded_by_name: String,
)

data class DeleteAttachmentRequest(val reason: String)

/** A chip in the sale/purchase "most used items" strip. */
data class QuickItem(
    val id: Int,
    val name: String,
    val sku: String,
    val unit: String,
    val current_selling_price: String,
    val current_stock: String,
    val pinned: Boolean,
    val uses: Int,
)
