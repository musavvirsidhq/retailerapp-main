@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.shops

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.ShopInput
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.ArchiveListTopBar
import com.retailapp.android.ui.common.ArchivedTag
import com.retailapp.android.ui.common.EmptyListMessage
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.ListSearchField
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.Terms

/**
 * Customers list. Tapping one opens its ledger, where Edit and Archive live in the ⋮ menu
 * (Cycle 5 removed the row trash icon - it was too easy to hit by mistake).
 */
@Composable
fun ShopsScreen(onBack: (() -> Unit)?, onOpenLedger: (Int) -> Unit, viewModel: ShopsViewModel = viewModel()) {
    var showAddDialog by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfChanged() }

    Scaffold(
        topBar = {
            ArchiveListTopBar(
                title = Terms.CUSTOMERS,
                onBack = onBack,
                showArchived = viewModel.showArchived,
                onToggleArchived = viewModel::toggleShowArchived,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add customer")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.shops.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = { viewModel.load(pull = true) },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val all = viewModel.visibleShops
                    if (all.isEmpty()) {
                        item { EmptyListMessage("No customers yet. Tap + to add one.") }
                        return@LazyColumn
                    }
                    item { ListSearchField(search, onChange = { search = it }, placeholder = "Search name, phone or area", modifier = Modifier.padding(top = 8.dp)) }
                    val q = search.trim().lowercase()
                    val visible = if (q.isEmpty()) {
                        all
                    } else {
                        all.filter { shop ->
                            listOfNotNull(shop.Name, shop.OwnerName, shop.PrimaryPhone, shop.Area).any { it.lowercase().contains(q) }
                        }
                    }
                    if (visible.isEmpty()) item { Text("No match.", modifier = Modifier.padding(16.dp)) }
                    items(visible, key = { it.ID }) { shop ->
                        // Tapping a customer opens their ledger (balance, bills, payments, photos).
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { if (Session.canSell) onOpenLedger(shop.ID) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp).alpha(if (shop.isArchived) 0.55f else 1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(shop.Name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                                        if (shop.isArchived) ArchivedTag()
                                    }
                                    Text(
                                        listOfNotNull(shop.OwnerName, shop.PrimaryPhone, shop.Area).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (shop.isArchived && Session.isCompanyAdmin) {
                                    TextButton(onClick = { viewModel.restore(shop) }) { Text("Restore") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddShopDialog(
            isSubmitting = viewModel.isSubmitting,
            errorMessage = viewModel.errorMessage,
            onDismiss = { showAddDialog = false },
            onConfirm = { input -> viewModel.addShop(input) { ok -> if (ok) showAddDialog = false } },
        )
    }
}

@Composable
private fun AddShopDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (ShopInput) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var primaryPhone by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var openingBalance by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text("Contact name") }, singleLine = true)
                OutlinedTextField(
                    value = primaryPhone,
                    onValueChange = { primaryPhone = it },
                    label = { Text("Primary phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = secondaryPhone,
                    onValueChange = { secondaryPhone = it },
                    label = { Text("Secondary phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Area") }, singleLine = true)
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text("Opening balance") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                InlineError(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && name.isNotBlank() && primaryPhone.isNotBlank(),
                onClick = {
                    onConfirm(
                        ShopInput(
                            name = name.trim(),
                            owner_name = ownerName.trim(),
                            primary_phone = primaryPhone.trim(),
                            secondary_phone = secondaryPhone.trim(),
                            area = area.trim(),
                            opening_balance = openingBalance.toDoubleOrNull() ?: 0.0,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
