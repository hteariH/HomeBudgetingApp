import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import './index.css';
import { I18nProvider } from './i18n';
import { ThemeProvider } from './theme';
import Landing from './landing/Landing';
import VerifyEmail from './landing/VerifyEmail';
import ForgotPassword from './landing/ForgotPassword';
import ResetPassword from './landing/ResetPassword';
import Login from './landing/Login';
import Register from './landing/Register';
import { AppRoutes } from './app/lib/routes';
import { PageLoading } from './app/shell/Page';

// Lazy, so a visitor to the landing page never downloads the application.
const AppRoot = lazy(() => import('./app/AppRoot'));

/**
 * Where the application's screens lived before there was an /app at all, when this React
 * client ran them at the top level. Bookmarks from then still exist, so each old path goes
 * to the screen it named rather than to the overview. `/transfers` has no screen of its
 * own any more - transfers are the third mode of the entry form.
 */
const LEGACY_ROUTES: Record<string, string> = {
    '/accounts': AppRoutes.accounts,
    '/transactions': AppRoutes.transactions,
    '/categories': AppRoutes.categories,
    '/currencies': AppRoutes.currencies,
    '/banks': AppRoutes.banks,
    '/transfers': AppRoutes.quickAdd('TRANSFER'),
};

/**
 * The public site. The root is the landing page to everyone - being signed in is not a
 * reason to be denied the page describing the product; it swaps "sign in" for a way into
 * the app. Anything unrecognised goes to the landing page rather than into the app.
 */
const Root: React.FC = () => {
    const { pathname } = useLocation();
    if (pathname === '/') return <Landing />;
    const target = LEGACY_ROUTES[pathname];
    return <Navigate to={target ?? '/'} replace />;
};

const App: React.FC = () => (
    <BrowserRouter>
        <I18nProvider>
            <ThemeProvider>
                <Routes>
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    <Route path="/verify-email" element={<VerifyEmail />} />
                    <Route path="/forgot-password" element={<ForgotPassword />} />
                    <Route path="/reset-password" element={<ResetPassword />} />
                    <Route
                        path="/app/*"
                        element={
                            <Suspense fallback={<PageLoading />}>
                                <AppRoot />
                            </Suspense>
                        }
                    />
                    <Route path="/*" element={<Root />} />
                </Routes>
            </ThemeProvider>
        </I18nProvider>
    </BrowserRouter>
);

export default App;
