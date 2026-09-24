import { expect, it } from 'vitest';
import type { RestCurrency } from '../../api/types';
import { canProceed, firstAccountRequest } from './onboarding.logic';

const currencies = [{ id: 7, code: 'UAH' }] as RestCurrency[];

it('each step asks for what it needs', () => {
    expect(canProceed(0, '', '')).toBe(true);
    expect(canProceed(1, '', '')).toBe(false);
    expect(canProceed(1, 'UAH', '')).toBe(true);
    expect(canProceed(2, 'UAH', '  ')).toBe(false);
    expect(canProceed(2, 'UAH', 'Cash')).toBe(true);
});

it('creates the first account only when one was named', () => {
    expect(firstAccountRequest(' Cash ', 'UAH', currencies)).toMatchObject({ name: 'Cash', currencyId: 7, initialBalance: '0' });
    expect(firstAccountRequest('', 'UAH', currencies)).toBeNull();
    expect(firstAccountRequest('Cash', 'XXX', currencies)).toBeNull();
});
