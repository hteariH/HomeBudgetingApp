import Decimal from 'decimal.js';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import { isoDate } from '../../lib/dates';
import type { Money } from '../../lib/money';

/**
 * What one statement line is imported as: a category, or another of the user's own accounts
 * when the line only moved money between them. They share one dropdown because they answer
 * the same question - "what was this?" - and a statement cannot tell a card payment from a
 * top-up of savings. Port of ImportChoice.kt, with the choice spelled as "c:12" / "a:3"
 * rather than the negated ids a Compose picker needed.
 */
export type Choice = string;

export const categoryChoice = (id: number): Choice => `c:${id}`;
export const accountChoice = (id: number): Choice => `a:${id}`;

export const categoryIdOf = (choice: Choice | undefined): number | null =>
    choice?.startsWith('c:') ? Number(choice.slice(2)) : null;
export const accountIdOf = (choice: Choice | undefined): number | null =>
    choice?.startsWith('a:') ? Number(choice.slice(2)) : null;

/**
 * The two maps the confirm endpoints take, for the rows actually being imported. Every row
 * appears in `categories` - the map is the selection as well as the categorisation - and only
 * the rows marked as transfers appear in `transfers`, where a null would mean "none".
 */
export function importSelections(
    freshIds: string[],
    choices: Record<string, Choice | undefined>,
): { categories: Record<string, number | null>; transfers: Record<string, number> } {
    const categories: Record<string, number | null> = {};
    const transfers: Record<string, number> = {};
    for (const id of freshIds) {
        categories[id] = categoryIdOf(choices[id]);
        const account = accountIdOf(choices[id]);
        if (account !== null) transfers[id] = account;
    }
    return { categories, transfers };
}

/** The server's suggestions, as the starting choices: nothing is written until import is pressed. */
export function suggestedChoices(rows: { externalId: string; alreadyImported?: boolean; suggestedCategoryId?: number | null }[]) {
    const out: Record<string, Choice | undefined> = {};
    for (const r of rows) {
        if (!r.alreadyImported && r.suggestedCategoryId) out[r.externalId] = categoryChoice(r.suggestedCategoryId);
    }
    return out;
}

/** Import rows carry integer minor units of the account's currency. */
export function minorToMoney(minor: number, code: string): Money {
    return { amount: new Decimal(minor).abs().div(new Decimal(10).pow(CurrencyRegistry.decimalsOrFallback(code))), currency: code };
}

/** A month back to today: the range a statement is most often wanted for. */
export function defaultImportRange(now = new Date()): { from: string; to: string } {
    const from = new Date(now.getFullYear(), now.getMonth() - 1, now.getDate());
    return { from: isoDate(from), to: isoDate(now) };
}
