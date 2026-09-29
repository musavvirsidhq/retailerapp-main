package com.retailapp.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.ledger.PartyKind

/**
 * One entry per screen reachable from the nav drawer. To add a new module: add an object here,
 * a `composable(...)` block in [com.retailapp.android.navigation.MainScreen], and a Retrofit
 * interface + screen/viewmodel pair following an existing package (e.g. ui.factories) as a template.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object Products : Screen("products", "Products", Icons.Default.Inventory2)
    data object Categories : Screen("categories", "Categories", Icons.Default.Category)
    // Cycle 4 renamed these in the UI only; the routes keep their old names.
    data object Shops : Screen("shops", Terms.CUSTOMERS, Icons.Default.People)
    data object Factories : Screen("factories", Terms.SUPPLIERS, Icons.Default.LocalShipping)
    data object Sales : Screen("sales", "Sales", Icons.Default.PointOfSale)
    data object Purchases : Screen("purchases", "Purchases", Icons.Default.ShoppingCart)
    data object Payments : Screen("payments", "Payments", Icons.Default.Payments)
    data object CompanyUsers : Screen("company_users", "Staff", Icons.Default.Group)
    data object SuperAdminCompanies : Screen("super_admin_companies", "Companies", Icons.Default.AdminPanelSettings)
}

/** Company Admin / Staff see the full retail operations menu. */
val companyDrawerScreens = listOf(
    Screen.Dashboard,
    Screen.Products,
    Screen.Categories,
    Screen.Shops,
    Screen.Factories,
    Screen.Sales,
    Screen.Purchases,
    Screen.Payments,
)

/** Only a Company Admin manages staff - added on top of [companyDrawerScreens] for that role. */
val companyAdminOnlyScreens = listOf(Screen.CompanyUsers)

/** A Super Admin only manages companies/subscriptions - none of the per-company data applies. */
val superAdminDrawerScreens = listOf(Screen.SuperAdminCompanies)

fun billRoute(isSale: Boolean, id: Int) = "bill/${if (isSale) "sale" else "purchase"}/$id"
const val BILL_ROUTE_PATTERN = "bill/{kind}/{id}"

// Cycle 4 routes. Sales/Purchases/Payments take optional query args so the dashboard quick
// actions and ledger buttons can open their "new" form directly, with a party preselected;
// plain "sales" etc. (from the drawer) still matches these patterns.
const val SALES_ROUTE_PATTERN = "sales?new={new}&partyId={partyId}"
const val PURCHASES_ROUTE_PATTERN = "purchases?new={new}&partyId={partyId}"
const val PAYMENTS_ROUTE_PATTERN = "payments?new={new}&partyId={partyId}"
const val DUES_ROUTE_PATTERN = "dues/{kind}"
const val LEDGER_ROUTE_PATTERN = "ledger/{kind}/{id}"
const val PAYMENT_DETAIL_ROUTE_PATTERN = "payment/{id}"
const val PHOTOS_ROUTE_PATTERN = "photos/{entity}/{id}?start={start}&title={title}"

fun newSaleRoute(shopId: Int? = null) = "sales?new=true" + (shopId?.let { "&partyId=$it" } ?: "")
fun newPurchaseRoute(factoryId: Int? = null) = "purchases?new=true" + (factoryId?.let { "&partyId=$it" } ?: "")

/** [partyType] is the backend's "shop" (collect from a customer) or "factory" (pay a supplier). */
fun newPaymentRoute(partyType: String, partyId: Int? = null) = "payments?new=$partyType" + (partyId?.let { "&partyId=$it" } ?: "")
fun duesRoute(kind: PartyKind) = "dues/${kind.name}"
fun ledgerRoute(kind: PartyKind, id: Int) = "ledger/${kind.name}/$id"
fun paymentDetailRoute(id: Int) = "payment/$id"
fun photosRoute(entity: AttachmentEntity, id: Int, start: Int, title: String) =
    "photos/${entity.name}/$id?start=$start&title=${android.net.Uri.encode(title)}"
