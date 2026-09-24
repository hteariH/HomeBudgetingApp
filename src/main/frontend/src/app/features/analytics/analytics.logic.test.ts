import { expect, it } from 'vitest';
import type { RestAccount, RestCategory, RestTransaction } from '../../api/types';
import { FxSnapshot } from '../../lib/fx';
import { buildAnalytics, monthStarts } from './analytics.logic';

const now = new Date(2026, 8, 24);
const fx = FxSnapshot.from([{ baseCode: 'USD', quoteCode: 'EUR', rate: 0.5 }]);
const usd = { id: 1, currency: { code: 'USD', decimals: 2 } } as RestAccount;
const eur = { id: 2, currency: { code: 'EUR', decimals: 2 } } as RestAccount;
const food = { id: 10, name: 'Food', icon: 'restaurant', color: '#E5484D', type: 'EXPENSE' } as RestCategory;
const fun = { id: 11, name: 'Fun', icon: 'movie', color: '#AB47BC', type: 'EXPENSE' } as RestCategory;

let seq = 1;
const row = (account: RestAccount, type: RestTransaction['type'], amount: string, at: Date, category?: RestCategory) =>
    ({ id: seq++, account, category, type, accountAmount: amount, amount, occurredAt: at.toISOString() }) as RestTransaction;

const rows = [
    row(usd, 'EXPENSE', '75.00', new Date(2026, 8, 2), food),
    row(eur, 'EXPENSE', '12.50', new Date(2026, 8, 3), fun), // 25 USD
    row(usd, 'INCOME', '1000.00', new Date(2026, 8, 1)),
    row(usd, 'EXPENSE', '50.00', new Date(2026, 7, 20), food),
    row(usd, 'EXPENSE', '10.00', new Date(2026, 5, 30), food), // outside a 3-month window
    row(usd, 'TRANSFER', '-99.00', new Date(2026, 8, 4)),
];

it('covers the chosen number of months, the current one last', () => {
    const { starts } = monthStarts(12, now);
    expect(starts).toHaveLength(12);
    expect([starts[0].getFullYear(), starts[0].getMonth()]).toEqual([2025, 9]);
    expect(starts[11].getMonth()).toBe(8);
});

it('buckets income and spending per month in the base currency', () => {
    const a = buildAnalytics({ rows, categories: [food, fun], fx, base: 'USD', months: 3, now });
    expect(a.months.map(m => m.month1)).toEqual([7, 8, 9]);
    expect(a.months[2].expense.toFixed(2)).toBe('100.00');
    expect(a.months[2].income.toFixed(2)).toBe('1000.00');
    expect(a.months[1].expense.toFixed(2)).toBe('50.00');
    expect(a.totalExpense.toFixed(2)).toBe('150.00');
});

it('splits a month by category with percentages', () => {
    const sep = buildAnalytics({ rows, categories: [food, fun], fx, base: 'USD', months: 3, now }).months[2];
    expect(sep.expenseCategories.map(c => [c.name, c.amount.toFixed(2), c.percentage])).toEqual([
        ['Food', '75.00', 75],
        ['Fun', '25.00', 25],
    ]);
    expect(sep.incomeCategories).toEqual([
        expect.objectContaining({ categoryId: null, name: null, percentage: 100 }),
    ]);
});

it('compares spending with last month and works out the savings rate', () => {
    const a = buildAnalytics({ rows, categories: [food, fun], fx, base: 'USD', months: 3, now });
    expect(a.expenseChangePercent).toBe(100);
    expect(a.savingsRatePercent).toBe(90);
});

it('has no comparison when last month spent nothing', () => {
    const a = buildAnalytics({ rows: [row(usd, 'EXPENSE', '5', new Date(2026, 8, 2))], categories: [], fx, base: 'USD', months: 3, now });
    expect(a.expenseChangePercent).toBeNull();
    expect(a.savingsRatePercent).toBeNull();
});
