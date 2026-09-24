import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { qk } from '../../api/queryClient';
import type { TransactionType } from '../../api/types';
import { fetchLedger, periodRange, type LedgerFilter, type Period } from './ledger.logic';

const PERIODS: Period[] = ['ALL', 'TODAY', 'WEEK', 'MONTH', 'YEAR'];
const TYPES: TransactionType[] = ['EXPENSE', 'INCOME', 'TRANSFER'];

/**
 * The ledger's filter, kept in the address. That is what lets the overview open the ledger
 * on one account with an ordinary link - the Compose client needed a shared request object
 * for it - and it makes a filtered ledger something a reload or a bookmark keeps.
 */
export function useLedgerFilter(): [LedgerFilter, (patch: Partial<LedgerFilter>) => void, () => void] {
    const [params, setParams] = useSearchParams();
    const num = (key: string) => Number(params.get(key)) || undefined;
    const period = params.get('period') as Period | null;
    const type = params.get('type') as TransactionType | null;
    const filter: LedgerFilter = {
        period: period && PERIODS.includes(period) ? period : 'ALL',
        accountId: num('accountId'),
        categoryId: num('categoryId'),
        type: type && TYPES.includes(type) ? type : undefined,
        query: params.get('q') ?? '',
    };

    const update = (patch: Partial<LedgerFilter>) => {
        const next = { ...filter, ...patch };
        const out = new URLSearchParams();
        if (next.period !== 'ALL') out.set('period', next.period);
        if (next.accountId) out.set('accountId', String(next.accountId));
        if (next.categoryId) out.set('categoryId', String(next.categoryId));
        if (next.type) out.set('type', next.type);
        if (next.query) out.set('q', next.query);
        setParams(out, { replace: true });
    };

    return [filter, update, () => setParams(new URLSearchParams(), { replace: true })];
}

export function useLedger(filter: Omit<LedgerFilter, 'query'>) {
    const range = periodRange(filter.period);
    return useQuery({
        queryKey: qk.transactions({ ...filter, day: new Date().toDateString() }),
        queryFn: () => fetchLedger({ ...range, accountId: filter.accountId, categoryId: filter.categoryId, type: filter.type }),
    });
}
