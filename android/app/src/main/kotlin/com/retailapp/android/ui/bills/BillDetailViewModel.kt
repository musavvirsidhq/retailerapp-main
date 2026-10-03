package com.retailapp.android.ui.bills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.BillData
import com.retailapp.android.data.model.CancelRequest
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.BillShare
import com.retailapp.android.ui.common.DataChanges
import kotlinx.coroutines.launch

/** Shared by both sale and purchase bills - [isSale] picks which backend endpoints to call. */
class BillDetailViewModel(private val id: Int, private val isSale: Boolean) : ViewModel() {

    class Factory(private val id: Int, private val isSale: Boolean) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BillDetailViewModel(id, isSale) as T
    }

    var bill by mutableStateOf<BillData?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSharing by mutableStateOf(false)
        private set
    var isCancelling by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            // The request must run inside safeCall: made outside it, an offline phone threw an
            // uncaught IOException here and crashed the app.
            NetworkModule.safeCall {
                if (isSale) NetworkModule.billApi.getSaleBill(id) else NetworkModule.billApi.getPurchaseBill(id)
            }
                .onSuccess {
                    bill = it
                    Session.companyName = it.CompanyName
                }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun cancel(reason: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isCancelling = true
            errorMessage = null
            val result = if (isSale) {
                NetworkModule.safeCall { NetworkModule.saleApi.cancelSale(id, CancelRequest(reason)) }
            } else {
                NetworkModule.safeCall { NetworkModule.purchaseApi.cancelPurchase(id, CancelRequest(reason)) }
            }
            result
                .onSuccess {
                    DataChanges.bump()
                    load()
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isCancelling = false
        }
    }

    fun sharePdf() {
        val billNumber = bill?.BillNumber ?: return
        viewModelScope.launch {
            isSharing = true
            errorMessage = null
            BillShare.downloadPdf(isSale, id, billNumber)
                .mapCatching { uri -> BillShare.sharePdf(RetailApp.instance, uri, billNumber).getOrThrow() }
                .onFailure { errorMessage = "Couldn't share the PDF: ${it.message}" }
            isSharing = false
        }
    }
}
