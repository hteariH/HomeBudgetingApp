import { describe, expect, it } from 'vitest';
import type { RestAccount, RestCategory, RestTransaction } from '../../api/types';
import { FxSnapshot } from '../../lib/fx';
import { sixMonths, summarize } from './dashboard.logic';

const now = new Date(2026, 8, 24, 12);
const fx = FxSnapshot.from([{ baseCode: 'USD', quoteCode: 'UAH', rate: 40 }]);

const acc = (id: number, code: string, balance: string): RestAccount =>
    ({ id, uuid: '', name: `a${id}`, type: 'X', balance, initialBalance: '0', currency: { id, code, name: code, symbol: code, decimals: 2 } }) as RestAccount;
const usd = acc(1, 'USD', '1000.00');
const uah = acc(2, 'UAH', '4000.00');
const btc = acc(3, 'BTC', '1.00');

const cat = (id: number, name: string, budgetLimit?: string): RestCategory =>
    ({ id, uuid: '', name, type: 'EXPENSE', icon: 'x', color: '#000000', budgetLimit }) as RestCategory;
const food = cat(10, 'Food', '100.00');
const rent = cat(11, 'Rent');

let seq = 1;
const row = (account: RestAccount, type: RestTransaction['type'], amount: string, at: Date, category?: RestCategory): RestTransaction =>
    ({ id: seq++, uuid: '', account, category, amount, accountAmount: amount, currencyCode: account.currency.code, type, date: '', occurredAt: at.toISOString() }) as RestTransaction;

describe('summarize', () => {
    const rows = [
        row(usd, 'EXPENSE', '60.00', new Date(2026, 8, 3), food),
        row(uah, 'EXPENSE', '2400.00', new Date(2026, 8, 5), food), // 60 USD
        row(usd, 'EXPENSE', '500.00', new Date(2026, 8, 1), rent),
        row(usd, 'INCOME', '2000.00', new Date(2026, 8, 2)),
        row(usd, 'INCOME', '1500.00', new Date(2026, 7, 15)),
        row(usd, 'EXPENSE', '100.00', new Date(2026, 3, 30)), // before the window
        row(btc, 'EXPENSE', '0.10', new Date(2026, 8, 6), food), // no rate: skipped
        row(usd, 'TRANSFER', '-50.00', new Date(2026, 8, 7)),
    ];
    const s = summarize({ accounts: [usd, uah, btc], rows, categories: [food, rent], fx, base: 'USD', now });

    it('totals this month in the base currency, leaving out transfers and unconvertible rows', () => {
        expect(s.monthIncome.amount.toFixed(2)).toBe('2000.00');
        expect(s.monthExpense.amount.toFixed(2)).toBe('620.00');
        expect(s.monthNet.amount.toFixed(2)).toBe('1380.00');
    });

    it('breaks spending down by category, largest first', () => {
        expect(s.slices.map(x => [x.name, x.total.toFixed(2)])).toEqual([
            ['Rent', '500.00'],
            ['Food', '120.00'],
        ]);
    });

    it('measures each budget against what the month spent', () => {
        expect(s.budgets).toHaveLength(1);
        expect(s.budgets[0].over).toBe(true);
        expect(s.budgets[0].fraction).toBe(1);
    });

    it('buckets six months of flow, the current month last and empty months at zero', () => {
        expect(s.flow.map(f => f.month1)).toEqual([4, 5, 6, 7, 8, 9]);
        expect(s.flow[5].income.toFixed(0)).toBe('2000');
        expect(s.flow[4].income.toFixed(0)).toBe('1500');
        expect(s.flow[1].expense.isZero()).toBe(true);
    });

    it('walks net worth back from today through every later row', () => {
        // Today: 1000 + 4000/40 = 1100 (BTC has no rate). Undo September for the end of August.
        expect(s.trend[5].toFixed(2)).toBe('1100.00');
        // September effect: -60 -60 -500 +2000 -50 = 1330 -> end of August was -230.
        expect(s.trend[4].toFixed(2)).toBe('-230.00');
    });
});

it('more than six categories collapse into a count', () => {
    const cats = Array.from({ length: 8 }, (_, i) => cat(100 + i, `c${i}`));
    const rows = cats.map((c, i) => row(usd, 'EXPENSE', `${i + 1}.00`, new Date(2026, 8, 2), c));
    const s = summarize({ accounts: [usd], rows, categories: cats, fx, base: 'USD', now });
    expect(s.slices).toHaveLength(6);
    expect(s.moreSlices).toBe(2);
});

it('six months end with the current one', () => {
    const { starts, end } = sixMonths(new Date(2026, 1, 10));
    expect(starts[0].getMonth()).toBe(8);
    expect(starts[0].getFullYear()).toBe(2025);
    expect(end.getMonth()).toBe(2);
});
