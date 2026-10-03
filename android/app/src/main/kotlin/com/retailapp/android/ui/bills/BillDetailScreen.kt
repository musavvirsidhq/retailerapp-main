@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.bills

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.BillData
import com.retailapp.android.data.model.BillItem
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.AttachmentsSection
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.DestructiveConfirmDialog
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.money
import com.retailapp.android.ui.common.trimQty
import com.retailapp.android.ui.common.successColor

@Composable
fun BillDetailScreen(id: Int, isSale: Boolean, onBack: () -> Unit, onOpenPhoto: (index: Int, title: String) -> Unit) {
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
                            if (viewModel.isSharing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Share, contentDescription = "Share PDF")
                            }
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
                billId = id,
                isSale = isSale,
                onOpenPhoto = onOpenPhoto,
                isCancelling = viewModel.isCancelling,
                // Share/cancel failures used to be set but never shown, so the tap looked dead.
                errorMessage = viewModel.errorMessage.takeUnless { showCancelDialog },
                modifier = Modifier.padding(padding),
                onCancelClick = { showCancelDialog = true },
            )
        }
    }

    if (showCancelDialog) {
        CancelBillDialog(
            isSubmitting = viewModel.isCancelling,
            errorMessage = viewModel.errorMessage,
            onDismiss = { showCancelDialog = false },
            onConfirm = { reason -> viewModel.cancel(reason) { ok -> if (ok) showCancelDialog = false } },
        )
    }
}

@Composable
private fun BillContent(
    bill: BillData,
    billId: Int,
    isSale: Boolean,
    onOpenPhoto: (index: Int, title: String) -> Unit,
    isCancelling: Boolean,
    errorMessage: String?,
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
                        Text(money(bill.TotalAmount), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid")
                        Text(money(bill.AmountPaid))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // Printing the raw double difference gave things like ₹1234.5600000001.
                        val balance = bill.TotalAmount - bill.AmountPaid
                        Text("Balance due", fontWeight = FontWeight.SemiBold)
                        Text(
                            money(balance),
                            fontWeight = FontWeight.SemiBold,
                            color = if (balance > 0.005) MaterialTheme.colorScheme.error else successColor(),
                        )
                    }
                }
            }
        }

        item {
            // Proof photos of the paper bill / delivery note (Cycle 4). Cancelled bills keep theirs.
            AttachmentsSection(
                entity = if (isSale) AttachmentEntity.SALE else AttachmentEntity.PURCHASE,
                entityId = billId,
                canAdd = if (isSale) Session.canSell else Session.canPurchase,
                onOpenPhoto = { index -> onOpenPhoto(index, "${bill.BillNumber} · ${bill.CounterpartyName}") },
            )
        }

        item { InlineError(errorMessage) }

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
                    "${item.ProductSKU} · ${trimQty(item.Quantity.toString())} ${item.Unit} × ${money(item.UnitPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(money(item.LineTotal), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CancelBillDialog(isSubmitting: Boolean, errorMessage: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    DestructiveConfirmDialog(
        title = "Cancel this bill?",
        message = "The bill stays in the ledger, marked Cancelled. Stock and balances are reversed.",
        confirmLabel = "Confirm cancel",
        reasonLabel = "Reason",
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
