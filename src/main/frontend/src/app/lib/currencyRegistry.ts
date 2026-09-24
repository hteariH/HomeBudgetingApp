export interface CurrencyInfo {
    code: string;
    symbol: string;
    decimals: number;
}

// Zero, three and eight-decimal codes a user may meet without having configured them.
// Mirrors CurrencyRegistry.kt; everything else defaults to 2.
const FALLBACK_DECIMALS: Record<string, number> = {
    JPY: 0, KRW: 0, VND: 0, CLP: 0, ISK: 0, HUF: 0, PYG: 0, RWF: 0, UGX: 0, VUV: 0,
    XAF: 0, XOF: 0, XPF: 0, KMF: 0, DJF: 0, GNF: 0, BIF: 0,
    BHD: 3, IQD: 3, JOD: 3, KWD: 3, LYD: 3, OMR: 3, TND: 3,
    BTC: 8, ETH: 8,
};

let snapshot = new Map<string, CurrencyInfo>();

/**
 * The user's own currencies, read synchronously by the money helpers, so a code like BTC
 * gets the symbol and precision the user chose rather than a guess. Replaced wholesale
 * whenever the currency list loads.
 */
export const CurrencyRegistry = {
    update(list: CurrencyInfo[]) {
        snapshot = new Map(list.map(c => [c.code, c]));
    },
    lookup: (code: string): CurrencyInfo | undefined => snapshot.get(code),
    decimalsOrFallback: (code: string): number =>
        snapshot.get(code)?.decimals ?? FALLBACK_DECIMALS[code.toUpperCase()] ?? 2,
};
