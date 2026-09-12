package com.retailapp.android.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Category
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.ProductInput
import com.retailapp.android.data.model.Subcategory
import com.retailapp.android.data.model.UnitDto
import com.retailapp.android.ui.common.DropdownField
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox

@Composable
fun ProductsScreen(viewModel: ProductsViewModel = viewModel()) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add product")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.products.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.products.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No products yet. Tap + to add one.")
                }
            else -> ProductList(
                products = viewModel.products,
                categoryName = viewModel::categoryName,
                padding = padding,
                onDelete = { pendingDeleteId = it },
            )
        }
    }

    if (showAddDialog) {
        AddProductDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { input -> viewModel.addProduct(input) { ok -> if (ok) showAddDialog = false } },
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete product?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProduct(id); pendingDeleteId = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProductList(
    products: List<Product>,
    categoryName: (Int) -> String,
    padding: PaddingValues,
    onDelete: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(products, key = { it.ID }) { product ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(product.Name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${product.Sku} · ${categoryName(product.CategoryID)} · ${product.CurrentStock} ${product.Unit}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("₹${product.CurrentSellingPrice}", style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onDelete(product.ID) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProductDialog(
    viewModel: ProductsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (ProductInput) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(viewModel.categories.firstOrNull()) }
    var selectedSubcategory by remember { mutableStateOf<Subcategory?>(null) }
    var selectedUnit by remember { mutableStateOf<UnitDto?>(viewModel.units.firstOrNull()) }
    var newCategoryName by remember { mutableStateOf("") }
    var newSubcategoryName by remember { mutableStateOf("") }

    // Reload the subcategory list (and drop any stale selection) whenever the category changes,
    // same as the web admin's product form.
    LaunchedEffect(selectedCategory?.ID) {
        selectedSubcategory = null
        selectedCategory?.let { viewModel.loadSubcategories(it.ID) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, singleLine = true)
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Selling price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                DropdownField(
                    label = "Category",
                    options = viewModel.categories,
                    selected = selectedCategory,
                    optionLabel = { it.Name },
                    onSelect = { selectedCategory = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("+ New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.addCategory(trimmed) { created ->
                                if (created != null) {
                                    selectedCategory = created
                                    newCategoryName = ""
                                }
                            }
                        }
                    }) { Icon(Icons.Default.Add, contentDescription = "Add category") }
                }

                DropdownField(
                    label = "Subcategory (optional)",
                    options = viewModel.subcategories,
                    selected = selectedSubcategory,
                    optionLabel = { it.Name },
                    onSelect = { selectedSubcategory = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSubcategoryName,
                        onValueChange = { newSubcategoryName = it },
                        label = { Text("+ New subcategory") },
                        singleLine = true,
                        enabled = selectedCategory != null,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = selectedCategory != null,
                        onClick = {
                            val trimmed = newSubcategoryName.trim()
                            val categoryId = selectedCategory?.ID
                            if (trimmed.isNotEmpty() && categoryId != null) {
                                viewModel.addSubcategory(categoryId, trimmed) { created ->
                                    if (created != null) {
                                        selectedSubcategory = created
                                        newSubcategoryName = ""
                                    }
                                }
                            }
                        },
                    ) { Icon(Icons.Default.Add, contentDescription = "Add subcategory") }
                }

                DropdownField(
                    label = "Unit",
                    options = viewModel.units,
                    selected = selectedUnit,
                    optionLabel = { it.Label },
                    onSelect = { selectedUnit = it },
                )
                InlineError(viewModel.errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !viewModel.isSubmitting && name.isNotBlank() && sku.isNotBlank() && selectedCategory != null && selectedUnit != null,
                onClick = {
                    onConfirm(
                        ProductInput(
                            name = name.trim(),
                            sku = sku.trim(),
                            unit = selectedUnit!!.Code,
                            category_id = selectedCategory!!.ID,
                            subcategory_id = selectedSubcategory?.ID,
                            selling_price = price.toDoubleOrNull() ?: 0.0,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
