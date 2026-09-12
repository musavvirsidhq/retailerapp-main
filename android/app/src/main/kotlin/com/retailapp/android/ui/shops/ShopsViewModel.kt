package com.retailapp.android.ui.shops

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.model.ShopInput
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.launch

class ShopsViewModel : ViewModel() {
    private val api = NetworkModule.shopApi

    var shops by mutableStateOf<List<Shop>>(emptyList())
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
            NetworkModule.safeCall { api.listShops() }
                .onSuccess { shops = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun addShop(input: ShopInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createShop(input) }
                .onSuccess {
                    shops = shops + it
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun deleteShop(id: Int) {
        viewModelScope.launch {
            NetworkModule.safeCall { api.deleteShop(id) }
                .onSuccess { shops = shops.filter { it.ID != id } }
                .onFailure { errorMessage = it.message }
        }
    }
}
