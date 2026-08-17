import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { LogOut, Menu, User as UserIcon } from 'lucide-react';
import { useT } from '../i18n';
import LanguageSwitcher from './LanguageSwitcher';
import ThemeToggle from './ThemeToggle';
import { NAV_ITEMS } from './Sidebar';

interface Props {
    onMenuToggle: () => void;
    navOpen: boolean;
}

/**
 * Reads the display name out of the stored user, falling back through the fields
 * that might be missing before giving up on the email's local part.
 */
function readUserName(): string | null {
    const raw = localStorage.getItem('user');
    if (!raw) return null;
    try {
        const user = JSON.parse(raw);
        if (user.firstName && user.lastName) return `${user.firstName} ${user.lastName}`;
        if (user.firstName) return user.firstName;
        if (user.email) return String(user.email).split('@')[0];
    } catch {
        // A corrupt entry is not worth failing the header over.
    }
    return null;
}

const Header: React.FC<Props> = ({ onMenuToggle, navOpen }) => {
    const navigate = useNavigate();
    const { pathname } = useLocation();
    const t = useT();
    const [userName, setUserName] = useState<string | null>(null);

    useEffect(() => setUserName(readUserName()), []);

    // The header used to be blank on the left while every page rendered its own
    // title; taking it from the nav table means the two can never disagree.
    const current = NAV_ITEMS.find(item => item.to === pathname);

    const handleLogout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        navigate('/login');
    };

    return (
        <header className="header">
            <div className="flex min-w-0 items-center gap-3">
                <button
                    className="btn btn-ghost btn-icon nav-toggle"
                    onClick={onMenuToggle}
                    aria-label={navOpen ? t('nav.closeMenu') : t('nav.openMenu')}
                    aria-expanded={navOpen}
                >
                    <Menu size={18} />
                </button>

                {current && <h1 className="header-title truncate">{t(current.labelKey)}</h1>}
            </div>

            <div className="flex items-center gap-2">
                <LanguageSwitcher />
                <ThemeToggle />

                <div className="user-chip">
                    <span className="user-chip-avatar">
                        <UserIcon size={15} />
                    </span>
                    <span className="user-chip-name">{userName ?? t('header.user')}</span>
                </div>

                <button
                    className="btn btn-ghost btn-icon border-transparent transition-colors hover:!border-bad/30 hover:!bg-bad/10 hover:!text-bad"
                    onClick={handleLogout}
                    title={t('header.signOut')}
                    aria-label={t('header.signOut')}
                >
                    <LogOut size={17} />
                </button>
            </div>
        </header>
    );
};

export default Header;
