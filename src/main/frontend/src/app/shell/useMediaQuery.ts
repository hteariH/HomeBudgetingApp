import { useSyncExternalStore } from 'react';

/**
 * Widths at which the frame changes shape, carried over from the Compose web client
 * (PersonalMoneyNavHost.kt and PageWidth.kt) so the layout breaks where it always did.
 */
export const BREAKPOINTS = {
    /** Sections across the header; below this they fold into a drawer. */
    headerNav: 1240,
    /** Where a transaction form fits inline beside the page, instead of a "+" in the header. */
    inlineEntry: 900,
    /** The product name beside the mark; below this it would push the account chip off. */
    wordmark: 480,
} as const;

export function useMediaQuery(query: string): boolean {
    return useSyncExternalStore(
        onChange => {
            const list = window.matchMedia(query);
            list.addEventListener('change', onChange);
            return () => list.removeEventListener('change', onChange);
        },
        () => window.matchMedia(query).matches,
        () => false,
    );
}

export const useMinWidth = (px: number) => useMediaQuery(`(min-width: ${px}px)`);
