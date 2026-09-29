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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.displayDate
import com.retailapp.android.ui.common.isoDate
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.successColor
import kotlinx.coroutines.launch
import java.util.Calendar

enum class LedgerRange(val label: String) {
    ALL("All time"),
    MONTH("This month"),
    THREE_MONTHS("Last 3 months"),
    YEAR("This year"),
    ;

    fun fromDate(): String? {
        val cal = Calendar.getInstance()
        return when (this) {
            ALL -> null
            MONTH -> isoDate(cal.apply { set(Calendar.DAY_OF_MONTH, 1) })
            THREE_MONTHS -> isoDate(cal.apply { add(Calendar.MONTH, -3) })
            YEAR -> isoDate(cal.apply { set(Calendar.DAY_OF_YEAR, 1) })
        }
    }
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

    fun load() {
        viewModelScope.launch {
            isLoading = data == null
            errorMessage = null
            NetworkModule.safeCall {
                val api = NetworkModule.ledgerApi
                if (kind == PartyKind.CUSTOMER) {
                    api.customerLedger(partyId, range.fromDate(), type.api, withPhotos)
                } else {
                    api.supplierLedger(partyId, range.fromDate(), type.api, withPhotos)
                }
            }
                .onSuccess { data = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun updateRange(value: LedgerRange) { range = value; load() }
    fun updateType(value: LedgerTypeFilter) { type = value; load() }
    fun toggleWithPhotos() { withPhotos = !withPhotos; load() }
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
) {
    val viewModel: LedgerViewModel = viewModel(key = "ledger-${kind.name}-$partyId", factory = LedgerViewModel.Factory(kind, partyId))
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    val data = viewModel.data
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(data?.party?.name ?: kind.singular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        data?.party?.contact_name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (data != null) {
                        IconButton(onClick = { dialPhone(context, data.party.phone) }) {
                            Icon(Icons.Default.Call, contentDescription = "Call")
                        }
                        IconButton(onClick = {
                            val pending = data.summary.pending.toDoubleOrNull() ?: 0.0
                            val message = if (kind == PartyKind.CUSTOMER && pending > 0) {
                                "Hello ${data.party.name}, your pending balance with us is ${money(pending)}. Kindly arrange the payment. Thank you."
                            } else {
                                "Hello ${data.party.name}, "
                            }
                            openWhatsApp(context, data.party.phone, message)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp")
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
                            "${viewModel.range.label}: ${kind.billLabel.lowercase()}s ${money(data.range_summary.billed)} · " +
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LedgerRange.entries) { range ->
                FilterChip(selected = viewModel.range == range, onClick = { viewModel.updateRange(range) }, label = { Text(range.label) })
            }
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
