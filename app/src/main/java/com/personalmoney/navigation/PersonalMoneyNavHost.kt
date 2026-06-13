package com.personalmoney.navigation

import android.content.Intent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.personalmoney.R
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.feature.accounts.AccountEditScreen
import com.personalmoney.feature.accounts.AccountPickerScreen
import com.personalmoney.feature.accounts.AccountsScreen
import com.personalmoney.feature.analytics.AnalyticsScreen
import com.personalmoney.feature.categories.CategoriesScreen
import com.personalmoney.feature.categories.CategoryEditScreen
import com.personalmoney.feature.currencies.CurrenciesScreen
import com.personalmoney.feature.currencies.CurrencyEditScreen
import com.personalmoney.feature.dashboard.DashboardScreen
import com.personalmoney.feature.more.MoreScreen
import com.personalmoney.feature.onboarding.OnboardingScreen
import com.personalmoney.feature.quickadd.QuickAddScreen
import com.personalmoney.feature.quickadd.QuickAddViewModel
import com.personalmoney.feature.settings.SettingsScreen
import com.personalmoney.feature.subscriptions.SubscriptionEditScreen
import com.personalmoney.feature.subscriptions.SubscriptionsScreen
import com.personalmoney.feature.transactions.TransactionEditScreen
import com.personalmoney.feature.transactions.TransactionsScreen
import com.personalmoney.feature.transfers.TransferScreen

private data class BottomNavItem(val route: String, val icon: ImageVector, val label: Int)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.DASHBOARD, Icons.Filled.Home, R.string.nav_overview),
    BottomNavItem(Routes.TRANSACTIONS, Icons.AutoMirrored.Filled.List, R.string.nav_transactions),
    BottomNavItem(Routes.ANALYTICS, Icons.Filled.BarChart, R.string.nav_analytics),
    BottomNavItem(Routes.MORE, Icons.Filled.MoreHoriz, R.string.nav_more),
)

private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun PersonalMoneyNavHost(
    deepLinkIntent: Intent? = null,
    userPreferences: UserPreferences
) {
    val onboarded by userPreferences.onboarded.collectAsStateWithLifecycle(initialValue = null)

    // Wait for preference to be read (typically <16ms from DataStore cache)
    if (onboarded == null) return

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onMainTab = bottomNavItems.any { it.route == currentRoute }
    val showFab = currentRoute == Routes.DASHBOARD || currentRoute == Routes.TRANSACTIONS

    Scaffold(
        bottomBar = {
            if (onMainTab) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.switchTab(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { navController.navigate(Routes.quickAdd("EXPENSE")) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_expense_fab))
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboarded == true) Routes.DASHBOARD else Routes.ONBOARDING,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen()
            }

            composable(Routes.MORE) {
                MoreScreen(
                    onOpenTransfer = { navController.navigate(Routes.TRANSFER) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenSubscriptions = { navController.navigate(Routes.SUBSCRIPTIONS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(
                route = Routes.QUICK_ADD,
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "EXPENSE"
                        nullable = false
                    },
                    navArgument("categoryId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "personalmoney://quickadd?type={type}&categoryId={categoryId}" }
                )
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "EXPENSE"
                val categoryParam = backStackEntry.arguments?.getString("categoryId").orEmpty()
                val categoryId = categoryParam.toLongOrNull()

                val quickAddViewModel: QuickAddViewModel = hiltViewModel()
                // Receive the account chosen on the full-screen picker.
                val pickedAccountId by backStackEntry.savedStateHandle
                    .getStateFlow(Routes.RESULT_PICKED_ACCOUNT_ID, -1L)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(pickedAccountId) {
                    if (pickedAccountId != -1L) {
                        quickAddViewModel.selectAccountById(pickedAccountId)
                        backStackEntry.savedStateHandle[Routes.RESULT_PICKED_ACCOUNT_ID] = -1L
                    }
                }

                QuickAddScreen(
                    initialType = type,
                    initialCategoryId = categoryId,
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onPickAccount = { currentId ->
                        navController.navigate(Routes.accountPicker(currentId))
                    },
                    viewModel = quickAddViewModel
                )
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onAdd = { navController.navigate(Routes.accountEdit()) },
                    onEdit = { id -> navController.navigate(Routes.accountEdit(id)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ACCOUNT_EDIT,
                arguments = listOf(
                    navArgument("accountId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    }
                )
            ) {
                AccountEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    onAdd = { type -> navController.navigate(Routes.categoryEdit(type = type.name)) },
                    onEdit = { id -> navController.navigate(Routes.categoryEdit(id = id)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.CATEGORY_EDIT,
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "EXPENSE"
                        nullable = false
                    }
                )
            ) {
                CategoryEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onBack = null,
                    onEdit = { id -> navController.navigate(Routes.transactionEdit(id)) }
                )
            }

            composable(
                route = Routes.TRANSACTION_EDIT,
                arguments = listOf(
                    navArgument("transactionId") {
                        type = NavType.StringType
                        nullable = false
                    }
                )
            ) {
                TransactionEditScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Routes.TRANSFER) {
                TransferScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Routes.SUBSCRIPTIONS) {
                SubscriptionsScreen(
                    onAdd = { navController.navigate(Routes.subscriptionEdit()) },
                    onEdit = { id -> navController.navigate(Routes.subscriptionEdit(id)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.SUBSCRIPTION_EDIT,
                arguments = listOf(
                    navArgument("subscriptionId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    }
                )
            ) {
                SubscriptionEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCurrencies = { navController.navigate(Routes.CURRENCIES) }
                )
            }
            composable(Routes.CURRENCIES) {
                CurrenciesScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.currencyEdit()) },
                    onEdit = { code -> navController.navigate(Routes.currencyEdit(code)) }
                )
            }
            composable(
                route = Routes.CURRENCY_EDIT,
                arguments = listOf(
                    navArgument("code") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    }
                )
            ) {
                CurrencyEditScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }

            composable(Routes.ANALYTICS) {
                AnalyticsScreen(onBack = null)
            }

            composable(
                route = Routes.ACCOUNT_PICKER,
                arguments = listOf(
                    navArgument("selectedId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = false
                    }
                )
            ) {
                AccountPickerScreen(
                    onPick = { id ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            Routes.RESULT_PICKED_ACCOUNT_ID, id
                        )
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
