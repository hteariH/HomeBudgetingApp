import Decimal from 'decimal.js';
import type { RestCategory, RestTransaction } from '../../api/types';
import type { FxSnapshot } from '../../lib/fx';
import { parseAmount } from '../../lib/money';

export type AnalyticsPeriod = 3 | 6 | 12;

export interface CategoryShare {
    categoryId: number | null;
    name: string | null;
    icon: string;
    color: string;
    amount: Decimal;
    percentage: number;
}

export interface MonthBar {
    year: number;
    month1: number;
    income: Decimal;
    expense: Decimal;
    expenseCategories: CategoryShare[];
    incomeCategories: CategoryShare[];
}

export interface Analytics {
    months: MonthBar[];
    totalIncome: Decimal;
    totalExpense: Decimal;
    /** Spending this month against last, in whole percent - null when last month had none. */
    expenseChangePercent: number | null;
    /** (income − spending) / income this month, in whole percent - null with no income. */
    savingsRatePercent: number | null;
}

/** The first day of each of the last `count` months, oldest first, and the end of the current one. */
export function monthStarts(count: number, now = new Date()): { starts: Date[]; end: Date } {
    const starts = Array.from({ length: count }, (_, i) => new Date(now.getFullYear(), now.getMonth() - (count - 1 - i), 1));
    return { starts, end: new Date(now.getFullYear(), now.getMonth() + 1, 1) };
}

function shares(byCategory: Map<number | null, Decimal>, total: Decimal, categories: Map<number, RestCategory>): CategoryShare[] {
    if (total.lte(0)) return [];
    return [...byCategory.entries()]
        .map(([id, amount]) => {
            const c = id !== null ? categories.get(id) : undefined;
            return {
                categoryId: id,
                name: c?.name ?? null,
                icon: c?.icon ?? 'more_horiz',
                color: c?.color ?? '#94A3B8',
                amount,
                percentage: Math.round(amount.div(total).times(100).toNumber()),
            };
        })
        .sort((a, b) => b.amount.comparedTo(a.amount));
}

/**
 * Month-by-month income and spending over the period, each with its category split, in the
 * base currency. Port of AnalyticsViewModel.kt - one ledger fetch for the whole period,
 * bucketed here, where the Compose client ran one query per month.
 */
export function buildAnalytics(input: {
    rows: RestTransaction[];
    categories: RestCategory[];
    fx: FxSnapshot;
    base: string;
    months: number;
    now?: Date;
}): Analytics {
    const { starts, end } = monthStarts(input.months, input.now);
    const categories = new Map(input.categories.map(c => [c.id, c]));
    const buckets = starts.map(() => ({
        income: new Decimal(0),
        expense: new Decimal(0),
        inc: new Map<number | null, Decimal>(),
        exp: new Map<number | null, Decimal>(),
    }));

    for (const tx of input.rows) {
        if (tx.type === 'TRANSFER') continue;
        const at = new Date(tx.occurredAt);
        if (at < starts[0] || at >= end) continue;
        const converted = input.fx.convert({ amount: parseAmount(tx.accountAmount).abs(), currency: tx.account.currency.code }, input.base);
        if (!converted) continue;
        const b = buckets[starts.findLastIndex(s => at >= s)];
        const key = tx.category?.id ?? null;
        if (tx.type === 'INCOME') {
            b.income = b.income.plus(converted.amount);
            b.inc.set(key, (b.inc.get(key) ?? new Decimal(0)).plus(converted.amount));
        } else {
            b.expense = b.expense.plus(converted.amount);
            b.exp.set(key, (b.exp.get(key) ?? new Decimal(0)).plus(converted.amount));
        }
    }

    const months: MonthBar[] = starts.map((s, i) => ({
        year: s.getFullYear(),
        month1: s.getMonth() + 1,
        income: buckets[i].income,
        expense: buckets[i].expense,
        expenseCategories: shares(buckets[i].exp, buckets[i].expense, categories),
        incomeCategories: shares(buckets[i].inc, buckets[i].income, categories),
    }));

    const current = months[months.length - 1];
    const previous = months[months.length - 2];
    const expenseChangePercent =
        previous && previous.expense.gt(0)
            ? Math.round(current.expense.minus(previous.expense).div(previous.expense).times(100).toNumber())
            : null;
    const savingsRatePercent =
        current && current.income.gt(0)
            ? Math.round(current.income.minus(current.expense).div(current.income).times(100).toNumber())
            : null;

    return {
        months,
        totalIncome: months.reduce((s, m) => s.plus(m.income), new Decimal(0)),
        totalExpense: months.reduce((s, m) => s.plus(m.expense), new Decimal(0)),
        expenseChangePercent,
        savingsRatePercent,
    };
}
