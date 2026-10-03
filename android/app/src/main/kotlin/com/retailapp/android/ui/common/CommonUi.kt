package com.retailapp.android.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.retailapp.android.R
import androidx.compose.ui.unit.dp

/**
 * Draws the app icon very faintly, centered, behind [content] on every screen. Screens must
 * leave their own container transparent (see Scaffold in MainScreen) for it to show through.
 */
@Composable
fun WatermarkBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_watermark),
            contentDescription = null,
            alpha = 0.06f,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.8f),
        )
        content()
    }
}

/** Full-size centered spinner for a screen's initial load. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Full-size error state with a retry button, for when the initial load fails. */
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** Inline error line under a form, e.g. after a failed submit. */
@Composable
fun InlineError(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** Search box at the top of a long list (customers, suppliers, products). Filtering is on-device. */
@Composable
fun ListSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
            }
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** "Discard this bill?" check before leaving a form that has unsaved input. */
@Composable
fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Discard changes?") },
        text = { Text("What you've entered on this form will be lost.") },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Keep editing") } },
    )
}

/**
 * M3 has no built-in "success" role, so this is a small hand-picked green, tuned separately
 * for light/dark so it stays legible on either surface. Use [MaterialTheme.colorScheme.error]
 * directly for the "cancelled/failed" half of a status pair - that's already theme-correct.
 */
@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C995) else Color(0xFF1E8E3E)
