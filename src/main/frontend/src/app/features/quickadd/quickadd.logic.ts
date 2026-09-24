import Decimal from 'decimal.js';
import type { RestCategory, RestTransaction } from '../../api/types';
import { Calculator } from '../../lib/calculator';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import { parseIsoDate } from '../../lib/dates';
import type { FxSnapshot } from '../../lib/fx';
import { roundTo, toFixedString, toTrimmedString } from '../../lib/money';

export type EntryMode = 'EXPENSE' | 'INCOME' | 'TRANSFER';

export const parseEntryMode = (v: string | null): EntryMode =>
    v === 'INCOME' || v === 'TRANSFER' ? v : 'EXPENSE';

const decimalsOf = (code: string) => CurrencyRegistry.decimalsOrFallback(code);

/** A positive amount the expression stands for, at the currency's scale - or null. */
export function positiveAmount(expression: string, code: string): Decimal | null {
    const v = Calculator.evaluate(expression, decimalsOf(code));
    return v && v.gt(0) ? v : null;
}

/**
 * When the entry happened: the day the user picked, at the current time of day - so rows
 * entered one after another on the same day keep their order. Sent as an instant, which the
 * server prefers over the bare date; the Compose web client sent only the date, and the
 * server's UTC midnight put a row entered in the evening west of Greenwich on the day before.
 */
export function occurredAtFor(dayIso: string, now = new Date()): string {
    const d = parseIsoDate(dayIso);
    d.setHours(now.getHours(), now.getMinutes(), now.getSeconds(), now.getMilliseconds());
    return d.toISOString();
}

/**
 * The destination amount a cross-currency transfer suggests: the source converted at the
 * stored rate, or 1:1 when there is none, so the field is never left blank for the user to
 * wonder about. Port of TransferViewModel.convertAmount.
 */
export function suggestedDestination(fromExpr: string, from: string, to: string, fx: FxSnapshot): string {
    if (!fromExpr.trim()) return '';
    const amount = Calculator.evaluate(fromExpr, decimalsOf(from));
    if (!amount) return '';
    const rate = fx.rate(from, to);
    return toFixedString(rate !== null ? amount.times(new Decimal(rate)) : amount, decimalsOf(to));
}

/** "1 USD ≈ 41.2 UAH", or empty when there is no rate to show. */
export function rateHint(from: string, to: string, fx: FxSnapshot): string {
    const rate = fx.rate(from, to);
    return rate === null ? '' : `1 ${from} ≈ ${toTrimmedString(roundTo(new Decimal(rate), 4))} ${to}`;
}

export type TransferAmounts =
    | { ok: true; fromAmount: string; toAmount: string; fee: string | null }
    | { ok: false; error: 'transfer_invalid_source' | 'transfer_invalid_destination' };

/**
 * The three amounts a transfer is sent with. What leaves the source, what arrives, and the
 * fee on top - the endpoint wants the moved amount positive and without the fee. Port of
 * TransferViewModel.save.
 */
export function transferAmounts(input: {
    fromExpr: string;
    toExpr: string;
    feeExpr: string;
    from: string;
    to: string;
    fx: FxSnapshot;
}): TransferAmounts {
    const fromAmount = positiveAmount(input.fromExpr, input.from);
    if (!fromAmount) return { ok: false, error: 'transfer_invalid_source' };
    let toAmount: Decimal | null;
    if (input.from === input.to) {
        toAmount = fromAmount;
    } else if (!input.toExpr.trim()) {
        const rate = input.fx.rate(input.from, input.to);
        toAmount = rate !== null ? fromAmount.times(new Decimal(rate)) : fromAmount;
    } else {
        toAmount = positiveAmount(input.toExpr, input.to);
        if (!toAmount) return { ok: false, error: 'transfer_invalid_destination' };
    }
    const fee = input.feeExpr.trim() ? Calculator.evaluate(input.feeExpr, decimalsOf(input.from)) : null;
    return {
        ok: true,
        fromAmount: toFixedString(fromAmount, decimalsOf(input.from)),
        toAmount: toFixedString(toAmount, decimalsOf(input.to)),
        fee: fee && fee.gt(0) ? toFixedString(fee, decimalsOf(input.from)) : null,
    };
}

/**
 * Categories ordered by how often they turn up in the recent ledger, then by name - what
 * the chips put within reach. The web has no GROUP BY endpoint, so, like the Compose web
 * client, this counts the page of recent rows it already has.
 */
export function mostUsed(categories: RestCategory[], recent: RestTransaction[], limit: number): RestCategory[] {
    const uses = new Map<number, number>();
    for (const r of recent) if (r.category) uses.set(r.category.id, (uses.get(r.category.id) ?? 0) + 1);
    return [...categories]
        .sort((a, b) => (uses.get(b.id) ?? 0) - (uses.get(a.id) ?? 0) || a.name.localeCompare(b.name))
        .slice(0, limit);
}
