@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.ledger

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.DuesResponse
import com.retailapp.android.data.model.DuesRow
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.RemindedStore
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.relativeTime
import com.retailapp.android.ui.common.successColor
import kotlinx.coroutines.launch

enum class DuesSort(val api: String, val label: String) {
    RECENT("recent", "Most recent activity"),
    AMOUNT("amount", "Highest pending"),
    OLDEST("oldest", "Longest since last payment"),
    NAME("name", "Name A-Z"),
}

class DuesViewModel(private val kind: PartyKind) : ViewModel() {
    class Factory(private val kind: PartyKind) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DuesViewModel(kind) as T
    }

    // The chosen sort is remembered on the device (Cycle 4 section 3.3).
    private val prefs = RetailApp.instance.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)
    private val sortKey = "dues_sort_${kind.name}"

    var data by mutableStateOf<DuesResponse?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var sort by mutableStateOf(DuesSort.entries.find { it.api == prefs.getString(sortKey, null) } ?: DuesSort.RECENT)
        private set
    var includeZero by mutableStateOf(false)
        private set
    var search by mutableStateOf("")

    val visibleRows: List<DuesRow>
        get() {
            val q = search.trim().lowercase()
            val rows = data?.rows.orEmpty()
            return if (q.isEmpty()) rows else rows.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
        }

    private var started = false

    fun loadIfNeeded() {
        if (!started) load()
    }

    fun load(pull: Boolean = false) {
        started = true
        viewModelScope.launch {
            if (pull) isRefreshing = true else isLoading = data == null
            errorMessage = null
            NetworkModule.safeCall {
                if (kind == PartyKind.CUSTOMER) {
                    NetworkModule.ledgerApi.customerDues(sort.api, includeZero)
                } else {
                    NetworkModule.ledgerApi.supplierDues(sort.api, includeZero)
                }
            }
                .onSuccess { data = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
            isRefreshing = false
        }
    }

    fun updateSort(value: DuesSort) {
        sort = value
        prefs.edit().putString(sortKey, value.api).apply()
        load()
    }

    fun updateIncludeZero(value: Boolean) {
        includeZero = value
        load()
    }
}

/**
 * The Cycle 5 "Dues" bottom-bar tab: Customer Dues, with a toggle to Supplier Dues for staff who
 * can see both. The chosen side survives switching tabs.
 */
@Composable
fun DuesTabScreen(onOpenLedger: (PartyKind, Int) -> Unit, onSendReminders: () -> Unit) {
    val kinds = PartyKind.entries.filter { it.hasAccess }
    if (kinds.isEmpty()) return
    var kindName by rememberSaveable { mutableStateOf(kinds.first().name) }
    val kind = kinds.find { it.name == kindName } ?: kinds.first()
    DuesScreen(
        kind = kind,
        onBack = null,
        onOpenLedger = { onOpenLedger(kind, it) },
        kindOptions = kinds,
        onKindChange = { kindName = it.name },
        onSendReminders = onSendReminders,
    )
}

/**
 * Who owes what. [onBack] is null when shown as the Dues tab. [onSendReminders] adds the
 * Cycle 5 "Send reminders" button on the customer side.
 */
@Composable
fun DuesScreen(
    kind: PartyKind,
    onBack: (() -> Unit)?,
    onOpenLedger: (Int) -> Unit,
    kindOptions: List<PartyKind> = emptyList(),
    onKindChange: (PartyKind) -> Unit = {},
    onSendReminders: (() -> Unit)? = null,
) {
    val viewModel: DuesViewModel = viewModel(key = "dues-${kind.name}", factory = DuesViewModel.Factory(kind))
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Reload whenever the screen comes back into view, e.g. after collecting money in a ledger.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    // The tab's toggle swaps the ViewModel without a resume, so load that side too.
    LaunchedEffect(kind) { viewModel.loadIfNeeded() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (onBack == null) "Dues" else kind.duesTitle) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            DuesSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = { if (option == viewModel.sort) Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {
                                        sortMenuOpen = false
                                        viewModel.updateSort(option)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = viewModel.data
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && data == null ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            data != null -> PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = { viewModel.load(pull = true) },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (kindOptions.size > 1) {
                    item {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            kindOptions.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = option == kind,
                                    onClick = { onKindChange(option) },
                                    shape = SegmentedButtonDefaults.itemShape(index, kindOptions.size),
                                ) { Text(option.plural) }
                            }
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(kind.duesLabel, style = MaterialTheme.typography.labelLarge)
                            Text(money(data.total_pending), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("${data.pending_count} ${kind.plural.lowercase()} with a pending balance", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (kind == PartyKind.CUSTOMER && onSendReminders != null && data.pending_count > 0) {
                    item {
                        OutlinedButton(onClick = onSendReminders, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Send reminders")
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = viewModel.search,
                        onValueChange = { viewModel.search = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search name or phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Sorted by: ${viewModel.sort.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Show all  ", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = viewModel.includeZero, onCheckedChange = viewModel::updateIncludeZero)
                    }
                }
                val rows = viewModel.visibleRows
                if (rows.isEmpty()) {
                    item {
                        Text(
                            if (viewModel.search.isNotBlank()) "No match." else "Nothing pending. 🎉",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(rows, key = { it.id }) { row ->
                    DuesRowCard(
                        row,
                        remindedToday = kind == PartyKind.CUSTOMER && RemindedStore.remindedToday(row.id),
                        onClick = { onOpenLedger(row.id) },
                    )
                }
                item { Box(modifier = Modifier.padding(8.dp)) }
            } }
        }
    }
}

@Composable
private fun DuesRowCard(row: DuesRow, remindedToday: Boolean, onClick: () -> Unit) {
    val balance = row.balance.toDoubleOrNull() ?: 0.0
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(row.area?.takeIf { it.isNotBlank() }, row.phone).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val activity = relativeTime(row.last_activity_at)
                Text(
                    if (activity.isEmpty()) "No activity yet" else "Last activity $activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (remindedToday) {
                    Text("Reminded today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                when {
                    balance < 0 -> {
                        Text(money(-balance), style = MaterialTheme.typography.titleMedium, color = successColor(), fontWeight = FontWeight.Bold)
                        Text("Advance", style = MaterialTheme.typography.labelSmall, color = successColor())
                    }
                    else -> {
                        Text(money(balance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
