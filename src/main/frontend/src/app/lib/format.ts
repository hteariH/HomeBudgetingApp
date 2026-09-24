import { CurrencyRegistry } from './currencyRegistry';
import type { Money } from './money';

// Follows the interface language; I18nProvider sets it whenever the language changes.
// Undefined means "the browser's own locale", which is what the wasm client always used.
let locale: string | undefined;

export const setFormatLocale = (l: string | undefined) => {
    locale = l;
};

/**
 * Intl takes a Number, so the amount leaves Decimal only here, at the point of display.
 * A double holds any real balance exactly, and Intl rounds to the decimals anyway.
 */
const number = (m: Money, decimals: number) =>
    new Intl.NumberFormat(locale, { minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(
        m.amount.toNumber(),
    );

/**
 * Money as the user's locale writes it. Port of MoneyFormatter.wasmJs.kt: the symbol the
 * user configured first, then Intl's own for an ISO code, then the raw code, so the value
 * is never ambiguous.
 */
export function formatMoney(m: Money): string {
    const info = CurrencyRegistry.lookup(m.currency);
    if (info) return `${info.symbol} ${number(m, info.decimals)}`;
    const decimals = CurrencyRegistry.decimalsOrFallback(m.currency);
    try {
        return new Intl.NumberFormat(locale, {
            style: 'currency',
            currency: m.currency,
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals,
        }).format(m.amount.toNumber());
    } catch {
        return `${m.currency} ${number(m, decimals)}`;
    }
}

/** Digits only - for inputs and places where the currency is already on screen. */
export const formatPlain = (m: Money): string => number(m, CurrencyRegistry.decimalsOrFallback(m.currency));

/**
 * "Feb 3, 2026" / "3 лют. 2026 р.". Built and read in UTC: a bare date has no zone, and
 * letting the local one in would move it a day for anyone west of Greenwich.
 */
export function formatMediumDate(iso: string): string {
    const [y, mo, d] = iso.slice(0, 10).split('-').map(Number);
    return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
        new Date(Date.UTC(y, mo - 1, d)),
    );
}

/** Jan / янв. / січ. - `month1` is 1-based. */
export const shortMonthName = (month1: number): string =>
    new Intl.DateTimeFormat(locale, { month: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(2000, month1 - 1, 1)));

export const longMonthYear = (year: number, month1: number): string =>
    new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
        new Date(Date.UTC(year, month1 - 1, 1)),
    );
