package com.retailapp.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.retailapp.android.data.model.QuickItem

private const val LOW_STOCK = 10.0 // same threshold as the backend's LowStockProducts query

/**
 * One-tap chips for the most-used (and pinned) items at the top of the sale/purchase forms.
 * A pinned item shows a star; an item running low shows an amber dot next to its stock.
 */
@Composable
fun QuickItemsStrip(title: String, items: List<QuickItem>, onPick: (QuickItem) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                val stock = item.current_stock.toDoubleOrNull() ?: 0.0
                AssistChip(
                    onClick = { onPick(item) },
                    leadingIcon = if (item.pinned) {
                        { Icon(Icons.Default.Star, contentDescription = "Pinned", modifier = Modifier.size(16.dp), tint = Color(0xFFF59E0B)) }
                    } else {
                        null
                    },
                    label = {
                        Column {
                            Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (stock < LOW_STOCK) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                }
                                Text(
                                    "${trimQty(item.current_stock)} ${item.unit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** "12" instead of "12.000" for stock quantities. */
fun trimQty(value: String): String {
    val d = value.toDoubleOrNull() ?: return value
    return if (d == Math.floor(d)) d.toLong().toString() else d.toString()
}
