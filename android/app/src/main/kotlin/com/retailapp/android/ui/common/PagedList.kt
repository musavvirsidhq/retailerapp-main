@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Headers
import java.util.Calendar
import kotlin.coroutines.coroutineContext

/** Date chips on the Sales, Purchases and Payments lists (Cycle 5 section 6). */
enum class DateFilter(val label: String) {
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
    ALL("All"),
    ;

    /** Inclusive from/to as YYYY-MM-DD in the phone's time zone; both null for [ALL]. */
    fun range(): Pair<String?, String?> {
        if (this == ALL) return null to null
        val cal = Calendar.getInstance()
        val to = isoDate(cal)
        when (this) {
            WEEK -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
            else -> Unit
        }
        return isoDate(cal) to to
    }

    /** Lower-case phrase for empty states: "No sales this month." */
    val phrase: String
        get() = when (this) {
            TODAY -> "today"
            WEEK -> "this week"
            MONTH -> "this month"
            ALL -> "yet"
        }
}

/** Fetches one page: (from, to, search, limit, offset) -> rows plus the response headers. */
typealias PageFetcher<T> = suspend (from: String?, to: String?, q: String?, limit: Int, offset: Int) -> Result<Pair<List<T>, Headers>>

/**
 * State and loading for a date-filtered, searchable, paged list (sales, purchases, payments).
 *
 * The list endpoints gained from/to/q/limit/offset in Cycle 5. A backend that supports them
 * answers with an X-Total-Count header; then pages are fetched from the server as the user
 * scrolls. A backend without them ignores the params and returns everything - in that case the
 * filtering, totals and paging happen here on the device instead, so the screens behave the same
 * against either.
 */
class PagedList<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> Any,
    private val dateOf: (T) -> String,
    /** What a row adds to the summary total (0 for a cancelled bill). */
    private val amountOf: (T) -> Double,
    /** On-device search fallback; the query arrives trimmed and lower-cased. */
    private val matches: (T, String) -> Boolean,
    private val fetch: PageFetcher<T>,
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var totalCount by mutableIntStateOf(0)
        private set
    var totalAmount by mutableDoubleStateOf(0.0)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var filter by mutableStateOf(DateFilter.MONTH)
        private set
    var query by mutableStateOf("")
        private set

    val canLoadMore: Boolean get() = items.size < totalCount

    private var serverPaged = false
    private var clientRows: List<T> = emptyList()
    private var loadJob: Job? = null
    private var generation = 0
    private var seenVersion = -1

    fun reload(pull: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch { load(pull) }
    }

    /** Reloads only if something was saved since the last load (see [DataChanges]). */
    fun refreshIfChanged() {
        if (seenVersion != DataChanges.version) reload()
    }

    fun updateFilter(value: DateFilter) {
        if (value == filter) return
        filter = value
        reload()
    }

    /** Search box input; waits for a pause in typing before asking the server. */
    fun updateQuery(value: String) {
        query = value
        loadJob?.cancel()
        loadJob = scope.launch {
            delay(300)
            load(pull = false)
        }
    }

    private suspend fun load(pull: Boolean) {
        val gen = ++generation
        seenVersion = DataChanges.version
        if (pull) isRefreshing = true else if (items.isEmpty()) isLoading = true
        errorMessage = null
        val (from, to) = filter.range()
        val q = query.trim().takeIf { it.isNotEmpty() }
        val result = fetch(from, to, q, PAGE_SIZE, 0)
        // safeCall turns a cancellation into a failure; a newer load owns the state now.
        coroutineContext.ensureActive()
        if (gen != generation) return
        result
            .onSuccess { (rows, headers) ->
                val serverTotal = headers["X-Total-Count"]?.toIntOrNull()
                if (serverTotal != null) {
                    serverPaged = true
                    clientRows = emptyList()
                    items = rows
                    totalCount = serverTotal
                    totalAmount = headers["X-Total-Amount"]?.toDoubleOrNull() ?: rows.sumOf(amountOf)
                } else {
                    serverPaged = false
                    val needle = q?.lowercase()
                    clientRows = rows.filter { row ->
                        inRange(dateOf(row), from, to) && (needle == null || matches(row, needle))
                    }
                    items = clientRows.take(PAGE_SIZE)
                    totalCount = clientRows.size
                    totalAmount = clientRows.sumOf(amountOf)
                }
            }
            .onFailure { errorMessage = it.message }
        isLoading = false
        isRefreshing = false
    }

    fun loadMore() {
        if (isLoading || isLoadingMore || !canLoadMore) return
        if (!serverPaged) {
            items = clientRows.take(items.size + PAGE_SIZE)
            return
        }
        val gen = generation
        isLoadingMore = true
        scope.launch {
            val (from, to) = filter.range()
            val result = fetch(from, to, query.trim().takeIf { it.isNotEmpty() }, PAGE_SIZE, items.size)
            if (gen == generation) {
                result
                    .onSuccess { (rows, _) ->
                        val seen = items.mapTo(HashSet(), key)
                        items = items + rows.filter { key(it) !in seen }
                        // Fewer rows than the header promised (e.g. one was just cancelled): stop asking.
                        if (rows.isEmpty()) totalCount = items.size
                    }
                    .onFailure { errorMessage = it.message }
            }
            isLoadingMore = false
        }
    }

    private fun inRange(date: String, from: String?, to: String?): Boolean {
        val day = date.take(10)
        return (from == null || day >= from) && (to == null || day <= to)
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}

@Composable
fun DateFilterChips(selected: DateFilter, onSelect: (DateFilter) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DateFilter.entries) { filter ->
            FilterChip(selected = selected == filter, onClick = { onSelect(filter) }, label = { Text(filter.label) })
        }
    }
}

/** "23 bills · ₹1,42,300.00" under the date chips. */
@Composable
fun ListSummary(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

fun countLabel(count: Int, singular: String, plural: String = singular + "s") = "$count ${if (count == 1) singular else plural}"

/** Spinner row shown at the bottom of a list while the next page loads. */
@Composable
fun LoadingMoreRow() {
    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** Calls [onLoadMore] when the user scrolls within a few rows of the end of the list. */
@Composable
fun LazyListState.LoadMoreWhenNearEnd(enabled: Boolean, onLoadMore: () -> Unit) {
    val nearEnd by remember(this) {
        derivedStateOf {
            val info = layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    // Through derivedStateOf so scrolling doesn't recompose the caller on every frame.
    val count by remember(this) { derivedStateOf { layoutInfo.totalItemsCount } }
    LaunchedEffect(nearEnd, enabled, count) {
        if (nearEnd && enabled) onLoadMore()
    }
}

/**
 * The body shared by the Sales, Purchases and Payments lists: search, date chips, the
 * "23 bills · ₹…" summary, pull-to-refresh and loading the next page on scroll.
 */
@Composable
fun <T> PagedListContent(
    list: PagedList<T>,
    padding: PaddingValues,
    summary: String,
    emptyText: String,
    key: (T) -> Any,
    searchPlaceholder: String? = null,
    extraFilters: (@Composable () -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    listState.LoadMoreWhenNearEnd(enabled = list.canLoadMore && !list.isLoadingMore) { list.loadMore() }

    when {
        list.isLoading && list.items.isEmpty() -> LoadingBox(modifier = Modifier.padding(padding))
        // Errors show inline (with the chips still usable) rather than replacing the whole list.
        else -> PullToRefreshBox(
            isRefreshing = list.isRefreshing,
            onRefresh = { list.reload(pull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searchPlaceholder != null) {
                    item(key = "search") {
                        ListSearchField(list.query, onChange = list::updateQuery, placeholder = searchPlaceholder, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                item(key = "dates") { DateFilterChips(list.filter, onSelect = list::updateFilter, modifier = Modifier.padding(top = if (searchPlaceholder == null) 8.dp else 0.dp)) }
                if (extraFilters != null) item(key = "extra") { extraFilters() }
                item(key = "summary") { ListSummary(summary) }
                list.errorMessage?.let { message ->
                    item(key = "error") {
                        Column {
                            InlineError(message)
                            TextButton(onClick = { list.reload() }) { Text("Retry") }
                        }
                    }
                }
                if (list.items.isEmpty() && !list.isLoading && list.errorMessage == null) {
                    item(key = "empty") { EmptyListMessage(emptyText) }
                }
                items(list.items, key = key) { row(it) }
                if (list.isLoadingMore) item(key = "more") { LoadingMoreRow() }
                item(key = "end") { Box(modifier = Modifier.padding(bottom = 80.dp)) }
            }
        }
    }
}
