@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.products

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.ProductInput
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.ArchivedTag
import com.retailapp.android.ui.common.DataChanges
import com.retailapp.android.ui.common.DestructiveConfirmDialog
import com.retailapp.android.ui.common.DiscardDialog
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.Messages
import com.retailapp.android.ui.common.OverflowMenu
import com.retailapp.android.ui.common.trimQty
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class EditProductViewModel(private val id: Int) : ProductFormViewModel() {
    class Factory(private val id: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditProductViewModel(id) as T
    }

    var product by mutableStateOf<Product?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var isArchiving by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            coroutineScope {
                val productDeferred = async { NetworkModule.safeCall { productApi.getProduct(id) } }
                val lookups = async { loadLookups() }
                productDeferred.await().onSuccess { product = it }.onFailure { errorMessage = it.message }
                lookups.await()
            }
            isLoading = false
        }
    }

    fun save(input: ProductInput, onDone: () -> Unit) {
        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            NetworkModule.safeCall { productApi.updateProduct(id, input) }
                .onSuccess {
                    DataChanges.bump()
                    onDone()
                }
                .onFailure {
                    errorMessage = if (isSkuConflict(it.message)) archivedSkuMessage(input.sku, excludeId = id) ?: it.message else it.message
                }
            isSaving = false
        }
    }

    /** Archive (Company Admin only). Stock on hand is allowed - the dialog warns about it. */
    fun archive(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isArchiving = true
            errorMessage = null
            NetworkModule.safeCall { productApi.archiveProduct(id) }
                .onSuccess {
                    DataChanges.bump()
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isArchiving = false
        }
    }

    fun restore() {
        viewModelScope.launch {
            NetworkModule.safeCall { productApi.restoreProduct(id) }
                .onSuccess {
                    DataChanges.bump()
                    load()
                }
                .onFailure { errorMessage = it.message }
        }
    }
}

/**
 * Cycle 5 section 4: edit a product's details. Stock is shown but not editable (it only moves
 * through purchases, sales and cancellations), and a price change only affects new bills.
 */
@Composable
fun EditProductScreen(id: Int, onBack: () -> Unit) {
    val viewModel: EditProductViewModel = viewModel(key = "edit-product-$id", factory = EditProductViewModel.Factory(id))
    val product = viewModel.product
    val context = LocalContext.current
    // Rebuilt once the product arrives, so the fields start pre-filled.
    val form = remember(product?.ID, product?.archivedAt) { product?.let { ProductFormState(initial = it) } }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }

    val requestBack = { if (form?.isDirty == true) confirmDiscard = true else onBack() }
    BackHandler(enabled = !viewModel.isSaving) { requestBack() }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onKeep = { confirmDiscard = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit product") },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !viewModel.isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (product != null && Session.isCompanyAdmin) {
                        OverflowMenu { close ->
                            if (product.isArchived) {
                                DropdownMenuItem(text = { Text("Restore") }, onClick = { close(); viewModel.restore() })
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Archive", color = MaterialTheme.colorScheme.error) },
                                    onClick = { close(); confirmArchive = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            product == null || form == null ->
                ErrorBox(viewModel.errorMessage ?: "Couldn't load the product.", onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (product.isArchived) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArchivedTag()
                        Text("Hidden from lists and new bills.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("In stock: ${trimQty(product.CurrentStock)} ${product.Unit}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Stock changes only through purchases, sales and cancellations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ProductFormFields(
                    form = form,
                    source = viewModel,
                    priceNote = "A new price applies to new bills only.",
                )
                InlineError(viewModel.errorMessage)
                Button(
                    enabled = !viewModel.isSaving && form.isValid && form.isDirty,
                    onClick = {
                        viewModel.save(form.toInput()) {
                            Toast.makeText(context, "Product saved", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save changes")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (confirmArchive && product != null) {
        val stock = product.CurrentStock.toDoubleOrNull() ?: 0.0
        DestructiveConfirmDialog(
            title = "Archive ${product.Name}?",
            message = "It will be hidden from lists, pickers and quick items. Old bills still show it, " +
                "and you can restore it from Products → Show archived.",
            warning = if (stock > 0) Messages.stockWillBeUnsellable(product.CurrentStock, product.Unit) else null,
            confirmLabel = "Archive",
            isSubmitting = viewModel.isArchiving,
            errorMessage = viewModel.errorMessage,
            onDismiss = { confirmArchive = false },
            onConfirm = {
                viewModel.archive { ok ->
                    if (ok) {
                        confirmArchive = false
                        Toast.makeText(context, "${product.Name} archived", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            },
        )
    }
}
