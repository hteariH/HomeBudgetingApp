import { FX_HUB, isPegged, type FxRateInput } from '../../lib/fx';

/** A typed rate, accepting the comma a Ukrainian or Russian keyboard puts under the thumb. */
export function parseRate(text: string): number | null {
    const value = Number(text.trim().replace(',', '.'));
    return text.trim() !== '' && Number.isFinite(value) && value > 0 ? value : null;
}

/** 1.0 reads better as "1"; anything else keeps every digit the user might have typed. */
export const formatRate = (value: number): string => (Number.isInteger(value) ? String(value) : String(value));

/**
 * A rate is worth asking for unless the currency already has one that cannot be wrong:
 * the hub is 1 by definition, and a dollar stablecoin is pegged.
 */
export const handRateOffered = (code: string): boolean =>
    code.trim() !== '' && code !== FX_HUB && !isPegged(code);

/**
 * The rate the user set by hand, if any. The server sends overrides down the same list as
 * the fetched rates; a pair quoted *into* USD is only ever one of theirs, since the nightly
 * job quotes from USD.
 */
export const handRateFor = (rates: FxRateInput[], code: string): number | null =>
    rates.find(r => r.baseCode === code.toUpperCase() && r.quoteCode === FX_HUB)?.rate ?? null;
