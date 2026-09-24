import React, { useEffect } from 'react';
import { BrowserRouter, Route, Routes, Navigate, useLocation } from 'react-router-dom';
import './index.css';
import { I18nProvider } from './i18n';
import { ThemeProvider } from './theme';
import Landing from './landing/Landing';
import VerifyEmail from './landing/VerifyEmail';
import ForgotPassword from './landing/ForgotPassword';
import ResetPassword from './landing/ResetPassword';
import Login from './landing/Login';
import Register from './landing/Register';
import { APP_PATH } from './landing/session';

/**
 * Where the application's screens used to live in this shell.
 *
 * They are Compose screens at /app/ now, and their addresses are fragments of that one
 * page. Bookmarks and links from before the move still exist, so each old path is
 * forwarded to the screen it named rather than dumped on the dashboard. `/transfers` has
 * no screen of its own any more - transfers became the third mode of the entry form.
 */
const LEGACY_ROUTES: Record<string, string> = {
    '/accounts': 'accounts',
    '/transactions': 'transactions',
    '/categories': 'categories',
    '/currencies': 'currencies',
    '/banks': 'banks',
    '/transfers': 'quickadd?type=TRANSFER',
};

/** A hard navigation: /app/ is a separate document, not a route of this one. */
const RedirectToApp: React.FC<{ fragment: string }> = ({ fragment }) => {
    useEffect(() => {
        window.location.replace(fragment ? `${APP_PATH}#${fragment}` : APP_PATH);
    }, [fragment]);

    return null;
};

/**
 * What the root of the site is.
 *
 * It used to mean two different things - the marketing page to a visitor, the dashboard to
 * someone signed in. It means one thing now: this shell is the public site, and the
 * application is somewhere else. A signed-in visitor still gets the landing page, with the
 * sign-in button replaced by a way into the app; being signed in is not a reason to be
 * denied the page describing the product.
 *
 * Anything unrecognised goes to the landing page rather than into the app, so a mistyped
 * address does not cost a seven-megabyte download to say "not found".
 */
const Root: React.FC = () => {
    const { pathname } = useLocation();

    if (pathname === '/') return <Landing />;

    const fragment = LEGACY_ROUTES[pathname];
    if (fragment !== undefined) return <RedirectToApp fragment={fragment} />;

    return <Navigate to="/" replace />;
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
                    <Route path="/*" element={<Root />} />
                </Routes>
            </ThemeProvider>
        </I18nProvider>
    </BrowserRouter>
);

export default App;
