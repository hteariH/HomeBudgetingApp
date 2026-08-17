import { Currency } from './types';

/**
 * Money formatting driven by the currency's own scale.
 *
 * The old code assumed two decimals everywhere. A third of the catalog is not
 * two-decimal - JPY and KRW have no minor unit, the Gulf dinars have three, BTC has
 * eight - so a hardcoded `toFixed(2)` shows "¥1,234.00" for a currency that has no
 * fractional part, and silently truncates a BTC amount.
 *
 * The API sends amounts as decimal strings for the same reason: passing them through a
 * JSON number means passing them through a double.
 */
export function formatAmount(amount: number | string, currency: Currency): string {
    const decimals = currency.decimals ?? 2;
    const value = typeof amount === 'string' ? Number(amount) : amount;
    if (!Number.isFinite(value)) return '—';
    return value.toLocaleString(undefined, {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
    });
}

/** Amount with the currency's symbol, e.g. "$1,234.56" or "¥1,234". */
export function formatMoney(amount: number | string, currency: Currency): string {
    return `${currency.symbol}${formatAmount(amount, currency)}`;
}

/** Amount with the ISO code instead, for lists mixing several currencies. */
export function formatMoneyWithCode(amount: number | string, currency: Currency): string {
    return `${formatAmount(amount, currency)} ${currency.code}`;
}
