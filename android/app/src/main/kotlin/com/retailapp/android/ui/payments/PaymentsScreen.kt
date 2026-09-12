package com.retailapp.android.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.Payment
import com.retailapp.android.data.model.PaymentInput
import com.retailapp.android.data.model.Shop
import com.retailapp.android.ui.common.DropdownField
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox

@Composable
fun PaymentsScreen(viewModel: PaymentsViewModel = viewModel()) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Record payment")
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
                    PaymentRow(payment, partyName = viewModel.partyName(payment.PartyType, payment.PartyID))
                }
            }
        }
    }

    if (showAddDialog) {
        AddPaymentDialog(
            shops = viewModel.shops,
            factories = viewModel.factories,
            isSubmitting = viewModel.isSubmitting,
            errorMessage = viewModel.errorMessage,
            onDismiss = { showAddDialog = false },
            onConfirm = { input -> viewModel.addPayment(input) { ok -> if (ok) showAddDialog = false } },
        )
    }
}

@Composable
private fun PaymentRow(payment: Payment, partyName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(partyName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${payment.PartyType} · ${payment.PaymentMode} · ${payment.PaymentDate}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!payment.Notes.isNullOrBlank()) {
                    Text(payment.Notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("₹${payment.Amount}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AddPaymentDialog(
    shops: List<Shop>,
    factories: List<Factory>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (PaymentInput) -> Unit,
) {
    var partyType by remember { mutableStateOf("shop") }
    var selectedShop by remember { mutableStateOf<Shop?>(shops.firstOrNull()) }
    var selectedFactory by remember { mutableStateOf<Factory?>(factories.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val partyId = if (partyType == "shop") selectedShop?.ID else selectedFactory?.ID

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = partyType == "shop", onClick = { partyType = "shop" })
                        Text("Shop")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = partyType == "factory", onClick = { partyType = "factory" })
                        Text("Factory")
                    }
                }
                if (partyType == "shop") {
                    DropdownField(
                        label = "Shop",
                        options = shops,
                        selected = selectedShop,
                        optionLabel = { it.Name },
                        onSelect = { selectedShop = it },
                    )
                } else {
                    DropdownField(
                        label = "Factory",
                        options = factories,
                        selected = selectedFactory,
                        optionLabel = { it.Name },
                        onSelect = { selectedFactory = it },
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = paymentMode,
                    onValueChange = { paymentMode = it },
                    label = { Text("Payment mode (cash, bank, UPI...)") },
                    singleLine = true,
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true)
                InlineError(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && partyId != null && amount.toDoubleOrNull() != null && paymentMode.isNotBlank(),
                onClick = {
                    onConfirm(
                        PaymentInput(
                            party_type = partyType,
                            party_id = partyId!!,
                            amount = amount.toDouble(),
                            payment_mode = paymentMode.trim(),
                            notes = notes.trim(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
