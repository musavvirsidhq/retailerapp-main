package com.retailapp.android.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.retailapp.android.data.model.User

/**
 * Holds the logged-in user for the whole app, the same job the React AuthContext does on the
 * frontend (see frontend/src/context). Plain Compose state on a singleton is enough here - no
 * DI framework, no ViewModel indirection - so screens just read/write [currentUser] directly.
 */
object Session {
    var currentUser: User? by mutableStateOf(null)

    val isSuperAdmin: Boolean get() = currentUser?.user_type == "SUPER_ADMIN"
    val isCompanyAdmin: Boolean get() = currentUser?.user_type == "COMPANY_ADMIN"
}
