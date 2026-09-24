import { expect, it } from 'vitest';
import { formatRate, handRateFor, handRateOffered, parseRate } from './currencies.logic';

it('parses a rate typed with either separator', () => {
    expect(parseRate('41.5')).toBe(41.5);
    expect(parseRate(' 41,5 ')).toBe(41.5);
});

it('rejects what is not a usable rate', () => {
    expect(parseRate('')).toBeNull();
    expect(parseRate('0')).toBeNull();
    expect(parseRate('-2')).toBeNull();
    expect(parseRate('abc')).toBeNull();
});

it('shows a whole rate without a trailing .0', () => {
    expect(formatRate(1)).toBe('1');
    expect(formatRate(0.0243)).toBe('0.0243');
});

it('does not ask for a rate that is fixed by definition', () => {
    expect(handRateOffered('USD')).toBe(false);
    expect(handRateOffered('USDT')).toBe(false);
    expect(handRateOffered('UAH')).toBe(true);
    expect(handRateOffered('')).toBe(false);
});

it('finds the hand-set rate among the fetched ones', () => {
    const rates = [
        { baseCode: 'USD', quoteCode: 'UAH', rate: 41.2 },
        { baseCode: 'BTC', quoteCode: 'USD', rate: 60000 },
    ];
    expect(handRateFor(rates, 'btc')).toBe(60000);
    expect(handRateFor(rates, 'UAH')).toBeNull();
});
