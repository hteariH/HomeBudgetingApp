import type { CategoryType, RestCategory } from '../../api/types';
import { Calculator } from '../../lib/calculator';
import { toFixedString } from '../../lib/money';

/**
 * The budget as the server wants it: a two-decimal string, or null for "no budget".
 * Only expense categories carry one, blank means none, and so does anything that is not a
 * positive amount. Typed like any amount field, so `300+50` is 350.
 *
 * Two decimals because that is what the server assumes for a budget, whatever the base
 * currency - the Compose client sent the same.
 */
export function budgetForRequest(type: CategoryType, typed: string): string | null {
    if (type !== 'EXPENSE' || typed.trim() === '') return null;
    const value = Calculator.evaluate(typed, 2);
    return value && value.gt(0) ? toFixedString(value, 2) : null;
}

/** The list for one tab: live categories of that type, in the server's order. */
export const categoriesOfType = (all: RestCategory[], type: CategoryType): RestCategory[] =>
    all.filter(c => c.type === type && !c.archived).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));

export const parseCategoryType = (value: string | null): CategoryType => (value === 'INCOME' ? 'INCOME' : 'EXPENSE');
