package com.retailapp.android.ui.common

/**
 * Every WhatsApp / share text the app writes, in one place so it can be translated later
 * (Cycle 5 section 7.3). Amounts go through [money], dates through [shortDate].
 */
object Messages {
    /** Sent with the bill PDF after a sale is saved. */
    fun saleBill(customer: String, billNumber: String, total: Double, date: String, paid: Double, pending: Double?, company: String?): String =
        buildString {
            append("Hello $customer, your bill $billNumber for ${money(total)} dated ${shortDate(date)}.")
            if (paid > 0) append(" Paid ${money(paid)}.")
            if (pending != null) append(" Total pending with us: ${money(pending.coerceAtLeast(0.0))}.")
            append(" Thank you")
            if (!company.isNullOrBlank()) append(", $company")
            append(".")
        }

    /** Receipt after collecting money from a customer. */
    fun paymentReceipt(customer: String, amount: Double, mode: String, date: String, pending: Double?, company: String?): String =
        buildString {
            append("Hello $customer, received ${money(amount)} by $mode on ${shortDate(date)}.")
            if (pending != null) append(" Pending: ${money(pending.coerceAtLeast(0.0))}.")
            append(" Thank you")
            if (!company.isNullOrBlank()) append(", $company")
            append(".")
        }

    /** Payment reminder from the ledger or the Customer Dues reminders flow. */
    fun paymentReminder(customer: String, pending: Double): String =
        "Hello $customer, your pending balance with us is ${money(pending)}. Kindly arrange the payment. Thank you."

    /** Opening line when there is nothing pending to mention. */
    fun greeting(name: String): String = "Hello $name, "

    fun saleSavedTitle(billNumber: String, total: Double) = "Sale $billNumber saved · ${money(total)}"
    fun paymentSavedTitle(amount: Double) = "Payment of ${money(amount)} saved"

    const val SETTLE_BEFORE_ARCHIVE = "Settle the %s balance before archiving."
    fun settleBeforeArchive(balance: Double) = SETTLE_BEFORE_ARCHIVE.format(money(kotlin.math.abs(balance)))

    fun stockWillBeUnsellable(stock: String, unit: String) =
        "${trimQty(stock)} $unit in stock will no longer be sellable."

    fun skuUsedByArchived(productName: String) = "SKU already used by archived product $productName — restore it instead."

    const val PHOTO_REQUIRED = "Add a receipt photo to save (required by your admin)."
}
