package com.retailapp.android.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.retailapp.android.data.model.User

/** The More tab: everything from the old drawer that isn't a tab, plus who is logged in and Logout. */
@Composable
fun MoreScreen(user: User, onOpen: (Screen) -> Unit, onLogout: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                Text(roleLabel(user.user_type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                val screens = moreScreens()
                screens.forEachIndexed { index, screen ->
                    ListItem(
                        headlineContent = { Text(screen.label) },
                        leadingContent = { Icon(screen.icon, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpen(screen) },
                    )
                    if (index < screens.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onLogout),
                )
            }
        }
    }
}


fun roleLabel(userType: String) = when (userType) {
    "SUPER_ADMIN" -> "Super Admin"
    "COMPANY_ADMIN" -> "Company Admin"
    else -> "Staff"
}
