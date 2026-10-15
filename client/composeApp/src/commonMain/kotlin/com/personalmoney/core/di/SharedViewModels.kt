package com.personalmoney.core.di

import com.personalmoney.core.data.LedgerFilterRequest
import com.personalmoney.feature.accounts.AccountEditViewModel
import com.personalmoney.feature.accounts.AccountPickerViewModel
import com.personalmoney.feature.accounts.AccountsViewModel
import com.personalmoney.feature.analytics.AnalyticsViewModel
import com.personalmoney.feature.auth.AuthViewModel
import com.personalmoney.feature.categories.CategoriesViewModel
import com.personalmoney.feature.categories.CategoryEditViewModel
import com.personalmoney.feature.currencies.CurrenciesViewModel
import com.personalmoney.feature.currencies.CurrencyEditViewModel
import com.personalmoney.feature.dashboard.DashboardViewModel
import com.personalmoney.feature.family.FamilyViewModel
import com.personalmoney.feature.onboarding.OnboardingViewModel
import com.personalmoney.feature.quickadd.QuickAddViewModel
import com.personalmoney.feature.settings.SettingsViewModel
import com.personalmoney.feature.shell.AppShellViewModel
import com.personalmoney.feature.subscriptions.SubscriptionEditViewModel
import com.personalmoney.feature.subscriptions.SubscriptionsViewModel
import com.personalmoney.feature.transactions.TransactionEditViewModel
import com.personalmoney.feature.transactions.TransactionsViewModel
import com.personalmoney.feature.transfers.TransferViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Every ViewModel that now lives in commonMain, registered once for both platforms.
 *
 * They ask only for the shared repository contracts and [com.personalmoney.core.data.UserPreferences],
 * so the same definitions resolve against Room on Android and REST in the browser - which
 * is the whole point of phase 5 arriving before this one. The Android module adds
 * `BackupViewModel` on top; it is the one that stayed platform-specific.
 */
val sharedViewModelModule = module {
    // Shared, because the overview writes it and the ledger reads it.
    single { LedgerFilterRequest() }

    viewModelOf(::AppShellViewModel)
    viewModelOf(::AccountEditViewModel)
    viewModelOf(::AccountPickerViewModel)
    viewModelOf(::AccountsViewModel)
    viewModelOf(::AnalyticsViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::CategoryEditViewModel)
    viewModelOf(::CurrenciesViewModel)
    viewModelOf(::CurrencyEditViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::FamilyViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::QuickAddViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SubscriptionEditViewModel)
    viewModelOf(::SubscriptionsViewModel)
    viewModelOf(::TransactionEditViewModel)
    viewModelOf(::TransactionsViewModel)
    viewModelOf(::TransferViewModel)
}
