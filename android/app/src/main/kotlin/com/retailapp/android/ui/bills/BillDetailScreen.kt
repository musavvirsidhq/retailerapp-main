@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.BillData
import com.retailapp.android.data.model.BillItem
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.successColor

@Composable
fun BillDetailScreen(id: Int, isSale: Boolean, onBack: () -> Unit) {
    val viewModel: BillDetailViewModel = viewModel(factory = BillDetailViewModel.Factory(id, isSale))
    var showCancelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.bill?.BillNumber ?: "Bill") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (viewModel.bill != null) {
                        IconButton(onClick = viewModel::sharePdf, enabled = !viewModel.isSharing) {
                            Icon(Icons.Default.Share, contentDescription = "Share PDF")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.bill == null ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.bill != null -> BillContent(
                bill = viewModel.bill!!,
                isCancelling = viewModel.isCancelling,
                modifier = Modifier.padding(padding),
                onCancelClick = { showCancelDialog = true },
            )
        }
    }

    if (showCancelDialog) {
        CancelBillDialog(
            isSubmitting = viewModel.isCancelling,
            onDismiss = { showCancelDialog = false },
            onConfirm = { reason -> viewModel.cancel(reason) { ok -> if (ok) showCancelDialog = false } },
        )
    }
}

@Composable
private fun BillContent(
    bill: BillData,
    isCancelling: Boolean,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(bill.DocumentTitle, style = MaterialTheme.typography.titleLarge)
                    Text("${bill.CompanyName} (${bill.CompanyCode})", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(bill.CounterpartyName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(bill.CounterpartyPhone, bill.CounterpartyArea).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(bill.BillDate, style = MaterialTheme.typography.bodySmall)
                    Text(
                        bill.Status,
                        color = if (bill.Status == "CANCELLED") MaterialTheme.colorScheme.error else successColor(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (bill.Status == "CANCELLED" && !bill.CancelledReason.isNullOrBlank()) {
                        Text("Reason: ${bill.CancelledReason}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { Text("Items", style = MaterialTheme.typography.titleMedium) }
        items(bill.Items) { item -> BillItemRow(item) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total")
                        Text("₹${bill.TotalAmount}", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid")
                        Text("₹${bill.AmountPaid}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Balance")
                        Text("₹${bill.TotalAmount - bill.AmountPaid}")
                    }
                }
            }
        }

        if (bill.Status == "COMPLETED") {
            item {
                OutlinedButton(
                    onClick = onCancelClick,
                    enabled = !isCancelling,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel bill") }
            }
        }
    }
}

@Composable
private fun BillItemRow(item: BillItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(item.ProductName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${item.ProductSKU} · ${item.Quantity} ${item.Unit} × ₹${item.UnitPrice}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("₹${item.LineTotal}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CancelBillDialog(isSubmitting: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this bill?") },
        text = {
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, singleLine = true)
        },
        confirmButton = {
            Button(enabled = !isSubmitting && reason.isNotBlank(), onClick = { onConfirm(reason) }) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Confirm cancel")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}
