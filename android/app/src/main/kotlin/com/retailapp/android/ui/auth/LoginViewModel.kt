package com.retailapp.android.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retailapp.android.data.model.LoginRequest
import com.retailapp.android.data.model.User
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun login(onSuccess: (User) -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "Enter your username and password"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            NetworkModule.safeCall { NetworkModule.authApi.login(LoginRequest(username.trim(), password)) }
                .onSuccess(onSuccess)
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }
}
