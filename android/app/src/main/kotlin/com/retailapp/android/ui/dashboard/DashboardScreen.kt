package com.retailapp.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.data.model.DashboardData
import com.retailapp.android.data.model.DueItem
import com.retailapp.android.data.model.LowStockItem
import com.retailapp.android.ui.common.ErrorBox
import com.retailapp.android.ui.common.LoadingBox
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Where the dashboard's quick actions and drill-downs lead; wired up in MainScreen. */
class DashboardActions(
    val newSale: () -> Unit,
    val newPurchase: () -> Unit,
    val collectMoney: () -> Unit,
    val paySupplier: () -> Unit,
    val customerDues: () -> Unit,
    val supplierDues: () -> Unit,
    val customerLedger: (Int) -> Unit,
    val supplierLedger: (Int) -> Unit,
)

@Composable
fun DashboardScreen(actions: DashboardActions, viewModel: DashboardViewModel = viewModel()) {
    // Refresh on every return, so totals reflect a sale or payment made from a quick action.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    when {
        viewModel.isLoading -> LoadingBox()
        viewModel.errorMessage != null && viewModel.data == null -> ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load)
        viewModel.data != null -> DashboardContent(viewModel.data!!, actions, viewModel.writesBlockedReason)
    }
}

@Composable
private fun DashboardContent(data: DashboardData, actions: DashboardActions, writesBlockedReason: String?) {
    val salesTotal = data.today_sales.Total.toDoubleOrNull() ?: 0.0
    val purchasesTotal = data.today_purchases.Total.toDoubleOrNull() ?: 0.0
    val profitTotal = data.total_profit.toDoubleOrNull() ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { GreetingHeader() }

        item { QuickActions(actions, writesBlockedReason) }

        if (Session.canSell || Session.canPurchase) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (Session.canSell) {
                        DueCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.People,
                            title = Terms.CUSTOMERS,
                            label = "To receive",
                            total = data.customer_due_total ?: data.shop_dues.sumOf { (it.Balance.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) }.toString(),
                            count = data.customer_due_count,
                            color = Color(0xFF0D9488),
                            onClick = actions.customerDues,
                        )
                    }
                    if (Session.canPurchase) {
                        DueCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.LocalShipping,
                            title = Terms.SUPPLIERS,
                            label = "To pay",
                            total = data.supplier_due_total ?: data.factory_payables.sumOf { (it.Balance.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) }.toString(),
                            count = data.supplier_due_count,
                            color = Color(0xFF525E7D),
                            onClick = actions.supplierDues,
                        )
                    }
                }
            }
        }

        item {
            HeroProfitCard(value = formatCurrency(profitTotal))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ShoppingCart,
                    label = "Today's Sales",
                    value = formatCurrency(salesTotal),
                    sub = "${data.today_sales.Count} bills",
                    gradient = Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF14B8A6))),
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Inventory2,
                    label = "Today's Purchases",
                    value = formatCurrency(purchasesTotal),
                    sub = "${data.today_purchases.Count} bills",
                    gradient = Brush.linearGradient(listOf(Color(0xFFEA580C), Color(0xFFF97316))),
                )
            }
        }
        item {
            ChartCard(
                title = "Today's Overview",
                bars = listOf(
                    BarEntry("Sales", salesTotal, Color(0xFF14B8A6)),
                    BarEntry("Purchases", purchasesTotal, Color(0xFFF97316)),
                    BarEntry("Profit", profitTotal, Color(0xFF22C55E)),
                ),
            )
        }
        if (data.low_stock.isNotEmpty()) {
            item { SectionHeader(Icons.Filled.Warning, "Low Stock", tint = Color(0xFFF59E0B)) }
            items(data.low_stock) { LowStockRow(it) }
        }
        // Top balances only; the full, recency-sorted list is one tap away on the dues screen.
        val customerDues = data.shop_dues.filter { (it.Balance.toDoubleOrNull() ?: 0.0) > 0 }.take(5)
        if (Session.canSell && customerDues.isNotEmpty()) {
            item {
                SectionHeader(Icons.Filled.People, "Customer Dues", tint = MaterialTheme.colorScheme.primary, onSeeAll = actions.customerDues)
            }
            item {
                DuesChartCard(customerDues, Color(0xFF0D9488), onClickItem = actions.customerLedger)
            }
        }
        val supplierDues = data.factory_payables.filter { (it.Balance.toDoubleOrNull() ?: 0.0) > 0 }.take(5)
        if (Session.canPurchase && supplierDues.isNotEmpty()) {
            item {
                SectionHeader(Icons.Filled.LocalShipping, "Supplier Dues", tint = MaterialTheme.colorScheme.tertiary, onSeeAll = actions.supplierDues)
            }
            item {
                DuesChartCard(supplierDues, Color(0xFF525E7D), onClickItem = actions.supplierLedger)
            }
        }
    }
}

/**
 * The four most frequent actions, one tap each. Buttons follow the staff permissions, and are
 * disabled (with the reason shown) when the subscription no longer allows writes.
 */
@Composable
private fun QuickActions(actions: DashboardActions, writesBlockedReason: String?) {
    val buttons = buildList {
        if (Session.canSell) add(QuickAction("+ Sale", Icons.Filled.AddShoppingCart, Color(0xFF0D9488), actions.newSale))
        if (Session.canPurchase) add(QuickAction("+ Purchase", Icons.Filled.Inventory2, Color(0xFFEA580C), actions.newPurchase))
        if (Session.canSell) add(QuickAction("Collect", Icons.Filled.AccountBalanceWallet, Color(0xFF15803D), actions.collectMoney))
        if (Session.canPurchase) add(QuickAction("Pay", Icons.Filled.Payments, Color(0xFF525E7D), actions.paySupplier))
    }
    if (buttons.isEmpty()) return
    val enabled = writesBlockedReason == null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            buttons.forEach { action ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(action.color.copy(alpha = if (enabled) 0.12f else 0.05f))
                        .clickable(enabled = enabled, onClick = action.onClick)
                        .padding(vertical = 14.dp),
                ) {
                    IconBadge(
                        action.icon,
                        tint = Color.White,
                        background = if (enabled) action.color else action.color.copy(alpha = 0.4f),
                        size = 40.dp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(action.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (writesBlockedReason != null) {
            Text(writesBlockedReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private class QuickAction(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

@Composable
private fun DueCard(
    icon: ImageVector,
    title: String,
    label: String,
    total: String,
    count: Int?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBadge(icon, tint = color, background = color.copy(alpha = 0.12f), size = 30.dp)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCurrency(total.toDoubleOrNull() ?: 0.0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            if (count != null) {
                Text("$count pending ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class BarEntry(val label: String, val value: Double, val color: Color)

private fun formatCurrency(value: Double): String = "₹" + String.format(Locale.US, "%,.2f", value)

@Composable
private fun GreetingHeader() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val today = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Calendar.getInstance().time)

    Column {
        Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(today, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroProfitCard(value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E3A2F), Color(0xFF15803D))))
            .padding(22.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.10f),
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp),
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBadge(Icons.Filled.Payments, tint = Color.White, background = Color.White.copy(alpha = 0.18f), size = 34.dp)
                Text("Total Profit", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(14.dp))
            Text(value, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color, background: Color, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, text: String, tint: Color, onSeeAll: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconBadge(icon, tint = tint, background = tint.copy(alpha = 0.12f), size = 30.dp)
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    gradient: Brush,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Box(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(gradient)
            .padding(16.dp),
    ) {
        Column {
            IconBadge(icon, tint = Color.White, background = Color.White.copy(alpha = 0.2f), size = 34.dp)
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Simple horizontal bar chart built from plain Compose layout - no charting library needed. */
@Composable
private fun ChartCard(title: String, bars: List<BarEntry>) {
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Icons.Filled.BarChart, tint = MaterialTheme.colorScheme.primary, background = MaterialTheme.colorScheme.primaryContainer, size = 30.dp)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            bars.forEach { bar -> BarRow(bar.label, bar.value, max, bar.color) }
        }
    }
}

@Composable
private fun DuesChartCard(items: List<DueItem>, color: Color, onClickItem: (Int) -> Unit) {
    val balances = items.map { it.Balance.toDoubleOrNull() ?: 0.0 }
    val max = balances.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val total = balances.sum()
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total outstanding", style = MaterialTheme.typography.labelMedium)
                Text(formatCurrency(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            }
            items.forEachIndexed { index, item ->
                BarRow(item.Name, balances[index], max, color, modifier = Modifier.clickable { onClickItem(item.ID) })
            }
        }
    }
}

@Composable
private fun BarRow(label: String, value: Double, max: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(formatCurrency(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value / max).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
            )
        }
    }
}

@Composable
private fun LowStockRow(item: LowStockItem) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFF59E0B)),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.Name, style = MaterialTheme.typography.bodyMedium)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${item.CurrentStock} ${item.Unit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
