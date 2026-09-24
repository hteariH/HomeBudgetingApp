import { beforeEach, describe, expect, it } from 'vitest';
import type { RestCategory, RestTransaction } from '../../api/types';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import { FxSnapshot } from '../../lib/fx';
import { mostUsed, occurredAtFor, parseEntryMode, positiveAmount, rateHint, suggestedDestination, transferAmounts } from './quickadd.logic';

const fx = FxSnapshot.from([{ baseCode: 'USD', quoteCode: 'UAH', rate: 41.2 }]);

beforeEach(() => CurrencyRegistry.update([]));

it('reads the mode from the address', () => {
    expect(parseEntryMode('TRANSFER')).toBe('TRANSFER');
    expect(parseEntryMode(null)).toBe('EXPENSE');
});

it('an amount must be a positive number', () => {
    expect(positiveAmount('12+3', 'USD')?.toFixed(2)).toBe('15.00');
    expect(positiveAmount('0', 'USD')).toBeNull();
    expect(positiveAmount('5-10', 'USD')).toBeNull();
    expect(positiveAmount('', 'USD')).toBeNull();
});

it('keeps the picked day and the current time', () => {
    const now = new Date(2026, 8, 24, 21, 45, 10);
    const at = new Date(occurredAtFor('2026-09-20', now));
    expect([at.getFullYear(), at.getMonth(), at.getDate(), at.getHours(), at.getMinutes()]).toEqual([2026, 8, 20, 21, 45]);
});

describe('transfers', () => {
    it('suggests the destination at the stored rate', () => {
        expect(suggestedDestination('100', 'USD', 'UAH', fx)).toBe('4120.00');
        expect(suggestedDestination('41.2*10', 'UAH', 'USD', fx)).toBe('10.00');
    });
    it('falls back to one-for-one when there is no rate', () => {
        expect(suggestedDestination('5', 'USD', 'BTC', fx)).toBe('5.00000000');
    });
    it('shows the rate it would use', () => {
        expect(rateHint('USD', 'UAH', fx)).toBe('1 USD ≈ 41.2 UAH');
        expect(rateHint('USD', 'BTC', fx)).toBe('');
    });
    it('sends the moved amount without the fee, and the fee apart', () => {
        expect(transferAmounts({ fromExpr: '100', toExpr: '', feeExpr: '1,5', from: 'USD', to: 'USD', fx })).toEqual({
            ok: true,
            fromAmount: '100.00',
            toAmount: '100.00',
            fee: '1.50',
        });
    });
    it('uses what the user typed for the destination', () => {
        const r = transferAmounts({ fromExpr: '100', toExpr: '4000', feeExpr: '', from: 'USD', to: 'UAH', fx });
        expect(r).toEqual({ ok: true, fromAmount: '100.00', toAmount: '4000.00', fee: null });
    });
    it('converts when the destination was left blank', () => {
        const r = transferAmounts({ fromExpr: '10', toExpr: '', feeExpr: '0', from: 'USD', to: 'UAH', fx });
        expect(r).toMatchObject({ toAmount: '412.00', fee: null });
    });
    it('says which amount is wrong', () => {
        expect(transferAmounts({ fromExpr: '', toExpr: '', feeExpr: '', from: 'USD', to: 'UAH', fx })).toEqual({
            ok: false,
            error: 'transfer_invalid_source',
        });
        expect(transferAmounts({ fromExpr: '1', toExpr: 'x', feeExpr: '', from: 'USD', to: 'UAH', fx })).toEqual({
            ok: false,
            error: 'transfer_invalid_destination',
        });
    });
});

it('puts the most used categories first', () => {
    const cat = (id: number, name: string) => ({ id, name }) as RestCategory;
    const row = (id: number) => ({ category: { id } }) as RestTransaction;
    const cats = [cat(1, 'Books'), cat(2, 'Food'), cat(3, 'Apps')];
    expect(mostUsed(cats, [row(2), row(2), row(1)], 8).map(c => c.name)).toEqual(['Food', 'Books', 'Apps']);
    expect(mostUsed(cats, [], 2).map(c => c.name)).toEqual(['Apps', 'Books']);
});
