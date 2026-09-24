import Decimal from 'decimal.js';
import { expect, it } from 'vitest';
import { FxSnapshot } from './fx';

// Ported case by case from FxSnapshotTest.kt.
const r = (baseCode: string, quoteCode: string, rate: number) => ({ baseCode, quoteCode, rate });

it('same currency returns 1', () => expect(FxSnapshot.from([]).rate('USD', 'USD')).toBe(1));

it('direct rate', () => expect(FxSnapshot.from([r('USD', 'EUR', 0.9)]).rate('USD', 'EUR')).toBeCloseTo(0.9, 4));

it('inverse rate', () => expect(FxSnapshot.from([r('USD', 'EUR', 0.9)]).rate('EUR', 'USD')).toBeCloseTo(1.111, 3));

it('pivot via USD', () =>
    expect(FxSnapshot.from([r('USD', 'EUR', 0.9), r('USD', 'GBP', 0.8)]).rate('EUR', 'GBP')).toBeCloseTo((1 / 0.9) * 0.8, 3));

it('unknown currencies return null', () => expect(FxSnapshot.from([]).rate('XXX', 'EUR')).toBeNull());

it('zero stored rate is ignored, not inverted to infinity', () => {
    const fx = FxSnapshot.from([r('USD', 'EUR', 0)]);
    expect(fx.rate('USD', 'EUR')).toBeNull();
    expect(fx.rate('EUR', 'USD')).toBeNull();
});

it('non-finite stored rate is ignored', () => {
    const fx = FxSnapshot.from([r('USD', 'EUR', NaN)]);
    expect(fx.rate('USD', 'EUR')).toBeNull();
    expect(fx.rate('EUR', 'USD')).toBeNull();
});

it('negative stored rate is ignored', () => expect(FxSnapshot.from([r('USD', 'EUR', -0.9)]).rate('USD', 'EUR')).toBeNull());

it('a dollar stablecoin converts with no stored rate at all', () => {
    const fx = FxSnapshot.from([]);
    expect(fx.rate('USDT', 'USD')).toBeCloseTo(1, 4);
    expect(fx.rate('USD', 'USDC')).toBeCloseTo(1, 4);
});

it('a pegged coin reaches every currency the feed does carry', () =>
    expect(FxSnapshot.from([r('EUR', 'USD', 1.08)]).rate('USDT', 'EUR')).toBeCloseTo(1 / 1.08, 4));

it('a stored rate beats the peg', () => expect(FxSnapshot.from([r('USDT', 'USD', 0.93)]).rate('USDT', 'USD')).toBeCloseTo(0.93, 4));

it('an unpegged crypto still needs a rate of its own', () =>
    expect(FxSnapshot.from([r('EUR', 'USD', 1.08)]).rate('BTC', 'EUR')).toBeNull());

it('convert respects target decimals', () => {
    const out = FxSnapshot.from([r('USD', 'JPY', 150)]).convert({ amount: new Decimal('1.00'), currency: 'USD' }, 'JPY');
    expect(out?.amount.toFixed()).toBe('150');
});
