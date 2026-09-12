package com.retailapp.android.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.DashboardData
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    var data by mutableStateOf<DashboardData?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            NetworkModule.safeCall { NetworkModule.reportApi.getDashboard() }
                .onSuccess { data = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }
}
