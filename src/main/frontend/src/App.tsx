import React, { useEffect, useState } from 'react';
import { BrowserRouter, Route, Routes, Navigate, useLocation } from 'react-router-dom';
import './index.css';
import { I18nProvider } from './i18n';
import { ThemeProvider } from './theme';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import Landing from './pages/Landing';
import Dashboard from './pages/Dashboard';
import Accounts from './pages/Accounts';
import Categories from './pages/Categories';
import Transactions from './pages/Transactions';
import Transfers from './pages/Transfers';
import Currencies from './pages/Currencies';
import Banks from './pages/Banks';
import VerifyEmail from './pages/VerifyEmail';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Login from './pages/Login';
import Register from './pages/Register';

const isAuthenticated = () => !!localStorage.getItem('token');

/**
 * The signed-in application: fixed sidebar, sticky header, routed page.
 *
 * The sidebar is a permanent column on wide screens and an off-canvas drawer below
 * 900px (see the media query in index.css); this component only owns whether the
 * drawer is open.
 */
const AppShell: React.FC = () => {
    const [navOpen, setNavOpen] = useState(false);
    const { pathname } = useLocation();

    // Following a link inside the drawer should reveal the page it led to, not leave
    // the drawer sitting on top of it.
    useEffect(() => setNavOpen(false), [pathname]);

    return (
        <div className="app-layout">
            <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />

            <div className="main-content">
                <Header onMenuToggle={() => setNavOpen(v => !v)} navOpen={navOpen} />

                <main className="flex-1 pb-8">
                    <Routes>
                        <Route path="/" element={<Dashboard />} />
                        <Route path="/accounts" element={<Accounts />} />
                        <Route path="/transactions" element={<Transactions />} />
                        <Route path="/transfers" element={<Transfers />} />
                        <Route path="/categories" element={<Categories />} />
                        <Route path="/currencies" element={<Currencies />} />
                        <Route path="/banks" element={<Banks />} />
                        {/* Any unknown signed-in path goes home rather than showing a blank shell. */}
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>
                </main>
            </div>
        </div>
    );
};

/**
 * Decides what the root of the site is.
 *
 * `/` is the one path that means two different things: the marketing page to a
 * visitor who has never signed in, and the dashboard to someone who has. Any other
 * path still requires a token and otherwise redirects to sign-in.
 */
const Root: React.FC = () => {
    const { pathname } = useLocation();

    if (!isAuthenticated()) {
        return pathname === '/' ? <Landing /> : <Navigate to="/login" replace />;
    }

    return <AppShell />;
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
