import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { en } from './locales/en';
import { ru } from './locales/ru';
import { uk } from './locales/uk';

/**
 * A small purpose-built translation layer instead of react-i18next.
 *
 * Everything from i18next v23 on declares a hard `typescript: ^5` peer, and CRA 5
 * pins TypeScript to 4.9 - installing the pair that still resolves means pinning
 * i18next 22 / react-i18next 12, both from 2023. This module covers what the app
 * actually needs (lookup, interpolation, plural forms, detection) in a fraction of
 * the size, and leans on the platform's own Intl for the hard part.
 */

export const LANGUAGES = [
    { code: 'en', label: 'English', flag: '🇬🇧' },
    { code: 'ru', label: 'Русский', flag: '🇷🇺' },
    { code: 'uk', label: 'Українська', flag: '🇺🇦' },
] as const;

export type Lang = (typeof LANGUAGES)[number]['code'];

const LANG_CODES = LANGUAGES.map(l => l.code) as readonly Lang[];
const FALLBACK: Lang = 'en';
const STORAGE_KEY = 'lang';

/**
 * English is the source of truth for the key set: the other locales are typed
 * against it, so a missing translation is a compile error rather than a key
 * leaking into the UI. They may add keys on top - Russian and Ukrainian need
 * `_few` / `_many` plural forms that English has no use for.
 */
export type TranslationKey = keyof typeof en;
export type Locale = Record<TranslationKey, string> & Record<string, string>;

// Widened to a plain string map on purpose: lookup happens with runtime-built keys
// (`${key}_${pluralForm}`), which a strict key union would reject.
const RESOURCES: Record<Lang, Record<string, string>> = { en, ru, uk };

/** Values allowed in an interpolation payload. */
export type TVars = Record<string, string | number>;

const isLang = (value: string): value is Lang => (LANG_CODES as readonly string[]).includes(value);

/**
 * Resolution order: an explicit earlier choice, then whatever the browser asks
 * for, then English. Browser tags are matched on the base subtag so that
 * `ru-RU`, `uk-UA` or `en-GB` all land somewhere sensible.
 */
export function detectLang(): Lang {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved && isLang(saved)) return saved;
    } catch {
        /* Storage can be unavailable (private mode, blocked cookies). */
    }

    const candidates = navigator.languages?.length ? navigator.languages : [navigator.language];
    for (const tag of candidates) {
        const base = tag?.toLowerCase().split('-')[0];
        if (base && isLang(base)) return base;
    }

    return FALLBACK;
}

/**
 * Plural form selection, delegated to Intl so Russian and Ukrainian get their
 * real one/few/many split rather than the English two-form approximation
 * (1 счёт, 2 счёта, 5 счётов).
 */
const pluralRules = new Map<Lang, Intl.PluralRules>();

function pluralSuffix(lang: Lang, count: number): string {
    let rules = pluralRules.get(lang);
    if (!rules) {
        rules = new Intl.PluralRules(lang);
        pluralRules.set(lang, rules);
    }
    return rules.select(count);
}

function interpolate(template: string, vars?: TVars): string {
    if (!vars) return template;
    return template.replace(/\{\{(\w+)\}\}/g, (match, name: string) =>
        name in vars ? String(vars[name]) : match,
    );
}

/**
 * Looks a key up in the active locale, falling back to English and finally to the
 * key itself, so a gap degrades to readable text instead of a blank element.
 *
 * When `vars.count` is present the plural variants are tried first:
 * `key_one` / `key_few` / `key_many` / `key_other` before plain `key`.
 */
export function translate(lang: Lang, key: string, vars?: TVars): string {
    const dict = RESOURCES[lang];
    const fallbackDict = RESOURCES[FALLBACK];

    const attempts: string[] = [];
    if (typeof vars?.count === 'number') {
        attempts.push(`${key}_${pluralSuffix(lang, vars.count)}`, `${key}_other`);
    }
    attempts.push(key);

    for (const candidate of attempts) {
        const hit = dict[candidate] ?? fallbackDict[candidate];
        if (hit !== undefined) return interpolate(hit, vars);
    }

    if (process.env.NODE_ENV === 'development') {
        console.warn(`[i18n] missing translation for "${key}" (${lang})`);
    }
    return key;
}

export type TFunction = (key: TranslationKey | string, vars?: TVars) => string;

interface I18nValue {
    lang: Lang;
    setLang: (lang: Lang) => void;
    t: TFunction;
}

const I18nContext = createContext<I18nValue | null>(null);

export const I18nProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [lang, setLangState] = useState<Lang>(detectLang);

    useEffect(() => {
        // Keeps assistive tech and the browser's own hyphenation in step with the UI.
        document.documentElement.setAttribute('lang', lang);
        // index.html can only carry one static title; this makes the tab match the
        // language the visitor is actually reading.
        document.title = translate(lang, 'meta.title');
    }, [lang]);

    const setLang = useCallback((next: Lang) => {
        setLangState(next);
        try {
            localStorage.setItem(STORAGE_KEY, next);
        } catch {
            /* Preference simply does not persist. */
        }
    }, []);

    const t = useCallback<TFunction>((key, vars) => translate(lang, key, vars), [lang]);

    const value = useMemo(() => ({ lang, setLang, t }), [lang, setLang, t]);

    return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
};

export function useI18n(): I18nValue {
    const ctx = useContext(I18nContext);
    if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
    return ctx;
}

/** Shorthand for the common case of only needing the translate function. */
export function useT(): TFunction {
    return useI18n().t;
}
