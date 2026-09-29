package com.retailapp.android.ui.common

import com.retailapp.android.data.model.Product

/** One editable item row on the new sale / new purchase forms. */
data class BillLine(
    val id: Long,
    val product: Product?,
    val quantity: String,
    val unitPrice: String,
) {
    val isValid: Boolean
        get() = product != null && (quantity.toDoubleOrNull() ?: 0.0) > 0 && unitPrice.toDoubleOrNull() != null
}

/**
 * What tapping a most-used item chip does: bump the quantity if the item is already on the
 * bill, otherwise fill the first empty row, otherwise add a new row with quantity 1.
 */
fun List<BillLine>.withQuickItem(product: Product, unitPrice: String, newId: () -> Long): List<BillLine> {
    val existing = find { it.product?.ID == product.ID }
    if (existing != null) {
        val qty = (existing.quantity.toDoubleOrNull() ?: 0.0) + 1
        return map { if (it.id == existing.id) it.copy(quantity = trimQty(qty.toString())) else it }
    }
    val empty = find { it.product == null }
    if (empty != null) {
        return map { if (it.id == empty.id) it.copy(product = product, quantity = "1", unitPrice = unitPrice) else it }
    }
    return this + BillLine(newId(), product, "1", unitPrice)
}
