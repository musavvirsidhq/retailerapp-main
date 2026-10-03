package com.retailapp.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one confirm dialog for destructive actions - cancel a bill, archive a record, delete a
 * photo (Cycle 5 section 11.2): red confirm button, and a required reason field when
 * [reasonLabel] is given. [warning] is an extra line in the error colour (e.g. stock on hand).
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit,
    reasonLabel: String? = null,
    warning: String? = null,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    dismissLabel: String = "Back",
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (warning != null) {
                    Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (reasonLabel != null) {
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text(reasonLabel) }, singleLine = true)
                }
                InlineError(errorMessage)
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && (reasonLabel == null || reason.isNotBlank()),
                onClick = { onConfirm(reason.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(dismissLabel) } },
    )
}

/** A plain "you can't do that yet" box, e.g. archiving a customer who still owes money. */
@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
