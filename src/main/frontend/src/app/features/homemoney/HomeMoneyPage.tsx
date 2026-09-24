import React, { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useAccounts, useCategories, useCurrencies } from '../../api/hooks';
import { invalidateLedger, qk, queryClient } from '../../api/queryClient';
import type { RestHomeMoneyPlan, RestHomeMoneyStatus, RestPlannedAccount } from '../../api/types';
import { takeHomeMoneyRedirect } from '../../boot';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorBanner, errorText } from '../../components/ErrorBanner';
import { Card, Field } from '../../components/forms';
import { formatMediumDate } from '../../lib/format';
import { prefs } from '../../prefs';
import { Page, PageLoading } from '../../shell/Page';
import { exchangePlan, isDone, isFailed, isInvalid, isRunning, planFacts } from './homemoney.logic';

const POLL_MS = 1_500;

/**
 * Bringing a whole history across from homemoney.ua: connect on their site, check how their
 * accounts and categories map onto these, then let the server work through it while this
 * page watches. Port of HomeMoneyImportScreen.kt and its view model.
 */
const HomeMoneyPage: React.FC = () => {
    const t = useT();
    const { data: accounts = [] } = useAccounts();
    const { data: categories = [] } = useCategories();
    const { data: currencies = [] } = useCurrencies();
    const [message, setMessage] = useState<string | null>(null);
    const [plan, setPlan] = useState<RestHomeMoneyPlan | null>(null);
    const [showCategories, setShowCategories] = useState(false);
    const [confirmDisconnect, setConfirmDisconnect] = useState(false);
    const [exchanging, setExchanging] = useState(false);
    // Read once: the code in it is single-use, and a re-render must not try to spend it again.
    const redirect = useRef(takeHomeMoneyRedirect());

    const connection = useQuery({ queryKey: [...qk.homeMoney, 'connection'], queryFn: endpoints.homeMoneyConnection, enabled: !exchanging });
    const status = useQuery({
        queryKey: [...qk.homeMoney, 'status'],
        queryFn: endpoints.homeMoneyStatus,
        // An import left running by an earlier visit is still running on the server; the
        // watch picks it back up rather than showing a stale figure.
        refetchInterval: q => (isRunning(q.state.data) ? POLL_MS : false),
    });

    // Coming back from homemoney.ua: pair the code with the state stored before leaving.
    useEffect(() => {
        const r = redirect.current;
        if (!r) return;
        redirect.current = null;
        const step = exchangePlan(r, prefs.homeMoneyAuthState);
        // Spent or refused, the stored state has done its job.
        prefs.setHomeMoneyAuthState(null);
        if (step.kind === 'message') {
            setMessage(t(step.key));
            return;
        }
        setExchanging(true);
        endpoints
            .connectHomeMoneyWithCode(step.code, step.state)
            .catch(e => setMessage(errorText(e, t)))
            .finally(() => {
                setExchanging(false);
                queryClient.invalidateQueries({ queryKey: qk.homeMoney });
            });
    }, [t]);

    // The import creates accounts and categories on the server; everything local is behind.
    const wasRunning = useRef(false);
    useEffect(() => {
        if (isRunning(status.data)) wasRunning.current = true;
        else if (wasRunning.current) {
            wasRunning.current = false;
            invalidateLedger();
            queryClient.invalidateQueries({ queryKey: qk.categories });
        }
    }, [status.data]);

    const connect = useMutation({
        mutationFn: endpoints.authorizeHomeMoney,
        onSuccess: auth => {
            // Written before the page leaves: it comes back reloaded from scratch, and
            // homemoney.ua returns no state of its own to pair the code with.
            prefs.setHomeMoneyAuthState(auth.state ?? '');
            window.location.assign(auth.authorizationUrl);
        },
    });
    const disconnect = useMutation({
        mutationFn: endpoints.disconnectHomeMoney,
        onSuccess: () => {
            // The plan belonged to the connection that just went.
            setPlan(null);
            queryClient.invalidateQueries({ queryKey: qk.homeMoney });
        },
    });
    const loadPlan = useMutation({ mutationFn: endpoints.homeMoneyPlan, onSuccess: setPlan });
    const start = useMutation({
        mutationFn: () => endpoints.applyHomeMoney({ accounts: plan!.accounts ?? [], categories: plan!.categories ?? [] }),
        onSuccess: (s: RestHomeMoneyStatus) => {
            wasRunning.current = true;
            queryClient.setQueryData([...qk.homeMoney, 'status'], s);
        },
    });

    const updateAccount = (externalId: string, patch: Partial<RestPlannedAccount>) =>
        setPlan(p => p && { ...p, accounts: (p.accounts ?? []).map(a => (a.externalId === externalId ? { ...a, ...patch } : a)) });
    const setCategoryTarget = (externalId: string, id: number | null) =>
        setPlan(p => p && { ...p, categories: (p.categories ?? []).map(c => (c.externalId === externalId ? { ...c, targetCategoryId: id } : c)) });

    if (connection.isLoading || exchanging) return <PageLoading />;

    const conn = connection.data;
    const facts = plan ? planFacts(plan) : null;
    const progress = status.data;
    const live = accounts.filter(a => !a.archived);

    return (
        <Page title={t('homemoney_title')} width="form">
            <div className="flex flex-col gap-4">
                {message && <div className="alert-error">{message}</div>}
                <ErrorBanner error={connection.error ?? connect.error ?? disconnect.error ?? loadPlan.error ?? start.error} />

                <Card title={t('homemoney_step_connect')}>
                    <div className="flex flex-col gap-3 px-5 pb-5 pt-2 text-sm">
                        {!conn?.connected ? (
                            <>
                                <p className="text-ink-soft">{t('homemoney_intro')}</p>
                                <button type="button" className="btn btn-primary self-start" disabled={connect.isPending} onClick={() => connect.mutate()}>
                                    {t('homemoney_connect')}
                                </button>
                            </>
                        ) : (
                            <>
                                <div className="text-ink">{t('homemoney_connected_as', { 0: conn.label ?? '—' })}</div>
                                {isInvalid(conn) && <div className="text-bad">{t('homemoney_invalid')}</div>}
                                <div className="flex flex-wrap gap-2">
                                    <button type="button" className="btn btn-secondary" disabled={loadPlan.isPending || isRunning(progress)} onClick={() => loadPlan.mutate()}>
                                        {t('homemoney_load_plan')}
                                    </button>
                                    <button type="button" className="btn btn-ghost" onClick={() => setConfirmDisconnect(true)}>
                                        {t('homemoney_disconnect')}
                                    </button>
                                </div>
                            </>
                        )}
                    </div>
                </Card>

                {plan && facts && (
                    <Card title={t('homemoney_step_review')}>
                        <div className="flex flex-col gap-4 px-5 pb-5 pt-2 text-sm">
                            <div className="text-ink-soft">
                                {t('homemoney_summary', { 0: plan.accounts?.length ?? 0, 1: plan.categories?.length ?? 0, 2: plan.transactionCount ?? 0 })}
                                {plan.firstDate && plan.lastDate && (
                                    <div>{t('homemoney_range', { 0: formatMediumDate(plan.firstDate), 1: formatMediumDate(plan.lastDate) })}</div>
                                )}
                            </div>
                            {plan.blockedReason && <div className="text-bad">{plan.blockedReason}</div>}

                            <div>
                                <div className="font-semibold text-ink">{t('homemoney_accounts_title')}</div>
                                <p className="mt-1 text-xs text-ink-dim">{t('homemoney_accounts_hint')}</p>
                            </div>
                            {(plan.accounts ?? []).map(a => (
                                <div key={a.externalId} className={`flex flex-col gap-2 rounded-token border border-line p-3 ${a.included ? '' : 'opacity-60'}`}>
                                    <label className="flex items-center gap-2">
                                        <input type="checkbox" checked={a.included} onChange={e => updateAccount(a.externalId, { included: e.target.checked })} />
                                        <span className="flex-1 font-medium text-ink">{a.name}</span>
                                        <span className="text-xs text-ink-soft">
                                            {a.balance} {a.currencyLabel}
                                        </span>
                                    </label>
                                    {a.included && (
                                        <>
                                            <Field label={t('homemoney_target_account')}>
                                                <select
                                                    className="form-control"
                                                    value={a.targetAccountId ?? ''}
                                                    // Pointing at an existing account settles the currency too.
                                                    onChange={e => updateAccount(a.externalId, { targetAccountId: Number(e.target.value) || null })}
                                                >
                                                    <option value="">{t('homemoney_create_new')}</option>
                                                    {live.map(x => (
                                                        <option key={x.id} value={x.id}>
                                                            {x.name} · {x.currency.code}
                                                        </option>
                                                    ))}
                                                </select>
                                            </Field>
                                            {a.targetAccountId == null && (
                                                <>
                                                    {!a.currencyCode && (
                                                        <div className="text-xs text-warn">{t('homemoney_currency_unknown', { 0: a.currencyLabel })}</div>
                                                    )}
                                                    <Field label={t('homemoney_choose_currency')}>
                                                        <select
                                                            className="form-control"
                                                            value={a.currencyCode ?? ''}
                                                            onChange={e => updateAccount(a.externalId, { currencyCode: e.target.value || null })}
                                                        >
                                                            <option value="">—</option>
                                                            {currencies.map(c => (
                                                                <option key={c.code} value={c.code}>
                                                                    {c.code} · {c.name}
                                                                </option>
                                                            ))}
                                                        </select>
                                                    </Field>
                                                    {a.currencyCode && <div className="text-xs text-ink-dim">{t('homemoney_will_create', { 0: a.currencyCode })}</div>}
                                                </>
                                            )}
                                        </>
                                    )}
                                </div>
                            ))}

                            <div className="flex items-center gap-3">
                                <div className="flex-1">
                                    <div className="font-semibold text-ink">{t('homemoney_categories_title')}</div>
                                    <div className="text-xs text-ink-soft">
                                        {t('homemoney_categories_summary', { 0: plan.categories?.length ?? 0, 1: facts.newCategories })}
                                    </div>
                                </div>
                                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowCategories(s => !s)}>
                                    {showCategories ? t('homemoney_hide_categories') : t('homemoney_review_categories')}
                                </button>
                            </div>
                            {showCategories &&
                                (plan.categories ?? []).map(c => (
                                    <div key={c.externalId} className="flex flex-wrap items-center gap-2">
                                        <span className="min-w-[160px] flex-1 text-ink">{c.fullName}</span>
                                        <select
                                            aria-label={t('homemoney_target_category')}
                                            className="form-control w-auto py-1.5 text-sm"
                                            value={c.targetCategoryId ?? ''}
                                            onChange={e => setCategoryTarget(c.externalId, Number(e.target.value) || null)}
                                        >
                                            <option value="">{t('homemoney_create_new')}</option>
                                            {categories
                                                .filter(x => x.type === c.type && !x.archived)
                                                .map(x => (
                                                    <option key={x.id} value={x.id}>
                                                        {x.name}
                                                    </option>
                                                ))}
                                        </select>
                                    </div>
                                ))}

                            {facts.unresolved.length > 0 && (
                                <div className="text-warn">{t('homemoney_currency_needed', { 0: facts.unresolved.length })}</div>
                            )}
                            <button
                                type="button"
                                className="btn btn-primary self-start"
                                disabled={!facts.canStart || start.isPending || isRunning(progress)}
                                onClick={() => start.mutate()}
                            >
                                {t('homemoney_start')}
                            </button>
                            <p className="text-xs text-ink-dim">{t('homemoney_rerun_note')}</p>
                        </div>
                    </Card>
                )}

                {progress && !['IDLE', undefined].includes(progress.state?.toUpperCase()) && (
                    <Card title={t('homemoney_step_import')}>
                        <div className="flex flex-col gap-2 px-5 pb-5 pt-2 text-sm">
                            {isRunning(progress) && (
                                <div className="flex items-center gap-3 text-ink-soft">
                                    <div className="spinner spinner-sm border-line border-t-brand" />
                                    {t('homemoney_running', { 0: progress.processedChunks ?? 0 })}
                                </div>
                            )}
                            {isDone(progress) && <div className="text-ok">{t('homemoney_done')}</div>}
                            {isFailed(progress) && <div className="text-bad">{progress.error ?? t('common_request_failed')}</div>}
                            <div className="text-ink-soft">
                                {t('homemoney_counts', {
                                    0: progress.accountsCreated ?? 0,
                                    1: progress.categoriesCreated ?? 0,
                                    2: progress.imported ?? 0,
                                    3: progress.skipped ?? 0,
                                })}
                            </div>
                        </div>
                    </Card>
                )}
            </div>
            <ConfirmDialog
                open={confirmDisconnect}
                title={t('homemoney_disconnect')}
                message={t('homemoney_confirm_disconnect')}
                confirmLabel={t('homemoney_disconnect')}
                destructive
                onConfirm={() => {
                    setConfirmDisconnect(false);
                    disconnect.mutate();
                }}
                onCancel={() => setConfirmDisconnect(false)}
            />
        </Page>
    );
};

export default HomeMoneyPage;
