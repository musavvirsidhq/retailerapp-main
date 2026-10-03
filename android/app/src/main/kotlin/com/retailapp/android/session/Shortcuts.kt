package com.retailapp.android.session

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The launcher shortcuts in res/xml/shortcuts.xml (Cycle 5 section 10.1). */
enum class AppShortcut(val id: String, val label: String) {
    NEW_SALE("new_sale", "New sale"),
    NEW_PURCHASE("new_purchase", "New purchase"),
    COLLECT_MONEY("collect_money", "Collect money"),
    ;

    /** Same permission rules as the dashboard quick actions. */
    val allowed: Boolean
        get() = when (this) {
            NEW_SALE, COLLECT_MONEY -> Session.canSell
            NEW_PURCHASE -> Session.canPurchase
        }

    companion object {
        const val EXTRA = "shortcut"
        fun fromIntent(intent: Intent?): AppShortcut? = intent?.getStringExtra(EXTRA)?.let { id -> entries.find { it.id == id } }
    }
}

/**
 * A shortcut the app was launched with, held until MainScreen can act on it - which may be only
 * after the user has logged in (section 10.1 rule 4).
 */
object PendingShortcut {
    var value: AppShortcut? by mutableStateOf(null)

    fun consume(): AppShortcut? = value.also { value = null }
}
