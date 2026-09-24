import { expect, it } from 'vitest';
import type { RestAccount } from '../../api/types';
import { FxSnapshot } from '../../lib/fx';
import { initialBalanceForRequest, splitAccounts, totalBalance } from './accounts.logic';

let nextId = 1;
const acc = (code: string, balance: string, extra: Partial<RestAccount> = {}): RestAccount =>
    ({
        id: nextId++,
        uuid: 'u',
        name: code,
        type: 'CHECKING',
        balance,
        initialBalance: '0',
        currency: { id: 1, code, name: code, symbol: code, decimals: code === 'BTC' ? 8 : 2 },
        ...extra,
    }) as RestAccount;

const fx = FxSnapshot.from([{ baseCode: 'USD', quoteCode: 'UAH', rate: 40 }]);

it('sums every counted account in the base currency', () => {
    const t = totalBalance([acc('UAH', '400.00'), acc('USD', '10.00')], fx, 'USD');
    expect(t.total.amount.toFixed(2)).toBe('20.00');
    expect(t.counted).toBe(2);
    expect(t.missingRate).toBe(0);
});

it('a stablecoin counts without any stored rate', () => {
    expect(totalBalance([acc('USDT', '5')], fx, 'USD').total.amount.toFixed(2)).toBe('5.00');
});

it('an account with no rate is left out of the sum, and said to be', () => {
    const t = totalBalance([acc('USD', '10.00'), acc('BTC', '0.5')], fx, 'USD');
    expect(t.total.amount.toFixed(2)).toBe('10.00');
    expect(t.missingRate).toBe(1);
});

it('archived accounts and ones kept out of the total do not count', () => {
    const t = totalBalance(
        [acc('USD', '10.00'), acc('USD', '99', { archived: true }), acc('USD', '99', { includeInTotal: false })],
        fx,
        'USD',
    );
    expect(t.total.amount.toFixed(2)).toBe('10.00');
    expect(t.counted).toBe(1);
});

it('splits live from archived, in the user order', () => {
    const a = acc('USD', '0', { sortOrder: 2 });
    const b = acc('USD', '0', { sortOrder: 1 });
    const c = acc('USD', '0', { archived: true });
    const { active, archived } = splitAccounts([a, b, c]);
    expect(active.map(x => x.id)).toEqual([b.id, a.id]);
    expect(archived.map(x => x.id)).toEqual([c.id]);
});

it('an opening balance is sent at the currency scale', () => {
    expect(initialBalanceForRequest('', 2)).toBe('0.00');
    expect(initialBalanceForRequest('12,5', 2)).toBe('12.50');
    expect(initialBalanceForRequest('-3', 0)).toBe('-3');
    expect(initialBalanceForRequest('.5', 2)).toBe('0.50');
    expect(initialBalanceForRequest('1.2.3', 2)).toBeNull();
});
