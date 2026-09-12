package com.retailapp.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.Session
import com.retailapp.android.ui.auth.LoginScreen
import com.retailapp.android.ui.common.LoadingBox
import kotlinx.coroutines.launch

/**
 * Top of the composable tree: restores a session from the persisted cookie on launch (so the
 * user isn't asked to log in every time the app is opened), then shows either the login screen
 * or the main drawer/nav-host, mirroring how frontend/src/context's AuthProvider gates the app.
 */
@Composable
fun RetailAppRoot() {
    var sessionChecked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (Session.currentUser == null) {
            NetworkModule.safeCall { NetworkModule.authApi.me() }.onSuccess { Session.currentUser = it }
        }
        sessionChecked = true
    }

    if (!sessionChecked) {
        LoadingBox()
        return
    }

    val user = Session.currentUser
    if (user == null) {
        LoginScreen(onLoggedIn = { Session.currentUser = it })
    } else {
        MainScreen(
            user = user,
            onLogout = {
                scope.launch {
                    NetworkModule.safeCall { NetworkModule.authApi.logout() }
                    NetworkModule.cookieJar.clear()
                    Session.currentUser = null
                }
            },
        )
    }
}
