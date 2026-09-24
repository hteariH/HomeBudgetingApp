import { expect, it } from 'vitest';
import { apiDateTo, inRange, isoDate, monthRange } from './dates';

it('a month range is [first day, first day of next month)', () => {
    const { from, to } = monthRange(2026, 12);
    expect(isoDate(from)).toBe('2026-12-01');
    expect(isoDate(to)).toBe('2027-01-01');
});

it('dateTo sent to the server is the last included day, not the exclusive bound', () =>
    expect(apiDateTo(monthRange(2026, 2).to)).toBe('2026-02-28'));

it('a transaction at the exclusive bound is outside the range', () => {
    const { from, to } = monthRange(2026, 3);
    expect(inRange(new Date(2026, 3, 1, 0, 0, 0).toISOString(), from, to)).toBe(false);
    expect(inRange(new Date(2026, 2, 31, 23, 59).toISOString(), from, to)).toBe(true);
    expect(inRange(new Date(2026, 2, 1, 0, 0).toISOString(), from, to)).toBe(true);
});

it('an open range lets everything through', () => expect(inRange('2020-01-01T00:00:00Z')).toBe(true));
