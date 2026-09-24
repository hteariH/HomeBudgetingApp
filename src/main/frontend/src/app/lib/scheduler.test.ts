import { expect, it } from 'vitest';
import { nextChargeDate as n } from './scheduler';

it('daily and weekly add days', () => {
    expect(n('2026-01-30', 'DAILY', 3)).toBe('2026-02-02');
    expect(n('2026-01-30', 'WEEKLY', 2)).toBe('2026-02-13');
});

it('monthly clamps to the end of a shorter month, like kotlinx-datetime', () => {
    expect(n('2026-01-31', 'MONTHLY', 1)).toBe('2026-02-28');
    expect(n('2028-01-31', 'MONTHLY', 1)).toBe('2028-02-29');
    expect(n('2026-11-30', 'MONTHLY', 3)).toBe('2027-02-28');
});

it('yearly from a leap day lands on Feb 28', () => expect(n('2028-02-29', 'YEARLY', 1)).toBe('2029-02-28'));

it('an interval below 1 counts as 1', () => expect(n('2026-01-01', 'DAILY', 0)).toBe('2026-01-02'));
