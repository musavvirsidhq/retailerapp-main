package com.retailapp.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

/**
 * M3 has no built-in "success" role, so this is a small hand-picked green, tuned separately
 * for light/dark so it stays legible on either surface. Use [MaterialTheme.colorScheme.error]
 * directly for the "cancelled/failed" half of a status pair - that's already theme-correct.
 */
@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C995) else Color(0xFF1E8E3E)
