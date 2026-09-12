package com.retailapp.android.ui.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.Sale
import com.retailapp.android.data.model.SaleInput
import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.remote.NetworkModule
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
                val salesDeferred = async { NetworkModule.safeCall { saleApi.listSales() } }
                val shopsDeferred = async { NetworkModule.safeCall { shopApi.listShops() } }
                val productsDeferred = async { NetworkModule.safeCall { productApi.listProducts() } }

                salesDeferred.await().onSuccess { sales = it }.onFailure { errorMessage = it.message }
                shopsDeferred.await().onSuccess { shops = it }
                productsDeferred.await().onSuccess { products = it }
            }
            isLoading = false
        }
    }

    fun createSale(input: SaleInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { saleApi.createSale(input) }
                .onSuccess {
                    sales = listOf(it) + sales
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
