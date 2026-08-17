import React, { useEffect, useRef, useState } from 'react';
import { Check, Globe } from 'lucide-react';
import { Lang, LANGUAGES, useI18n } from '../i18n';

/**
 * Language picker shared by the app header and the landing page.
 *
 * A menu rather than a native <select> so the flag and the language's own name can
 * be shown together - a first-time visitor who cannot read the current interface
 * language needs to recognise their own by sight.
 */
const LanguageSwitcher: React.FC<{ align?: 'left' | 'right' }> = ({ align = 'right' }) => {
    const { lang, setLang, t } = useI18n();
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);

    // Dismiss on outside click and on Escape: the menu covers page content, so
    // leaving it open when attention has moved on is its own small annoyance.
    useEffect(() => {
        if (!open) return;

        const onPointerDown = (e: MouseEvent) => {
            if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
        };
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setOpen(false);
        };

        document.addEventListener('mousedown', onPointerDown);
        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('mousedown', onPointerDown);
            document.removeEventListener('keydown', onKeyDown);
        };
    }, [open]);

    const active = LANGUAGES.find(l => l.code === lang) ?? LANGUAGES[0];

    const choose = (code: Lang) => {
        setLang(code);
        setOpen(false);
    };

    return (
        <div className="lang-switcher" ref={rootRef}>
            <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setOpen(v => !v)}
                title={t('header.language')}
                aria-haspopup="menu"
                aria-expanded={open}
                aria-label={t('header.language')}
            >
                <Globe size={15} />
                <span className="uppercase tracking-wide">{active.code}</span>
            </button>

            {open && (
                <div className="lang-menu" role="menu" style={align === 'left' ? { left: 0, right: 'auto' } : undefined}>
                    {LANGUAGES.map(option => (
                        <button
                            key={option.code}
                            type="button"
                            role="menuitemradio"
                            aria-checked={option.code === lang}
                            className={`lang-option${option.code === lang ? ' active' : ''}`}
                            onClick={() => choose(option.code)}
                        >
                            <span aria-hidden className="text-base leading-none">{option.flag}</span>
                            <span className="flex-1">{option.label}</span>
                            {option.code === lang && <Check size={14} />}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

export default LanguageSwitcher;
