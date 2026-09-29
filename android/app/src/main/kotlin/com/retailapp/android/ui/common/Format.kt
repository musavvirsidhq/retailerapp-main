package com.retailapp.android.ui.common

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "₹12,450.00" from a backend decimal string (or a Double). */
fun money(value: String?): String = money(value?.toDoubleOrNull() ?: 0.0)

fun money(value: Double): String = "₹" + String.format(Locale.US, "%,.2f", value)

/**
 * Parses the RFC 3339 timestamps Go's encoding/json writes (e.g. 2026-09-28T10:15:30.123+05:30
 * or ...Z). java.time needs API 26 and this app supports 24, so it goes through
 * SimpleDateFormat after dropping the fractional seconds it can't handle.
 */
fun parseTimestamp(value: String?): Date? {
    if (value.isNullOrBlank()) return null
    val normalized = value.replace(Regex("\\.\\d+"), "").replace(Regex("Z$"), "+00:00")
    return try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(normalized)
    } catch (e: Exception) {
        null
    }
}

/** "2 days ago", "Yesterday", ... for a backend timestamp; empty when there isn't one. */
fun relativeTime(value: String?): String {
    val date = parseTimestamp(value) ?: return ""
    return DateUtils.getRelativeTimeSpanString(
        date.time,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

/** "28 Sep 2026" from a YYYY-MM-DD date string. */
fun displayDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value.take(10)) ?: return value
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        value
    }
}

/** "28 Sep 2026, 10:15 am" from a backend timestamp. */
fun displayDateTime(value: String?): String {
    val date = parseTimestamp(value) ?: return value.orEmpty()
    return SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(date)
}

/** YYYY-MM-DD for [daysAgo]-style ledger range filters, or the start of this month/year. */
fun isoDate(calendar: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
