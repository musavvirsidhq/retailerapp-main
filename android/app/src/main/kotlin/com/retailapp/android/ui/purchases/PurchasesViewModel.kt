package com.retailapp.android.ui.purchases

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.Purchase
import com.retailapp.android.data.model.PurchaseInput
import com.retailapp.android.data.model.QuickItem
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.DataChanges
import com.retailapp.android.ui.common.PagedList
import com.retailapp.android.ui.common.PendingUploads
import com.retailapp.android.ui.common.PhotoUploader
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Backs both the purchases list and the new-purchase form, like SalesViewModel. */
class PurchasesViewModel : ViewModel() {
    private val purchaseApi = NetworkModule.purchaseApi
    private val factoryApi = NetworkModule.factoryApi
    private val productApi = NetworkModule.productApi

    /** Cycle 5: date chips, search by bill/invoice number or supplier, 50 at a time. */
    val list = PagedList(
        scope = viewModelScope,
        key = Purchase::ID,
        dateOf = Purchase::PurchaseDate,
        amountOf = { if (it.Status == "CANCELLED") 0.0 else it.TotalAmount.toDoubleOrNull() ?: 0.0 },
        matches = { purchase, q ->
            purchase.BillNumber.lowercase().contains(q) || purchase.FactoryName.lowercase().contains(q) ||
                purchase.InvoiceNo?.lowercase()?.contains(q) == true
        },
    ) { from, to, q, limit, offset ->
        NetworkModule.safeCallWithHeaders { purchaseApi.listPurchases(from, to, q, limit, offset) }
    }

    var factories by mutableStateOf<List<Factory>>(emptyList())
        private set
    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var frequentItems by mutableStateOf<List<QuickItem>>(emptyList())
        private set
    var isFormLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var progressMessage by mutableStateOf<String?>(null)
        private set

    private var listStarted = false
    private var formStarted = false
    private var formSeenVersion = -1

    fun startList() {
        if (listStarted) list.refreshIfChanged() else {
            listStarted = true
            list.reload()
        }
    }

    fun startForm() {
        if (formStarted) return
        formStarted = true
        loadForm()
    }

    /** Re-fetches pickers if something changed meanwhile, e.g. a product added from the scanner's "Add product". */
    fun refreshFormIfChanged() {
        if (formStarted && !isSubmitting && formSeenVersion != DataChanges.version) loadForm(showSpinner = false)
    }

    private fun loadForm(showSpinner: Boolean = true) {
        formSeenVersion = DataChanges.version
        viewModelScope.launch {
            if (showSpinner) isFormLoading = true
            coroutineScope {
                val factoriesDeferred = async { NetworkModule.safeCall { factoryApi.listFactories() } }
                val productsDeferred = async { NetworkModule.safeCall { productApi.listProducts() } }
                val frequentDeferred = async { NetworkModule.safeCall { productApi.frequentItems("purchase") } }
                // Archived suppliers/products can't go on a new bill (Cycle 5 section 3.3 rule 3).
                factoriesDeferred.await().onSuccess { factories = it.filter { f -> !f.isArchived } }.onFailure { errorMessage = it.message }
                productsDeferred.await().onSuccess { products = it.filter { p -> !p.isArchived } }.onFailure { errorMessage = it.message }
                frequentDeferred.await().onSuccess { frequentItems = it }
            }
            isFormLoading = false
        }
    }

    fun createPurchase(input: PurchaseInput, photos: List<Uri>, onDone: (purchase: Purchase, failedPhotos: Int) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { purchaseApi.createPurchase(input) }
                .onSuccess { purchase ->
                    DataChanges.bump()
                    formSeenVersion = DataChanges.version
                    val failed = PhotoUploader.upload(AttachmentEntity.PURCHASE, purchase.ID, photos) { done, total ->
                        progressMessage = if (done < total) "Uploading photo ${done + 1} of $total…" else null
                    }
                    progressMessage = null
                    PendingUploads.put(AttachmentEntity.PURCHASE, purchase.ID, failed)
                    onDone(purchase, failed.size)
                }
                .onFailure { errorMessage = it.message }
            isSubmitting = false
        }
    }
}
