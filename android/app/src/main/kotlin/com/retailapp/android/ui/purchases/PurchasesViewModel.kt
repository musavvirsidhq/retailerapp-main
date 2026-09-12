package com.retailapp.android.ui.purchases

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.Purchase
import com.retailapp.android.data.model.PurchaseInput
import com.retailapp.android.data.remote.NetworkModule
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
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
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

                purchasesDeferred.await().onSuccess { purchases = it }.onFailure { errorMessage = it.message }
                factoriesDeferred.await().onSuccess { factories = it }
                productsDeferred.await().onSuccess { products = it }
            }
            isLoading = false
        }
    }

    fun createPurchase(input: PurchaseInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { purchaseApi.createPurchase(input) }
                .onSuccess {
                    purchases = listOf(it) + purchases
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }
}
