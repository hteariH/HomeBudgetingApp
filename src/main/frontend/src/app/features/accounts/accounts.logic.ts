import Decimal from 'decimal.js';
import type { RestAccount } from '../../api/types';
import type { FxSnapshot } from '../../lib/fx';
import { parseAmount, type Money } from '../../lib/money';

export const accountBalance = (a: RestAccount): Money => ({ amount: parseAmount(a.balance), currency: a.currency.code });

/** Live accounts in the user's order, and the archived ones apart. */
export function splitAccounts(all: RestAccount[]): { active: RestAccount[]; archived: RestAccount[] } {
    const sorted = [...all].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id);
    return { active: sorted.filter(a => !a.archived), archived: sorted.filter(a => a.archived) };
}

export interface TotalBalance {
    total: Money;
    /** Accounts that count towards the total. */
    counted: number;
    /** Of those, how many had no rate to the base currency and are left out of the sum. */
    missingRate: number;
}

/**
 * The overview's headline number. Port of the loop in DashboardViewModel.kt: live accounts
 * with "include in total" on, each converted to the base currency. An account with no rate
 * is left out of the sum rather than counted as zero - and counted, so the screen can say so.
 */
export function totalBalance(accounts: RestAccount[], fx: FxSnapshot, base: string): TotalBalance {
    let total = new Decimal(0);
    let counted = 0;
    let converted = 0;
    for (const a of accounts) {
        if (a.archived || a.includeInTotal === false) continue;
        counted++;
        const inBase = fx.convert(accountBalance(a), base);
        if (inBase) {
            total = total.plus(inBase.amount);
            converted++;
        }
    }
    return { total: { amount: total, currency: base }, counted, missingRate: counted - converted };
}

/** Whatever the user typed as an opening balance, as a number the server accepts. Blank is 0. */
export function initialBalanceForRequest(typed: string, decimals: number): string | null {
    const text = typed.trim().replace(',', '.');
    if (text === '' || text === '-') return (0).toFixed(Math.max(0, decimals));
    if (!/^-?\d*\.?\d*$/.test(text)) return null;
    return new Decimal(text.startsWith('.') || text.startsWith('-.') ? text.replace('.', '0.') : text)
        .toDecimalPlaces(Math.max(0, decimals), Decimal.ROUND_HALF_UP)
        .toFixed(Math.max(0, decimals));
}
