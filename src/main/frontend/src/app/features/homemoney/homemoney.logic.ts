import type { RestHomeMoneyConnection, RestHomeMoneyPlan, RestHomeMoneyStatus, RestPlannedAccount } from '../../api/types';

/** An account with no existing target and no currency cannot be created - there is nothing to make it in. */
export const needsCurrency = (a: RestPlannedAccount) => a.targetAccountId == null && !a.currencyCode;

/** What the import will create, and whether it can start. Port of HomeMoneyImportPlan. */
export function planFacts(plan: RestHomeMoneyPlan) {
    const accounts = plan.accounts ?? [];
    const categories = plan.categories ?? [];
    const unresolved = accounts.filter(a => a.included && needsCurrency(a));
    return {
        newAccounts: accounts.filter(a => a.included && a.targetAccountId == null).length,
        newCategories: categories.filter(c => c.targetCategoryId == null).length,
        unresolved,
        canStart: !plan.blockedReason && unresolved.length === 0 && accounts.some(a => a.included),
    };
}

export const isRunning = (s?: RestHomeMoneyStatus | null) => s?.state?.toUpperCase() === 'RUNNING';
export const isDone = (s?: RestHomeMoneyStatus | null) => s?.state?.toUpperCase() === 'DONE';
export const isFailed = (s?: RestHomeMoneyStatus | null) => s?.state?.toUpperCase() === 'FAILED';

/** A connection HomeMoney has stopped honouring - it has to be made again. */
export const isInvalid = (c?: RestHomeMoneyConnection | null) => !!c?.connected && c.status?.toUpperCase() === 'INVALID';

/**
 * The redirect from homemoney.ua carries a code and nothing else, so the state written before
 * leaving is what pairs them. With none stored the attempt is stale; say so rather than send
 * a code the server would refuse anyway.
 */
export function exchangePlan(
    redirect: { kind: 'pending'; code: string } | { kind: 'denied' },
    storedState: string | null,
): { kind: 'exchange'; code: string; state: string } | { kind: 'message'; key: 'homemoney_denied' | 'homemoney_link_expired' } {
    if (redirect.kind === 'denied') return { kind: 'message', key: 'homemoney_denied' };
    if (!storedState) return { kind: 'message', key: 'homemoney_link_expired' };
    return { kind: 'exchange', code: redirect.code, state: storedState };
}
