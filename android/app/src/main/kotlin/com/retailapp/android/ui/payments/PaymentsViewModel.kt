package com.retailapp.android.ui.payments

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.Payment
import com.retailapp.android.data.model.PaymentInput
import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.PendingUploads
import com.retailapp.android.ui.common.PhotoUploader
import com.retailapp.android.ui.common.Terms
import kotlinx.coroutines.Job
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
    var progressMessage by mutableStateOf<String?>(null)
        private set

    /** Pending balance of the party picked in the form, shown as a hint above the amount. */
    var selectedBalance by mutableStateOf<String?>(null)
        private set
    private var balanceJob: Job? = null

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
            shops.find { it.ID == partyId }?.Name ?: "${Terms.CUSTOMER} #$partyId"
        } else {
            factories.find { it.ID == partyId }?.Name ?: "${Terms.SUPPLIER} #$partyId"
        }

    fun loadBalance(partyType: String, partyId: Int?) {
        balanceJob?.cancel()
        selectedBalance = null
        if (partyId == null) return
        balanceJob = viewModelScope.launch {
            val result = if (partyType == "shop") {
                NetworkModule.safeCall { paymentApi.getShopBalance(partyId) }
            } else {
                NetworkModule.safeCall { paymentApi.getFactoryBalance(partyId) }
            }
            result.onSuccess { selectedBalance = it.balance }
        }
    }

    /**
     * Saves the payment, then uploads its photos. The payment stays saved even if photos fail;
     * those are handed to [PendingUploads] so the payment's detail screen can retry them.
     */
    fun addPayment(input: PaymentInput, photos: List<Uri>, onDone: (payment: Payment, failedPhotos: Int) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { paymentApi.createPayment(input) }
                .onSuccess { payment ->
                    payments = listOf(payment) + payments
                    val failed = PhotoUploader.upload(AttachmentEntity.PAYMENT, payment.ID, photos) { done, total ->
                        progressMessage = if (done < total) "Uploading photo ${done + 1} of $total…" else null
                    }
                    progressMessage = null
                    PendingUploads.put(AttachmentEntity.PAYMENT, payment.ID, failed)
                    onDone(payment, failed.size)
                }
                .onFailure { errorMessage = it.message }
            isSubmitting = false
        }
    }
}
