import { endpoints } from '../../api/endpoints';
import type { RestTransaction, TransactionType } from '../../api/types';
import { apiDateTo, inRange, isoDate } from '../../lib/dates';
import { parseAmount } from '../../lib/money';

export type Period = 'ALL' | 'TODAY' | 'WEEK' | 'MONTH' | 'YEAR';

export interface LedgerFilter {
    period: Period;
    accountId?: number;
    categoryId?: number;
    type?: TransactionType;
    /** Only what this household member recorded. */
    createdBy?: number;
    query: string;
}

/**
 * The period as local instants, [from, to). TransactionsScreen.kt's periods: a week starts
 * on Monday, and "this month" and "this year" run up to the end of today.
 */
export function periodRange(period: Period, now = new Date()): { from?: Date; to?: Date } {
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);
    switch (period) {
        case 'ALL':
            return {};
        case 'TODAY':
            return { from: today, to: tomorrow };
        case 'WEEK': {
            const mondayOffset = (today.getDay() + 6) % 7;
            return { from: new Date(today.getFullYear(), today.getMonth(), today.getDate() - mondayOffset), to: tomorrow };
        }
        case 'MONTH':
            return { from: new Date(today.getFullYear(), today.getMonth(), 1), to: tomorrow };
        case 'YEAR':
            return { from: new Date(today.getFullYear(), 0, 1), to: tomorrow };
    }
}

const PAGE_SIZE = 500;
const MAX_ROWS = 5000;

/**
 * Every row of the ledger inside a range, as RestTransactionRepository.observeFiltered
 * fetched it: pages of 500 up to 5000 rows, the day bound sent the way the server reads it,
 * and then filtered on the instant as well.
 */
export async function fetchLedger(q: {
    from?: Date;
    to?: Date;
    accountId?: number;
    categoryId?: number;
    type?: TransactionType;
    createdBy?: number;
}): Promise<RestTransaction[]> {
    const rows: RestTransaction[] = [];
    let page = 0;
    let totalPages = 1;
    do {
        const res = await endpoints.transactions({
            accountId: q.accountId,
            categoryId: q.categoryId,
            type: q.type,
            createdBy: q.createdBy,
            dateFrom: q.from ? isoDate(q.from) : undefined,
            dateTo: q.to ? apiDateTo(q.to) : undefined,
            page,
            size: PAGE_SIZE,
        });
        rows.push(...res.content);
        totalPages = res.totalPages;
        page++;
    } while (page < totalPages && rows.length < MAX_ROWS);
    return rows.filter(r => inRange(r.occurredAt, q.from, q.to));
}

/** A transfer leg whose amount went out of its account - the source side. */
export const isDebitLeg = (t: RestTransaction) => t.type === 'TRANSFER' && parseAmount(t.accountAmount).isNegative();

/**
 * A transfer is two rows. Show it once: drop the credit leg whenever its debit partner is
 * in the list too. If a filter kept only one leg (the destination account, say), that leg
 * stays.
 */
export function collapseTransfers(rows: RestTransaction[]): RestTransaction[] {
    const withDebit = new Set(rows.filter(isDebitLeg).map(r => r.transferGroupId));
    return rows.filter(r => !(r.transferGroupId && !isDebitLeg(r) && withDebit.has(r.transferGroupId)));
}

/** For each transfer row, the other leg of it, when that leg is among the rows. */
export function counterparts(rows: RestTransaction[]): Map<number, RestTransaction> {
    const byGroup = new Map<string, RestTransaction[]>();
    for (const r of rows) {
        if (!r.transferGroupId) continue;
        byGroup.set(r.transferGroupId, [...(byGroup.get(r.transferGroupId) ?? []), r]);
    }
    const out = new Map<number, RestTransaction>();
    for (const legs of byGroup.values()) {
        for (const leg of legs) {
            const other = legs.find(l => l.id !== leg.id);
            if (other) out.set(leg.id, other);
        }
    }
    return out;
}

/**
 * The search box: the note, the category, either account, the currency, and the amount as
 * written ("15" finds 15.00 and 150.25).
 */
export function matchesQuery(t: RestTransaction, query: string, counterpart?: RestTransaction): boolean {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    const has = (s?: string | null) => !!s && s.toLowerCase().includes(q);
    if (has(t.description) || has(t.category?.name) || has(t.account.name) || has(counterpart?.account.name) || has(t.currencyCode)) {
        return true;
    }
    return /\d/.test(q) && parseAmount(t.accountAmount).abs().toFixed(t.account.currency.decimals).includes(q);
}

/** Rows grouped by the local day they happened on, newest day first. */
export function groupByDay(rows: RestTransaction[]): { day: string; rows: RestTransaction[] }[] {
    const groups = new Map<string, RestTransaction[]>();
    for (const r of rows) {
        const day = isoDate(new Date(r.occurredAt));
        groups.set(day, [...(groups.get(day) ?? []), r]);
    }
    return [...groups.entries()]
        .sort(([a], [b]) => (a < b ? 1 : a > b ? -1 : 0))
        .map(([day, dayRows]) => ({
            day,
            rows: dayRows.sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime()),
        }));
}
