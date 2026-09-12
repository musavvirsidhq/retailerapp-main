package com.retailapp.android.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One entry per screen reachable from the nav drawer. To add a new module: add an object here,
 * a `composable(...)` block in [com.retailapp.android.navigation.MainScreen], and a Retrofit
 * interface + screen/viewmodel pair following an existing package (e.g. ui.factories) as a template.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object Products : Screen("products", "Products", Icons.Default.Inventory2)
    data object Categories : Screen("categories", "Categories", Icons.Default.Category)
    data object Shops : Screen("shops", "Shops", Icons.Default.Store)
    data object Factories : Screen("factories", "Factories", Icons.Default.Factory)
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
