package com.retailapp.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Like [DropdownField], but opens a dialog with a search box, for lists that grow long on a
 * real shop (customers, suppliers, products). Typing filters on [optionLabel] and
 * [optionDetail], so a customer can be found by name, phone or area, and a product by name or SKU.
 */
@Composable
fun <T> SearchablePickerField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionDetail: (T) -> String? = { null },
    isError: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Tap to choose") },
            isError = isError,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Same trick as DropdownField: a readOnly field swallows taps, so an overlay catches them.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true },
        )
    }

    if (open) {
        SearchablePickerDialog(
            title = label,
            options = options,
            selected = selected,
            optionLabel = optionLabel,
            optionDetail = optionDetail,
            onSelect = onSelect,
            onDismiss = { open = false },
        )
    }
}

/**
 * The search-and-pick dialog behind [SearchablePickerField], also opened on its own - e.g. by the
 * barcode scanner's "Search" with the unknown code already typed in as [initialQuery].
 */
@Composable
fun <T> SearchablePickerDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    selected: T? = null,
    optionDetail: (T) -> String? = { null },
    initialQuery: String = "",
) {
    run {
        var query by remember { mutableStateOf(initialQuery) }
        val focusRequester = remember { FocusRequester() }
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            options
        } else {
            options.filter { option ->
                optionLabel(option).lowercase().contains(q) || optionDetail(option)?.lowercase()?.contains(q) == true
            }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    if (options.isEmpty()) "Nothing to choose yet." else "No match.",
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(filtered) { option ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(option)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    optionLabel(option),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                optionDetail(option)?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
        // Only pull up the keyboard straight away when there's enough to search through.
        LaunchedEffect(Unit) { if (options.size > 8) runCatching { focusRequester.requestFocus() } }
    }
}
