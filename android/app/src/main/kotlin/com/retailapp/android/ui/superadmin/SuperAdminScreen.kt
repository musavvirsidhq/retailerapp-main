@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.superadmin

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.Company
import com.retailapp.android.data.model.CreateCompanyInput
import com.retailapp.android.data.model.Subscription
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.successColor

@Composable
fun SuperAdminScreen(viewModel: SuperAdminViewModel = viewModel()) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCompany by remember { mutableStateOf<Company?>(null) }

    selectedCompany?.let { company ->
        CompanyDetailScreen(
            company = company,
            viewModel = viewModel,
            onBack = { selectedCompany = null },
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add company")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.companies.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            viewModel.companies.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No companies yet. Tap + to add one.")
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.companies, key = { it.ID }) { company ->
                    CompanyRow(company, onClick = { selectedCompany = company })
                }
            }
        }
    }

    if (showAddDialog) {
        AddCompanyDialog(
            isSubmitting = viewModel.isSubmitting,
            errorMessage = viewModel.errorMessage,
            onDismiss = { showAddDialog = false },
            onConfirm = { input -> viewModel.createCompany(input) { ok -> if (ok) showAddDialog = false } },
        )
    }
}

@Composable
private fun CompanyRow(company: Company, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(company.CompanyName, style = MaterialTheme.typography.titleMedium)
                Text(company.CompanyCode, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    company.Status,
                    color = if (company.Status == "ACTIVE") successColor() else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                company.subscription_status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun CompanyDetailScreen(company: Company, viewModel: SuperAdminViewModel, onBack: () -> Unit) {
    var showGrantDialog by remember { mutableStateOf(false) }
    var showExtendDialog by remember { mutableStateOf(false) }

    LaunchedEffect(company.ID) { viewModel.loadHistory(company.ID) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(company.CompanyName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Code: ${company.CompanyCode}")
                        Text("Status: ${company.Status}")
                        Text("Joined: ${company.JoiningDate}")
                        company.subscription_type?.let { Text("Plan: $it") }
                        company.expiry_date?.let { Text("Expires: $it") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.grantTrial(company.ID) {} },
                        enabled = !viewModel.isSubmitting,
                    ) { Text("Grant trial") }
                    OutlinedButton(onClick = { showGrantDialog = true }) { Text("Grant plan") }
                    OutlinedButton(onClick = { showExtendDialog = true }) { Text("Extend") }
                }
            }
            item { InlineError(viewModel.errorMessage) }
            item { Text("Subscription history", style = MaterialTheme.typography.titleMedium) }
            if (viewModel.isHistoryLoading) {
                item { Text("Loading...") }
            } else if (viewModel.subscriptionHistory.isEmpty()) {
                item { Text("No subscription history yet.") }
            } else {
                items(viewModel.subscriptionHistory) { sub -> SubscriptionRow(sub) }
            }
        }
    }

    if (showGrantDialog) {
        GrantSubscriptionDialog(
            isSubmitting = viewModel.isSubmitting,
            onDismiss = { showGrantDialog = false },
            onConfirm = { type, amount, months ->
                viewModel.grantSubscription(company.ID, type, amount, months) { ok -> if (ok) showGrantDialog = false }
            },
        )
    }

    if (showExtendDialog) {
        ExtendSubscriptionDialog(
            isSubmitting = viewModel.isSubmitting,
            onDismiss = { showExtendDialog = false },
            onConfirm = { months, days, amount ->
                viewModel.extendSubscription(company.ID, months, days, amount) { ok -> if (ok) showExtendDialog = false }
            },
        )
    }
}

@Composable
private fun SubscriptionRow(sub: Subscription) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(sub.SubscriptionType, style = MaterialTheme.typography.bodyMedium)
                Text("${sub.StartDate} → ${sub.ExpiryDate}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${sub.Amount}")
                Text(sub.Status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AddCompanyDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (CreateCompanyInput) -> Unit,
) {
    var companyName by remember { mutableStateOf("") }
    var companyCode by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var adminUsername by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New company") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text("Company name") }, singleLine = true)
                OutlinedTextField(value = companyCode, onValueChange = { companyCode = it }, label = { Text("Company code") }, singleLine = true)
                OutlinedTextField(value = adminName, onValueChange = { adminName = it }, label = { Text("Admin name") }, singleLine = true)
                OutlinedTextField(value = adminUsername, onValueChange = { adminUsername = it }, label = { Text("Admin username") }, singleLine = true)
                OutlinedTextField(value = adminPassword, onValueChange = { adminPassword = it }, label = { Text("Admin password") }, singleLine = true)
                InlineError(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && companyName.isNotBlank() && companyCode.isNotBlank() &&
                    adminUsername.isNotBlank() && adminPassword.isNotBlank(),
                onClick = {
                    onConfirm(
                        CreateCompanyInput(
                            company_name = companyName.trim(),
                            company_code = companyCode.trim(),
                            admin_name = adminName.trim(),
                            admin_username = adminUsername.trim(),
                            admin_password = adminPassword,
                        ),
                    )
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GrantSubscriptionDialog(isSubmitting: Boolean, onDismiss: () -> Unit, onConfirm: (String, Double, Int) -> Unit) {
    var type by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grant subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Plan type") }, singleLine = true)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it },
                    label = { Text("Months") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && type.isNotBlank() && amount.toDoubleOrNull() != null && months.toIntOrNull() != null,
                onClick = { onConfirm(type.trim(), amount.toDouble(), months.toInt()) },
            ) { Text("Grant") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExtendSubscriptionDialog(isSubmitting: Boolean, onDismiss: () -> Unit, onConfirm: (Int, Int, Double) -> Unit) {
    var months by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extend subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it },
                    label = { Text("Months") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && (months.toIntOrNull() ?: 0) + (days.toIntOrNull() ?: 0) > 0 && amount.toDoubleOrNull() != null,
                onClick = { onConfirm(months.toIntOrNull() ?: 0, days.toIntOrNull() ?: 0, amount.toDouble()) },
            ) { Text("Extend") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
