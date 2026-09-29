@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.retailapp.android.data.model.User
import com.retailapp.android.ui.bills.BillDetailScreen
import com.retailapp.android.ui.categories.CategoriesScreen
import com.retailapp.android.ui.companyusers.CompanyUsersScreen
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.ui.common.PhotoViewerScreen
import com.retailapp.android.ui.dashboard.DashboardActions
import com.retailapp.android.ui.dashboard.DashboardScreen
import com.retailapp.android.ui.ledger.DuesScreen
import com.retailapp.android.ui.ledger.LedgerScreen
import com.retailapp.android.ui.ledger.PartyKind
import com.retailapp.android.ui.payments.PaymentDetailScreen
import com.retailapp.android.ui.factories.FactoriesScreen
import com.retailapp.android.ui.payments.PaymentsScreen
import com.retailapp.android.ui.products.ProductsScreen
import com.retailapp.android.ui.purchases.PurchasesScreen
import com.retailapp.android.ui.sales.SalesScreen
import com.retailapp.android.ui.shops.ShopsScreen
import com.retailapp.android.ui.superadmin.SuperAdminScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(user: User, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Strip optional query args so "sales?new={new}&partyId={partyId}" still matches the "sales" drawer entry.
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('?')

    val isSuperAdmin = user.user_type == "SUPER_ADMIN"
    val drawerScreens = when {
        isSuperAdmin -> superAdminDrawerScreens
        user.user_type == "COMPANY_ADMIN" -> companyDrawerScreens + companyAdminOnlyScreens
        else -> companyDrawerScreens
    }
    val startDestination = if (isSuperAdmin) Screen.SuperAdminCompanies.route else Screen.Dashboard.route
    // Drawer screens get the shared top bar; detail screens and forms opened from a quick action
    // draw their own bar with a back arrow instead.
    val showMainTopBar = drawerScreens.any { it.route == currentRoute } && backStackEntry?.arguments?.getString("new") == null

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = user.user_type,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                drawerScreens.forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(screen.label) },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                launchSingleTop = true
                                popUpTo(startDestination)
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Logout") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    selected = false,
                    onClick = onLogout,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (showMainTopBar) TopAppBar(
                    title = { Text(drawerScreens.find { it.route == currentRoute }?.label ?: "BulqBee") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                )
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxWidth().padding(padding),
            ) {
                val back: () -> Unit = { navController.popBackStack() }
                val openPhotos: (AttachmentEntity, Int, Int, String) -> Unit = { entity, id, start, title ->
                    navController.navigate(photosRoute(entity, id, start, title))
                }
                // Optional query args shared by the sales/purchases/payments routes. partyId is
                // a string because navigation can't have a nullable Int argument.
                val formArgs = listOf(
                    navArgument("new") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("partyId") { type = NavType.StringType; nullable = true; defaultValue = null },
                )

                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        DashboardActions(
                            newSale = { navController.navigate(newSaleRoute()) },
                            newPurchase = { navController.navigate(newPurchaseRoute()) },
                            collectMoney = { navController.navigate(newPaymentRoute("shop")) },
                            paySupplier = { navController.navigate(newPaymentRoute("factory")) },
                            customerDues = { navController.navigate(duesRoute(PartyKind.CUSTOMER)) },
                            supplierDues = { navController.navigate(duesRoute(PartyKind.SUPPLIER)) },
                            customerLedger = { id -> navController.navigate(ledgerRoute(PartyKind.CUSTOMER, id)) },
                            supplierLedger = { id -> navController.navigate(ledgerRoute(PartyKind.SUPPLIER, id)) },
                        ),
                    )
                }
                composable(Screen.Products.route) { ProductsScreen() }
                composable(Screen.Categories.route) { CategoriesScreen() }
                composable(Screen.Shops.route) {
                    ShopsScreen(onOpenLedger = { id -> navController.navigate(ledgerRoute(PartyKind.CUSTOMER, id)) })
                }
                composable(Screen.Factories.route) {
                    FactoriesScreen(onOpenLedger = { id -> navController.navigate(ledgerRoute(PartyKind.SUPPLIER, id)) })
                }
                composable(SALES_ROUTE_PATTERN, arguments = formArgs) { entry ->
                    SalesScreen(
                        onOpenBill = { id -> navController.navigate(billRoute(isSale = true, id = id)) },
                        startNew = entry.arguments?.getString("new") != null,
                        presetShopId = entry.arguments?.getString("partyId")?.toIntOrNull(),
                        onClose = back,
                    )
                }
                composable(PURCHASES_ROUTE_PATTERN, arguments = formArgs) { entry ->
                    PurchasesScreen(
                        onOpenBill = { id -> navController.navigate(billRoute(isSale = false, id = id)) },
                        startNew = entry.arguments?.getString("new") != null,
                        presetFactoryId = entry.arguments?.getString("partyId")?.toIntOrNull(),
                        onClose = back,
                    )
                }
                composable(PAYMENTS_ROUTE_PATTERN, arguments = formArgs) { entry ->
                    PaymentsScreen(
                        onOpenPayment = { id -> navController.navigate(paymentDetailRoute(id)) },
                        startNew = entry.arguments?.getString("new"),
                        presetPartyId = entry.arguments?.getString("partyId")?.toIntOrNull(),
                        onClose = back,
                    )
                }
                composable(Screen.CompanyUsers.route) { CompanyUsersScreen() }
                composable(Screen.SuperAdminCompanies.route) { SuperAdminScreen() }
                composable(
                    route = BILL_ROUTE_PATTERN,
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType },
                        navArgument("id") { type = NavType.IntType },
                    ),
                ) { entry ->
                    val kind = entry.arguments?.getString("kind")
                    val id = entry.arguments?.getInt("id") ?: 0
                    val isSale = kind == "sale"
                    BillDetailScreen(
                        id = id,
                        isSale = isSale,
                        onBack = back,
                        onOpenPhoto = { index, title ->
                            openPhotos(if (isSale) AttachmentEntity.SALE else AttachmentEntity.PURCHASE, id, index, title)
                        },
                    )
                }
                composable(DUES_ROUTE_PATTERN, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
                    val kind = PartyKind.valueOf(entry.arguments?.getString("kind") ?: PartyKind.CUSTOMER.name)
                    DuesScreen(kind = kind, onBack = back, onOpenLedger = { id -> navController.navigate(ledgerRoute(kind, id)) })
                }
                composable(
                    LEDGER_ROUTE_PATTERN,
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType },
                        navArgument("id") { type = NavType.IntType },
                    ),
                ) { entry ->
                    val kind = PartyKind.valueOf(entry.arguments?.getString("kind") ?: PartyKind.CUSTOMER.name)
                    val isCustomer = kind == PartyKind.CUSTOMER
                    LedgerScreen(
                        kind = kind,
                        partyId = entry.arguments?.getInt("id") ?: 0,
                        onBack = back,
                        onOpenBill = { id -> navController.navigate(billRoute(isSale = isCustomer, id = id)) },
                        onOpenPayment = { id -> navController.navigate(paymentDetailRoute(id)) },
                        onOpenPhotos = { entity, id, title -> openPhotos(entity, id, 0, title) },
                        onPay = { partyId -> navController.navigate(newPaymentRoute(kind.partyType, partyId)) },
                        onNewBill = { partyId ->
                            navController.navigate(if (isCustomer) newSaleRoute(partyId) else newPurchaseRoute(partyId))
                        },
                    )
                }
                composable(PAYMENT_DETAIL_ROUTE_PATTERN, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                    val id = entry.arguments?.getInt("id") ?: 0
                    PaymentDetailScreen(
                        id = id,
                        onBack = back,
                        onOpenPhoto = { index, title -> openPhotos(AttachmentEntity.PAYMENT, id, index, title) },
                    )
                }
                composable(
                    PHOTOS_ROUTE_PATTERN,
                    arguments = listOf(
                        navArgument("entity") { type = NavType.StringType },
                        navArgument("id") { type = NavType.IntType },
                        navArgument("start") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    PhotoViewerScreen(
                        entity = AttachmentEntity.valueOf(args?.getString("entity") ?: AttachmentEntity.SALE.name),
                        entityId = args?.getInt("id") ?: 0,
                        startIndex = args?.getInt("start") ?: 0,
                        title = args?.getString("title").orEmpty(),
                        onBack = back,
                    )
                }
            }
        }
    }
}
