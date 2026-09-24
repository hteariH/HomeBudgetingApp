import Decimal from 'decimal.js';
import { beforeEach, expect, it } from 'vitest';
import { CurrencyRegistry } from './currencyRegistry';
import { parseAmount, roundTo, toFixedString, toTrimmedString } from './money';

beforeEach(() => CurrencyRegistry.update([]));

// MoneyTest.kt's rounding case. Its Long-clamping cases have no equivalent: Decimal does
// not overflow.
it('multiplication rounds half away from zero to minor units', () => {
    expect(roundTo(new Decimal('1.00').times('2.5'), 2).toFixed(2)).toBe('2.50');
    expect(roundTo(new Decimal('0.005'), 2).toFixed(2)).toBe('0.01');
    expect(roundTo(new Decimal('-0.005'), 2).toFixed(2)).toBe('-0.01');
});
it('parses API strings and treats garbage as zero', () => {
    expect(parseAmount('12.50').toFixed(2)).toBe('12.50');
    expect(parseAmount(null).isZero()).toBe(true);
    expect(parseAmount('abc').isZero()).toBe(true);
});
it('keeps the scale on the wire', () => {
    expect(toFixedString(new Decimal('90'), 2)).toBe('90.00');
    expect(toFixedString(new Decimal('12.5'), 2)).toBe('12.50');
    expect(toFixedString(new Decimal('10'), 0)).toBe('10');
});
it('trims for display', () => {
    expect(toTrimmedString(new Decimal('12.50'))).toBe('12.5');
    expect(toTrimmedString(new Decimal('12.00'))).toBe('12');
});
it('falls back to known decimals for codes the user has not configured', () => {
    expect(CurrencyRegistry.decimalsOrFallback('JPY')).toBe(0);
    expect(CurrencyRegistry.decimalsOrFallback('KWD')).toBe(3);
    expect(CurrencyRegistry.decimalsOrFallback('BTC')).toBe(8);
    expect(CurrencyRegistry.decimalsOrFallback('XYZ')).toBe(2);
    CurrencyRegistry.update([{ code: 'XYZ', symbol: 'X', decimals: 4 }]);
    expect(CurrencyRegistry.decimalsOrFallback('XYZ')).toBe(4);
});
