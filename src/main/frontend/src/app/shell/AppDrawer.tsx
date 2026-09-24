import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import { useT } from '../../i18n';
import { BrandMark } from './BrandMark';
import { DRAWER_SECTIONS } from './sections';

/**
 * The sections, for a window too narrow to wear them across the top. Same list and same
 * highlighting as the header - only the shape differs.
 */
export const AppDrawer: React.FC<{ open: boolean; current: string; onClose: () => void }> = ({ open, current, onClose }) => {
    const t = useT();

    useEffect(() => {
        if (!open) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [open, onClose]);

    return (
        <div className={`fixed inset-0 z-50 ${open ? '' : 'pointer-events-none'}`} aria-hidden={!open}>
            <div
                className={`absolute inset-0 bg-black/50 transition-opacity ${open ? 'opacity-100' : 'opacity-0'}`}
                onClick={onClose}
            />
            <nav
                className={`absolute inset-y-0 left-0 flex w-[300px] max-w-[85vw] flex-col border-r border-line bg-surface shadow-token-lg transition-transform ${open ? 'translate-x-0' : '-translate-x-full'}`}
            >
                <div className="flex h-[60px] items-center border-b border-line pl-5 pr-2">
                    <BrandMark showWordmark onClick={onClose} />
                    <div className="flex-1" />
                    <button type="button" className="btn btn-ghost btn-icon border-0" onClick={onClose} aria-label={t('nav_close_menu')}>
                        <X size={20} />
                    </button>
                </div>
                <div className="px-7 pb-1 pt-4 text-xs font-medium text-ink-dim">{t('nav_menu')}</div>
                <div className="flex-1 overflow-y-auto px-3 pb-4">
                    {DRAWER_SECTIONS.map(s => {
                        const selected = current === s.path;
                        const Icon = s.icon;
                        return (
                            <Link
                                key={s.path}
                                to={s.path}
                                onClick={onClose}
                                className={`my-0.5 flex items-center gap-3 rounded-full px-4 py-3 text-sm font-medium transition-colors ${
                                    selected ? 'bg-brand/15 text-brand-light' : 'text-ink-soft hover:bg-raised hover:text-ink'
                                }`}
                            >
                                <Icon size={20} />
                                {t(s.label)}
                            </Link>
                        );
                    })}
                </div>
            </nav>
        </div>
    );
};
