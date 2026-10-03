@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.factories

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
import com.retailapp.android.data.model.FactoryInput
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.ArchiveListTopBar
import com.retailapp.android.ui.common.ArchivedTag
import com.retailapp.android.ui.common.EmptyListMessage
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.ListSearchField
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.Terms

/** Suppliers list; works like the Customers list (Edit and Archive live in the ledger's ⋮ menu). */
@Composable
fun FactoriesScreen(onBack: (() -> Unit)?, onOpenLedger: (Int) -> Unit, viewModel: FactoriesViewModel = viewModel()) {
    var showAddDialog by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfChanged() }

    Scaffold(
        topBar = {
            ArchiveListTopBar(
                title = Terms.SUPPLIERS,
                onBack = onBack,
                showArchived = viewModel.showArchived,
                onToggleArchived = viewModel::toggleShowArchived,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add supplier")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.factories.isEmpty() ->
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
                    val all = viewModel.visibleFactories
                    if (all.isEmpty()) {
                        item { EmptyListMessage("No suppliers yet. Tap + to add one.") }
                        return@LazyColumn
                    }
                    item { ListSearchField(search, onChange = { search = it }, placeholder = "Search name, contact or phone", modifier = Modifier.padding(top = 8.dp)) }
                    val q = search.trim().lowercase()
                    val visible = if (q.isEmpty()) {
                        all
                    } else {
                        all.filter { factory ->
                            listOfNotNull(factory.Name, factory.ContactPerson, factory.PrimaryPhone, factory.Address).any { it.lowercase().contains(q) }
                        }
                    }
                    if (visible.isEmpty()) item { Text("No match.", modifier = Modifier.padding(16.dp)) }
                    items(visible, key = { it.ID }) { factory ->
                        // Tapping a supplier opens their ledger (balance, bills, payments, photos).
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { if (Session.canPurchase) onOpenLedger(factory.ID) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp).alpha(if (factory.isArchived) 0.55f else 1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(factory.Name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                                        if (factory.isArchived) ArchivedTag()
                                    }
                                    Text(
                                        listOfNotNull(factory.ContactPerson, factory.PrimaryPhone, factory.Address).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (factory.isArchived && Session.isCompanyAdmin) {
                                    TextButton(onClick = { viewModel.restore(factory) }) { Text("Restore") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFactoryDialog(
            isSubmitting = viewModel.isSubmitting,
            errorMessage = viewModel.errorMessage,
            onDismiss = { showAddDialog = false },
            onConfirm = { input -> viewModel.addFactory(input) { ok -> if (ok) showAddDialog = false } },
        )
    }
}

@Composable
private fun AddFactoryDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (FactoryInput) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var contactPerson by remember { mutableStateOf("") }
    var primaryPhone by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = contactPerson, onValueChange = { contactPerson = it }, label = { Text("Contact name") }, singleLine = true)
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
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true)
                InlineError(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && name.isNotBlank() && primaryPhone.isNotBlank(),
                onClick = {
                    onConfirm(
                        FactoryInput(
                            name = name.trim(),
                            contact_person = contactPerson.trim(),
                            primary_phone = primaryPhone.trim(),
                            secondary_phone = secondaryPhone.trim(),
                            address = address.trim(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
