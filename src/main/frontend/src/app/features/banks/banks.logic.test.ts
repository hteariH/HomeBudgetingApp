import { beforeEach, expect, it } from 'vitest';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import {
    accountChoice,
    accountIdOf,
    categoryChoice,
    categoryIdOf,
    defaultImportRange,
    importSelections,
    minorToMoney,
    suggestedChoices,
} from './banks.logic';

beforeEach(() => CurrencyRegistry.update([]));

it('a choice is a category or an account, never both', () => {
    expect(categoryIdOf(categoryChoice(12))).toBe(12);
    expect(accountIdOf(categoryChoice(12))).toBeNull();
    expect(accountIdOf(accountChoice(3))).toBe(3);
    expect(categoryIdOf(accountChoice(3))).toBeNull();
    expect(categoryIdOf(undefined)).toBeNull();
});

it('sends every fresh row as selected, and only the transfers as transfers', () => {
    const s = importSelections(['a', 'b', 'c'], { a: categoryChoice(5), b: accountChoice(9) });
    expect(s.categories).toEqual({ a: 5, b: null, c: null });
    expect(s.transfers).toEqual({ b: 9 });
});

it('starts from the server suggestions, skipping rows already imported', () => {
    expect(
        suggestedChoices([
            { externalId: 'x', suggestedCategoryId: 4 },
            { externalId: 'y', suggestedCategoryId: 4, alreadyImported: true },
            { externalId: 'z', suggestedCategoryId: null },
        ]),
    ).toEqual({ x: 'c:4' });
});

it('reads minor units at the currency scale', () => {
    expect(minorToMoney(-12550, 'UAH').amount.toFixed(2)).toBe('125.50');
    expect(minorToMoney(1500, 'JPY').amount.toFixed(0)).toBe('1500');
});

it('defaults to the last month', () => {
    expect(defaultImportRange(new Date(2026, 8, 24))).toEqual({ from: '2026-08-24', to: '2026-09-24' });
});
