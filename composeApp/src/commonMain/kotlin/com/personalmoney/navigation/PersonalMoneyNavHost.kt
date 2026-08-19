package com.personalmoney.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.unit.dp
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
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
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
import androidx.savedstate.read
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.ui.PageContainer
import com.personalmoney.feature.accounts.AccountEditScreen
import com.personalmoney.feature.accounts.AccountPickerScreen
import com.personalmoney.feature.accounts.AccountsScreen
import com.personalmoney.feature.analytics.AnalyticsScreen
import com.personalmoney.feature.auth.AuthScreen
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
import org.koin.compose.viewmodel.koinViewModel

private data class NavDestination(
    val route: String,
    val icon: ImageVector,
    val label: StringResource,
    /** Shown in the bottom bar as well as the rail. The rest live behind More on a phone. */
    val primary: Boolean = false,
)

/**
 * One list, two shapes.
 *
 * A phone shows the four primaries plus More, which is the layout the app has always had.
 * A wide window shows every destination in a rail and no More, because there is nothing
 * left to hide behind it - the same set the React sidebar lists
 * (`frontend/src/components/Sidebar.tsx`). Same screens, same routes; only the chrome
 * differs, which is the point of doing this by window size rather than by platform.
 */
private val navDestinations = listOf(
    NavDestination(Routes.DASHBOARD, Icons.Filled.Home, Res.string.nav_overview, primary = true),
    NavDestination(Routes.TRANSACTIONS, Icons.AutoMirrored.Filled.List, Res.string.nav_transactions, primary = true),
    NavDestination(Routes.ANALYTICS, Icons.Filled.BarChart, Res.string.nav_analytics, primary = true),
    NavDestination(Routes.ACCOUNTS, Icons.Filled.Wallet, Res.string.nav_accounts),
    NavDestination(Routes.CATEGORIES, Icons.Filled.Category, Res.string.nav_categories),
    NavDestination(Routes.SUBSCRIPTIONS, Icons.Filled.Subscriptions, Res.string.dashboard_subscriptions),
    NavDestination(Routes.CURRENCIES, Icons.Filled.Language, Res.string.nav_currencies),
    NavDestination(Routes.SETTINGS, Icons.Filled.Settings, Res.string.dashboard_settings),
)

private val bottomNavItems =
    navDestinations.filter { it.primary } +
        NavDestination(Routes.MORE, Icons.Filled.MoreHoriz, Res.string.nav_more, primary = true)

/**
 * Material's own breakpoint for an expanded window. The React sidebar switches at 900px,
 * close enough that the two agree in practice; this one is the documented value and does
 * not need a new adaptive artifact to read.
 */
private val RAIL_BREAKPOINT = 840.dp

private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun PersonalMoneyNavHost(
    deepLink: String? = null,
    userPreferences: UserPreferences,
    tokenStore: TokenStore
) {
    val onboarded by userPreferences.onboarded.collectAsStateWithLifecycle(initialValue = null)
    val signedIn by tokenStore.signedIn.collectAsStateWithLifecycle()

    // Wait for preference to be read (typically <16ms from DataStore cache)
    if (onboarded == null) return

    // The app is a client of an account now, so nothing can be shown before there is
    // one. This is a whole separate NavHost rather than a start destination, so that
    // signing out cannot leave a signed-in screen on the back stack.
    if (!signedIn) {
        AuthScreen(onSignedIn = { })
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onMainTab = bottomNavItems.any { it.route == currentRoute }
    val showFab = currentRoute == Routes.DASHBOARD || currentRoute == Routes.TRANSACTIONS

    BoxWithConstraints {
    val wide = maxWidth >= RAIL_BREAKPOINT
    // Present on every screen of the app, including detail ones - a sidebar that vanished
    // when you opened an account would be a worse sidebar than none. Onboarding is the
    // exception: the user is not in the app yet, and offering navigation out of a three-step
    // setup would be offering a way to skip it.
    val showRail = wide && currentRoute != Routes.ONBOARDING
    val showBottomBar = !wide && onMainTab

    Row(Modifier.fillMaxSize()) {
        if (showRail) {
            NavigationRail {
                Spacer(Modifier.height(12.dp))
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.quickAdd("EXPENSE")) },
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(Res.string.action_add_expense_fab),
                    )
                }
                Spacer(Modifier.height(12.dp))
                navDestinations.forEach { item ->
                    NavigationRailItem(
                        selected = currentRoute == item.route,
                        onClick = { navController.switchTab(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
            // The rail hosts its own, so this would be a second one on a wide window.
            if (showFab && !wide) {
                FloatingActionButton(onClick = { navController.navigate(Routes.quickAdd("EXPENSE")) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.action_add_expense_fab))
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        // Every screen renders inside the same capped, centred column, so none of them has
        // to know how wide the window is. On a phone the cap is wider than the screen and
        // this is a no-op.
        PageContainer(
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            horizontalPadding = if (wide) 24.dp else 0.dp,
        ) {
        NavHost(
            navController = navController,
            startDestination = if (onboarded == true) Routes.DASHBOARD else Routes.ONBOARDING,
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
                    onOpenTransfer = { navController.navigate(Routes.quickAdd("TRANSFER")) },
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
                // SavedState rather than a Bundle: the multiplatform navigation carries
                // arguments in its own container, read through this block.
                val args = backStackEntry.arguments
                val type = args?.read { getStringOrNull("type") } ?: "EXPENSE"
                val categoryParam = args?.read { getStringOrNull("categoryId") }.orEmpty()
                val categoryId = categoryParam.toLongOrNull()

                val quickAddViewModel: QuickAddViewModel = koinViewModel()
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
    }
    }
}
