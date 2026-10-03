@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.ledger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.DuesRow
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.RemindedStore
import com.retailapp.android.ui.common.EmptyListMessage
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.Messages
import com.retailapp.android.ui.common.hasPhone
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.relativeTime
import com.retailapp.android.ui.common.successColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RemindersViewModel : ViewModel() {
    var rows by mutableStateOf<List<DuesRow>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Ticked customer ids; everyone with a phone starts ticked. */
    var selected by mutableStateOf<Set<Int>>(emptySet())
        private set

    // The running flow: who is being reminded, in order, and where we are.
    var queue by mutableStateOf<List<DuesRow>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var running by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set

    /** True while WhatsApp is open for queue[index]; the next resume moves on. */
    var awaitingReturn by mutableStateOf(false)
        private set
    var sent by mutableStateOf<Set<Int>>(emptySet())
        private set

    val current: DuesRow? get() = queue.getOrNull(index)
    val finished: Boolean get() = running && index >= queue.size

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            // "oldest" = longest since the last payment, so the most overdue come first.
            NetworkModule.safeCall { NetworkModule.ledgerApi.customerDues("oldest", false) }
                .onSuccess { data ->
                    rows = data.rows.filter { (it.balance.toDoubleOrNull() ?: 0.0) > 0 }
                    selected = rows.filter { hasPhone(it.phone) }.map { it.id }.toSet()
                }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun toggle(id: Int) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll(all: Boolean) {
        selected = if (all) rows.filter { hasPhone(it.phone) }.map { it.id }.toSet() else emptySet()
    }

    fun start() {
        queue = rows.filter { it.id in selected && hasPhone(it.phone) }
        index = 0
        sent = emptySet()
        paused = false
        running = queue.isNotEmpty()
    }

    /** Called just before WhatsApp opens for [current]. */
    fun markOpened() {
        current?.let {
            RemindedStore.markReminded(it.id)
            sent = sent + it.id
        }
        awaitingReturn = true
    }

    /** Back from WhatsApp: on to the next customer. */
    fun onReturned() {
        if (!awaitingReturn) return
        awaitingReturn = false
        index++
    }

    fun skip() {
        awaitingReturn = false
        index++
    }

    fun pause(value: Boolean) {
        paused = value
    }

    fun stop() {
        running = false
        awaitingReturn = false
        queue = emptyList()
        index = 0
    }
}

/**
 * Cycle 5 section 8: work through reminders to every overdue customer without leaving the Dues
 * flow. WhatsApp never lets an app send by itself, so each chat opens with the text filled in
 * and the user taps Send; coming back here moves on to the next customer automatically.
 */
@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = viewModel()) {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReturned() }

    fun openCurrent() {
        val row = viewModel.current ?: return
        viewModel.markOpened()
        openWhatsApp(context, row.phone, Messages.paymentReminder(row.name, row.balance.toDoubleOrNull() ?: 0.0))
    }

    // Auto-advance: a short pause (so the user sees who is next and can stop) then open the chat.
    LaunchedEffect(viewModel.running, viewModel.index, viewModel.paused, viewModel.awaitingReturn) {
        if (viewModel.running && !viewModel.paused && !viewModel.awaitingReturn && viewModel.current != null) {
            if (viewModel.index > 0) delay(1500)
            openCurrent()
        }
    }

    BackHandler(enabled = viewModel.running && !viewModel.finished) { viewModel.stop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send reminders") },
                navigationIcon = {
                    IconButton(onClick = { if (viewModel.running && !viewModel.finished) viewModel.stop() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null -> ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.rows.isEmpty() -> EmptyListMessage("No customer has a pending balance. 🎉", modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if (viewModel.running) {
                        ProgressCard(
                            viewModel = viewModel,
                            onOpenNow = { viewModel.pause(false); if (!viewModel.awaitingReturn) openCurrent() },
                            onDone = onBack,
                        )
                    } else {
                        SetupCard(viewModel)
                    }
                }
                items(viewModel.rows, key = { it.id }) { row ->
                    ReminderRow(
                        row = row,
                        checked = row.id in viewModel.selected,
                        sent = row.id in viewModel.sent,
                        isCurrent = viewModel.running && viewModel.current?.id == row.id,
                        enabled = !viewModel.running,
                        onToggle = { viewModel.toggle(row.id) },
                    )
                }
                item { Column(modifier = Modifier.padding(bottom = 24.dp)) {} }
            }
        }
    }
}

@Composable
private fun SetupCard(viewModel: RemindersViewModel) {
    val withPhone = viewModel.rows.count { hasPhone(it.phone) }
    val count = viewModel.selected.size
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Most overdue first. Untick anyone you don't want to remind today.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$count of $withPhone selected", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.selectAll(count < withPhone) }) { Text(if (count < withPhone) "Select all" else "Select none") }
        }
        Button(onClick = viewModel::start, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (count == 0) "Start" else "Start · $count reminder${if (count == 1) "" else "s"}")
        }
    }
}

@Composable
private fun ProgressCard(viewModel: RemindersViewModel, onOpenNow: () -> Unit, onDone: () -> Unit) {
    val total = viewModel.queue.size
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (viewModel.finished) {
                Text("All done", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${viewModel.sent.size} of $total reminders opened in WhatsApp.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                return@Column
            }
            val row = viewModel.current ?: return@Column
            Text("${viewModel.index + 1} of $total", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { viewModel.index.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text(row.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Pending ${money(row.balance)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    viewModel.awaitingReturn -> "Tap Send in WhatsApp, then come back here."
                    viewModel.paused -> "Paused."
                    else -> "Opening WhatsApp…"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.paused || viewModel.awaitingReturn) {
                    Button(onClick = onOpenNow) { Text(if (viewModel.awaitingReturn) "Open again" else "Resume") }
                } else {
                    OutlinedButton(onClick = { viewModel.pause(true) }) { Text("Pause") }
                }
                OutlinedButton(onClick = viewModel::skip) { Text("Skip") }
                TextButton(onClick = viewModel::stop) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun ReminderRow(row: DuesRow, checked: Boolean, sent: Boolean, isCurrent: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val phone = hasPhone(row.phone)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (phone) 1f else 0.6f)
            .clickable(enabled = enabled && phone, onClick = onToggle),
        colors = if (isCurrent) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sent) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Reminded", tint = successColor(), modifier = Modifier.padding(12.dp).size(24.dp))
            } else {
                Checkbox(checked = checked && phone, onCheckedChange = { onToggle() }, enabled = enabled && phone)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = when {
                    !phone -> "No phone"
                    RemindedStore.remindedToday(row.id) -> "Reminded today"
                    else -> relativeTime(row.last_payment_at).let { if (it.isEmpty()) "No payment yet" else "Last paid $it" }
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!phone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(money(row.balance), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
        }
    }
}
