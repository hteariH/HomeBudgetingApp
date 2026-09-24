import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Menu, Moon, Plus, Sun } from 'lucide-react';
import { useT } from '../../i18n';
import { useTheme } from '../../theme';
import { AppRoutes } from '../lib/routes';
import { AccountMenu } from './AccountMenu';
import { AppDrawer } from './AppDrawer';
import { BrandMark } from './BrandMark';
import { HEADER_SECTIONS, sectionOf } from './sections';
import { BREAKPOINTS, useMinWidth } from './useMediaQuery';

/**
 * The site header: brand, sections, and the account - the frame a website wears, rather
 * than a phone's rail and bottom bar. Port of AppHeader.kt.
 */
export const AppHeader: React.FC<{ email: string | null; onSignOut: () => void }> = ({ email, onSignOut }) => {
    const t = useT();
    const { theme, toggleTheme } = useTheme();
    const { pathname } = useLocation();
    const navInline = useMinWidth(BREAKPOINTS.headerNav);
    const wordmark = useMinWidth(BREAKPOINTS.wordmark);
    const inlineEntry = useMinWidth(BREAKPOINTS.inlineEntry);
    const [drawer, setDrawer] = useState(false);
    const current = sectionOf(pathname);

    // Widening the window puts the sections back in the header; a drawer left open over
    // them would be covering navigation with the same navigation.
    useEffect(() => {
        if (navInline) setDrawer(false);
    }, [navInline]);

    return (
        <>
            <header className="sticky top-0 z-40 border-b border-line bg-surface/95 backdrop-blur">
                <div className={`flex h-[60px] items-center gap-1 ${navInline ? 'px-6' : 'px-3'}`}>
                    {!navInline && (
                        <button type="button" className="btn btn-ghost btn-icon border-0" onClick={() => setDrawer(true)} aria-label={t('nav_open_menu')}>
                            <Menu size={20} />
                        </button>
                    )}
                    <BrandMark showWordmark={wordmark} />
                    {navInline ? (
                        // Scrolls rather than wraps: Ukrainian labels are longer, and a header
                        // that grows a second row shifts the whole page as the window resizes.
                        <nav className="ml-3 flex min-w-0 flex-1 items-center gap-0.5 overflow-x-auto">
                            {HEADER_SECTIONS.map(s => (
                                <Link
                                    key={s.path}
                                    to={s.path}
                                    className={`whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                                        current === s.path ? 'bg-brand/15 text-brand-light' : 'text-ink-soft hover:text-ink'
                                    }`}
                                >
                                    {t(s.label)}
                                </Link>
                            ))}
                        </nav>
                    ) : (
                        <div className="flex-1" />
                    )}
                    {/* Below the inline-entry width no page carries the entry form, so the
                        header offers it instead. */}
                    {!inlineEntry && (
                        <Link
                            to={AppRoutes.quickAdd('EXPENSE')}
                            className="btn btn-primary btn-icon mr-1 rounded-full"
                            aria-label={t('action_add_expense_fab')}
                            title={t('action_add_expense_fab')}
                        >
                            <Plus size={18} />
                        </Link>
                    )}
                    <button
                        type="button"
                        className="btn btn-ghost btn-icon border-0"
                        onClick={toggleTheme}
                        aria-label={t(theme === 'dark' ? 'header_to_light' : 'header_to_dark')}
                        title={t(theme === 'dark' ? 'header_to_light' : 'header_to_dark')}
                    >
                        {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
                    </button>
                    <AccountMenu email={email} showName={navInline} onSignOut={onSignOut} />
                </div>
            </header>
            {!navInline && <AppDrawer open={drawer} current={current} onClose={() => setDrawer(false)} />}
        </>
    );
};
