package com.retailapp.android.ui.shops

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.model.ShopInput
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.DataChanges
import kotlinx.coroutines.launch

class ShopsViewModel : ViewModel() {
    private val api = NetworkModule.shopApi

    var shops by mutableStateOf<List<Shop>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    /** Cycle 5 "Show archived": archived customers are listed greyed, with Restore. */
    var showArchived by mutableStateOf(false)
        private set

    private var seenVersion = -1

    /** Active customers first; archived ones (only when shown) at the end. */
    val visibleShops: List<Shop>
        get() = if (showArchived) shops.sortedBy { it.isArchived } else shops.filter { !it.isArchived }

    init {
        load()
    }

    fun load(pull: Boolean = false) {
        viewModelScope.launch {
            seenVersion = DataChanges.version
            if (pull) isRefreshing = true else isLoading = shops.isEmpty()
            errorMessage = null
            NetworkModule.safeCall { api.listShops(includeArchived = showArchived.takeIf { it }) }
                .onSuccess { shops = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
            isRefreshing = false
        }
    }

    fun refreshIfChanged() {
        if (seenVersion != DataChanges.version) load()
    }

    fun toggleShowArchived() {
        showArchived = !showArchived
        load()
    }

    fun addShop(input: ShopInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createShop(input) }
                .onSuccess {
                    shops = shops + it
                    DataChanges.bump()
                    seenVersion = DataChanges.version
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    /** Company Admin only; the backend refuses anyone else. */
    fun restore(shop: Shop) {
        viewModelScope.launch {
            NetworkModule.safeCall { api.restoreShop(shop.ID) }
                .onSuccess {
                    DataChanges.bump()
                    Toast.makeText(RetailApp.instance, "${shop.Name} restored", Toast.LENGTH_SHORT).show()
                    load()
                }
                // errorMessage has no visible slot once the list is loaded, so toast it.
                .onFailure { Toast.makeText(RetailApp.instance, "Couldn't restore: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
