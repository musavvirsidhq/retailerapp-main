@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.purchases

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
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.Purchase
import com.retailapp.android.data.model.PurchaseInput
import com.retailapp.android.data.model.PurchaseItemInput
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.BillLine
import com.retailapp.android.ui.common.DiscardDialog
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.PagedListContent
import com.retailapp.android.ui.common.PhotoPickerRow
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
import com.retailapp.android.ui.common.withQuickItem

/**
 * The purchases list, or - when [startNew] - the new-purchase form, each as its own navigation
 * entry so the bottom bar can hide on the form. [presetFactoryId] preselects a supplier.
 */
@Composable
fun PurchasesScreen(
    onOpenBill: (Int) -> Unit,
    onNewPurchase: () -> Unit = {},
    startNew: Boolean = false,
    presetFactoryId: Int? = null,
    onClose: () -> Unit = {},
    onSavedOpenBill: (Int) -> Unit = onOpenBill,
    onAddProductFromScan: ((String) -> Unit)? = null,
    viewModel: PurchasesViewModel = viewModel(),
) {
    if (startNew && Session.canPurchase) {
        LaunchedEffect(Unit) { viewModel.startForm() }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshFormIfChanged() }
        if (viewModel.isFormLoading) {
            LoadingBox()
            return
        }
        NewPurchaseScreen(
            viewModel = viewModel,
            presetFactoryId = presetFactoryId,
            onBack = onClose,
            onAddProductFromScan = onAddProductFromScan,
            onSaved = { purchase, failed -> if (failed > 0) onSavedOpenBill(purchase.ID) else onClose() },
        )
        return
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.startList() }
    val list = viewModel.list
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        floatingActionButton = {
            if (Session.canPurchase) {
                FloatingActionButton(onClick = onNewPurchase) {
                    Icon(Icons.Default.Add, contentDescription = "New purchase")
                }
            }
        },
    ) { padding ->
        PagedListContent(
            list = list,
            padding = padding,
            summary = "${countLabel(list.totalCount, "bill")} · ${money(list.totalAmount)}",
            emptyText = when {
                list.query.isNotBlank() -> "No purchases match \"${list.query.trim()}\"."
                Session.canPurchase -> "No purchases ${list.filter.phrase}. Tap + to add one."
                else -> "No purchases ${list.filter.phrase}."
            },
            key = { it.ID },
            searchPlaceholder = "Search bill, invoice or ${Terms.SUPPLIER.lowercase()}",
        ) { purchase -> PurchaseRow(purchase, onClick = { onOpenBill(purchase.ID) }) }
    }
}

@Composable
private fun PurchaseRow(purchase: Purchase, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(purchase.BillNumber, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(purchase.FactoryName, purchase.InvoiceNo?.takeIf { it.isNotBlank() }, displayDate(purchase.PurchaseDate)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(purchase.TotalAmount), style = MaterialTheme.typography.bodyMedium)
                Text(
                    purchase.Status,
                    color = if (purchase.Status == "CANCELLED") MaterialTheme.colorScheme.error else successColor(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NewPurchaseScreen(
    viewModel: PurchasesViewModel,
    presetFactoryId: Int?,
    onBack: () -> Unit,
    onAddProductFromScan: ((String) -> Unit)?,
    onSaved: (Purchase, Int) -> Unit,
) {
    val factories = viewModel.factories
    val products = viewModel.products
    // No default supplier unless one was passed in, so a bill can't land on the wrong one unnoticed.
    var selectedFactory by remember { mutableStateOf<Factory?>(factories.find { it.ID == presetFactoryId }) }
    var invoiceNo by remember { mutableStateOf("") }
    var amountPaid by remember { mutableStateOf("") }
    var nextLineId by remember { mutableLongStateOf(1L) }
    var lineItems by remember { mutableStateOf(listOf(BillLine(0L, null, "", ""))) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    fun updateLine(id: Long, transform: (BillLine) -> BillLine) {
        lineItems = lineItems.map { if (it.id == id) transform(it) else it }
    }

    // Buying prices vary per purchase, so a chip or a scan adds the item with the price left
    // blank for the user to fill in from the supplier's invoice.
    fun addProduct(product: Product) {
        lineItems = lineItems.withQuickItem(product, "") { nextLineId++ }
    }

    // Cycle 5 barcode scanning: a matching SKU adds the item like a quick-item chip.
    val scan = rememberBillScanner(
        products = products,
        onAdd = ::addProduct,
        onAddProduct = onAddProductFromScan.takeIf { Session.isCompanyAdmin },
    )

    val total = lineItems.sumOf { (it.quantity.toDoubleOrNull() ?: 0.0) * (it.unitPrice.toDoubleOrNull() ?: 0.0) }
    val paidValue = amountPaid.toDoubleOrNull()
    val overpaid = paidValue != null && paidValue > total + 0.005
    val blocker = when {
        selectedFactory == null -> "Choose a ${Terms.SUPPLIER.lowercase()}"
        lineItems.any { it.product == null } -> "Choose a product on every item row"
        !lineItems.all { it.isValid } -> "Enter a quantity and buying price for every item"
        overpaid -> "Amount paid can't be more than the bill total"
        else -> null
    }
    val isDirty = lineItems.any { it.product != null } || amountPaid.isNotBlank() || invoiceNo.isNotBlank() || photos.isNotEmpty()
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (isDirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = !viewModel.isSubmitting) { requestBack() }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onKeep = { confirmDiscard = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New purchase") },
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
                    label = Terms.SUPPLIER,
                    options = factories,
                    selected = selectedFactory,
                    optionLabel = { it.Name },
                    optionDetail = { listOfNotNull(it.ContactPerson?.takeIf(String::isNotBlank), it.PrimaryPhone).joinToString(" · ") },
                    onSelect = { selectedFactory = it },
                )
            }

            item {
                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text("Invoice number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                // Buying prices vary per purchase, so a chip adds the item with the price left
                // blank for the user to fill in from the supplier's invoice.
                QuickItemsStrip(
                    title = "Most used",
                    items = viewModel.frequentItems,
                    onPick = { item -> products.find { it.ID == item.id }?.let(::addProduct) },
                )
            }

            item { Text("Items", style = MaterialTheme.typography.titleMedium) }

            items(lineItems, key = { it.id }) { line ->
                PurchaseLineItemRow(
                    line = line,
                    products = products,
                    onProductChange = { product -> updateLine(line.id) { it.copy(product = product) } },
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
                OutlinedTextField(
                    value = amountPaid,
                    onValueChange = { amountPaid = it },
                    label = { Text("Amount paid now") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    isError = overpaid,
                    supportingText = {
                        val due = total - (paidValue ?: 0.0)
                        Text(if (overpaid) "More than the bill total" else "Added to what you owe the ${Terms.SUPPLIER.lowercase()}: ${money(due.coerceAtLeast(0.0))}")
                    },
                    trailingIcon = {
                        if (total > 0) TextButton(onClick = { amountPaid = String.format(java.util.Locale.US, "%.2f", total) }) { Text("Full") }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    enabled = !viewModel.isSubmitting && blocker == null,
                    onClick = {
                        viewModel.createPurchase(
                            PurchaseInput(
                                factory_id = selectedFactory!!.ID,
                                invoice_no = invoiceNo.trim(),
                                amount_paid = amountPaid.toDoubleOrNull() ?: 0.0,
                                items = lineItems.map {
                                    PurchaseItemInput(
                                        product_id = it.product!!.ID,
                                        quantity = it.quantity.toDouble(),
                                        unit_price = it.unitPrice.toDouble(),
                                    )
                                },
                            ),
                            photos,
                            onDone = onSaved,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  ${viewModel.progressMessage ?: "Saving…"}")
                    } else {
                        Text("Create purchase · ${money(total)}")
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PurchaseLineItemRow(
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
                OutlinedTextField(
                    value = line.quantity,
                    onValueChange = onQuantityChange,
                    label = { Text("Qty") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.unitPrice,
                    onValueChange = onUnitPriceChange,
                    label = { Text("Buying price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
