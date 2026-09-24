import { useQuery } from '@tanstack/react-query';
import { useMemo, useSyncExternalStore } from 'react';
import { prefs } from '../prefs';
import { CurrencyRegistry } from '../lib/currencyRegistry';
import { FxSnapshot, type FxRateInput } from '../lib/fx';
import { endpoints } from './endpoints';
import { qk } from './queryClient';
import type { CategoryType } from './types';

/**
 * The currency list. Loading it also feeds CurrencyRegistry, because money formatting
 * reads decimals and symbols from there synchronously and would otherwise fall back to
 * two decimals, and to Intl's own symbol, for a currency the user holds. The registry is
 * filled inside the fetch rather than in an effect: an effect runs after the screens have
 * already rendered their amounts, and a registry change re-renders nothing.
 */
export function useCurrencies() {
    return useQuery({
        queryKey: qk.currencies,
        queryFn: async () => {
            const list = await endpoints.currencies();
            CurrencyRegistry.update(list.map(c => ({ code: c.code, symbol: c.symbol, decimals: c.decimals })));
            return list;
        },
        staleTime: 5 * 60_000,
    });
}

/**
 * The rates the server already fetched - one request a day for the whole install rather
 * than one per tab. A rate that will not parse is dropped, not zeroed: FxSnapshot can then
 * triangulate or report it cannot convert, where 0 would silently erase the amount.
 */
export function useFxRates() {
    return useQuery({
        queryKey: qk.fx,
        queryFn: async (): Promise<FxRateInput[]> =>
            (await endpoints.fxRates())
                .map(r => ({ baseCode: r.baseCode, quoteCode: r.quoteCode, rate: Number(r.rate) }))
                .filter(r => Number.isFinite(r.rate) && r.rate > 0),
        staleTime: 5 * 60_000,
    });
}

export function useFxSnapshot(): FxSnapshot {
    const { data } = useFxRates();
    return useMemo(() => (data ? FxSnapshot.from(data) : FxSnapshot.from([])), [data]);
}

export const useAccounts = () => useQuery({ queryKey: qk.accounts, queryFn: endpoints.accounts });

export const useCategories = (type?: CategoryType) =>
    useQuery({
        queryKey: [...qk.categories, type ?? 'ALL'],
        queryFn: () => endpoints.categories(type),
    });

export const useMe = () => useQuery({ queryKey: qk.me, queryFn: endpoints.me, staleTime: 5 * 60_000 });

/** The currency every total is shown in. See prefs.baseCurrency. */
export function useBaseCurrency(): string {
    return useSyncExternalStore(prefs.subscribe, () => prefs.baseCurrency, () => 'USD');
}

/** Any preference, re-read whenever one is written. */
export function usePref<T>(read: () => T): T {
    return useSyncExternalStore(prefs.subscribe, read, read);
}
