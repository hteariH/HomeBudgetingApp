package com.personalmoney.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.feature.accounts.AccountEditScreen
import com.personalmoney.feature.accounts.AccountsScreen
import com.personalmoney.feature.analytics.AnalyticsScreen
import com.personalmoney.feature.categories.CategoriesScreen
import com.personalmoney.feature.categories.CategoryEditScreen
import com.personalmoney.feature.currencies.CurrenciesScreen
import com.personalmoney.feature.currencies.CurrencyEditScreen
import com.personalmoney.feature.dashboard.DashboardScreen
import com.personalmoney.feature.onboarding.OnboardingScreen
import com.personalmoney.feature.quickadd.QuickAddScreen
import com.personalmoney.feature.settings.SettingsScreen
import com.personalmoney.feature.subscriptions.SubscriptionEditScreen
import com.personalmoney.feature.subscriptions.SubscriptionsScreen
import com.personalmoney.feature.transactions.TransactionEditScreen
import com.personalmoney.feature.transactions.TransactionsScreen
import com.personalmoney.feature.transfers.TransferScreen

@Composable
fun PersonalMoneyNavHost(
    deepLinkIntent: Intent? = null,
    userPreferences: UserPreferences
) {
    val onboarded by userPreferences.onboarded.collectAsStateWithLifecycle(initialValue = null)

    // Wait for preference to be read (typically <16ms from DataStore cache)
    if (onboarded == null) return

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (onboarded == true) Routes.DASHBOARD else Routes.ONBOARDING
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
            DashboardScreen(
                onAddExpense = { navController.navigate(Routes.quickAdd("EXPENSE")) },
                onAddIncome = { navController.navigate(Routes.quickAdd("INCOME")) },
                onOpenTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                onOpenSubscriptions = { navController.navigate(Routes.SUBSCRIPTIONS) },
                onOpenTransfer = { navController.navigate(Routes.TRANSFER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) }
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
            QuickAddScreen(
                initialType = type,
                initialCategoryId = categoryId,
                onClose = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() },
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
            AnalyticsScreen(onBack = { navController.popBackStack() })
        }
    }
}
