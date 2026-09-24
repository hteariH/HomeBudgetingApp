import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

/** What is on screen. */
export type Theme = 'dark' | 'light';

/** What the user chose. `system` follows the OS, and is what an unset preference means. */
export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'theme';

/**
 * The stored choice. The Compose web client wrote the same key in uppercase
 * (SYSTEM / LIGHT / DARK), so both spellings are read - a user who picked light there
 * keeps light here.
 */
export function readThemeMode(): ThemeMode {
    try {
        const stored = (localStorage.getItem(STORAGE_KEY) ?? '').toLowerCase();
        if (stored === 'light' || stored === 'dark') return stored;
    } catch {
        /* Storage unavailable: follow the OS. */
    }
    return 'system';
}

const systemTheme = (): Theme =>
    window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';

interface ThemeValue {
    /** What is on screen. */
    theme: Theme;
    mode: ThemeMode;
    setMode: (mode: ThemeMode) => void;
    /** The header's one-tap switch: commits to the side opposite what is showing. */
    toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeValue | null>(null);

/**
 * Theme lives in a context rather than in per-component state because more than one
 * place needs to *read* it, not just toggle it: the dashboard charts resolve their
 * own colours, and with independent useState hooks they would keep the old palette
 * until something else re-rendered them.
 */
export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [mode, setModeState] = useState<ThemeMode>(readThemeMode);
    const [system, setSystem] = useState<Theme>(systemTheme);
    const theme: Theme = mode === 'system' ? system : mode;

    // Only matters while following the OS, but cheap enough to keep listening always.
    useEffect(() => {
        const query = window.matchMedia?.('(prefers-color-scheme: light)');
        if (!query) return;
        const onChange = () => setSystem(query.matches ? 'light' : 'dark');
        query.addEventListener('change', onChange);
        return () => query.removeEventListener('change', onChange);
    }, []);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
    }, [theme]);

    const setMode = useCallback((next: ThemeMode) => {
        setModeState(next);
        try {
            localStorage.setItem(STORAGE_KEY, next);
        } catch {
            /* Preference simply does not persist. */
        }
    }, []);

    const toggleTheme = useCallback(() => setMode(theme === 'dark' ? 'light' : 'dark'), [setMode, theme]);

    const value = useMemo(() => ({ theme, mode, setMode, toggleTheme }), [theme, mode, setMode, toggleTheme]);

    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};

export function useTheme(): ThemeValue {
    const ctx = useContext(ThemeContext);
    if (!ctx) throw new Error('useTheme must be used inside <ThemeProvider>');
    return ctx;
}

export interface ChartColors {
    axis: string;
    grid: string;
    tooltipBg: string;
    tooltipBorder: string;
    text: string;
    income: string;
    expense: string;
}

/** Categorical palette for the pie slices. Fixed hues - they are data, not chrome. */
export const CATEGORY_COLORS = [
    '#6366F1', '#10B981', '#F59E0B', '#EF4444',
    '#06B6D4', '#8B5CF6', '#EC4899', '#14B8A6',
];

const readToken = (name: string, fallback: string): string => {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
};

/**
 * Recharts takes colours as props, so it cannot use CSS variables the way the rest of
 * the UI does. Resolving the tokens here - and re-resolving whenever the theme flips -
 * is what stops the charts from staying dark-on-white in light mode.
 */
export function useChartColors(): ChartColors {
    const { theme } = useTheme();
    const isDark = theme === 'dark';

    // Recomputed whenever the theme flips, which is when the tokens behind these
    // values change. The fallbacks are per-theme for the same reason: a dark default
    // would be unreadable if the variable ever failed to resolve in light mode.
    return useMemo(
        () => ({
            axis: readToken('--text-muted', isDark ? '#555D7C' : '#5A6779'),
            grid: readToken('--border', isDark ? '#2D3448' : '#C3CCDA'),
            tooltipBg: readToken('--bg-card', isDark ? '#1E2330' : '#FFFFFF'),
            tooltipBorder: readToken('--border', isDark ? '#2D3448' : '#C3CCDA'),
            text: readToken('--text-primary', isDark ? '#F0F2F8' : '#0F172A'),
            // Per-theme like the rest now: --green and --red are themselves overridden
            // in light mode, so a single fallback would hand the chart the dark palette's
            // pastels on white in exactly the case the fallback exists for.
            income: readToken('--green', isDark ? '#10B981' : '#047857'),
            expense: readToken('--red', isDark ? '#EF4444' : '#C81E1E'),
        }),
        [isDark],
    );
}
