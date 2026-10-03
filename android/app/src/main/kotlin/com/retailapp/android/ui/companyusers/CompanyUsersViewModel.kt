package com.retailapp.android.ui.companyusers

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.CompanySettings
import com.retailapp.android.data.model.CompanyUser
import com.retailapp.android.data.model.CreateStaffInput
import com.retailapp.android.data.model.SubscriptionStatus
import com.retailapp.android.data.model.UpdatePermissionsRequest
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class CompanyUsersViewModel : ViewModel() {
    private val api = NetworkModule.companyApi

    var users by mutableStateOf<List<CompanyUser>>(emptyList())
        private set
    var subscriptionStatus by mutableStateOf<SubscriptionStatus?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    /** Cycle 5 company settings; null when the server doesn't offer them (older backend). */
    var settings by mutableStateOf<CompanySettings?>(null)
        private set
    var isSavingSettings by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            coroutineScope {
                val usersDeferred = async { NetworkModule.safeCall { api.listUsers() } }
                val statusDeferred = async { NetworkModule.safeCall { api.getSubscriptionStatus() } }
                val settingsDeferred = async { NetworkModule.safeCall { api.getSettings() } }

                usersDeferred.await().onSuccess { users = it }.onFailure { errorMessage = it.message }
                statusDeferred.await().onSuccess { subscriptionStatus = it }
                settingsDeferred.await().onSuccess { settings = it }
            }
            isLoading = false
        }
    }

    fun setRequirePaymentPhoto(value: Boolean) {
        val previous = settings ?: return
        settings = previous.copy(require_payment_photo = value)
        viewModelScope.launch {
            isSavingSettings = true
            NetworkModule.safeCall { api.updateSettings(CompanySettings(require_payment_photo = value)) }
                .onSuccess { settings = it }
                .onFailure {
                    settings = previous
                    Toast.makeText(RetailApp.instance, "Couldn't save the setting: ${it.message}", Toast.LENGTH_LONG).show()
                }
            isSavingSettings = false
        }
    }

    fun createStaff(input: CreateStaffInput, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall { api.createStaff(input) }
                .onSuccess {
                    users = users + it
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun updatePermissions(id: Int, purchaseAccess: Boolean, salesAccess: Boolean, salesBelowCostApprove: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isSubmitting = true
            errorMessage = null
            NetworkModule.safeCall {
                api.updatePermissions(id, UpdatePermissionsRequest(purchaseAccess, salesAccess, salesBelowCostApprove))
            }
                .onSuccess { updated ->
                    users = users.map { if (it.id == updated.id) updated else it }
                    onDone(true)
                }
                .onFailure {
                    errorMessage = it.message
                    onDone(false)
                }
            isSubmitting = false
        }
    }

    fun disableUser(id: Int) {
        viewModelScope.launch {
            NetworkModule.safeCall { api.disableUser(id) }
                .onSuccess { updated -> users = users.map { if (it.id == updated.id) updated else it } }
                .onFailure { Toast.makeText(RetailApp.instance, "Couldn't disable: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
