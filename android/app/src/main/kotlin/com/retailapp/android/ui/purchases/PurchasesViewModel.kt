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
import com.retailapp.android.ui.common.PendingUploads
import com.retailapp.android.ui.common.PhotoUploader
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PurchasesViewModel : ViewModel() {
    private val purchaseApi = NetworkModule.purchaseApi
    private val factoryApi = NetworkModule.factoryApi
    private val productApi = NetworkModule.productApi

    var purchases by mutableStateOf<List<Purchase>>(emptyList())
        private set
    var factories by mutableStateOf<List<Factory>>(emptyList())
        private set
    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var frequentItems by mutableStateOf<List<QuickItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var progressMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            coroutineScope {
                val purchasesDeferred = async { NetworkModule.safeCall { purchaseApi.listPurchases() } }
                val factoriesDeferred = async { NetworkModule.safeCall { factoryApi.listFactories() } }
                val productsDeferred = async { NetworkModule.safeCall { productApi.listProducts() } }
                val frequentDeferred = async { NetworkModule.safeCall { productApi.frequentItems("purchase") } }

                purchasesDeferred.await().onSuccess { purchases = it }.onFailure { errorMessage = it.message }
                factoriesDeferred.await().onSuccess { factories = it }
                productsDeferred.await().onSuccess { products = it }
                frequentDeferred.await().onSuccess { frequentItems = it }
            }
            isLoading = false
        }
    }

    fun createPurchase(input: PurchaseInput, photos: List<Uri>, onDone: (purchase: Purchase, failedPhotos: Int) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { purchaseApi.createPurchase(input) }
                .onSuccess { purchase ->
                    purchases = listOf(purchase) + purchases
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
