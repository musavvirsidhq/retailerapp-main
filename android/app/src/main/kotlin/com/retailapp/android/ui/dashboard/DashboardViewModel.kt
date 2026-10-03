package com.retailapp.android.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.DashboardData
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    var data by mutableStateOf<DashboardData?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Set when the company's subscription no longer allows writes (the backend's
     * RequireActiveSubscription), so the quick actions can say so instead of failing later.
     */
    var writesBlockedReason by mutableStateOf<String?>(null)
        private set

    /** Called on every resume, so numbers are fresh after a quick action. Only the first load shows a spinner. */
    fun load(pull: Boolean = false) {
        viewModelScope.launch {
            if (pull) isRefreshing = true
            isLoading = data == null
            errorMessage = null
            coroutineScope {
                val dashboard = async { NetworkModule.safeCall { NetworkModule.reportApi.getDashboard() } }
                val subscription = async { NetworkModule.safeCall { NetworkModule.companyApi.getSubscriptionStatus() } }
                dashboard.await()
                    .onSuccess { data = it }
                    .onFailure { errorMessage = it.message }
                subscription.await().onSuccess {
                    writesBlockedReason = when (it.status) {
                        "EXPIRED" -> "Your subscription has expired. Renew it to add sales, purchases or payments."
                        "SUSPENDED" -> "Your company is suspended. Contact support to add sales, purchases or payments."
                        else -> null
                    }
                }
            }
            isLoading = false
            isRefreshing = false
        }
    }
}
