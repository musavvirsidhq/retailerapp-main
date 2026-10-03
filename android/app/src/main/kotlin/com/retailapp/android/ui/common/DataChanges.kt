package com.retailapp.android.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Bumped whenever the app saves something that other screens list (a sale, a payment, an edited
 * or archived customer, ...). With the Cycle 5 bottom bar each tab keeps its own screens alive,
 * so lists compare this on resume and reload only when something actually changed - that keeps
 * their scroll position when the user just looked at a bill and came back.
 */
object DataChanges {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }
}
