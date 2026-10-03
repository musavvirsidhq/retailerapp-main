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
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.DataChanges
import com.retailapp.android.ui.common.Messages
import com.retailapp.android.ui.common.PagedList
import com.retailapp.android.ui.common.PendingUploads
import com.retailapp.android.ui.common.PhotoUploader
import com.retailapp.android.ui.common.SavedShare
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.common.money
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Backs both the payments list and the Collect / Pay form, like SalesViewModel. */
class PaymentsViewModel : ViewModel() {
    private val paymentApi = NetworkModule.paymentApi

    /** null = both; "shop" = collected from customers; "factory" = paid to suppliers. */
    var partyFilter by mutableStateOf<String?>(null)
        private set

    /** Cycle 5: date chips and paging, 50 at a time. */
    val list = PagedList(
        scope = viewModelScope,
        key = Payment::ID,
        dateOf = Payment::PaymentDate,
        amountOf = { it.Amount.toDoubleOrNull() ?: 0.0 },
        matches = { _, _ -> true },
    ) { from, to, _, limit, offset ->
        val type = partyFilter
        NetworkModule.safeCallWithHeaders { paymentApi.listPayments(from, to, type, limit, offset) }.map { (rows, headers) ->
            // An older backend ignores party_type, so apply it here too.
            (if (type == null) rows else rows.filter { it.PartyType == type }) to headers
        }
    }

    var shops by mutableStateOf<List<Shop>>(emptyList())
        private set
    var factories by mutableStateOf<List<Factory>>(emptyList())
        private set
    var isFormLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var progressMessage by mutableStateOf<String?>(null)
        private set

    /** Company setting (Cycle 5 section 10.3): Save stays disabled until a photo is added. */
    var requirePhoto by mutableStateOf(false)
        private set

    /** Set after a collection from a customer: drives the "WhatsApp receipt" sheet. */
    var saved by mutableStateOf<SavedShare?>(null)
        private set

    /** Pending balance of the party picked in the form, shown as a hint above the amount. */
    var selectedBalance by mutableStateOf<String?>(null)
        private set
    private var balanceJob: Job? = null
    private var listStarted = false
    private var formStarted = false

    fun startList() {
        if (listStarted) list.refreshIfChanged() else {
            listStarted = true
            list.reload()
            // Names for the rows - archived parties included, their payments still show (section 3.2).
            loadParties(includeArchived = true)
        }
    }

    fun startForm() {
        if (formStarted) return
        formStarted = true
        viewModelScope.launch {
            isFormLoading = true
            coroutineScope {
                val parties = async { loadPartiesNow(includeArchived = false) }
                val settings = async { NetworkModule.safeCall { NetworkModule.companyApi.getSettings() } }
                parties.await()
                // A backend without the setting (404) means it is off.
                requirePhoto = settings.await().getOrNull()?.require_payment_photo == true
            }
            isFormLoading = false
        }
    }

    fun updatePartyFilter(value: String?) {
        if (value == partyFilter) return
        partyFilter = value
        list.reload()
    }

    private fun loadParties(includeArchived: Boolean) {
        viewModelScope.launch { loadPartiesNow(includeArchived) }
    }

    private suspend fun loadPartiesNow(includeArchived: Boolean) = coroutineScope {
        val flag = includeArchived.takeIf { it }
        val shopsDeferred = async { NetworkModule.safeCall { NetworkModule.shopApi.listShops(flag) } }
        val factoriesDeferred = async { NetworkModule.safeCall { NetworkModule.factoryApi.listFactories(flag) } }
        // The form's pickers never offer an archived party (Cycle 5 section 3.3 rule 3).
        shopsDeferred.await().onSuccess { shops = if (includeArchived) it else it.filter { s -> !s.isArchived } }
        factoriesDeferred.await().onSuccess { factories = if (includeArchived) it else it.filter { f -> !f.isArchived } }
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
     * [onDone] runs straight away for a supplier payment; a customer collection shows the
     * receipt sheet first ([saved]) and the screen calls [onDone] when it is closed.
     */
    fun addPayment(input: PaymentInput, photos: List<Uri>, onDone: (payment: Payment, failedPhotos: Int) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { paymentApi.createPayment(input) }
                .onSuccess { payment ->
                    DataChanges.bump()
                    val isCollection = payment.PartyType == "shop"
                    coroutineScope {
                        val balance = async {
                            if (isCollection) NetworkModule.safeCall { paymentApi.getShopBalance(payment.PartyID) }.getOrNull() else null
                        }
                        val failed = PhotoUploader.upload(AttachmentEntity.PAYMENT, payment.ID, photos) { done, total ->
                            progressMessage = if (done < total) "Uploading photo ${done + 1} of $total…" else null
                        }
                        progressMessage = null
                        PendingUploads.put(AttachmentEntity.PAYMENT, payment.ID, failed)
                        if (isCollection) {
                            val shop = shops.find { it.ID == payment.PartyID }
                            val name = shop?.Name ?: partyName(payment.PartyType, payment.PartyID)
                            val amount = payment.Amount.toDoubleOrNull() ?: input.amount
                            val pending = balance.await()?.balance?.toDoubleOrNull()
                            pendingDone = { onDone(payment, failed.size) }
                            saved = SavedShare(
                                title = Messages.paymentSavedTitle(amount),
                                subtitle = pending?.let { "$name now owes ${money(it.coerceAtLeast(0.0))}" },
                                partyName = name,
                                phone = shop?.PrimaryPhone,
                                message = Messages.paymentReceipt(name, amount, payment.PaymentMode, payment.PaymentDate, pending, Session.companyName),
                                pdf = null,
                            )
                        } else {
                            onDone(payment, failed.size)
                        }
                    }
                }
                .onFailure { errorMessage = it.message }
            isSubmitting = false
        }
    }

    private var pendingDone: (() -> Unit)? = null

    /** Closes the receipt sheet and continues where [addPayment] would have. */
    fun finishSaved() {
        saved = null
        pendingDone?.invoke()
        pendingDone = null
    }
}
