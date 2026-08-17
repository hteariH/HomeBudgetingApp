import React from 'react';
import { NavLink } from 'react-router-dom';
import {
    LayoutDashboard, Wallet, Tag,
    Receipt, Globe, TrendingUp, ArrowLeftRight, Building2, X,
} from 'lucide-react';
import { useT } from '../i18n';

/** Route table shared with Header, which uses it to title the current page. */
export const NAV_ITEMS = [
    { to: '/', icon: LayoutDashboard, labelKey: 'nav.dashboard' },
    { to: '/accounts', icon: Wallet, labelKey: 'nav.accounts' },
    { to: '/transactions', icon: Receipt, labelKey: 'nav.transactions' },
    { to: '/transfers', icon: ArrowLeftRight, labelKey: 'nav.transfers' },
    { to: '/categories', icon: Tag, labelKey: 'nav.categories' },
    { to: '/currencies', icon: Globe, labelKey: 'nav.currencies' },
    { to: '/banks', icon: Building2, labelKey: 'nav.banks' },
] as const;

interface Props {
    /** Only meaningful below the 900px breakpoint, where the sidebar is a drawer. */
    open: boolean;
    onClose: () => void;
}

const Sidebar: React.FC<Props> = ({ open, onClose }) => {
    const t = useT();

    return (
        <>
            {/* Rendered only while the drawer is open; CSS hides it above the breakpoint,
                where the sidebar is permanent and nothing should be dimmed. */}
            {open && <button className="sidebar-backdrop" aria-label={t('nav.closeMenu')} onClick={onClose} />}

            <aside className={`sidebar${open ? ' open' : ''}`}>
                <div className="sidebar-logo">
                    <div className="sidebar-logo-icon">
                        <TrendingUp size={18} color="#fff" strokeWidth={2.5} />
                    </div>
                    <div className="sidebar-logo-text">
                        Personal<span>Money</span>
                    </div>

                    <button
                        className="btn btn-ghost btn-icon btn-sm nav-close"
                        onClick={onClose}
                        aria-label={t('nav.closeMenu')}
                    >
                        <X size={16} />
                    </button>
                </div>

                <nav className="sidebar-nav">
                    <p className="sidebar-section-label">{t('nav.menu')}</p>
                    {NAV_ITEMS.map(({ to, icon: Icon, labelKey }) => (
                        <NavLink
                            key={to}
                            to={to}
                            end={to === '/'}
                            className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
                        >
                            <Icon size={16} />
                            {t(labelKey)}
                        </NavLink>
                    ))}
                </nav>
            </aside>
        </>
    );
};

export default Sidebar;
