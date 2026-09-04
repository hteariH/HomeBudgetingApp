import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

export type Theme = 'dark' | 'light';

const STORAGE_KEY = 'theme';

/**
 * Reads whatever the inline script in index.html already put on <html>, so the
 * provider agrees with what is on screen instead of re-deciding and causing a flash.
 */
function initialTheme(): Theme {
    return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
}

interface ThemeValue {
    theme: Theme;
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
    const [theme, setTheme] = useState<Theme>(initialTheme);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
    }, [theme]);

    const toggleTheme = useCallback(() => {
        setTheme(prev => {
            const next: Theme = prev === 'dark' ? 'light' : 'dark';
            try {
                localStorage.setItem(STORAGE_KEY, next);
            } catch {
                /* Preference simply does not persist. */
            }
            return next;
        });
    }, []);

    const value = useMemo(() => ({ theme, toggleTheme }), [theme, toggleTheme]);

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
