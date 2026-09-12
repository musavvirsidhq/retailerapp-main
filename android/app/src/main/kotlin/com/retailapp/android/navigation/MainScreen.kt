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
import com.retailapp.android.ui.dashboard.DashboardScreen
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
    val currentRoute = backStackEntry?.destination?.route

    val isSuperAdmin = user.user_type == "SUPER_ADMIN"
    val drawerScreens = when {
        isSuperAdmin -> superAdminDrawerScreens
        user.user_type == "COMPANY_ADMIN" -> companyDrawerScreens + companyAdminOnlyScreens
        else -> companyDrawerScreens
    }
    val startDestination = if (isSuperAdmin) Screen.SuperAdminCompanies.route else Screen.Dashboard.route

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
            topBar = {
                TopAppBar(
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
                composable(Screen.Dashboard.route) { DashboardScreen() }
                composable(Screen.Products.route) { ProductsScreen() }
                composable(Screen.Categories.route) { CategoriesScreen() }
                composable(Screen.Shops.route) { ShopsScreen() }
                composable(Screen.Factories.route) { FactoriesScreen() }
                composable(Screen.Sales.route) {
                    SalesScreen(onOpenBill = { id -> navController.navigate(billRoute(isSale = true, id = id)) })
                }
                composable(Screen.Purchases.route) {
                    PurchasesScreen(onOpenBill = { id -> navController.navigate(billRoute(isSale = false, id = id)) })
                }
                composable(Screen.Payments.route) { PaymentsScreen() }
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
                    BillDetailScreen(id = id, isSale = kind == "sale", onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
