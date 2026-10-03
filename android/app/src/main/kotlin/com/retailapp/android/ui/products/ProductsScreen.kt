@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Product
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.ArchiveListTopBar
import com.retailapp.android.ui.common.ArchivedTag
import com.retailapp.android.ui.common.EmptyListMessage
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.ListSearchField
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.trimQty

/**
 * Products list. A Company Admin taps a product to edit it (Cycle 5 section 4; per open
 * question Q1 only the admin edits products, since prices drive margins and the below-cost
 * check). [startAddSku] opens "New product" straight away with that SKU filled in - the
 * scanner's "Add product" for an unknown barcode lands here.
 */
@Composable
fun ProductsScreen(
    onBack: (() -> Unit)?,
    onEditProduct: (Int) -> Unit,
    startAddSku: String? = null,
    viewModel: ProductsViewModel = viewModel(),
) {
    var showAddDialog by rememberSaveable { mutableStateOf(startAddSku != null && Session.isCompanyAdmin) }
    var search by rememberSaveable { mutableStateOf("") }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfChanged() }

    Scaffold(
        topBar = {
            ArchiveListTopBar(
                title = "Products",
                onBack = onBack,
                showArchived = viewModel.showArchived,
                onToggleArchived = viewModel::toggleShowArchived,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.clearError(); showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add product")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.products.isEmpty() && !showAddDialog ->
                ErrorBox(viewModel.errorMessage!!, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = { viewModel.load(pull = true) },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                ProductList(
                    products = viewModel.visibleProducts,
                    search = search,
                    onSearchChange = { search = it },
                    categoryName = viewModel::categoryName,
                    onOpen = { if (Session.isCompanyAdmin) onEditProduct(it.ID) },
                    onTogglePin = viewModel::togglePin,
                    onRestore = viewModel::restore,
                )
            }
        }
    }

    if (showAddDialog) {
        AddProductDialog(
            viewModel = viewModel,
            initialSku = startAddSku,
            onDismiss = { showAddDialog = false },
            onConfirm = { input -> viewModel.addProduct(input) { ok -> if (ok) showAddDialog = false } },
        )
    }
}

@Composable
private fun ProductList(
    products: List<Product>,
    search: String,
    onSearchChange: (String) -> Unit,
    categoryName: (Int) -> String,
    onOpen: (Product) -> Unit,
    onTogglePin: (Product) -> Unit,
    onRestore: (Product) -> Unit,
) {
    val q = search.trim().lowercase()
    val visible = if (q.isEmpty()) {
        products
    } else {
        products.filter { it.Name.lowercase().contains(q) || it.Sku.lowercase().contains(q) || categoryName(it.CategoryID).lowercase().contains(q) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (products.isEmpty()) {
            item { EmptyListMessage("No products yet. Tap + to add one.") }
            return@LazyColumn
        }
        item { ListSearchField(search, onChange = onSearchChange, placeholder = "Search name, SKU or category", modifier = Modifier.padding(top = 8.dp)) }
        if (visible.isEmpty()) item { Text("No match.", modifier = Modifier.padding(16.dp)) }
        items(visible, key = { it.ID }) { product ->
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(product) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp).alpha(if (product.isArchived) 0.55f else 1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(product.Name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                            if (product.isArchived) ArchivedTag()
                        }
                        Text(
                            "${product.Sku} · ${categoryName(product.CategoryID)} · ${trimQty(product.CurrentStock)} ${product.Unit} in stock",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(money(product.CurrentSellingPrice), style = MaterialTheme.typography.bodyMedium)
                    }
                    when {
                        product.isArchived && Session.isCompanyAdmin -> TextButton(onClick = { onRestore(product) }) { Text("Restore") }
                        // Pinned items always lead the "most used" chips on the sale and purchase forms.
                        !product.isArchived && Session.isCompanyAdmin -> IconButton(onClick = { onTogglePin(product) }) {
                            Icon(
                                if (product.Pinned) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (product.Pinned) "Unpin" else "Pin to quick access",
                                tint = if (product.Pinned) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProductDialog(
    viewModel: ProductsViewModel,
    initialSku: String?,
    onDismiss: () -> Unit,
    onConfirm: (com.retailapp.android.data.model.ProductInput) -> Unit,
) {
    val form = remember { ProductFormState(initialSku = initialSku) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New product") },
        text = {
            // Scrolls: seven fields plus the "+ New" rows don't fit a small phone otherwise.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ProductFormFields(form = form, source = viewModel)
                InlineError(viewModel.errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !viewModel.isSubmitting && form.isValid,
                onClick = { onConfirm(form.toInput()) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
