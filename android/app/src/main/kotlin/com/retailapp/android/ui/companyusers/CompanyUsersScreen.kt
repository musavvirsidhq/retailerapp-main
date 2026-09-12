package com.retailapp.android.ui.companyusers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.data.model.CompanyUser
import com.retailapp.android.data.model.CreateStaffInput
import com.retailapp.android.data.model.SubscriptionStatus
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.InlineError
import com.retailapp.android.ui.common.LoadingBox
import com.retailapp.android.ui.common.successColor

@Composable
fun CompanyUsersScreen(viewModel: CompanyUsersViewModel = viewModel()) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<CompanyUser?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add staff")
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> LoadingBox(modifier = Modifier.padding(padding))
            viewModel.errorMessage != null && viewModel.users.isEmpty() ->
                ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                viewModel.subscriptionStatus?.let { SubscriptionBanner(it) }
                if (viewModel.users.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No staff yet. Tap + to add one.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(viewModel.users, key = { it.id }) { user ->
                            StaffRow(user, onClick = { editingUser = user })
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddStaffDialog(
            isSubmitting = viewModel.isSubmitting,
            errorMessage = viewModel.errorMessage,
            onDismiss = { showAddDialog = false },
            onConfirm = { input -> viewModel.createStaff(input) { ok -> if (ok) showAddDialog = false } },
        )
    }

    editingUser?.let { user ->
        EditPermissionsDialog(
            user = user,
            isSubmitting = viewModel.isSubmitting,
            errorMessage = viewModel.errorMessage,
            onDismiss = { editingUser = null },
            onSave = { p, s, b -> viewModel.updatePermissions(user.id, p, s, b) { ok -> if (ok) editingUser = null } },
            onDisable = { viewModel.disableUser(user.id); editingUser = null },
        )
    }
}

@Composable
private fun SubscriptionBanner(status: SubscriptionStatus) {
    val color = when (status.warning_level) {
        "URGENT", "EXPIRED" -> MaterialTheme.colorScheme.error
        "SOON" -> MaterialTheme.colorScheme.tertiary
        else -> successColor()
    }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Subscription: ${status.status}", color = color, style = MaterialTheme.typography.titleSmall)
            if (status.expiry_date != null) {
                Text(
                    "Expires ${status.expiry_date}" + (status.days_remaining?.let { " ($it days left)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StaffRow(user: CompanyUser, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                Text("@${user.username} · ${user.user_type}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                user.status,
                color = if (user.status == "DISABLED") MaterialTheme.colorScheme.error else successColor(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AddStaffDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (CreateStaffInput) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var purchaseAccess by remember { mutableStateOf(false) }
    var salesAccess by remember { mutableStateOf(false) }
    var salesBelowCostApprove by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New staff member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true)
                PermissionCheckboxes(
                    purchaseAccess = purchaseAccess,
                    salesAccess = salesAccess,
                    salesBelowCostApprove = salesBelowCostApprove,
                    onPurchaseAccessChange = { purchaseAccess = it },
                    onSalesAccessChange = { salesAccess = it },
                    onSalesBelowCostApproveChange = { salesBelowCostApprove = it },
                )
                InlineError(errorMessage)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && name.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    onConfirm(
                        CreateStaffInput(
                            name = name.trim(),
                            username = username.trim(),
                            password = password,
                            purchase_access = purchaseAccess,
                            sales_access = salesAccess,
                            sales_below_cost_approve = salesBelowCostApprove,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditPermissionsDialog(
    user: CompanyUser,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (Boolean, Boolean, Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    var purchaseAccess by remember { mutableStateOf(user.purchase_access) }
    var salesAccess by remember { mutableStateOf(user.sales_access) }
    var salesBelowCostApprove by remember { mutableStateOf(user.sales_below_cost_approve) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.name) },
        text = {
            Column {
                PermissionCheckboxes(
                    purchaseAccess = purchaseAccess,
                    salesAccess = salesAccess,
                    salesBelowCostApprove = salesBelowCostApprove,
                    onPurchaseAccessChange = { purchaseAccess = it },
                    onSalesAccessChange = { salesAccess = it },
                    onSalesBelowCostApproveChange = { salesBelowCostApprove = it },
                )
                InlineError(errorMessage)
                if (user.status != "DISABLED") {
                    TextButton(onClick = onDisable) { Text("Disable this user", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = { onSave(purchaseAccess, salesAccess, salesBelowCostApprove) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PermissionCheckboxes(
    purchaseAccess: Boolean,
    salesAccess: Boolean,
    salesBelowCostApprove: Boolean,
    onPurchaseAccessChange: (Boolean) -> Unit,
    onSalesAccessChange: (Boolean) -> Unit,
    onSalesBelowCostApproveChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = purchaseAccess, onCheckedChange = onPurchaseAccessChange)
        Text("Purchase access")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = salesAccess, onCheckedChange = onSalesAccessChange)
        Text("Sales access")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = salesBelowCostApprove, onCheckedChange = onSalesBelowCostApproveChange)
        Text("Can sell below cost")
    }
}
