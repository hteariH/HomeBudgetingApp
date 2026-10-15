import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ExternalLink, Landmark, Trash2 } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useAccounts, useCategories } from '../../api/hooks';
import { invalidateLedger, qk, queryClient } from '../../api/queryClient';
import type { RestBankConnection, RestBankProvider, RestConsentChallenge, RestPendingImport, RestImportResult } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Dialog } from '../../components/Dialog';
import { ErrorBanner, errorText } from '../../components/ErrorBanner';
import { Card, Field } from '../../components/forms';
import { MoneyText } from '../../components/MoneyText';
import { QrRows } from '../../components/QrRows';
import { Page, PageLoading } from '../../shell/Page';
import { splitAccounts } from '../accounts/accounts.logic';
import { defaultImportRange, importSelections, minorToMoney, suggestedChoices, type Choice } from './banks.logic';
import { CsvImportCard } from './CsvImportCard';
import { ImportRows } from './ImportRows';

const CONSENT_POLL_MS = 2_500;
const refreshConnections = () => queryClient.invalidateQueries({ queryKey: [...qk.bank, 'connections'] });

/** Asking the bank for a connection the user approves in the bank's own app. Port of ConsentDialog. */
const ConsentDialog: React.FC<{ provider: RestBankProvider; onClose: () => void }> = ({ provider, onClose }) => {
    const t = useT();
    const [attempt, setAttempt] = useState(0);
    const [challenge, setChallenge] = useState<RestConsentChallenge | null>(null);
    const [error, setError] = useState<string | null>(null);
    // Kept in refs so a re-render of the page behind the dialog does not restart the request -
    // each restart would ask the bank for a fresh approval and invalidate the QR on screen.
    const closeRef = useRef(onClose);
    const tRef = useRef(t);
    closeRef.current = onClose;
    tRef.current = t;

    useEffect(() => {
        let alive = true;
        let timer: ReturnType<typeof setTimeout>;
        setChallenge(null);
        setError(null);
        (async () => {
            try {
                const c = await endpoints.beginBankConsent(provider.id);
                if (!alive) return;
                setChallenge(c);
                const poll = async () => {
                    try {
                        const status = await endpoints.bankConsentStatus(provider.id, c.reference);
                        if (!alive) return;
                        if (status.status.toUpperCase() === 'CONNECTED') {
                            await refreshConnections();
                            closeRef.current();
                            return;
                        }
                        timer = setTimeout(poll, CONSENT_POLL_MS);
                    } catch (e) {
                        // Terminal: expired or refused. The dialog stays, holding the reason,
                        // rather than leaving a QR that stopped working and no word why.
                        if (alive) setError(errorText(e, tRef.current));
                    }
                };
                timer = setTimeout(poll, CONSENT_POLL_MS);
            } catch (e) {
                if (alive) setError(errorText(e, tRef.current));
            }
        })();
        return () => {
            alive = false;
            clearTimeout(timer);
        };
    }, [provider.id, attempt]);

    return (
        <Dialog
            open
            onClose={onClose}
            title={t('banks_connect_title', { 0: provider.displayName })}
            footer={
                <>
                    {error && (
                        <button type="button" className="btn btn-ghost" onClick={() => setAttempt(a => a + 1)}>
                            {t('banks_consent_retry')}
                        </button>
                    )}
                    <button type="button" className="btn btn-ghost" onClick={onClose}>
                        {t('action_cancel')}
                    </button>
                </>
            }
        >
            <div className="flex flex-col gap-3">
                {error ? (
                    <div className="text-bad">{error}</div>
                ) : !challenge ? (
                    <div className="loading p-6">
                        <div className="spinner" />
                    </div>
                ) : (
                    <>
                        <p>{t('banks_consent_scan', { 0: provider.displayName })}</p>
                        <QrRows rows={challenge.qrRows ?? []} />
                        <a href={challenge.approveUrl} target="_blank" rel="noopener noreferrer" className="btn btn-secondary self-center">
                            {t('banks_consent_open')} <ExternalLink size={14} />
                        </a>
                        <div className="text-center text-xs text-ink-dim">{t('banks_consent_waiting')}</div>
                    </>
                )}
            </div>
        </Dialog>
    );
};

/**
 * Connecting with credentials the user types - a personal API token, typically. Left open on
 * failure: a rejected token is a typo often enough that throwing the form away would be the
 * wrong answer to the commonest case.
 */
const ConnectDialog: React.FC<{ provider: RestBankProvider; onClose: () => void }> = ({ provider, onClose }) => {
    const t = useT();
    const [values, setValues] = useState<Record<string, string>>({});
    const fields = provider.credentialFields ?? [];
    const canSubmit = fields.filter(f => f.required !== false).every(f => values[f.name]?.trim());
    const connect = useMutation({
        mutationFn: () => endpoints.connectBank({ provider: provider.id, credentials: values }),
        onSuccess: async () => {
            await refreshConnections();
            onClose();
        },
    });
    return (
        <Dialog
            open
            onClose={onClose}
            title={t('banks_connect_title', { 0: provider.displayName })}
            footer={
                <>
                    <button type="button" className="btn btn-ghost" onClick={onClose}>
                        {t('action_cancel')}
                    </button>
                    <button type="button" className="btn btn-primary" disabled={!canSubmit || connect.isPending} onClick={() => connect.mutate()}>
                        {connect.isPending ? t('banks_checking') : t('banks_check_and_connect')}
                    </button>
                </>
            }
        >
            <form
                className="flex flex-col gap-4"
                onSubmit={e => {
                    e.preventDefault();
                    if (canSubmit) connect.mutate();
                }}
            >
                {fields.map(f => (
                    <Field key={f.name} label={f.label} hint={f.helpText ?? undefined}>
                        <input
                            className="form-control"
                            type={f.type?.toUpperCase() === 'SECRET' ? 'password' : 'text'}
                            autoComplete="off"
                            placeholder={f.placeholder ?? undefined}
                            value={values[f.name] ?? ''}
                            onChange={e => setValues(v => ({ ...v, [f.name]: e.target.value }))}
                        />
                    </Field>
                ))}
                {provider.helpUrl && (
                    <a href={provider.helpUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-sm text-brand-light">
                        {t('banks_where_from')} <ExternalLink size={14} />
                    </a>
                )}
                <ErrorBanner error={connect.error} />
                <button type="submit" hidden />
            </form>
        </Dialog>
    );
};

const ConnectionCard: React.FC<{ connection: RestBankConnection; accounts: ReturnType<typeof splitAccounts>['active']; linkedIds: Set<number> }> = ({
    connection,
    accounts,
    linkedIds,
}) => {
    const t = useT();
    const [open, setOpen] = useState(false);
    const [confirm, setConfirm] = useState(false);
    const remote = useQuery({
        queryKey: [...qk.bank, 'remote', connection.id],
        queryFn: () => endpoints.remoteAccounts(connection.id),
        enabled: open,
    });
    const act = useMutation({
        mutationFn: (fn: () => Promise<unknown>) => fn(),
        onSuccess: async () => {
            await refreshConnections();
            await queryClient.invalidateQueries({ queryKey: [...qk.bank, 'remote', connection.id] });
            await queryClient.invalidateQueries({ queryKey: qk.accounts });
        },
    });
    const invalid = connection.status.toUpperCase() === 'INVALID';

    return (
        <Card>
            <div className="flex flex-wrap items-center gap-3 px-5 py-4">
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand/15 text-brand-light">
                    <Landmark size={20} />
                </span>
                <div className="min-w-0 flex-1">
                    <div className="font-medium text-ink">{connection.providerDisplayName}</div>
                    <div className="text-xs text-ink-soft">
                        {connection.label ?? t('common_none')}
                        {invalid && <span className="ml-2 text-bad">{t('banks_credentials_rejected')}</span>}
                    </div>
                </div>
                <button type="button" className="btn btn-ghost btn-sm" disabled={act.isPending} onClick={() => act.mutate(() => endpoints.verifyBank(connection.id))}>
                    {t('banks_verify')}
                </button>
                <button type="button" className="btn btn-ghost btn-sm" aria-expanded={open} onClick={() => setOpen(o => !o)}>
                    {t('banks_accounts')}
                </button>
                <button type="button" className="btn btn-ghost btn-icon" aria-label={t('banks_disconnect')} title={t('banks_disconnect')} onClick={() => setConfirm(true)}>
                    <Trash2 size={16} />
                </button>
            </div>
            <ErrorBanner error={act.error ?? remote.error} className="mx-5 mb-3" />
            {open && (
                <div className="border-t border-line px-5 py-3">
                    {remote.isLoading ? (
                        <div className="text-sm text-ink-soft">{t('banks_loading_accounts')}</div>
                    ) : (
                        <>
                            <div className="mb-2 text-xs font-semibold text-ink-soft">{t('banks_at_the_bank')}</div>
                            {(remote.data ?? []).map(r => {
                                const linkedTo = accounts.find(a => a.id === r.linkedAccountId);
                                // Only accounts in the same currency and not linked elsewhere.
                                const candidates = accounts.filter(a => !linkedIds.has(a.id) && a.currency.code === r.currencyCode);
                                return (
                                    <div key={r.externalId} className="flex flex-wrap items-center gap-3 py-2">
                                        <div className="min-w-[160px] flex-1">
                                            <div className="text-sm text-ink">{r.displayName}</div>
                                            <MoneyText money={minorToMoney(r.balanceMinor, r.currencyCode)} className="text-xs text-ink-soft" />
                                        </div>
                                        {linkedTo ? (
                                            <>
                                                <span className="text-xs text-ink-soft">
                                                    {t('banks_linked_to')} <span className="font-medium text-ink">{linkedTo.name}</span>
                                                </span>
                                                <button type="button" className="btn btn-ghost btn-sm" onClick={() => act.mutate(() => endpoints.unlinkAccount(linkedTo.id))}>
                                                    {t('banks_unlink')}
                                                </button>
                                            </>
                                        ) : (
                                            <select
                                                aria-label={t('banks_link_to')}
                                                className="form-control w-auto py-1.5 text-sm"
                                                value=""
                                                onChange={e =>
                                                    e.target.value &&
                                                    act.mutate(() =>
                                                        endpoints.linkAccount(connection.id, Number(e.target.value), { externalAccountId: r.externalId }),
                                                    )
                                                }
                                            >
                                                <option value="">{t('banks_link_to')}</option>
                                                {candidates.map(a => (
                                                    <option key={a.id} value={a.id}>
                                                        {a.name}
                                                    </option>
                                                ))}
                                            </select>
                                        )}
                                    </div>
                                );
                            })}
                        </>
                    )}
                </div>
            )}
            <ConfirmDialog
                open={confirm}
                title={t('banks_disconnect')}
                message={t('banks_confirm_disconnect')}
                confirmLabel={t('banks_disconnect')}
                destructive
                onConfirm={() => {
                    setConfirm(false);
                    act.mutate(() => endpoints.disconnectBank(connection.id));
                }}
                onCancel={() => setConfirm(false)}
            />
        </Card>
    );
};

/** Importing a statement for a linked account over a range of days. */
const BankImportCard: React.FC<{ linked: ReturnType<typeof splitAccounts>['active']; all: ReturnType<typeof splitAccounts>['active'] }> = ({
    linked,
    all,
}) => {
    const t = useT();
    const { data: categories = [] } = useCategories();
    const [accountId, setAccountId] = useState<number | null>(null);
    const [range, setRange] = useState(defaultImportRange);
    const [rows, setRows] = useState<RestPendingImport[] | null>(null);
    const [choices, setChoices] = useState<Record<string, Choice | undefined>>({});
    const [result, setResult] = useState<RestImportResult | null>(null);
    const account = linked.find(a => a.id === accountId);
    const fresh = (rows ?? []).filter(r => !r.alreadyImported);

    // A range aimed at an account that has since been unlinked cannot be imported into.
    useEffect(() => {
        if (accountId && !linked.some(a => a.id === accountId)) {
            setAccountId(null);
            setRows(null);
        }
    }, [linked, accountId]);

    const preview = useMutation({
        mutationFn: () => endpoints.previewBankImport({ accountId: accountId!, dateFrom: range.from, dateTo: range.to }),
        onMutate: () => setResult(null),
        onSuccess: r => {
            setRows(r);
            // The server's MCC guess, and still only a suggestion.
            setChoices(suggestedChoices(r));
        },
    });
    const confirm = useMutation({
        mutationFn: () => {
            const { categories: cats, transfers } = importSelections(fresh.map(r => r.externalId), choices);
            return endpoints.confirmBankImport({ accountId: accountId!, dateFrom: range.from, dateTo: range.to, categories: cats, transfers });
        },
        onSuccess: async r => {
            setRows(null);
            setChoices({});
            setResult(r);
            await invalidateLedger();
        },
    });
    const change = (patch: Partial<typeof range>) => {
        setRange(r => ({ ...r, ...patch }));
        setRows(null);
        setResult(null);
    };

    return (
        <Card title={t('banks_import_title')}>
            <div className="flex flex-col gap-4 px-5 pb-5 pt-2">
                <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_150px_150px]">
                    <Field label={t('common_account')}>
                        <select
                            className="form-control"
                            value={accountId ?? ''}
                            onChange={e => {
                                setAccountId(Number(e.target.value) || null);
                                setRows(null);
                                setResult(null);
                            }}
                        >
                            <option value="">{t('common_choose')}</option>
                            {linked.map(a => (
                                <option key={a.id} value={a.id}>
                                    {a.name}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label={t('common_from')}>
                        <input type="date" className="form-control" value={range.from} onChange={e => e.target.value && change({ from: e.target.value })} />
                    </Field>
                    <Field label={t('common_to')}>
                        <input type="date" className="form-control" value={range.to} onChange={e => e.target.value && change({ to: e.target.value })} />
                    </Field>
                </div>
                <button type="button" className="btn btn-secondary self-start" disabled={!accountId || preview.isPending} onClick={() => preview.mutate()}>
                    {t('banks_preview')}
                </button>
                <ErrorBanner error={preview.error ?? confirm.error} />
                {result && <div className="text-sm text-ok">{t('banks_imported', { 0: result.imported, 1: result.skipped })}</div>}
                {rows && account && (
                    <>
                        <div className="text-sm text-ink-soft">{t('csv_summary', { 0: fresh.length, 1: rows.length - fresh.length })}</div>
                        <ImportRows
                            rows={rows}
                            currency={account.currency.code}
                            accountId={account.id}
                            accounts={all}
                            categories={categories}
                            choices={choices}
                            onChoose={(id, c) => setChoices(prev => ({ ...prev, [id]: c }))}
                        />
                        <button type="button" className="btn btn-primary self-start" disabled={fresh.length === 0 || confirm.isPending} onClick={() => confirm.mutate()}>
                            {t('banks_import_count', { 0: fresh.length })}
                        </button>
                    </>
                )}
            </div>
        </Card>
    );
};

/** Bank connections, statement import, and CSV import. Port of BanksScreen.kt. */
const BanksPage: React.FC = () => {
    const t = useT();
    const providers = useQuery({ queryKey: [...qk.bank, 'providers'], queryFn: endpoints.bankProviders, staleTime: 10 * 60_000 });
    const connections = useQuery({ queryKey: [...qk.bank, 'connections'], queryFn: endpoints.bankConnections });
    const { data: accounts = [] } = useAccounts();
    const { data: categories = [] } = useCategories();
    const [connecting, setConnecting] = useState<RestBankProvider | null>(null);
    const all = useMemo(() => splitAccounts(accounts).active, [accounts]);
    const linkedIds = useMemo(() => new Set((connections.data ?? []).flatMap(c => c.linkedAccountIds ?? [])), [connections.data]);
    const linked = all.filter(a => linkedIds.has(a.id));

    if (providers.isLoading || connections.isLoading) return <PageLoading />;

    return (
        <Page title={t('banks_title')}>
            <div className="flex flex-col gap-4">
                <div className="text-sm text-ink-soft">{t('banks_connection_count', { count: connections.data?.length ?? 0 })}</div>
                <ErrorBanner error={providers.error ?? connections.error} fallbackKey="common_load_failed" />
                <Card title={t('banks_add_connection')}>
                    <div className="flex flex-wrap gap-2 px-5 pb-5 pt-2">
                        {(providers.data ?? []).length === 0 && <div className="text-sm text-ink-soft">{t('banks_no_providers')}</div>}
                        {(providers.data ?? []).map(p => (
                            <button key={p.id} type="button" className="btn btn-secondary" onClick={() => setConnecting(p)}>
                                <Landmark size={16} /> {p.displayName}
                            </button>
                        ))}
                    </div>
                </Card>
                {(connections.data ?? []).map(c => (
                    <ConnectionCard key={c.id} connection={c} accounts={accounts.filter(a => !a.archived)} linkedIds={linkedIds} />
                ))}
                {linked.length > 0 && <BankImportCard linked={linked} all={accounts} />}
                <CsvImportCard accounts={accounts} categories={categories} />
            </div>
            {connecting &&
                (connecting.authMode?.toUpperCase() === 'CONSENT' ? (
                    <ConsentDialog provider={connecting} onClose={() => setConnecting(null)} />
                ) : (
                    <ConnectDialog provider={connecting} onClose={() => setConnecting(null)} />
                ))}
        </Page>
    );
};

export default BanksPage;
