package com.retailapp.android.ui.sales

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.QuickItem
import com.retailapp.android.data.model.Sale
import com.retailapp.android.data.model.SaleInput
import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.PendingUploads
import com.retailapp.android.ui.common.PhotoUploader
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class SalesViewModel : ViewModel() {
    private val saleApi = NetworkModule.saleApi
    private val shopApi = NetworkModule.shopApi
    private val productApi = NetworkModule.productApi

    var sales by mutableStateOf<List<Sale>>(emptyList())
        private set
    var shops by mutableStateOf<List<Shop>>(emptyList())
        private set
    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var frequentItems by mutableStateOf<List<QuickItem>>(emptyList())
        private set
    var usualItems by mutableStateOf<List<QuickItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var progressMessage by mutableStateOf<String?>(null)
        private set

    private var usualJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            coroutineScope {
                val salesDeferred = async { NetworkModule.safeCall { saleApi.listSales() } }
                val shopsDeferred = async { NetworkModule.safeCall { shopApi.listShops() } }
                val productsDeferred = async { NetworkModule.safeCall { productApi.listProducts() } }
                val frequentDeferred = async { NetworkModule.safeCall { productApi.frequentItems("sale") } }

                salesDeferred.await().onSuccess { sales = it }.onFailure { errorMessage = it.message }
                shopsDeferred.await().onSuccess { shops = it }
                productsDeferred.await().onSuccess { products = it }
                // Quick-access chips are a convenience: if they fail the form still works.
                frequentDeferred.await().onSuccess { frequentItems = it }
            }
            isLoading = false
        }
    }

    /** "Usually buys" chips for the customer picked on the new-sale form. */
    fun loadUsualItems(shopId: Int?) {
        usualJob?.cancel()
        usualItems = emptyList()
        if (shopId == null) return
        usualJob = viewModelScope.launch {
            NetworkModule.safeCall { productApi.frequentItems("sale", shopId = shopId, limit = 5) }
                .onSuccess { usualItems = it }
        }
    }

    fun createSale(input: SaleInput, photos: List<Uri>, onDone: (sale: Sale, failedPhotos: Int) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { saleApi.createSale(input) }
                .onSuccess { sale ->
                    sales = listOf(sale) + sales
                    val failed = PhotoUploader.upload(AttachmentEntity.SALE, sale.ID, photos) { done, total ->
                        progressMessage = if (done < total) "Uploading photo ${done + 1} of $total…" else null
                    }
                    progressMessage = null
                    PendingUploads.put(AttachmentEntity.SALE, sale.ID, failed)
                    onDone(sale, failed.size)
                }
                .onFailure { errorMessage = it.message }
            isSubmitting = false
        }
    }
}
