import Decimal from 'decimal.js';
import { CurrencyRegistry } from './currencyRegistry';
import { roundTo, type Money } from './money';

export interface FxRateInput {
    baseCode: string;
    quoteCode: string;
    rate: number;
}

/** The currency every triangulated conversion hops through. */
export const FX_HUB = 'USD';

/**
 * Currencies whose rate is a definition rather than a quote. One USDT is one dollar by
 * construction, and no rates feed carries it - so without this an imported crypto account
 * silently drops out of every total. Only dollar-pegged coins belong here; BTC and ETH have
 * a real, moving price and stay unconvertible until someone sets a rate by hand.
 */
export const FX_PEGS: Record<string, number> = { USDT: 1, USDC: 1 };

export const isPegged = (code: string) => code.toUpperCase() in FX_PEGS;

// Only positive, finite rates are trusted: a 0 or NaN would turn into 0 or Infinity and
// poison every conversion downstream.
const usable = (v: number | undefined): v is number => v !== undefined && Number.isFinite(v) && v > 0;

/**
 * A synchronous snapshot of the stored rates, for conversions inside render. Port of
 * FxSnapshot in CurrencyConverter.kt: direct, then inverse, then through USD.
 */
export class FxSnapshot {
    private constructor(private readonly byPair: Map<string, number>) {}

    static readonly EMPTY = new FxSnapshot(new Map());

    /**
     * The stored rates, with the pegs filling the gaps no feed can. A peg is dropped the
     * moment any stored rate mentions the same currency: a hand-set rate outranks the default.
     */
    static from(rates: FxRateInput[]): FxSnapshot {
        const pairs = new Map<string, number>();
        const quoted = new Set<string>();
        for (const r of rates) {
            if (!usable(r.rate)) continue;
            pairs.set(`${r.baseCode}>${r.quoteCode}`, r.rate);
            quoted.add(r.baseCode);
            quoted.add(r.quoteCode);
        }
        for (const [code, v] of Object.entries(FX_PEGS)) {
            if (!quoted.has(code)) pairs.set(`${code}>${FX_HUB}`, v);
        }
        return new FxSnapshot(pairs);
    }

    private direct(base: string, quote: string): number | null {
        const v = this.byPair.get(`${base}>${quote}`);
        return usable(v) ? v : null;
    }

    private inverse(base: string, quote: string): number | null {
        const v = this.byPair.get(`${quote}>${base}`);
        return usable(v) ? 1 / v : null;
    }

    rate(base: string, quote: string): number | null {
        if (base === quote) return 1;
        const d = this.direct(base, quote) ?? this.inverse(base, quote);
        if (d !== null) return d;
        const toHub = this.direct(base, FX_HUB) ?? this.inverse(base, FX_HUB);
        const fromHub = this.direct(FX_HUB, quote) ?? this.inverse(FX_HUB, quote);
        return toHub !== null && fromHub !== null ? toHub * fromHub : null;
    }

    convert(m: Money, target: string): Money | null {
        const r = this.rate(m.currency, target);
        if (r === null) return null;
        return {
            amount: roundTo(m.amount.times(new Decimal(r)), CurrencyRegistry.decimalsOrFallback(target)),
            currency: target,
        };
    }
}
