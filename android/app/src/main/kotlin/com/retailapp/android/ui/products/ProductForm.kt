package com.retailapp.android.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.retailapp.android.data.model.Category
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.ProductInput
import com.retailapp.android.data.model.Subcategory
import com.retailapp.android.data.model.UnitDto
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.DropdownField
import com.retailapp.android.ui.common.Messages

/** What the product form needs from its screen's ViewModel (Products list or Edit product). */
interface ProductFormSource {
    val categories: List<Category>
    val subcategories: List<Subcategory>
    val units: List<UnitDto>
    fun loadSubcategories(categoryId: Int)
    fun addCategory(name: String, onDone: (Category?) -> Unit)
    fun addSubcategory(categoryId: Int, name: String, onDone: (Subcategory?) -> Unit)
}

/**
 * The product fields, shared by the "New product" dialog and the Cycle 5 Edit product screen.
 * Category, subcategory and unit are kept as ids so a pre-filled edit survives the lists
 * arriving after the form is shown.
 */
class ProductFormState(initial: Product? = null, initialSku: String? = null) {
    var name by mutableStateOf(initial?.Name.orEmpty())
    var sku by mutableStateOf(initial?.Sku ?: initialSku.orEmpty())
    var price by mutableStateOf(initial?.CurrentSellingPrice.orEmpty())
    var categoryId by mutableStateOf(initial?.CategoryID)
    var subcategoryId by mutableStateOf(initial?.SubcategoryID)
    var unitCode by mutableStateOf(initial?.Unit)
    var newCategoryName by mutableStateOf("")
    var newSubcategoryName by mutableStateOf("")

    private val original = initial

    val isValid: Boolean
        get() = name.isNotBlank() && sku.isNotBlank() && categoryId != null && unitCode != null &&
            (price.isBlank() || price.toDoubleOrNull() != null)

    val isDirty: Boolean
        get() = if (original == null) {
            name.isNotBlank() || price.isNotBlank()
        } else {
            name != original.Name || sku != original.Sku || price != original.CurrentSellingPrice ||
                categoryId != original.CategoryID || subcategoryId != original.SubcategoryID || unitCode != original.Unit
        }

    fun toInput() = ProductInput(
        name = name.trim(),
        sku = sku.trim(),
        unit = unitCode!!,
        category_id = categoryId!!,
        subcategory_id = subcategoryId,
        selling_price = price.toDoubleOrNull() ?: 0.0,
    )
}

@Composable
fun ProductFormFields(
    form: ProductFormState,
    source: ProductFormSource,
    modifier: Modifier = Modifier,
    priceNote: String? = null,
) {
    // A new product defaults to the first category/unit once those lists have loaded.
    LaunchedEffect(source.categories) { if (form.categoryId == null) form.categoryId = source.categories.firstOrNull()?.ID }
    LaunchedEffect(source.units) { if (form.unitCode == null) form.unitCode = source.units.firstOrNull()?.Code }
    LaunchedEffect(form.categoryId) { form.categoryId?.let(source::loadSubcategories) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = form.name, onValueChange = { form.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = form.sku, onValueChange = { form.sku = it }, label = { Text("SKU / barcode") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = form.price,
            onValueChange = { form.price = it },
            label = { Text("Selling price") },
            prefix = { Text("₹") },
            singleLine = true,
            isError = form.price.isNotBlank() && form.price.toDoubleOrNull() == null,
            supportingText = priceNote?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = "Category",
            options = source.categories,
            selected = source.categories.find { it.ID == form.categoryId },
            optionLabel = { it.Name },
            onSelect = {
                if (it.ID != form.categoryId) form.subcategoryId = null
                form.categoryId = it.ID
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.newCategoryName,
                onValueChange = { form.newCategoryName = it },
                label = { Text("+ New category") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val trimmed = form.newCategoryName.trim()
                if (trimmed.isNotEmpty()) {
                    source.addCategory(trimmed) { created ->
                        if (created != null) {
                            form.categoryId = created.ID
                            form.subcategoryId = null
                            form.newCategoryName = ""
                        }
                    }
                }
            }) { Icon(Icons.Default.Add, contentDescription = "Add category") }
        }

        DropdownField(
            label = "Subcategory (optional)",
            options = source.subcategories,
            selected = source.subcategories.find { it.ID == form.subcategoryId },
            optionLabel = { it.Name },
            onSelect = { form.subcategoryId = it.ID },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.newSubcategoryName,
                onValueChange = { form.newSubcategoryName = it },
                label = { Text("+ New subcategory") },
                singleLine = true,
                enabled = form.categoryId != null,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                enabled = form.categoryId != null,
                onClick = {
                    val trimmed = form.newSubcategoryName.trim()
                    val categoryId = form.categoryId
                    if (trimmed.isNotEmpty() && categoryId != null) {
                        source.addSubcategory(categoryId, trimmed) { created ->
                            if (created != null) {
                                form.subcategoryId = created.ID
                                form.newSubcategoryName = ""
                            }
                        }
                    }
                },
            ) { Icon(Icons.Default.Add, contentDescription = "Add subcategory") }
        }

        DropdownField(
            label = "Unit",
            options = source.units,
            selected = source.units.find { it.Code == form.unitCode },
            optionLabel = { it.Label },
            onSelect = { form.unitCode = it.Code },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * SKUs are unique per company and an archived product keeps its SKU, so saving one that clashes
 * fails with a generic conflict. This turns that into "SKU already used by archived product X -
 * restore it instead" (Cycle 5 section 3.3 rule 4); null when the clash is with an active product.
 */
suspend fun archivedSkuMessage(sku: String, excludeId: Int? = null): String? {
    val all = NetworkModule.safeCall { NetworkModule.productApi.listProducts(includeArchived = true) }.getOrNull() ?: return null
    val clash = all.find { it.isArchived && it.ID != excludeId && it.Sku.trim().equals(sku.trim(), ignoreCase = true) } ?: return null
    return Messages.skuUsedByArchived(clash.Name)
}

/** The backend's 409 text for a duplicate SKU (internal/handlers/products.go). */
fun isSkuConflict(message: String?) = message?.contains("SKU already exists", ignoreCase = true) == true
