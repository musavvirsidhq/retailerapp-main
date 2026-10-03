@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.payments

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.Payment
import com.retailapp.android.data.model.PaymentInput
import com.retailapp.android.data.model.Shop
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.DiscardDialog
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.Messages
import com.retailapp.android.ui.common.PagedListContent
import com.retailapp.android.ui.common.PhotoPickerRow
import com.retailapp.android.ui.common.SavedSheet
import com.retailapp.android.ui.common.countLabel
import com.retailapp.android.ui.common.SearchablePickerField
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.common.displayDate
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.successColor

val PAYMENT_MODES = listOf("Cash", "UPI", "Bank", "Cheque")

/**
 * The payments list, or - when [startNew] is "shop" / "factory" - the Collect / Pay form, each
 * as its own navigation entry so the bottom bar can hide on the form. [presetPartyId]
 * preselects the customer or supplier. After a collection the receipt sheet offers WhatsApp.
 */
@Composable
fun PaymentsScreen(
    onOpenPayment: (Int) -> Unit,
    onNewPayment: (partyType: String) -> Unit = {},
    startNew: String? = null,
    presetPartyId: Int? = null,
    onClose: () -> Unit = {},
    onSavedOpenPayment: (Int) -> Unit = onOpenPayment,
    viewModel: PaymentsViewModel = viewModel(),
) {
    val allowedTypes = listOfNotNull("shop".takeIf { Session.canSell }, "factory".takeIf { Session.canPurchase })

    if (startNew != null && allowedTypes.isNotEmpty()) {
        LaunchedEffect(Unit) { viewModel.startForm() }
        if (viewModel.isFormLoading) {
            LoadingBox()
            return
        }
        PaymentFormScreen(
            viewModel = viewModel,
            initialPartyType = startNew,
            allowedTypes = allowedTypes,
            presetPartyId = presetPartyId,
            onBack = onClose,
            onSaved = { payment, failed -> if (failed > 0) onSavedOpenPayment(payment.ID) else onClose() },
        )
        viewModel.saved?.let { share -> SavedSheet(share, onDone = viewModel::finishSaved) }
        return
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.startList() }
    val list = viewModel.list
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        floatingActionButton = {
            if (allowedTypes.isNotEmpty()) {
                FloatingActionButton(onClick = { onNewPayment(viewModel.partyFilter ?: allowedTypes.first()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Record payment")
                }
            }
        },
    ) { padding ->
        // A total only means something for one direction; mixing collections and payouts doesn't.
        val oneDirection = viewModel.partyFilter != null || allowedTypes.size == 1
        PagedListContent(
            list = list,
            padding = padding,
            summary = countLabel(list.totalCount, "payment") + if (oneDirection) " · ${money(list.totalAmount)}" else "",
            emptyText = if (allowedTypes.isNotEmpty()) {
                "No payments ${list.filter.phrase}. Tap + to record one."
            } else {
                "No payments ${list.filter.phrase}."
            },
            key = { it.ID },
            extraFilters = if (allowedTypes.size > 1) {
                {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = viewModel.partyFilter == null, onClick = { viewModel.updatePartyFilter(null) }, label = { Text("All") }) }
                        item { FilterChip(selected = viewModel.partyFilter == "shop", onClick = { viewModel.updatePartyFilter("shop") }, label = { Text("Collected") }) }
                        item { FilterChip(selected = viewModel.partyFilter == "factory", onClick = { viewModel.updatePartyFilter("factory") }, label = { Text("Paid") }) }
                    }
                }
            } else {
                null
            },
        ) { payment ->
            PaymentRow(
                payment,
                partyName = viewModel.partyName(payment.PartyType, payment.PartyID),
                onClick = { onOpenPayment(payment.ID) },
            )
        }
    }
}

@Composable
private fun PaymentRow(payment: Payment, partyName: String, onClick: () -> Unit) {
    val collected = payment.PartyType == "shop"
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(partyName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Terms.partyLabel(payment.PartyType)} · ${payment.PaymentMode} · ${displayDate(payment.PaymentDate)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!payment.Notes.isNullOrBlank()) {
                    Text(payment.Notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(payment.Amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (collected) "Collected" else "Paid",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (collected) successColor() else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PaymentFormScreen(
    viewModel: PaymentsViewModel,
    initialPartyType: String,
    allowedTypes: List<String>,
    presetPartyId: Int?,
    onBack: () -> Unit,
    onSaved: (Payment, Int) -> Unit,
) {
    var partyType by rememberSaveable { mutableStateOf(initialPartyType.takeIf { it in allowedTypes } ?: allowedTypes.firstOrNull() ?: "shop") }
    var selectedShop by remember {
        mutableStateOf<Shop?>(viewModel.shops.find { it.ID == presetPartyId && initialPartyType == "shop" })
    }
    var selectedFactory by remember {
        mutableStateOf<Factory?>(viewModel.factories.find { it.ID == presetPartyId && initialPartyType == "factory" })
    }
    var amount by rememberSaveable { mutableStateOf("") }
    var paymentMode by rememberSaveable { mutableStateOf(PAYMENT_MODES.first()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val isCustomer = partyType == "shop"
    val partyId = if (isCustomer) selectedShop?.ID else selectedFactory?.ID
    LaunchedEffect(partyType, partyId) { viewModel.loadBalance(partyType, partyId) }

    val pending = viewModel.selectedBalance?.toDoubleOrNull()
    val amountValue = amount.toDoubleOrNull()
    val isDirty = amount.isNotBlank() || notes.isNotBlank() || photos.isNotEmpty()
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (isDirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = !viewModel.isSubmitting && viewModel.saved == null) { requestBack() }
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onBack() }, onKeep = { confirmDiscard = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCustomer) "Collect money" else "Pay supplier") },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !viewModel.isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (allowedTypes.size > 1) {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        allowedTypes.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = partyType == type,
                                onClick = { partyType = type },
                                shape = SegmentedButtonDefaults.itemShape(index, allowedTypes.size),
                            ) { Text(if (type == "shop") "From ${Terms.CUSTOMER.lowercase()}" else "To ${Terms.SUPPLIER.lowercase()}") }
                        }
                    }
                }
            }
            item {
                if (isCustomer) {
                    SearchablePickerField(
                        label = Terms.CUSTOMER,
                        options = viewModel.shops,
                        selected = selectedShop,
                        optionLabel = { it.Name },
                        optionDetail = { listOfNotNull(it.PrimaryPhone, it.Area?.takeIf(String::isNotBlank)).joinToString(" · ") },
                        onSelect = { selectedShop = it },
                    )
                } else {
                    SearchablePickerField(
                        label = Terms.SUPPLIER,
                        options = viewModel.factories,
                        selected = selectedFactory,
                        optionLabel = { it.Name },
                        optionDetail = { listOfNotNull(it.ContactPerson?.takeIf(String::isNotBlank), it.PrimaryPhone).joinToString(" · ") },
                        onSelect = { selectedFactory = it },
                    )
                }
            }
            viewModel.selectedBalance?.let { balance ->
                item {
                    val value = balance.toDoubleOrNull() ?: 0.0
                    Text(
                        when {
                            value > 0 && isCustomer -> "Pending from this customer: ${money(value)}"
                            value > 0 -> "You owe this supplier: ${money(value)}"
                            value < 0 -> "Advance: ${money(-value)}"
                            else -> "No pending balance"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                // Most collections clear the whole due, so one tap fills it in; going over the
                // pending amount is allowed (it becomes an advance) but is called out.
                val overPending = pending != null && pending > 0 && amountValue != null && amountValue > pending + 0.005
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    trailingIcon = {
                        if (pending != null && pending > 0) {
                            TextButton(onClick = { amount = String.format(java.util.Locale.US, "%.2f", pending) }) { Text("Full") }
                        }
                    },
                    supportingText = if (overPending) {
                        { Text("${money(amountValue - pending)} more than pending - it will be kept as an advance") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mode", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PAYMENT_MODES) { mode ->
                            FilterChip(selected = paymentMode == mode, onClick = { paymentMode = mode }, label = { Text(mode) })
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (UPI ref, cheque no…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { PhotoPickerRow(photos = photos, onPhotosChange = { photos = it }) }
            item { InlineError(viewModel.errorMessage) }
            // Cycle 5 "Require photo for payments": enforced here, since photos upload only
            // after the payment is created and so the backend can't check it.
            val photoMissing = viewModel.requirePhoto && photos.isEmpty()
            if (photoMissing) {
                item {
                    Text(Messages.PHOTO_REQUIRED, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                Button(
                    enabled = !viewModel.isSubmitting && viewModel.saved == null && !photoMissing &&
                        partyId != null && amountValue != null && amountValue > 0,
                    onClick = {
                        viewModel.addPayment(
                            PaymentInput(
                                party_type = partyType,
                                party_id = partyId!!,
                                amount = amountValue!!,
                                payment_mode = paymentMode,
                                notes = notes.trim(),
                            ),
                            photos,
                            onDone = onSaved,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  ${viewModel.progressMessage ?: "Saving…"}")
                    } else {
                        Text(
                            when {
                                amountValue == null || amountValue <= 0 -> "Save"
                                isCustomer -> "Save · collected ${money(amountValue)}"
                                else -> "Save · paid ${money(amountValue)}"
                            },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
