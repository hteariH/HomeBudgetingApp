import Decimal from 'decimal.js';
import { beforeEach, expect, it } from 'vitest';
import { CurrencyRegistry } from './currencyRegistry';
import { formatMediumDate, formatMoney, formatPlain, setFormatLocale, shortMonthName } from './format';

// Pinned to en-US so the result does not depend on the machine running the tests.
beforeEach(() => {
    setFormatLocale('en-US');
    CurrencyRegistry.update([]);
});

it('uses the symbol the user configured', () => {
    CurrencyRegistry.update([{ code: 'UAH', symbol: '₴', decimals: 2 }]);
    expect(formatMoney({ amount: new Decimal('1250.5'), currency: 'UAH' })).toBe('₴ 1,250.50');
});

it('falls back to Intl for ISO codes', () => expect(formatMoney({ amount: new Decimal('3'), currency: 'EUR' })).toBe('€3.00'));

it('prefixes the raw code for anything Intl rejects', () =>
    expect(formatMoney({ amount: new Decimal('1'), currency: 'USDTX' })).toBe('USDTX 1.00'));

it('plain has digits only', () => expect(formatPlain({ amount: new Decimal('1250.75'), currency: 'USD' })).toBe('1,250.75'));

it('a date is not shifted by the time zone', () => expect(formatMediumDate('2026-02-03')).toBe('Feb 3, 2026'));

it('reads only the date part of a timestamp', () => expect(formatMediumDate('2026-02-03T23:30:00Z')).toBe('Feb 3, 2026'));

it('short month names come from Intl', () => expect(shortMonthName(1)).toBe('Jan'));
