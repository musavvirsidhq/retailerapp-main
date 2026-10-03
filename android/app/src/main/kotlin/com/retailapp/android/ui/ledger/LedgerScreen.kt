@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.LedgerEntry
import com.retailapp.android.data.model.LedgerResponse
import com.retailapp.android.data.remote.AttachmentEntity
import android.widget.Toast
import com.retailapp.android.RetailApp
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.RemindedStore
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.ArchivedTag
import com.retailapp.android.ui.common.DataChanges
import com.retailapp.android.ui.common.DestructiveConfirmDialog
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InfoDialog
import com.retailapp.android.ui.common.Messages
import com.retailapp.android.ui.common.OverflowMenu
import com.retailapp.android.ui.common.hasPhone
import com.retailapp.android.ui.common.shortDate
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.displayDate
import com.retailapp.android.ui.common.isoDate
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.successColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class LedgerRange(val label: String) {
    ALL("All time"),
    MONTH("This month"),
    THREE_MONTHS("Last 3 months"),
    YEAR("This year"),

    /** Cycle 5: any from/to picked in a DateRangePicker; the dates live in LedgerViewModel. */
    CUSTOM("Custom…"),
    ;

    fun fromDate(): String? {
        val cal = Calendar.getInstance()
        return when (this) {
            ALL, CUSTOM -> null
            MONTH -> isoDate(cal.apply { set(Calendar.DAY_OF_MONTH, 1) })
            THREE_MONTHS -> isoDate(cal.apply { add(Calendar.MONTH, -3) })
            YEAR -> isoDate(cal.apply { set(Calendar.DAY_OF_YEAR, 1) })
        }
    }
}

/** A custom ledger range, as YYYY-MM-DD dates (both inclusive). */
data class CustomRange(val from: String, val to: String) {
    /** "1 Sep – 15 Sep" for the chip. */
    val label: String get() = if (from == to) shortDate(from) else "${shortDate(from)} – ${shortDate(to)}"
}

enum class LedgerTypeFilter(val api: String?) { ALL(null), BILLS("bill"), PAYMENTS("payment") }

class LedgerViewModel(private val kind: PartyKind, private val partyId: Int) : ViewModel() {
    class Factory(private val kind: PartyKind, private val id: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(kind, id) as T
    }

    var data by mutableStateOf<LedgerResponse?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var range by mutableStateOf(LedgerRange.ALL)
        private set
    var type by mutableStateOf(LedgerTypeFilter.ALL)
        private set
    var withPhotos by mutableStateOf(false)
        private set
    var customRange by mutableStateOf<CustomRange?>(null)
        private set
    var isArchiving by mutableStateOf(false)
        private set
    var actionError by mutableStateOf<String?>(null)
        private set

    /** Label of the selected range, e.g. "This month" or "1 Sep – 15 Sep". */
    val rangeLabel: String get() = if (range == LedgerRange.CUSTOM) customRange?.label ?: range.label else range.label

    private fun fromDate(): String? = if (range == LedgerRange.CUSTOM) customRange?.from else range.fromDate()
    private fun toDate(): String? = if (range == LedgerRange.CUSTOM) customRange?.to else null

    fun load() {
        viewModelScope.launch {
            isLoading = data == null
            errorMessage = null
            NetworkModule.safeCall {
                val api = NetworkModule.ledgerApi
                if (kind == PartyKind.CUSTOMER) {
                    api.customerLedger(partyId, fromDate(), toDate(), type.api, withPhotos)
                } else {
                    api.supplierLedger(partyId, fromDate(), toDate(), type.api, withPhotos)
                }
            }
                .onSuccess { data = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun updateRange(value: LedgerRange) { range = value; load() }
    fun updateCustomRange(value: CustomRange) {
        customRange = value
        range = LedgerRange.CUSTOM
        load()
    }
    fun updateType(value: LedgerTypeFilter) { type = value; load() }
    fun toggleWithPhotos() { withPhotos = !withPhotos; load() }

    fun clearActionError() {
        actionError = null
    }

    /** Archive (Company Admin only). The screen has already checked the balance is zero. */
    fun archive(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            isArchiving = true
            actionError = null
            NetworkModule.safeCall {
                if (kind == PartyKind.CUSTOMER) NetworkModule.shopApi.archiveShop(partyId) else NetworkModule.factoryApi.archiveFactory(partyId)
            }
                .onSuccess {
                    DataChanges.bump()
                    onDone(true)
                }
                .onFailure {
                    actionError = it.message
                    onDone(false)
                }
            isArchiving = false
        }
    }

    fun restore() {
        viewModelScope.launch {
            NetworkModule.safeCall {
                if (kind == PartyKind.CUSTOMER) NetworkModule.shopApi.restoreShop(partyId) else NetworkModule.factoryApi.restoreFactory(partyId)
            }
                .onSuccess {
                    DataChanges.bump()
                    Toast.makeText(RetailApp.instance, "Restored", Toast.LENGTH_SHORT).show()
                    load()
                }
                .onFailure { Toast.makeText(RetailApp.instance, "Couldn't restore: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}

/**
 * One customer's or supplier's full history: what they owe, every bill and payment with the
 * running balance after it, and one-tap access to the proof photos for any entry.
 */
@Composable
fun LedgerScreen(
    kind: PartyKind,
    partyId: Int,
    onBack: () -> Unit,
    onOpenBill: (id: Int) -> Unit,
    onOpenPayment: (id: Int) -> Unit,
    onOpenPhotos: (entity: AttachmentEntity, id: Int, title: String) -> Unit,
    onPay: (partyId: Int) -> Unit,
    onNewBill: (partyId: Int) -> Unit,
    onEdit: (partyId: Int) -> Unit,
) {
    val viewModel: LedgerViewModel = viewModel(key = "ledger-${kind.name}-$partyId", factory = LedgerViewModel.Factory(kind, partyId))
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    var archiveStep by remember { mutableStateOf<ArchiveStep?>(null) }

    val data = viewModel.data
    val archived = data?.party?.archived_at != null
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(data?.party?.name ?: kind.singular, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (archived) ArchivedTag()
                        }
                        data?.party?.contact_name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // Without a number these opened an empty dialer / an invalid wa.me link.
                    if (data != null && hasPhone(data.party.phone)) {
                        IconButton(onClick = { dialPhone(context, data.party.phone) }) {
                            Icon(Icons.Default.Call, contentDescription = "Call")
                        }
                        IconButton(onClick = {
                            val pending = data.summary.pending.toDoubleOrNull() ?: 0.0
                            val isReminder = kind == PartyKind.CUSTOMER && pending > 0
                            val message = if (isReminder) Messages.paymentReminder(data.party.name, pending) else Messages.greeting(data.party.name)
                            if (isReminder) RemindedStore.markReminded(partyId)
                            openWhatsApp(context, data.party.phone, message)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp")
                        }
                    }
                    if (data != null) {
                        OverflowMenu { close ->
                            DropdownMenuItem(
                                text = { Text("Edit ${kind.singular.lowercase()}") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { close(); onEdit(partyId) },
                            )
                            // Archive/restore is Company Admin only (Cycle 5 section 3.2 rule 4).
                            if (Session.isCompanyAdmin) {
                                if (archived) {
                                    DropdownMenuItem(
                                        text = { Text("Restore") },
                                        leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                                        onClick = { close(); viewModel.restore() },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Archive", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            close()
                                            viewModel.clearActionError()
                                            val pending = data.summary.pending.toDoubleOrNull() ?: 0.0
                                            // A non-zero balance would hide money still owed (rule 3.3.1).
                                            archiveStep = if (kotlin.math.abs(pending) >= 0.005) ArchiveStep.Blocked(pending) else ArchiveStep.Confirm
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && data == null ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            data != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SummaryCard(kind, data, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onPay(partyId) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  ${kind.payAction}")
                        }
                        OutlinedButton(onClick = { onNewBill(partyId) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  ${kind.newBillAction}")
                        }
                    }
                }
                item { Filters(viewModel) }
                if (viewModel.range != LedgerRange.ALL) {
                    item {
                        Text(
                            "${viewModel.rangeLabel}: ${kind.billLabel.lowercase()}s ${money(data.range_summary.billed)} · " +
                                "${if (kind == PartyKind.CUSTOMER) "collected" else "paid"} ${money(data.range_summary.collected)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (data.entries.isEmpty()) {
                    item { Text("No entries for this filter.", modifier = Modifier.padding(24.dp)) }
                }
                items(data.entries, key = { "${it.type}-${it.id}" }) { entry ->
                    EntryRow(
                        kind = kind,
                        entry = entry,
                        onClick = {
                            when (entry.type) {
                                "sale", "purchase" -> onOpenBill(entry.id)
                                "payment" -> onOpenPayment(entry.id)
                            }
                        },
                        onPhotos = {
                            val (entity, title) = if (entry.type == "payment") {
                                AttachmentEntity.PAYMENT to "${kind.paymentLabel} · ${money(entry.paid)}"
                            } else {
                                kind.billEntity to "${entry.ref ?: kind.billLabel} · ${money(entry.amount)}"
                            }
                            onOpenPhotos(entity, entry.id, title)
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    when (val step = archiveStep) {
        null -> Unit
        is ArchiveStep.Blocked -> InfoDialog(
            title = "Can't archive yet",
            message = Messages.settleBeforeArchive(step.balance),
            onDismiss = { archiveStep = null },
        )
        ArchiveStep.Confirm -> DestructiveConfirmDialog(
            title = "Archive ${data?.party?.name ?: kind.singular}?",
            message = "They'll be hidden from lists, pickers and dues. Their bills and payments stay in your records, " +
                "and you can restore them from ${kind.plural} → Show archived.",
            confirmLabel = "Archive",
            isSubmitting = viewModel.isArchiving,
            errorMessage = viewModel.actionError,
            onDismiss = { archiveStep = null },
            onConfirm = {
                viewModel.archive { ok ->
                    if (ok) {
                        archiveStep = null
                        Toast.makeText(context, "${data?.party?.name ?: kind.singular} archived", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            },
        )
    }
}

private sealed interface ArchiveStep {
    data class Blocked(val balance: Double) : ArchiveStep
    data object Confirm : ArchiveStep
}

@Composable
private fun SummaryCard(kind: PartyKind, data: LedgerResponse, modifier: Modifier = Modifier) {
    val pending = data.summary.pending.toDoubleOrNull() ?: 0.0
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (pending < 0) "Advance" else kind.duesLabel, style = MaterialTheme.typography.labelLarge)
            Text(
                money(kotlin.math.abs(pending)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (pending < 0) successColor() else Color.Unspecified,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            SummaryLine(kind.totalBilledLabel, money(data.summary.total_billed))
            SummaryLine(kind.totalPaidLabel, money(data.summary.total_collected))
            val lastPayment = data.summary.last_payment_at
            SummaryLine(
                "Last payment",
                if (lastPayment == null) "None yet" else "${money(data.summary.last_payment_amount)} on ${displayDate(lastPayment)}",
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Filters(viewModel: LedgerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var pickingRange by remember { mutableStateOf(false) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LedgerRange.entries) { range ->
                if (range == LedgerRange.CUSTOM) {
                    FilterChip(
                        selected = viewModel.range == range,
                        onClick = { pickingRange = true },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(viewModel.customRange?.label ?: range.label) },
                    )
                } else {
                    FilterChip(selected = viewModel.range == range, onClick = { viewModel.updateRange(range) }, label = { Text(range.label) })
                }
            }
        }
        if (pickingRange) {
            DateRangeDialog(
                onDismiss = { pickingRange = false },
                onPicked = {
                    pickingRange = false
                    viewModel.updateCustomRange(it)
                },
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = viewModel.type == LedgerTypeFilter.ALL, onClick = { viewModel.updateType(LedgerTypeFilter.ALL) }, label = { Text("All") }) }
            item { FilterChip(selected = viewModel.type == LedgerTypeFilter.BILLS, onClick = { viewModel.updateType(LedgerTypeFilter.BILLS) }, label = { Text("Bills") }) }
            item { FilterChip(selected = viewModel.type == LedgerTypeFilter.PAYMENTS, onClick = { viewModel.updateType(LedgerTypeFilter.PAYMENTS) }, label = { Text("Payments") }) }
            item {
                FilterChip(
                    selected = viewModel.withPhotos,
                    onClick = viewModel::toggleWithPhotos,
                    leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("With photos") },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(kind: PartyKind, entry: LedgerEntry, onClick: () -> Unit, onPhotos: () -> Unit) {
    val isPayment = entry.type == "payment"
    val isOpening = entry.type == "opening"
    val (icon: ImageVector, tint: Color) = when {
        isPayment -> Icons.Default.AccountBalanceWallet to successColor()
        isOpening -> Icons.Default.Flag to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.AutoMirrored.Filled.ReceiptLong to MaterialTheme.colorScheme.primary
    }
    val strike = if (entry.cancelled) TextDecoration.LineThrough else null

    Card(
        onClick = onClick,
        enabled = !isOpening,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                val title = when {
                    isPayment -> "${kind.paymentLabel}${entry.mode?.let { " · $it" }.orEmpty()}"
                    isOpening -> "Opening balance"
                    else -> "${kind.billLabel} ${entry.ref.orEmpty()}"
                }
                Text(title, style = MaterialTheme.typography.titleSmall, textDecoration = strike)
                val details = listOfNotNull(
                    displayDate(entry.date),
                    entry.invoice_no?.takeIf { it.isNotBlank() }?.let { "Inv $it" },
                    entry.item_count.takeIf { it > 0 }?.let { "$it item${if (it == 1) "" else "s"}" },
                    entry.notes?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.cancelled) {
                        Text("CANCELLED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (entry.photo_count > 0) {
                        AssistChip(
                            onClick = onPhotos,
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("${entry.photo_count} photo${if (entry.photo_count == 1) "" else "s"}") },
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                when {
                    isPayment -> Text("− ${money(entry.paid)}", color = successColor(), fontWeight = FontWeight.SemiBold)
                    else -> {
                        Text(money(entry.amount), fontWeight = FontWeight.SemiBold, textDecoration = strike)
                        val paid = entry.paid?.toDoubleOrNull() ?: 0.0
                        if (paid > 0) {
                            Text("paid ${money(paid)}", style = MaterialTheme.typography.bodySmall, color = successColor(), textDecoration = strike)
                        }
                    }
                }
                Text(
                    "Bal ${money(entry.balance_after)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** M3 DateRangePicker in a dialog; a single tapped day counts as a one-day range. */
@Composable
private fun DateRangeDialog(onDismiss: () -> Unit, onPicked: (CustomRange) -> Unit) {
    val state = rememberDateRangePickerState()
    // The picker works in UTC-midnight millis.
    val utc = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    onPicked(CustomRange(utc.format(Date(start)), utc.format(Date(end))))
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
