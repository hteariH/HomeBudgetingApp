import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RestAccount, RestTransaction } from '../../api/types';
import { isoDate } from '../../lib/dates';
import { collapseTransfers, counterparts, fetchLedger, groupByDay, matchesQuery, periodRange } from './ledger.logic';

const account = (id: number, name: string, code = 'USD'): RestAccount =>
    ({ id, uuid: '', name, type: 'CHECKING', balance: '0', initialBalance: '0', currency: { id, code, name: code, symbol: '$', decimals: 2 } }) as RestAccount;

let seq = 1;
const tx = (partial: Partial<RestTransaction> & { accountAmount: string }): RestTransaction =>
    ({
        id: seq++,
        uuid: '',
        account: account(1, 'Cash'),
        amount: partial.accountAmount,
        currencyCode: 'USD',
        type: 'EXPENSE',
        date: '2026-03-10',
        occurredAt: new Date(2026, 2, 10, 12).toISOString(),
        ...partial,
    }) as RestTransaction;

describe('periodRange', () => {
    const wednesday = new Date(2026, 8, 23, 15, 30);
    it('a week starts on Monday and runs through today', () => {
        const { from, to } = periodRange('WEEK', wednesday);
        expect(isoDate(from!)).toBe('2026-09-21');
        expect(isoDate(to!)).toBe('2026-09-24');
    });
    it('on a Sunday the week began six days earlier', () => {
        expect(isoDate(periodRange('WEEK', new Date(2026, 8, 27)).from!)).toBe('2026-09-21');
    });
    it('month and year start on the first', () => {
        expect(isoDate(periodRange('MONTH', wednesday).from!)).toBe('2026-09-01');
        expect(isoDate(periodRange('YEAR', wednesday).from!)).toBe('2026-01-01');
    });
    it('all has no bounds', () => expect(periodRange('ALL', wednesday)).toEqual({}));
});

describe('transfers', () => {
    const debit = tx({ type: 'TRANSFER', transferGroupId: 'g', accountAmount: '-10.00', account: account(1, 'Cash') });
    const credit = tx({ type: 'TRANSFER', transferGroupId: 'g', accountAmount: '10.00', account: account(2, 'Card') });

    it('shows a transfer once, as its debit leg', () => {
        expect(collapseTransfers([debit, credit]).map(t => t.id)).toEqual([debit.id]);
    });
    it('keeps the credit leg when the filter dropped the debit', () => {
        expect(collapseTransfers([credit]).map(t => t.id)).toEqual([credit.id]);
    });
    it('finds the other leg, for the "from → to" title', () => {
        expect(counterparts([debit, credit]).get(debit.id)?.account.name).toBe('Card');
    });
});

describe('matchesQuery', () => {
    const t = tx({ accountAmount: '150.25', description: 'Lunch at Sushi', category: { id: 1, name: 'Food' } as never });
    it('matches the note, category, account and currency', () => {
        expect(matchesQuery(t, 'sushi')).toBe(true);
        expect(matchesQuery(t, 'FOOD')).toBe(true);
        expect(matchesQuery(t, 'cash')).toBe(true);
        expect(matchesQuery(t, 'usd')).toBe(true);
        expect(matchesQuery(t, 'rent')).toBe(false);
    });
    it('matches the amount as written', () => {
        expect(matchesQuery(t, '150')).toBe(true);
        expect(matchesQuery(t, '150.2')).toBe(true);
        expect(matchesQuery(t, '999')).toBe(false);
    });
});

it('groups by local day, newest first', () => {
    const a = tx({ accountAmount: '1', occurredAt: new Date(2026, 2, 9, 23, 59).toISOString() });
    const b = tx({ accountAmount: '1', occurredAt: new Date(2026, 2, 10, 0, 1).toISOString() });
    const c = tx({ accountAmount: '1', occurredAt: new Date(2026, 2, 10, 18).toISOString() });
    const groups = groupByDay([a, b, c]);
    expect(groups.map(g => g.day)).toEqual(['2026-03-10', '2026-03-09']);
    expect(groups[0].rows.map(r => r.id)).toEqual([c.id, b.id]);
});

describe('fetchLedger', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('does not let the 1st of the next month into a month', async () => {
        const inside = tx({ accountAmount: '1', occurredAt: new Date(2026, 2, 31, 22).toISOString() });
        const bleed = tx({ accountAmount: '1', occurredAt: new Date(2026, 3, 1, 0, 0).toISOString() });
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({ content: [inside, bleed], page: 0, size: 500, totalElements: 2, totalPages: 1 }), {
                status: 200,
            }),
        );
        vi.stubGlobal('fetch', fetchMock);
        const rows = await fetchLedger({ from: new Date(2026, 2, 1), to: new Date(2026, 3, 1) });
        expect(rows.map(r => r.id)).toEqual([inside.id]);
        expect(fetchMock.mock.calls[0][0]).toContain('dateFrom=2026-03-01');
        expect(fetchMock.mock.calls[0][0]).toContain('dateTo=2026-03-31');
    });

    it('walks every page', async () => {
        const page = (n: number) =>
            new Response(JSON.stringify({ content: [tx({ accountAmount: String(n) })], page: n, size: 500, totalElements: 3, totalPages: 3 }), {
                status: 200,
            });
        const fetchMock = vi.fn().mockResolvedValueOnce(page(0)).mockResolvedValueOnce(page(1)).mockResolvedValueOnce(page(2));
        vi.stubGlobal('fetch', fetchMock);
        expect(await fetchLedger({})).toHaveLength(3);
        expect(fetchMock).toHaveBeenCalledTimes(3);
    });
});

describe('fetchLedger author filter', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('sends createdBy to the server, and nothing when there is none', async () => {
        const empty = () => new Response(JSON.stringify({ content: [], page: 0, size: 500, totalElements: 0, totalPages: 1 }), { status: 200 });
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(empty()));
        vi.stubGlobal('fetch', fetchMock);
        await fetchLedger({ createdBy: 7 });
        expect(fetchMock.mock.calls[0][0]).toContain('createdBy=7');
        await fetchLedger({});
        expect(fetchMock.mock.calls[1][0]).not.toContain('createdBy');
    });
});
