@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.retailapp.android.data.model.User
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.session.AppShortcut
import com.retailapp.android.session.PendingShortcut
import com.retailapp.android.ui.bills.BillDetailScreen
import com.retailapp.android.ui.categories.CategoriesScreen
import com.retailapp.android.ui.common.PhotoViewerScreen
import com.retailapp.android.ui.companyusers.CompanyUsersScreen
import com.retailapp.android.ui.dashboard.DashboardActions
import com.retailapp.android.ui.dashboard.DashboardScreen
import com.retailapp.android.ui.factories.FactoriesScreen
import com.retailapp.android.ui.ledger.DuesScreen
import com.retailapp.android.ui.ledger.DuesTabScreen
import com.retailapp.android.ui.ledger.EditPartyScreen
import com.retailapp.android.ui.ledger.LedgerScreen
import com.retailapp.android.ui.ledger.PartyKind
import com.retailapp.android.ui.ledger.RemindersScreen
import com.retailapp.android.ui.payments.PaymentDetailScreen
import com.retailapp.android.ui.payments.PaymentsScreen
import com.retailapp.android.ui.products.EditProductScreen
import com.retailapp.android.ui.products.ProductsScreen
import com.retailapp.android.ui.purchases.PurchasesScreen
import com.retailapp.android.ui.sales.SalesScreen
import com.retailapp.android.ui.shops.ShopsScreen
import com.retailapp.android.ui.superadmin.SuperAdminScreen

/**
 * Cycle 5: a bottom bar (Home, Sales, Dues, Purchases, More) replaces the drawer, so the main
 * jobs are one tap away. Each tab keeps its own back stack; the bar hides on full-screen forms.
 */
@Composable
fun MainScreen(user: User, onLogout: () -> Unit) {
    // The Super Admin only manages companies: keep the single-screen layout (section 5.3 rule 4).
    if (user.user_type == "SUPER_ADMIN") {
        LaunchedEffect(Unit) { PendingShortcut.consume() }
        SuperAdminHome(onLogout)
        return
    }

    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val pattern = backStackEntry?.destination?.route
    // Strip optional query args so "sales?new={new}&partyId={partyId}" still matches "sales".
    val baseRoute = pattern?.substringBefore('?')
    val isForm = backStackEntry?.arguments?.getString("new") != null
    val tabs = Tab.entries.filter { it.visible }

    // Which tab is highlighted. Detail screens keep the tab they were opened from.
    var currentTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    LaunchedEffect(baseRoute, isForm) {
        if (!isForm) Tab.entries.find { it.route == baseRoute }?.let { currentTab = it }
    }

    val showBottomBar = !isForm && pattern != null && fullScreenRoutes.none { pattern.startsWith(it) }
    // Screens without a top bar of their own get one here; [isTabRoot] ones have no back arrow.
    val mainTitle = if (isForm) null else mainTitles[baseRoute]
    val isTabRoot = Tab.entries.any { it.route == baseRoute }

    // Launcher shortcuts (section 10.1): acted on once logged in, permission-checked like the
    // dashboard quick actions.
    val pendingShortcut = PendingShortcut.value
    LaunchedEffect(pendingShortcut) {
        val shortcut = PendingShortcut.consume() ?: return@LaunchedEffect
        if (!shortcut.allowed) {
            Toast.makeText(context, "You don't have access to ${shortcut.label}.", Toast.LENGTH_LONG).show()
            navController.switchTab(Tab.HOME)
            return@LaunchedEffect
        }
        navController.navigate(
            when (shortcut) {
                AppShortcut.NEW_SALE -> newSaleRoute()
                AppShortcut.NEW_PURCHASE -> newPurchaseRoute()
                AppShortcut.COLLECT_MONEY -> newPaymentRoute("shop")
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (mainTitle != null) {
                TopAppBar(
                    title = { Text(mainTitle) },
                    navigationIcon = {
                        if (!isTabRoot) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                currentTab = tab
                                navController.switchTab(tab)
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            // Consumed so the screens' own Scaffolds don't pad for the system bars a second time.
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            val back: () -> Unit = { navController.popBackStack() }
            val openPhotos: (AttachmentEntity, Int, Int, String) -> Unit = { entity, id, start, title ->
                navController.navigate(photosRoute(entity, id, start, title))
            }
            val openLedger: (PartyKind, Int) -> Unit = { kind, id -> navController.navigate(ledgerRoute(kind, id)) }
            val addProductFromScan: (String) -> Unit = { code -> navController.navigate(addProductRoute(code)) }
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
                        customerLedger = { id -> openLedger(PartyKind.CUSTOMER, id) },
                        supplierLedger = { id -> openLedger(PartyKind.SUPPLIER, id) },
                    ),
                )
            }
            composable(Screen.DuesTab.route) {
                DuesTabScreen(onOpenLedger = openLedger, onSendReminders = { navController.navigate(REMINDERS_ROUTE) })
            }
            composable(Screen.More.route) {
                MoreScreen(user = user, onOpen = { navController.navigate(it.route) }, onLogout = onLogout)
            }
            composable(
                PRODUCTS_ROUTE_PATTERN,
                arguments = listOf(navArgument("addSku") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                ProductsScreen(
                    onBack = back,
                    onEditProduct = { id -> navController.navigate(editProductRoute(id)) },
                    startAddSku = entry.arguments?.getString("addSku"),
                )
            }
            composable(Screen.Categories.route) { CategoriesScreen() }
            composable(Screen.Shops.route) {
                ShopsScreen(onBack = back, onOpenLedger = { id -> openLedger(PartyKind.CUSTOMER, id) })
            }
            composable(Screen.Factories.route) {
                FactoriesScreen(onBack = back, onOpenLedger = { id -> openLedger(PartyKind.SUPPLIER, id) })
            }
            composable(SALES_ROUTE_PATTERN, arguments = formArgs) { entry ->
                SalesScreen(
                    onOpenBill = { id -> navController.navigate(billRoute(isSale = true, id = id)) },
                    onNewSale = { navController.navigate(newSaleRoute()) },
                    startNew = entry.arguments?.getString("new") != null,
                    presetShopId = entry.arguments?.getString("partyId")?.toIntOrNull(),
                    onClose = back,
                    onSavedOpenBill = { id -> navController.replaceCurrent(billRoute(isSale = true, id = id)) },
                    onAddProductFromScan = addProductFromScan,
                )
            }
            composable(PURCHASES_ROUTE_PATTERN, arguments = formArgs) { entry ->
                PurchasesScreen(
                    onOpenBill = { id -> navController.navigate(billRoute(isSale = false, id = id)) },
                    onNewPurchase = { navController.navigate(newPurchaseRoute()) },
                    startNew = entry.arguments?.getString("new") != null,
                    presetFactoryId = entry.arguments?.getString("partyId")?.toIntOrNull(),
                    onClose = back,
                    onSavedOpenBill = { id -> navController.replaceCurrent(billRoute(isSale = false, id = id)) },
                    onAddProductFromScan = addProductFromScan,
                )
            }
            composable(PAYMENTS_ROUTE_PATTERN, arguments = formArgs) { entry ->
                PaymentsScreen(
                    onOpenPayment = { id -> navController.navigate(paymentDetailRoute(id)) },
                    onNewPayment = { type -> navController.navigate(newPaymentRoute(type)) },
                    startNew = entry.arguments?.getString("new"),
                    presetPartyId = entry.arguments?.getString("partyId")?.toIntOrNull(),
                    onClose = back,
                    onSavedOpenPayment = { id -> navController.replaceCurrent(paymentDetailRoute(id)) },
                )
            }
            composable(Screen.CompanyUsers.route) { CompanyUsersScreen() }
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
                DuesScreen(
                    kind = kind,
                    onBack = back,
                    onOpenLedger = { id -> openLedger(kind, id) },
                    onSendReminders = { navController.navigate(REMINDERS_ROUTE) },
                )
            }
            composable(REMINDERS_ROUTE) { RemindersScreen(onBack = back) }
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
                    onEdit = { partyId -> navController.navigate(editPartyRoute(kind, partyId)) },
                )
            }
            composable(EDIT_SHOP_ROUTE_PATTERN, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                EditPartyScreen(kind = PartyKind.CUSTOMER, id = entry.arguments?.getInt("id") ?: 0, onBack = back)
            }
            composable(EDIT_FACTORY_ROUTE_PATTERN, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                EditPartyScreen(kind = PartyKind.SUPPLIER, id = entry.arguments?.getInt("id") ?: 0, onBack = back)
            }
            composable(EDIT_PRODUCT_ROUTE_PATTERN, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                EditProductScreen(id = entry.arguments?.getInt("id") ?: 0, onBack = back)
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

/** Top bar titles for screens that don't draw their own bar. */
private val mainTitles = mapOf(
    Screen.Dashboard.route to "BulqBee",
    Screen.Sales.route to Screen.Sales.label,
    Screen.Purchases.route to Screen.Purchases.label,
    Screen.More.route to Screen.More.label,
    Screen.Payments.route to Screen.Payments.label,
    Screen.Categories.route to Screen.Categories.label,
    Screen.CompanyUsers.route to Screen.CompanyUsers.label,
)

/**
 * Standard bottom-bar navigation: pop back to Home, saving the tab being left, and restore the
 * chosen tab's own back stack and scroll position (section 5.3 rule 2).
 */
private fun NavController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Opens [route] in place of the current screen, e.g. a just-saved form replaced by its bill. */
private fun NavController.replaceCurrent(route: String) {
    val current = currentDestination?.id
    navigate(route) {
        if (current != null) popUpTo(current) { inclusive = true }
    }
}

@Composable
private fun SuperAdminHome(onLogout: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(Screen.SuperAdminCompanies.label) },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout") }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            SuperAdminScreen()
        }
    }
}
