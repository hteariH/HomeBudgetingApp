import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Check, Globe } from 'lucide-react';
import { en } from './locales/en';
import { ru } from './locales/ru';
import { uk } from './locales/uk';

export const LANGUAGES = [
    { code: 'en', label: 'English', flag: '🇬🇧' },
    { code: 'ru', label: 'Русский', flag: '🇷🇺' },
    { code: 'uk', label: 'Українська', flag: '🇺🇦' },
] as const;

export type Lang = (typeof LANGUAGES)[number]['code'];
export type Key = keyof typeof en;

const DICTIONARIES: Record<Lang, Record<string, string>> = { en, ru, uk };
const STORAGE_KEY = 'lang';

const isLang = (value: string | null | undefined): value is Lang =>
    LANGUAGES.some(l => l.code === value);

/** A saved choice, then the browser's languages (matched on the base subtag), then English. */
function detectLang(): Lang {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (isLang(saved)) return saved;
    } catch {
        /* Storage can be unavailable (private mode, blocked cookies). */
    }
    for (const tag of navigator.languages ?? []) {
        const base = tag.toLowerCase().split('-')[0];
        if (isLang(base)) return base;
    }
    return 'en';
}

interface I18nValue {
    lang: Lang;
    setLang: (lang: Lang) => void;
    t: (key: Key) => string;
}

const I18nContext = createContext<I18nValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
    const [lang, setLangState] = useState<Lang>(detectLang);

    useEffect(() => {
        document.documentElement.setAttribute('lang', lang);
        document.title = DICTIONARIES[lang]['meta.title'] ?? en['meta.title'];
    }, [lang]);

    const setLang = (next: Lang) => {
        setLangState(next);
        try {
            localStorage.setItem(STORAGE_KEY, next);
        } catch {
            /* The choice simply does not persist. */
        }
    };

    const t = (key: Key) => DICTIONARIES[lang][key] ?? en[key];

    return <I18nContext.Provider value={{ lang, setLang, t }}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
    const ctx = useContext(I18nContext);
    if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
    return ctx;
}

export const useT = () => useI18n().t;

/**
 * A menu rather than a native select so the flag and the language's own name show together:
 * a visitor who cannot read the current language needs to recognise their own by sight.
 */
export function LanguageSwitcher() {
    const { lang, setLang, t } = useI18n();
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);

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
                <span className="uppercase tracking-wide">{lang}</span>
            </button>

            {open && (
                <div className="lang-menu" role="menu">
                    {LANGUAGES.map(option => (
                        <button
                            key={option.code}
                            type="button"
                            role="menuitemradio"
                            aria-checked={option.code === lang}
                            className={`lang-option${option.code === lang ? ' active' : ''}`}
                            onClick={() => {
                                setLang(option.code);
                                setOpen(false);
                            }}
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
}
