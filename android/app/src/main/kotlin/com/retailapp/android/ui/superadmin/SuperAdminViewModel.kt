package com.retailapp.android.ui.superadmin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.Company
import com.retailapp.android.data.model.CreateCompanyInput
import com.retailapp.android.data.model.ExtendSubscriptionRequest
import com.retailapp.android.data.model.GrantSubscriptionRequest
import com.retailapp.android.data.model.Subscription
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.launch

class SuperAdminViewModel : ViewModel() {
    private val api = NetworkModule.superAdminApi

    var companies by mutableStateOf<List<Company>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    var subscriptionHistory by mutableStateOf<List<Subscription>>(emptyList())
        private set
    var isHistoryLoading by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            NetworkModule.safeCall { api.listCompanies() }
                .onSuccess { companies = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun loadHistory(companyId: Int) {
        viewModelScope.launch {
            isHistoryLoading = true
            NetworkModule.safeCall { api.getSubscriptionHistory(companyId) }
                .onSuccess { subscriptionHistory = it }
                .onFailure { errorMessage = it.message }
            isHistoryLoading = false
        }
    }

    fun createCompany(input: CreateCompanyInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createCompany(input) }
                .onSuccess {
                    companies = companies + it
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun grantTrial(companyId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.grantTrial(companyId) }
                .onSuccess { loadHistory(companyId); load(); onDone(true) }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun grantSubscription(companyId: Int, type: String, amount: Double, months: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.grantSubscription(companyId, GrantSubscriptionRequest(type, amount, months)) }
                .onSuccess { loadHistory(companyId); load(); onDone(true) }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun extendSubscription(companyId: Int, months: Int, days: Int, amount: Double, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.extendSubscription(companyId, ExtendSubscriptionRequest(months, days, amount)) }
                .onSuccess { loadHistory(companyId); load(); onDone(true) }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }
}
