@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.purchases

import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.retailapp.android.ui.common.DropdownField
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.PhotoPickerRow
import com.retailapp.android.ui.common.QuickItemsStrip
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.successColor
import com.retailapp.android.ui.common.withQuickItem

/**
 * Purchases list plus the new-purchase form. [startNew] opens the form directly (dashboard
 * "+ Purchase", a supplier ledger's "New purchase") with [presetFactoryId] preselected.
 */
@Composable
fun PurchasesScreen(
    onOpenBill: (Int) -> Unit,
    startNew: Boolean = false,
    presetFactoryId: Int? = null,
    onClose: () -> Unit = {},
    viewModel: PurchasesViewModel = viewModel(),
) {
    var showNewPurchase by rememberSaveable { mutableStateOf(startNew && Session.canPurchase) }

    if (showNewPurchase) {
        if (viewModel.isLoading) {
            LoadingBox()
            return
        }
        NewPurchaseScreen(
            viewModel = viewModel,
            presetFactoryId = presetFactoryId,
            onBack = { if (startNew) onClose() else showNewPurchase = false },
            onSaved = { purchase, failed ->
                showNewPurchase = false
                when {
                    failed > 0 -> onOpenBill(purchase.ID)
                    startNew -> onClose()
                }
            },
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            if (Session.canPurchase) {
                FloatingActionButton(onClick = { showNewPurchase = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New purchase")
                }
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.purchases.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.purchases.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No purchases yet.")
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.purchases, key = { it.ID }) { purchase ->
                    PurchaseRow(purchase, onClick = { onOpenBill(purchase.ID) })
                }
            }
        }
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
            Column {
                Text(purchase.BillNumber, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(purchase.FactoryName, purchase.InvoiceNo).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${purchase.TotalAmount}", style = MaterialTheme.typography.bodyMedium)
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
    onSaved: (Purchase, Int) -> Unit,
) {
    val factories = viewModel.factories
    val products = viewModel.products
    var selectedFactory by remember { mutableStateOf<Factory?>(factories.find { it.ID == presetFactoryId } ?: factories.firstOrNull()) }
    var invoiceNo by remember { mutableStateOf("") }
    var amountPaid by remember { mutableStateOf("") }
    var nextLineId by remember { mutableLongStateOf(1L) }
    var lineItems by remember { mutableStateOf(listOf(BillLine(0L, null, "", ""))) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    fun updateLine(id: Long, transform: (BillLine) -> BillLine) {
        lineItems = lineItems.map { if (it.id == id) transform(it) else it }
    }

    val total = lineItems.sumOf { (it.quantity.toDoubleOrNull() ?: 0.0) * (it.unitPrice.toDoubleOrNull() ?: 0.0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New purchase") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !viewModel.isSubmitting) {
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
                DropdownField(
                    label = Terms.SUPPLIER,
                    options = factories,
                    selected = selectedFactory,
                    optionLabel = { it.Name },
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
                    onPick = { item ->
                        products.find { it.ID == item.id }?.let { product ->
                            lineItems = lineItems.withQuickItem(product, "") { nextLineId++ }
                        }
                    },
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
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Total ${money(total)}", style = MaterialTheme.typography.titleMedium)
                }
            }

            item {
                OutlinedTextField(
                    value = amountPaid,
                    onValueChange = { amountPaid = it },
                    label = { Text("Amount paid") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { PhotoPickerRow(photos = photos, onPhotosChange = { photos = it }) }

            item { InlineError(viewModel.errorMessage) }

            item {
                Button(
                    enabled = !viewModel.isSubmitting && selectedFactory != null && lineItems.all { it.isValid },
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
                        Text("Create purchase")
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
                DropdownField(
                    label = "Product",
                    options = products,
                    selected = line.product,
                    optionLabel = { it.Name },
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
