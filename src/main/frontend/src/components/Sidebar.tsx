import React from 'react';
import { NavLink } from 'react-router-dom';
import {
    LayoutDashboard, Wallet, Tag,
    Receipt, Globe, TrendingUp, ArrowLeftRight, Download
} from 'lucide-react';

const navItems = [
    { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/accounts', icon: Wallet, label: 'Accounts' },
    { to: '/transactions', icon: Receipt, label: 'Transactions' },
    { to: '/transfers', icon: ArrowLeftRight, label: 'Transfers' },
    { to: '/categories', icon: Tag, label: 'Categories' },
    { to: '/currencies', icon: Globe, label: 'Currencies' },
    { to: '/import', icon: Download, label: 'Import' },
];

const Sidebar: React.FC = () => (
    <aside className="sidebar">
        <div className="sidebar-logo">
            <div className="sidebar-logo-icon">
                <TrendingUp size={18} color="#fff" />
            </div>
            <div className="sidebar-logo-text">
                Home<span>Budget</span>
            </div>
        </div>

        <nav className="sidebar-nav">
            <p className="sidebar-section-label">Menu</p>
            {navItems.map(({ to, icon: Icon, label }) => (
                <NavLink
                    key={to}
                    to={to}
                    end={to === '/'}
                    className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
                >
                    <Icon size={16} />
                    {label}
                </NavLink>
            ))}
        </nav>
    </aside>
);

export default Sidebar;
