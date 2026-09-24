import Decimal from 'decimal.js';
import type { RestAccount, RestCategory, RestTransaction } from '../../api/types';
import type { FxSnapshot } from '../../lib/fx';
import { parseAmount, type Money } from '../../lib/money';

export interface Slice {
    categoryId: number | null;
    name: string | null;
    icon: string;
    color: string;
    total: Decimal;
}

export interface BudgetProgress {
    category: RestCategory;
    spent: Decimal;
    limit: Decimal;
    fraction: number;
    over: boolean;
}

export interface MonthFlow {
    month1: number;
    income: Decimal;
    expense: Decimal;
}

export interface DashboardSummary {
    monthIncome: Money;
    monthExpense: Money;
    monthNet: Money;
    slices: Slice[];
    moreSlices: number;
    budgets: BudgetProgress[];
    flow: MonthFlow[];
    /** Net worth at the end of each of the six months, oldest first. */
    trend: Decimal[];
}

/** The six months the overview covers, as local month starts, oldest first, plus the next one. */
export function sixMonths(now = new Date()): { starts: Date[]; end: Date } {
    const starts = [5, 4, 3, 2, 1, 0].map(i => new Date(now.getFullYear(), now.getMonth() - i, 1));
    return { starts, end: new Date(now.getFullYear(), now.getMonth() + 1, 1) };
}

/** What a row did to its account's balance. Expense and income amounts are unsigned; transfer legs are signed. */
export function balanceEffect(tx: RestTransaction): Decimal {
    const amount = parseAmount(tx.accountAmount);
    if (tx.type === 'EXPENSE') return amount.abs().neg();
    if (tx.type === 'INCOME') return amount.abs();
    return amount;
}

/**
 * Everything the overview shows below the headline balance, from one pass over six months
 * of ledger. Port of DashboardViewModel.kt. Every amount is converted to the base currency;
 * a row with no rate is skipped rather than counted as zero, transfers are neither income
 * nor spending, and the six months end with the current one.
 *
 * The trend differs from the Compose web client in the right direction: that one rebuilt
 * past balances from the latest 500 rows, which forgot anything older; this walks back
 * from today's balances using every row since, which the six-month window holds.
 */
export function summarize(input: {
    accounts: RestAccount[];
    rows: RestTransaction[];
    categories: RestCategory[];
    fx: FxSnapshot;
    base: string;
    now?: Date;
}): DashboardSummary {
    const { accounts, rows, categories, fx, base } = input;
    const now = input.now ?? new Date();
    const { starts, end } = sixMonths(now);
    const monthStart = starts[5];
    const toBase = (amount: Decimal, code: string) => fx.convert({ amount, currency: code }, base)?.amount ?? null;

    let income = new Decimal(0);
    let expense = new Decimal(0);
    const byCategory = new Map<number | null, Decimal>();
    const flow: MonthFlow[] = starts.map(s => ({ month1: s.getMonth() + 1, income: new Decimal(0), expense: new Decimal(0) }));

    for (const tx of rows) {
        if (tx.type === 'TRANSFER') continue;
        const at = new Date(tx.occurredAt);
        if (at < starts[0] || at >= end) continue;
        const inBase = toBase(parseAmount(tx.accountAmount).abs(), tx.account.currency.code);
        if (inBase === null) continue;
        const bucket = starts.findLastIndex(s => at >= s);
        if (tx.type === 'INCOME') flow[bucket].income = flow[bucket].income.plus(inBase);
        else flow[bucket].expense = flow[bucket].expense.plus(inBase);
        if (at >= monthStart) {
            if (tx.type === 'INCOME') income = income.plus(inBase);
            else {
                expense = expense.plus(inBase);
                const key = tx.category?.id ?? null;
                byCategory.set(key, (byCategory.get(key) ?? new Decimal(0)).plus(inBase));
            }
        }
    }

    const categoriesById = new Map(categories.map(c => [c.id, c]));
    const allSlices: Slice[] = [...byCategory.entries()]
        .map(([id, total]) => {
            const c = id !== null ? categoriesById.get(id) : undefined;
            return { categoryId: id, name: c?.name ?? null, icon: c?.icon ?? 'more_horiz', color: c?.color ?? '#94A3B8', total };
        })
        .sort((a, b) => b.total.comparedTo(a.total));

    const budgets = categories
        .filter(c => !c.archived && c.type === 'EXPENSE' && c.budgetLimit && parseAmount(c.budgetLimit).gt(0))
        .map(c => {
            const limit = parseAmount(c.budgetLimit);
            const spent = byCategory.get(c.id) ?? new Decimal(0);
            return { category: c, spent, limit, fraction: Math.min(1, spent.div(limit).toNumber()), over: spent.gt(limit) };
        })
        .sort((a, b) => b.fraction - a.fraction);

    // Net worth at each month end: today's balances, minus whatever happened after that end.
    let current = new Decimal(0);
    const live = accounts.filter(a => !a.archived);
    const liveIds = new Set(live.map(a => a.id));
    for (const a of live) current = current.plus(toBase(parseAmount(a.balance), a.currency.code) ?? 0);
    const trend = starts.map((_, i) => {
        const monthEnd = i === 5 ? end : starts[i + 1];
        const cutoff = monthEnd < now ? monthEnd : now;
        let later = new Decimal(0);
        for (const tx of rows) {
            if (!liveIds.has(tx.account.id) || new Date(tx.occurredAt) < cutoff) continue;
            later = later.plus(toBase(balanceEffect(tx), tx.account.currency.code) ?? 0);
        }
        return current.minus(later);
    });

    const m = (amount: Decimal): Money => ({ amount, currency: base });
    return {
        monthIncome: m(income),
        monthExpense: m(expense),
        monthNet: m(income.minus(expense)),
        slices: allSlices.slice(0, 6),
        moreSlices: Math.max(0, allSlices.length - 6),
        budgets,
        flow,
        trend,
    };
}
