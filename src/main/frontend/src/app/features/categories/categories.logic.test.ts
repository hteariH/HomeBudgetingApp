import { expect, it } from 'vitest';
import type { RestCategory } from '../../api/types';
import { budgetForRequest, categoriesOfType, parseCategoryType } from './categories.logic';

it('sends a budget as a two-decimal string', () => {
    expect(budgetForRequest('EXPENSE', '300')).toBe('300.00');
    expect(budgetForRequest('EXPENSE', '300+50,5')).toBe('350.50');
});

it('has no budget when blank, non-positive, unreadable, or on income', () => {
    expect(budgetForRequest('EXPENSE', '')).toBeNull();
    expect(budgetForRequest('EXPENSE', '0')).toBeNull();
    expect(budgetForRequest('EXPENSE', '5-10')).toBeNull();
    expect(budgetForRequest('EXPENSE', '1..2')).toBeNull();
    expect(budgetForRequest('INCOME', '300')).toBeNull();
});

it('lists the live categories of one type, in order', () => {
    const c = (id: number, type: 'EXPENSE' | 'INCOME', sortOrder: number, archived = false) =>
        ({ id, uuid: String(id), name: `c${id}`, type, icon: 'x', color: '#000000', sortOrder, archived }) as RestCategory;
    const all = [c(1, 'EXPENSE', 2), c(2, 'EXPENSE', 1), c(3, 'INCOME', 0), c(4, 'EXPENSE', 0, true)];
    expect(categoriesOfType(all, 'EXPENSE').map(x => x.id)).toEqual([2, 1]);
    expect(categoriesOfType(all, 'INCOME').map(x => x.id)).toEqual([3]);
});

it('anything but INCOME in the address is an expense tab', () => {
    expect(parseCategoryType('INCOME')).toBe('INCOME');
    expect(parseCategoryType(null)).toBe('EXPENSE');
    expect(parseCategoryType('bogus')).toBe('EXPENSE');
});
