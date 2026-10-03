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
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.DataChanges
import com.retailapp.android.ui.common.Messages
import com.retailapp.android.ui.common.PagedList
import com.retailapp.android.ui.common.PdfRef
import com.retailapp.android.ui.common.PendingUploads
import com.retailapp.android.ui.common.PhotoUploader
import com.retailapp.android.ui.common.SavedShare
import com.retailapp.android.ui.common.money
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Backs both the sales list and the new-sale form; each is its own navigation entry (and so its
 * own ViewModel), and only loads what it shows - see [startList] / [startForm].
 */
class SalesViewModel : ViewModel() {
    private val saleApi = NetworkModule.saleApi
    private val shopApi = NetworkModule.shopApi
    private val productApi = NetworkModule.productApi

    /** Cycle 5: date chips, search by bill number or customer, 50 at a time. */
    val list = PagedList(
        scope = viewModelScope,
        key = Sale::ID,
        dateOf = Sale::SaleDate,
        amountOf = { if (it.Status == "CANCELLED") 0.0 else it.TotalAmount.toDoubleOrNull() ?: 0.0 },
        matches = { sale, q -> sale.BillNumber.lowercase().contains(q) || sale.ShopName.lowercase().contains(q) },
    ) { from, to, q, limit, offset ->
        NetworkModule.safeCallWithHeaders { saleApi.listSales(from, to, q, limit, offset) }
    }

    var shops by mutableStateOf<List<Shop>>(emptyList())
        private set
    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var frequentItems by mutableStateOf<List<QuickItem>>(emptyList())
        private set
    var usualItems by mutableStateOf<List<QuickItem>>(emptyList())
        private set
    var isFormLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var progressMessage by mutableStateOf<String?>(null)
        private set

    /** Set once a sale is saved: drives the "WhatsApp bill / Share PDF / Done" sheet. */
    var saved by mutableStateOf<SavedShare?>(null)
        private set
    var savedSale by mutableStateOf<Sale?>(null)
        private set
    var savedFailedPhotos = 0
        private set

    private var usualJob: Job? = null
    private var listStarted = false
    private var formStarted = false

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
        if (formStarted && saved == null && formSeenVersion != DataChanges.version) loadForm(showSpinner = false)
    }

    private var formSeenVersion = -1

    private fun loadForm(showSpinner: Boolean = true) {
        formSeenVersion = DataChanges.version
        viewModelScope.launch {
            if (showSpinner) isFormLoading = true
            coroutineScope {
                val shopsDeferred = async { NetworkModule.safeCall { shopApi.listShops() } }
                val productsDeferred = async { NetworkModule.safeCall { productApi.listProducts() } }
                val frequentDeferred = async { NetworkModule.safeCall { productApi.frequentItems("sale") } }
                // Archived customers/products can't go on a new bill (Cycle 5 section 3.3 rule 3).
                shopsDeferred.await().onSuccess { shops = it.filter { shop -> !shop.isArchived } }.onFailure { errorMessage = it.message }
                productsDeferred.await().onSuccess { products = it.filter { product -> !product.isArchived } }.onFailure { errorMessage = it.message }
                // Quick-access chips are a convenience: if they fail the form still works.
                frequentDeferred.await().onSuccess { frequentItems = it }
            }
            isFormLoading = false
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

    fun createSale(input: SaleInput, photos: List<Uri>) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { saleApi.createSale(input) }
                .onSuccess { sale ->
                    DataChanges.bump()
                    coroutineScope {
                        // Fetched while the photos upload, for the WhatsApp text.
                        val balance = async { NetworkModule.safeCall { NetworkModule.paymentApi.getShopBalance(sale.ShopID) } }
                        val bill = async {
                            if (Session.companyName == null) NetworkModule.safeCall { NetworkModule.billApi.getSaleBill(sale.ID) }.getOrNull() else null
                        }
                        val failed = PhotoUploader.upload(AttachmentEntity.SALE, sale.ID, photos) { done, total ->
                            progressMessage = if (done < total) "Uploading photo ${done + 1} of $total…" else null
                        }
                        progressMessage = null
                        PendingUploads.put(AttachmentEntity.SALE, sale.ID, failed)
                        bill.await()?.let { Session.companyName = it.CompanyName }
                        val pending = balance.await().getOrNull()?.balance?.toDoubleOrNull()
                        val shop = shops.find { it.ID == sale.ShopID }
                        val total = sale.TotalAmount.toDoubleOrNull() ?: 0.0
                        savedFailedPhotos = failed.size
                        savedSale = sale
                        saved = SavedShare(
                            title = Messages.saleSavedTitle(sale.BillNumber, total),
                            subtitle = pending?.let { "${sale.ShopName} now owes ${money(it.coerceAtLeast(0.0))}" },
                            partyName = sale.ShopName,
                            phone = shop?.PrimaryPhone,
                            message = Messages.saleBill(
                                customer = sale.ShopName,
                                billNumber = sale.BillNumber,
                                total = total,
                                date = sale.SaleDate,
                                paid = sale.AmountPaid.toDoubleOrNull() ?: 0.0,
                                pending = pending,
                                company = Session.companyName,
                            ),
                            pdf = PdfRef(isSale = true, id = sale.ID, billNumber = sale.BillNumber),
                        )
                    }
                }
                .onFailure { errorMessage = it.message }
            isSubmitting = false
        }
    }
}
