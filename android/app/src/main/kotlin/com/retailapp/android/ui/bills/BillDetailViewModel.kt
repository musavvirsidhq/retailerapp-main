package com.retailapp.android.ui.bills

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.BillData
import com.retailapp.android.data.model.CancelRequest
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.launch
import java.io.File

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
            val call = if (isSale) NetworkModule.billApi.getSaleBill(id) else NetworkModule.billApi.getPurchaseBill(id)
            NetworkModule.safeCall { call }
                .onSuccess { bill = it }
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
                .onSuccess { load(); onDone(true) }
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
            val call = if (isSale) NetworkModule.billApi.getSalePdf(id) else NetworkModule.billApi.getPurchasePdf(id)
            NetworkModule.safeCall { call }
                .onSuccess { body ->
                    // File I/O, FileProvider and startActivity can all throw (bad path config,
                    // no app installed to handle the share sheet, disk full, ...). None of that
                    // is a network error safeCall already handles, so it must be caught here too
                    // - left unguarded, any of it previously crashed the whole app on tap.
                    try {
                        val app = RetailApp.instance
                        val dir = File(app.cacheDir, "bills").apply { mkdirs() }
                        val file = File(dir, "$billNumber.pdf")
                        file.outputStream().use { out -> body.byteStream().copyTo(out) }
                        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, billNumber).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        app.startActivity(chooser)
                    } catch (e: Exception) {
                        errorMessage = "Couldn't share the PDF: ${e.message}"
                    }
                }
                .onFailure { errorMessage = it.message }
            isSharing = false
        }
    }
}
