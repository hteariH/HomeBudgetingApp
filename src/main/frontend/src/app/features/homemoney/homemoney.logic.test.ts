import { expect, it } from 'vitest';
import type { RestPlannedAccount, RestPlannedCategory } from '../../api/types';
import { exchangePlan, isInvalid, isRunning, planFacts } from './homemoney.logic';

const acc = (p: Partial<RestPlannedAccount>): RestPlannedAccount =>
    ({ externalId: 'x', name: 'n', currencyLabel: 'грн', currencyCode: 'UAH', balance: '0', included: true, targetAccountId: null, ...p }) as RestPlannedAccount;
const cat = (p: Partial<RestPlannedCategory>) => ({ externalId: 'c', name: 'c', fullName: 'c', type: 'EXPENSE', archived: false, ...p }) as RestPlannedCategory;

it('counts what the import will create', () => {
    const f = planFacts({
        accounts: [acc({}), acc({ targetAccountId: 5 }), acc({ included: false })],
        categories: [cat({}), cat({ targetCategoryId: 9 })],
    });
    expect(f.newAccounts).toBe(1);
    expect(f.newCategories).toBe(1);
    expect(f.canStart).toBe(true);
});

it('will not start while an included account has no currency', () => {
    const f = planFacts({ accounts: [acc({ currencyCode: null })] });
    expect(f.unresolved).toHaveLength(1);
    expect(f.canStart).toBe(false);
    // Pointing it at an existing account settles its currency.
    expect(planFacts({ accounts: [acc({ currencyCode: null, targetAccountId: 3 })] }).canStart).toBe(true);
    // And leaving it out removes the question.
    expect(planFacts({ accounts: [acc({ currencyCode: null, included: false }), acc({})] }).canStart).toBe(true);
});

it('will not start when the server blocked it, or with nothing to import', () => {
    expect(planFacts({ accounts: [acc({})], blockedReason: 'busy' }).canStart).toBe(false);
    expect(planFacts({ accounts: [acc({ included: false })] }).canStart).toBe(false);
});

it('pairs the returning code with the state stored before leaving', () => {
    expect(exchangePlan({ kind: 'pending', code: 'abc' }, 's1')).toEqual({ kind: 'exchange', code: 'abc', state: 's1' });
    expect(exchangePlan({ kind: 'pending', code: 'abc' }, null)).toEqual({ kind: 'message', key: 'homemoney_link_expired' });
    expect(exchangePlan({ kind: 'denied' }, 's1')).toEqual({ kind: 'message', key: 'homemoney_denied' });
});

it('reads the server states', () => {
    expect(isRunning({ state: 'RUNNING' })).toBe(true);
    expect(isRunning({ state: 'DONE' })).toBe(false);
    expect(isInvalid({ connected: true, status: 'INVALID' })).toBe(true);
    expect(isInvalid({ connected: false, status: 'INVALID' })).toBe(false);
});
