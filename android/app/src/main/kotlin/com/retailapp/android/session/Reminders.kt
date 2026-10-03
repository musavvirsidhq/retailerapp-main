package com.retailapp.android.session

import android.content.Context
import com.retailapp.android.RetailApp
import com.retailapp.android.ui.common.todayIso

/**
 * Which customers were sent a payment reminder today. Kept on this device only (Cycle 5 section
 * 8.3 rule 2 / open question Q3), keyed by company + customer, holding the date of the last
 * reminder - so "Reminded today" clears itself the next day without any cleanup.
 */
object RemindedStore {
    private val prefs by lazy { RetailApp.instance.getSharedPreferences("payment_reminders", Context.MODE_PRIVATE) }

    private fun key(customerId: Int) = "c${Session.currentUser?.company_id ?: 0}_$customerId"

    fun markReminded(customerId: Int) {
        prefs.edit().putString(key(customerId), todayIso()).apply()
    }

    fun remindedToday(customerId: Int): Boolean = prefs.getString(key(customerId), null) == todayIso()
}
