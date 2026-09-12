package com.retailapp.android.ui.payments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.Payment
import com.retailapp.android.data.model.PaymentInput
import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PaymentsViewModel : ViewModel() {
    private val paymentApi = NetworkModule.paymentApi

    var payments by mutableStateOf<List<Payment>>(emptyList())
        private set
    var shops by mutableStateOf<List<Shop>>(emptyList())
        private set
    var factories by mutableStateOf<List<Factory>>(emptyList())
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
                val paymentsDeferred = async { NetworkModule.safeCall { paymentApi.listPayments() } }
                val shopsDeferred = async { NetworkModule.safeCall { NetworkModule.shopApi.listShops() } }
                val factoriesDeferred = async { NetworkModule.safeCall { NetworkModule.factoryApi.listFactories() } }

                paymentsDeferred.await().onSuccess { payments = it }.onFailure { errorMessage = it.message }
                shopsDeferred.await().onSuccess { shops = it }
                factoriesDeferred.await().onSuccess { factories = it }
            }
            isLoading = false
        }
    }

    fun partyName(partyType: String, partyId: Int): String =
        if (partyType == "shop") {
            shops.find { it.ID == partyId }?.Name ?: "Shop #$partyId"
        } else {
            factories.find { it.ID == partyId }?.Name ?: "Factory #$partyId"
        }

    fun addPayment(input: PaymentInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { paymentApi.createPayment(input) }
                .onSuccess {
                    payments = listOf(it) + payments
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
