import Decimal from 'decimal.js';

// Half away from zero, the rounding every money path in the Kotlin client uses. Precision
// is generous so a division inside the calculator never loses a digit that rounding needs.
Decimal.set({ rounding: Decimal.ROUND_HALF_UP, precision: 40 });

export type Money = { amount: Decimal; currency: string };

/**
 * Every amount arrives from the server as a JSON string, precisely so it never touches a
 * double. Anything unparseable is zero rather than an exception: a row with a broken
 * amount should still render.
 */
export function parseAmount(s: string | null | undefined): Decimal {
    if (s == null || s === '') return new Decimal(0);
    try {
        return new Decimal(s);
    } catch {
        return new Decimal(0);
    }
}

export const money = (amount: string | Decimal | null | undefined, currency: string): Money => ({
    amount: amount instanceof Decimal ? amount : parseAmount(amount),
    currency,
});

export const roundTo = (d: Decimal, decimals: number): Decimal =>
    d.toDecimalPlaces(Math.max(0, decimals), Decimal.ROUND_HALF_UP);

/** The wire format: a fixed scale, so "12.50" never goes out as "12.5". */
export const toFixedString = (d: Decimal, decimals: number): string =>
    roundTo(d, decimals).toFixed(Math.max(0, decimals));

/** Display form with no trailing zeros: 12.50 -> "12.5", 12.00 -> "12". */
export const toTrimmedString = (d: Decimal): string => d.toFixed();

export const sumDecimals = (items: Decimal[]): Decimal =>
    items.reduce((a, b) => a.plus(b), new Decimal(0));
