package com.retailapp.android.ui.factories

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.FactoryInput
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.DataChanges
import kotlinx.coroutines.launch

class FactoriesViewModel : ViewModel() {
    private val api = NetworkModule.factoryApi

    var factories by mutableStateOf<List<Factory>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    /** Cycle 5 "Show archived": archived suppliers are listed greyed, with Restore. */
    var showArchived by mutableStateOf(false)
        private set

    private var seenVersion = -1

    /** Active suppliers first; archived ones (only when shown) at the end. */
    val visibleFactories: List<Factory>
        get() = if (showArchived) factories.sortedBy { it.isArchived } else factories.filter { !it.isArchived }

    init {
        load()
    }

    fun load(pull: Boolean = false) {
        viewModelScope.launch {
            seenVersion = DataChanges.version
            if (pull) isRefreshing = true else isLoading = factories.isEmpty()
            errorMessage = null
            NetworkModule.safeCall { api.listFactories(includeArchived = showArchived.takeIf { it }) }
                .onSuccess { factories = it }
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

    fun addFactory(input: FactoryInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createFactory(input) }
                .onSuccess {
                    factories = factories + it
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
    fun restore(factory: Factory) {
        viewModelScope.launch {
            NetworkModule.safeCall { api.restoreFactory(factory.ID) }
                .onSuccess {
                    DataChanges.bump()
                    Toast.makeText(RetailApp.instance, "${factory.Name} restored", Toast.LENGTH_SHORT).show()
                    load()
                }
                // errorMessage has no visible slot once the list is loaded, so toast it.
                .onFailure { Toast.makeText(RetailApp.instance, "Couldn't restore: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
