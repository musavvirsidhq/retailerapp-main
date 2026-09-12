package com.retailapp.android.ui.factories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.FactoryInput
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.launch

class FactoriesViewModel : ViewModel() {
    private val api = NetworkModule.factoryApi

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
            NetworkModule.safeCall { api.listFactories() }
                .onSuccess { factories = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun addFactory(input: FactoryInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createFactory(input) }
                .onSuccess {
                    factories = factories + it
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun deleteFactory(id: Int) {
        viewModelScope.launch {
            NetworkModule.safeCall { api.deleteFactory(id) }
                .onSuccess { factories = factories.filter { it.ID != id } }
                .onFailure { errorMessage = it.message }
        }
    }
}
