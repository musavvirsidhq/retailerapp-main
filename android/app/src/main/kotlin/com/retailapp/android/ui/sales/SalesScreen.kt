@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.sales

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.Sale
import com.retailapp.android.data.model.SaleInput
import com.retailapp.android.data.model.SaleItemInput
import com.retailapp.android.data.model.Shop
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.BillLine
import com.retailapp.android.ui.common.DiscardDialog
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.PagedListContent
import com.retailapp.android.ui.common.PhotoPickerRow
import com.retailapp.android.ui.common.SavedSheet
import com.retailapp.android.ui.common.ScanButton
import com.retailapp.android.ui.common.countLabel
import com.retailapp.android.ui.common.displayDate
import com.retailapp.android.ui.common.rememberBillScanner
import com.retailapp.android.ui.common.QuickItemsStrip
import com.retailapp.android.ui.common.SearchablePickerField
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.productDetail
import com.retailapp.android.ui.common.successColor
import com.retailapp.android.ui.common.trimQty
import com.retailapp.android.ui.common.withQuickItem

/**
 * The sales list, or - when [startNew] - the new-sale form, each as its own navigation entry so
 * the bottom bar can hide on the form. [presetShopId] preselects a customer (ledger "New sale").
 * After saving, the "Sale saved" sheet offers WhatsApp / Share PDF; Done closes the form, or
 * opens the bill via [onSavedOpenBill] when some photos still need uploading.
 */
@Composable
fun SalesScreen(
    onOpenBill: (Int) -> Unit,
    onNewSale: () -> Unit = {},
    startNew: Boolean = false,
    presetShopId: Int? = null,
    onClose: () -> Unit = {},
    onSavedOpenBill: (Int) -> Unit = onOpenBill,
    onAddProductFromScan: ((String) -> Unit)? = null,
    viewModel: SalesViewModel = viewModel(),
) {
    if (startNew && Session.canSell) {
        LaunchedEffect(Unit) { viewModel.startForm() }
        // Picks up a product added from the scanner's "Add product" while this form waited.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshFormIfChanged() }
        if (viewModel.isFormLoading) {
            LoadingBox()
            return
        }
        NewSaleScreen(
            viewModel = viewModel,
            presetShopId = presetShopId,
            onBack = onClose,
            onAddProductFromScan = onAddProductFromScan,
        )
        viewModel.saved?.let { share ->
            SavedSheet(share, onDone = {
                val sale = viewModel.savedSale
                if (sale != null && viewModel.savedFailedPhotos > 0) onSavedOpenBill(sale.ID) else onClose()
            })
        }
        return
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.startList() }
    val list = viewModel.list
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        floatingActionButton = {
            if (Session.canSell) {
                FloatingActionButton(onClick = onNewSale) {
                    Icon(Icons.Default.Add, contentDescription = "New sale")
                }
            }
        },
    ) { padding ->
        PagedListContent(
            list = list,
            padding = padding,
            summary = "${countLabel(list.totalCount, "bill")} · ${money(list.totalAmount)}",
            emptyText = when {
                list.query.isNotBlank() -> "No sales match \"${list.query.trim()}\"."
                Session.canSell -> "No sales ${list.filter.phrase}. Tap + to make one."
                else -> "No sales ${list.filter.phrase}."
            },
            key = { it.ID },
            searchPlaceholder = "Search bill number or ${Terms.CUSTOMER.lowercase()}",
        ) { sale -> SaleRow(sale, onClick = { onOpenBill(sale.ID) }) }
    }
}

@Composable
private fun SaleRow(sale: Sale, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(sale.BillNumber, style = MaterialTheme.typography.titleMedium)
                Text("${sale.ShopName} · ${sale.PaymentType} · ${displayDate(sale.SaleDate)}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(sale.TotalAmount), style = MaterialTheme.typography.bodyMedium)
                Text(
                    sale.Status,
                    color = if (sale.Status == "CANCELLED") MaterialTheme.colorScheme.error else successColor(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NewSaleScreen(
    viewModel: SalesViewModel,
    presetShopId: Int?,
    onBack: () -> Unit,
    onAddProductFromScan: ((String) -> Unit)?,
) {
    val shops = viewModel.shops
    val products = viewModel.products
    // No default customer unless one was passed in: defaulting to the first in the list meant a
    // hurried user could bill the wrong customer without ever touching the field.
    var selectedShop by remember { mutableStateOf<Shop?>(shops.find { it.ID == presetShopId }) }
    var paymentType by remember { mutableStateOf("cash") }
    var amountPaid by remember { mutableStateOf("") }
    var nextLineId by remember { mutableLongStateOf(1L) }
    var lineItems by remember { mutableStateOf(listOf(BillLine(0L, null, "", ""))) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(selectedShop?.ID) { viewModel.loadUsualItems(selectedShop?.ID) }

    fun updateLine(id: Long, transform: (BillLine) -> BillLine) {
        lineItems = lineItems.map { if (it.id == id) transform(it) else it }
    }

    fun addProduct(product: Product) {
        lineItems = lineItems.withQuickItem(product, product.CurrentSellingPrice) { nextLineId++ }
    }

    fun addQuick(productId: Int) {
        products.find { it.ID == productId }?.let(::addProduct)
    }

    // Cycle 5 barcode scanning: a matching SKU adds the item like a quick-item chip.
    val scan = rememberBillScanner(
        products = products,
        onAdd = ::addProduct,
        onAddProduct = onAddProductFromScan.takeIf { Session.isCompanyAdmin },
    )

    val total = lineItems.sumOf { (it.quantity.toDoubleOrNull() ?: 0.0) * (it.unitPrice.toDoubleOrNull() ?: 0.0) }
    val paidValue = amountPaid.toDoubleOrNull()
    val overpaid = paymentType == "credit" && paidValue != null && paidValue > total + 0.005
    val blocker = when {
        selectedShop == null -> "Choose a ${Terms.CUSTOMER.lowercase()}"
        lineItems.any { it.product == null } -> "Choose a product on every item row"
        !lineItems.all { it.isValid } -> "Enter a quantity and price for every item"
        overpaid -> "Amount paid can't be more than the bill total"
        else -> null
    }
    val isDirty = lineItems.any { it.product != null } || amountPaid.isNotBlank() || photos.isNotEmpty()
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (isDirty) confirmDiscard = true else onBack() }
    // System back used to leave the Sales screen entirely and silently drop a half-made bill.
    BackHandler(enabled = !viewModel.isSubmitting && viewModel.saved == null) { requestBack() }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onKeep = { confirmDiscard = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New sale") },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !viewModel.isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SearchablePickerField(
                    label = Terms.CUSTOMER,
                    options = shops,
                    selected = selectedShop,
                    optionLabel = { it.Name },
                    optionDetail = { listOfNotNull(it.PrimaryPhone, it.Area?.takeIf(String::isNotBlank)).joinToString(" · ") },
                    onSelect = { selectedShop = it },
                )
            }

            item {
                QuickItemsStrip(title = "Most used", items = viewModel.frequentItems, onPick = { addQuick(it.id) })
            }
            if (viewModel.usualItems.isNotEmpty()) {
                item {
                    QuickItemsStrip(
                        title = "${selectedShop?.Name ?: "Customer"} usually buys",
                        items = viewModel.usualItems,
                        onPick = { addQuick(it.id) },
                    )
                }
            }

            item { Text("Items", style = MaterialTheme.typography.titleMedium) }

            items(lineItems, key = { it.id }) { line ->
                LineItemRow(
                    line = line,
                    products = products,
                    onProductChange = { product ->
                        updateLine(line.id) { it.copy(product = product, unitPrice = product.CurrentSellingPrice) }
                    },
                    onQuantityChange = { qty -> updateLine(line.id) { it.copy(quantity = qty) } },
                    onUnitPriceChange = { price -> updateLine(line.id) { it.copy(unitPrice = price) } },
                    onRemove = { lineItems = lineItems.filter { it.id != line.id } },
                    removable = lineItems.size > 1,
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { lineItems = lineItems + BillLine(nextLineId++, null, "", "") }) { Text("+ Add item") }
                    ScanButton(onClick = scan)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Total ${money(total)}", style = MaterialTheme.typography.titleMedium)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = paymentType == "cash", onClick = { paymentType = "cash" })
                        Text("Cash")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = paymentType == "credit", onClick = { paymentType = "credit" })
                        Text("Credit")
                    }
                }
            }

            item {
                // The backend always records a cash sale as paid in full and ignores amount_paid,
                // so the box only makes sense for credit - showing it for cash was misleading.
                if (paymentType == "cash") {
                    Text(
                        "Paid in full: ${money(total)}",
                        color = successColor(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    OutlinedTextField(
                        value = amountPaid,
                        onValueChange = { amountPaid = it },
                        label = { Text("Amount paid now") },
                        prefix = { Text("₹") },
                        singleLine = true,
                        isError = overpaid,
                        supportingText = {
                            val due = total - (paidValue ?: 0.0)
                            Text(if (overpaid) "More than the bill total" else "Balance added to ${Terms.CUSTOMER.lowercase()}'s dues: ${money(due.coerceAtLeast(0.0))}")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { PhotoPickerRow(photos = photos, onPhotosChange = { photos = it }) }

            item { InlineError(viewModel.errorMessage) }

            item {
                if (blocker != null && !viewModel.isSubmitting) {
                    Text(blocker, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Button(
                    enabled = !viewModel.isSubmitting && viewModel.saved == null && blocker == null,
                    onClick = {
                        viewModel.createSale(
                            SaleInput(
                                shop_id = selectedShop!!.ID,
                                amount_paid = if (paymentType == "cash") total else paidValue ?: 0.0,
                                payment_type = paymentType,
                                items = lineItems.map {
                                    SaleItemInput(
                                        product_id = it.product!!.ID,
                                        quantity = it.quantity.toDouble(),
                                        unit_price = it.unitPrice.toDouble(),
                                    )
                                },
                            ),
                            photos,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  ${viewModel.progressMessage ?: "Saving…"}")
                    } else {
                        Text("Create sale · ${money(total)}")
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LineItemRow(
    line: BillLine,
    products: List<Product>,
    onProductChange: (Product) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitPriceChange: (String) -> Unit,
    onRemove: () -> Unit,
    removable: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchablePickerField(
                    label = "Product",
                    options = products,
                    selected = line.product,
                    optionLabel = { it.Name },
                    optionDetail = { productDetail(it) },
                    onSelect = onProductChange,
                    modifier = Modifier.weight(1f),
                )
                if (removable) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The backend rejects the whole bill with a generic "insufficient stock" error,
                // so flag the exact line here before the user submits.
                val stock = line.product?.CurrentStock?.toDoubleOrNull()
                val overStock = stock != null && (line.quantity.toDoubleOrNull() ?: 0.0) > stock
                OutlinedTextField(
                    value = line.quantity,
                    onValueChange = onQuantityChange,
                    label = { Text("Qty") },
                    suffix = line.product?.let { { Text(it.Unit) } },
                    singleLine = true,
                    isError = overStock,
                    supportingText = if (overStock) {
                        { Text("Only ${trimQty(line.product.CurrentStock)} in stock") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.unitPrice,
                    onValueChange = onUnitPriceChange,
                    label = { Text("Unit price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
