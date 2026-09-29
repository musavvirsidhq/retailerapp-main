@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.payments

import android.net.Uri
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.retailapp.android.ui.common.DropdownField
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.PhotoPickerRow
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.common.displayDate
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.successColor

val PAYMENT_MODES = listOf("Cash", "UPI", "Bank", "Cheque")

/**
 * Payments list plus the Collect/Pay form. [startNew] ("shop" or "factory") opens the form
 * straight away - that's how the dashboard quick actions and ledger buttons land here - with
 * [presetPartyId] preselected; [onClose] then returns to wherever the user came from.
 */
@Composable
fun PaymentsScreen(
    onOpenPayment: (Int) -> Unit,
    startNew: String? = null,
    presetPartyId: Int? = null,
    onClose: () -> Unit = {},
    viewModel: PaymentsViewModel = viewModel(),
) {
    val launchedForForm = startNew != null
    var formPartyType by rememberSaveable { mutableStateOf(startNew) }

    val allowedTypes = listOfNotNull("shop".takeIf { Session.canSell }, "factory".takeIf { Session.canPurchase })

    formPartyType?.let { partyType ->
        if (viewModel.isLoading) {
            LoadingBox()
            return
        }
        PaymentFormScreen(
            viewModel = viewModel,
            initialPartyType = partyType,
            allowedTypes = allowedTypes,
            presetPartyId = presetPartyId,
            onBack = { if (launchedForForm) onClose() else formPartyType = null },
            onSaved = { payment, failed ->
                formPartyType = null
                when {
                    failed > 0 -> onOpenPayment(payment.ID)
                    launchedForForm -> onClose()
                }
            },
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            if (allowedTypes.isNotEmpty()) {
                FloatingActionButton(onClick = { formPartyType = allowedTypes.first() }) {
                    Icon(Icons.Default.Add, contentDescription = "Record payment")
                }
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.payments.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.payments.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No payments recorded yet.")
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.payments, key = { it.ID }) { payment ->
                    PaymentRow(
                        payment,
                        partyName = viewModel.partyName(payment.PartyType, payment.PartyID),
                        onClick = { onOpenPayment(payment.ID) },
                    )
                }
            }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCustomer) "Collect money" else "Pay supplier") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !viewModel.isSubmitting) {
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
                    DropdownField(
                        label = Terms.CUSTOMER,
                        options = viewModel.shops,
                        selected = selectedShop,
                        optionLabel = { it.Name },
                        onSelect = { selectedShop = it },
                    )
                } else {
                    DropdownField(
                        label = Terms.SUPPLIER,
                        options = viewModel.factories,
                        selected = selectedFactory,
                        optionLabel = { it.Name },
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
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
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
            item {
                val amountValue = amount.toDoubleOrNull()
                Button(
                    enabled = !viewModel.isSubmitting && partyId != null && amountValue != null && amountValue > 0,
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
                        Text("Save")
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
