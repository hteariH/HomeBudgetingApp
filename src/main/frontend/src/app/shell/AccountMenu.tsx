import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, Settings, User } from 'lucide-react';
import { useT } from '../../i18n';
import { AppRoutes } from '../lib/routes';

/**
 * Who is signed in, and the things one does about it. Settings is here because this is
 * where a site keeps it - and it is why the header's own row has no Settings link.
 */
export const AccountMenu: React.FC<{ email: string | null; showName: boolean; onSignOut: () => void }> = ({
    email,
    showName,
    onSignOut,
}) => {
    const t = useT();
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const root = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;
        const onDown = (e: MouseEvent) => {
            if (!root.current?.contains(e.target as Node)) setOpen(false);
        };
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setOpen(false);
        };
        document.addEventListener('mousedown', onDown);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onDown);
            document.removeEventListener('keydown', onKey);
        };
    }, [open]);

    const go = (path: string) => {
        setOpen(false);
        navigate(path);
    };

    const item = 'flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-raised';

    return (
        <div className="relative" ref={root}>
            <button
                type="button"
                onClick={() => setOpen(o => !o)}
                aria-haspopup="menu"
                aria-expanded={open}
                aria-label={showName ? undefined : t('header_user')}
                className={`flex items-center gap-2 rounded-full border border-line bg-raised/60 p-1 transition-colors hover:border-line-strong ${showName ? 'pr-3.5' : ''}`}
            >
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-[#6366F1] to-[#A855F7] text-white">
                    <User size={16} />
                </span>
                {showName && (
                    // The local part, as the old header showed it; the whole address is in the menu.
                    <span className="max-w-[160px] truncate text-sm font-medium text-ink">
                        {email?.split('@')[0] ?? t('header_user')}
                    </span>
                )}
            </button>
            {open && (
                <div role="menu" className="absolute right-0 top-[calc(100%+6px)] z-50 min-w-[220px] rounded-token border border-line bg-card p-1.5 shadow-token-lg">
                    {email && <div className="truncate px-3 py-2 text-xs text-ink-dim">{email}</div>}
                    <div className="my-1 border-t border-line" />
                    <button role="menuitem" className={`${item} text-ink-soft hover:text-ink`} onClick={() => go(AppRoutes.settings)}>
                        <Settings size={16} /> {t('dashboard_settings')}
                    </button>
                    <button
                        role="menuitem"
                        className={`${item} text-bad`}
                        onClick={() => {
                            setOpen(false);
                            onSignOut();
                        }}
                    >
                        <LogOut size={16} /> {t('header_sign_out')}
                    </button>
                </div>
            )}
        </div>
    );
};
