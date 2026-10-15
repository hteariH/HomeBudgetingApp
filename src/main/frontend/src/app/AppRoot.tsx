import React, { Suspense, lazy, useCallback, useEffect } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { endpoints } from './api/endpoints';
import { useAccounts, useCurrencies, usePref } from './api/hooks';
import { queryClient } from './api/queryClient';
import { session } from './api/session';
import { prefs } from './prefs';
import { AppHeader } from './shell/AppHeader';
import { AuthGuard, useSession } from './shell/AuthGuard';
import { PageLoading } from './shell/Page';

const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage'));
const QuickAddPage = lazy(() => import('./features/quickadd/QuickAddPage'));
const TransactionsPage = lazy(() => import('./features/transactions/TransactionsPage'));
const TransactionEditPage = lazy(() => import('./features/transactions/TransactionEditPage'));
const AccountsPage = lazy(() => import('./features/accounts/AccountsPage'));
const AccountEditPage = lazy(() => import('./features/accounts/AccountEditPage'));
const CategoriesPage = lazy(() => import('./features/categories/CategoriesPage'));
const CategoryEditPage = lazy(() => import('./features/categories/CategoryEditPage'));
const SubscriptionsPage = lazy(() => import('./features/subscriptions/SubscriptionsPage'));
const SubscriptionEditPage = lazy(() => import('./features/subscriptions/SubscriptionEditPage'));
const CurrenciesPage = lazy(() => import('./features/currencies/CurrenciesPage'));
const CurrencyEditPage = lazy(() => import('./features/currencies/CurrencyEditPage'));
const AnalyticsPage = lazy(() => import('./features/analytics/AnalyticsPage'));
const BanksPage = lazy(() => import('./features/banks/BanksPage'));
const HomeMoneyPage = lazy(() => import('./features/homemoney/HomeMoneyPage'));
const SettingsPage = lazy(() => import('./features/settings/SettingsPage'));
const OnboardingPage = lazy(() => import('./features/onboarding/OnboardingPage'));

/**
 * Ends the session: the server is told (best effort - a user who signs out expects to be
 * signed out even offline), then what belongs to the account goes, tokens last. Clearing
 * the tokens is what the guard reacts to, so doing it first would leave the outgoing
 * account's data on screen for as long as the rest takes.
 */
export function useSignOut() {
    const navigate = useNavigate();
    return useCallback(async () => {
        try {
            await endpoints.logout();
        } catch {
            /* Clearing locally anyway. */
        }
        prefs.clearAccountScoped();
        queryClient.clear();
        session.clear();
        navigate('/login', { replace: true });
    }, [navigate]);
}

/**
 * A new account starts with setup; everyone else lands on the overview. An account that
 * already has accounts on the server is past setup whatever this browser remembers - the
 * Compose client drew the same conclusion whenever it loaded them.
 */
const Home: React.FC = () => {
    const onboarded = usePref(() => prefs.onboarded);
    const { data: accounts, isLoading } = useAccounts();
    const hasAccounts = (accounts?.length ?? 0) > 0;
    useEffect(() => {
        if (!onboarded && hasAccounts) prefs.setOnboarded(true);
    }, [onboarded, hasAccounts]);
    if (onboarded || hasAccounts) return <DashboardPage />;
    if (isLoading) return <PageLoading />;
    return <Navigate to="/app/onboarding" replace />;
};

const Shell: React.FC = () => {
    const current = useSession();
    const signOut = useSignOut();
    const { pathname } = useLocation();
    // Loaded before any screen, so money formatting knows the user's symbols and decimals
    // by the time the first amount is drawn. A failure does not hold the app up - amounts
    // then fall back to Intl's formatting, and the screens show their own errors.
    const currencies = useCurrencies();
    // Setup is a three-step flow the user is not in the app yet for; offering navigation
    // out of it would be offering a way to skip it.
    const onboarding = pathname.startsWith('/app/onboarding');

    return (
        <div className="min-h-screen bg-base">
            {!onboarding && <AppHeader email={current?.email ?? null} onSignOut={signOut} />}
            <main>
                {currencies.isLoading ? (
                    <PageLoading />
                ) : (
                    <Suspense fallback={<PageLoading />}>
                        <Routes>
                            <Route index element={<Home />} />
                            <Route path="quickadd" element={<QuickAddPage />} />
                            <Route path="transactions" element={<TransactionsPage />} />
                            <Route path="transactions/:id" element={<TransactionEditPage />} />
                            <Route path="accounts" element={<AccountsPage />} />
                            <Route path="accounts/edit" element={<AccountEditPage />} />
                            <Route path="categories" element={<CategoriesPage />} />
                            <Route path="categories/edit" element={<CategoryEditPage />} />
                            <Route path="subscriptions" element={<SubscriptionsPage />} />
                            <Route path="subscriptions/edit" element={<SubscriptionEditPage />} />
                            <Route path="currencies" element={<CurrenciesPage />} />
                            <Route path="currencies/edit" element={<CurrencyEditPage />} />
                            <Route path="analytics" element={<AnalyticsPage />} />
                            <Route path="banks" element={<BanksPage />} />
                            <Route path="import/homemoney" element={<HomeMoneyPage />} />
                            <Route path="settings" element={<SettingsPage />} />
                            <Route path="onboarding" element={<OnboardingPage />} />
                            <Route path="*" element={<Navigate to="/app" replace />} />
                        </Routes>
                    </Suspense>
                )}
            </main>
        </div>
    );
};

/** Everything under /app: the signed-in application. Lazy-loaded, so the sign-in pages load without it. */
const AppRoot: React.FC = () => (
    <QueryClientProvider client={queryClient}>
        <AuthGuard>
            <Shell />
        </AuthGuard>
    </QueryClientProvider>
);

export default AppRoot;
