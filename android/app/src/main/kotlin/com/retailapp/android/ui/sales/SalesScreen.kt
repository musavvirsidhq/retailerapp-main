@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.sales

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
 * Sales list plus the new-sale form. [startNew] opens the form directly (dashboard "+ Sale",
 * a customer ledger's "New sale") with [presetShopId] preselected; [onClose] then goes back.
 */
@Composable
fun SalesScreen(
    onOpenBill: (Int) -> Unit,
    startNew: Boolean = false,
    presetShopId: Int? = null,
    onClose: () -> Unit = {},
    viewModel: SalesViewModel = viewModel(),
) {
    var showNewSale by rememberSaveable { mutableStateOf(startNew && Session.canSell) }

    if (showNewSale) {
        if (viewModel.isLoading) {
            LoadingBox()
            return
        }
        NewSaleScreen(
            viewModel = viewModel,
            presetShopId = presetShopId,
            onBack = { if (startNew) onClose() else showNewSale = false },
            onSaved = { sale, failed ->
                showNewSale = false
                when {
                    failed > 0 -> onOpenBill(sale.ID)
                    startNew -> onClose()
                }
            },
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            if (Session.canSell) {
                FloatingActionButton(onClick = { showNewSale = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New sale")
                }
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.sales.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.sales.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No sales yet.")
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.sales, key = { it.ID }) { sale -> SaleRow(sale, onClick = { onOpenBill(sale.ID) }) }
            }
        }
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
            Column {
                Text(sale.BillNumber, style = MaterialTheme.typography.titleMedium)
                Text("${sale.ShopName} · ${sale.PaymentType}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${sale.TotalAmount}", style = MaterialTheme.typography.bodyMedium)
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
    onSaved: (Sale, Int) -> Unit,
) {
    val shops = viewModel.shops
    val products = viewModel.products
    var selectedShop by remember { mutableStateOf<Shop?>(shops.find { it.ID == presetShopId } ?: shops.firstOrNull()) }
    var paymentType by remember { mutableStateOf("cash") }
    var amountPaid by remember { mutableStateOf("") }
    var nextLineId by remember { mutableLongStateOf(1L) }
    var lineItems by remember { mutableStateOf(listOf(BillLine(0L, null, "", ""))) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(selectedShop?.ID) { viewModel.loadUsualItems(selectedShop?.ID) }

    fun updateLine(id: Long, transform: (BillLine) -> BillLine) {
        lineItems = lineItems.map { if (it.id == id) transform(it) else it }
    }

    fun addQuick(productId: Int) {
        val product = products.find { it.ID == productId } ?: return
        lineItems = lineItems.withQuickItem(product, product.CurrentSellingPrice) { nextLineId++ }
    }

    val total = lineItems.sumOf { (it.quantity.toDoubleOrNull() ?: 0.0) * (it.unitPrice.toDoubleOrNull() ?: 0.0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New sale") },
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
                    label = Terms.CUSTOMER,
                    options = shops,
                    selected = selectedShop,
                    optionLabel = { it.Name },
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
                    enabled = !viewModel.isSubmitting && selectedShop != null && lineItems.all { it.isValid },
                    onClick = {
                        viewModel.createSale(
                            SaleInput(
                                shop_id = selectedShop!!.ID,
                                amount_paid = amountPaid.toDoubleOrNull() ?: 0.0,
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
                            onDone = onSaved,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  ${viewModel.progressMessage ?: "Saving…"}")
                    } else {
                        Text("Create sale")
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
                    label = { Text("Unit price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
