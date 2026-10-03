package com.retailapp.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.session.Session
import com.retailapp.android.ui.common.Terms
import com.retailapp.android.ui.ledger.PartyKind

/**
 * Named screens with a fixed route. To add a new module: add an object here, a `composable(...)`
 * block in [com.retailapp.android.navigation.MainScreen], an entry in [moreScreens] (or a [Tab]),
 * and a Retrofit interface + screen/viewmodel pair following an existing package as a template.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
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
    data object DuesTab : Screen("dues_tab", "Dues", Icons.Default.AccountBalanceWallet)
    data object More : Screen("more", "More", Icons.Default.Menu)
}

/**
 * Cycle 5 bottom bar (section 5.2). Each tab keeps its own back stack; tabs follow the same
 * permission rules as the dashboard quick actions.
 */
enum class Tab(val screen: Screen, val icon: ImageVector) {
    HOME(Screen.Dashboard, Icons.Default.Home),
    SALES(Screen.Sales, Icons.Default.PointOfSale),
    DUES(Screen.DuesTab, Icons.AutoMirrored.Filled.ReceiptLong),
    PURCHASES(Screen.Purchases, Icons.Default.ShoppingCart),
    MORE(Screen.More, Icons.Default.Menu),
    ;

    val route: String get() = screen.route
    val label: String get() = screen.label

    val visible: Boolean
        get() = when (this) {
            HOME, MORE -> true
            SALES -> Session.canSell
            PURCHASES -> Session.canPurchase
            DUES -> Session.canSell || Session.canPurchase
        }
}

/** What the More tab lists (the old drawer entries that aren't tabs). Staff is admin only. */
fun moreScreens(): List<Screen> = buildList {
    add(Screen.Shops)
    add(Screen.Factories)
    add(Screen.Products)
    add(Screen.Categories)
    add(Screen.Payments)
    if (Session.isCompanyAdmin) add(Screen.CompanyUsers)
}

fun billRoute(isSale: Boolean, id: Int) = "bill/${if (isSale) "sale" else "purchase"}/$id"
const val BILL_ROUTE_PATTERN = "bill/{kind}/{id}"

// Sales/Purchases/Payments take optional query args: "new" opens the form (as its own back stack
// entry, so the bottom bar can hide) with "partyId" preselected; plain "sales" etc. is the list.
const val SALES_ROUTE_PATTERN = "sales?new={new}&partyId={partyId}"
const val PURCHASES_ROUTE_PATTERN = "purchases?new={new}&partyId={partyId}"
const val PAYMENTS_ROUTE_PATTERN = "payments?new={new}&partyId={partyId}"
const val PRODUCTS_ROUTE_PATTERN = "products?addSku={addSku}"
const val DUES_ROUTE_PATTERN = "dues/{kind}"
const val LEDGER_ROUTE_PATTERN = "ledger/{kind}/{id}"
const val PAYMENT_DETAIL_ROUTE_PATTERN = "payment/{id}"
const val PHOTOS_ROUTE_PATTERN = "photos/{entity}/{id}?start={start}&title={title}"

// Cycle 5 routes (section 14).
const val EDIT_SHOP_ROUTE_PATTERN = "edit_shop/{id}"
const val EDIT_FACTORY_ROUTE_PATTERN = "edit_factory/{id}"
const val EDIT_PRODUCT_ROUTE_PATTERN = "edit_product/{id}"
const val REMINDERS_ROUTE = "reminders"

fun newSaleRoute(shopId: Int? = null) = "sales?new=true" + (shopId?.let { "&partyId=$it" } ?: "")
fun newPurchaseRoute(factoryId: Int? = null) = "purchases?new=true" + (factoryId?.let { "&partyId=$it" } ?: "")

/** [partyType] is the backend's "shop" (collect from a customer) or "factory" (pay a supplier). */
fun newPaymentRoute(partyType: String, partyId: Int? = null) = "payments?new=$partyType" + (partyId?.let { "&partyId=$it" } ?: "")
fun duesRoute(kind: PartyKind) = "dues/${kind.name}"
fun ledgerRoute(kind: PartyKind, id: Int) = "ledger/${kind.name}/$id"
fun paymentDetailRoute(id: Int) = "payment/$id"
fun photosRoute(entity: AttachmentEntity, id: Int, start: Int, title: String) =
    "photos/${entity.name}/$id?start=$start&title=${android.net.Uri.encode(title)}"
fun editPartyRoute(kind: PartyKind, id: Int) = if (kind == PartyKind.CUSTOMER) "edit_shop/$id" else "edit_factory/$id"
fun editProductRoute(id: Int) = "edit_product/$id"
fun addProductRoute(sku: String) = "products?addSku=${android.net.Uri.encode(sku)}"

/** Routes drawn full screen, without the bottom bar (forms and the photo viewer, section 5.3 rule 3). */
val fullScreenRoutes = setOf(
    "photos/{entity}/{id}",
    EDIT_SHOP_ROUTE_PATTERN,
    EDIT_FACTORY_ROUTE_PATTERN,
    EDIT_PRODUCT_ROUTE_PATTERN,
    REMINDERS_ROUTE,
)
