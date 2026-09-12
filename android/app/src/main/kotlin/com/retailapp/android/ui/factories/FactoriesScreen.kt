package com.retailapp.android.ui.factories

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.FactoryInput
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox

@Composable
fun FactoriesScreen(viewModel: FactoriesViewModel = viewModel()) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add factory")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.factories.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.factories.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No factories/suppliers yet. Tap + to add one.")
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.factories, key = { it.ID }) { factory ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(factory.Name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(factory.ContactPerson, factory.PrimaryPhone, factory.Address).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { pendingDeleteId = factory.ID }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete factory/supplier?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteFactory(id); pendingDeleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
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
        title = { Text("New factory / supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = contactPerson, onValueChange = { contactPerson = it }, label = { Text("Contact person") }, singleLine = true)
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
