import React, { useCallback, useEffect, useState } from 'react';
import { accountApi, bankApi, categoryApi } from '../api';
import {
    Account, BankConnection, BankProvider, Category, CredentialField, PendingImport, RemoteAccount,
} from '../types';
import { formatAmount } from '../money';
import CsvImport from './CsvImport';
import { Plus, Link2, Unlink, RefreshCw, Trash2, X, Download, AlertTriangle, Building2 } from 'lucide-react';
import { useT } from '../i18n';

const today = () => new Date().toISOString().slice(0, 10);
const monthAgo = () => new Date(Date.now() - 30 * 864e5).toISOString().slice(0, 10);

/**
 * Bank connections and statement import.
 *
 * Nothing on this page knows which banks exist. The provider list and each provider's
 * connect form come from the server, so adding a bank is a server-side change only -
 * which is the whole reason the credential form is rendered from metadata instead of
 * being written per bank.
 *
 * The markup used to reach for .page-header / .card-header / .page-subtitle, none of
 * which the stylesheet defines; it now uses the same section-header and card patterns
 * as every other page, which is also what stops the headings from collapsing to body
 * text under Tailwind's preflight reset.
 */
const Banks: React.FC = () => {
    const t = useT();
    const [providers, setProviders] = useState<BankProvider[]>([]);
    const [connections, setConnections] = useState<BankConnection[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Connect dialog
    const [connectProvider, setConnectProvider] = useState<BankProvider | null>(null);
    const [credentials, setCredentials] = useState<Record<string, string>>({});
    const [connecting, setConnecting] = useState(false);
    const [connectError, setConnectError] = useState<string | null>(null);

    // Linking
    const [openConnectionId, setOpenConnectionId] = useState<number | null>(null);
    const [remoteAccounts, setRemoteAccounts] = useState<RemoteAccount[]>([]);
    const [remoteBusy, setRemoteBusy] = useState(false);

    // Import
    const [importAccountId, setImportAccountId] = useState<number | null>(null);
    const [dateFrom, setDateFrom] = useState(monthAgo());
    const [dateTo, setDateTo] = useState(today());
    const [pending, setPending] = useState<PendingImport[] | null>(null);
    const [chosen, setChosen] = useState<Record<string, number | null>>({});
    const [importBusy, setImportBusy] = useState(false);
    const [importMessage, setImportMessage] = useState<string | null>(null);

    const reload = useCallback(async () => {
        setLoading(true);
        try {
            const [p, c, a, cat] = await Promise.all([
                bankApi.providers(), bankApi.connections(), accountApi.getAll(), categoryApi.getAll(),
            ]);
            setProviders(p); setConnections(c); setAccounts(a); setCategories(cat);
            setError(null);
        } catch (e: any) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { reload(); }, [reload]);

    const openConnect = (provider: BankProvider) => {
        setConnectProvider(provider);
        setCredentials({});
        setConnectError(null);
    };

    const submitConnect = async () => {
        if (!connectProvider) return;
        setConnecting(true);
        setConnectError(null);
        try {
            // The server checks the credentials with the bank before storing them, so
            // a mistyped token fails here rather than becoming a dead connection.
            await bankApi.connect(connectProvider.id, credentials);
            setConnectProvider(null);
            await reload();
        } catch (e: any) {
            setConnectError(e.message);
        } finally {
            setConnecting(false);
        }
    };

    const showAccounts = async (connectionId: number) => {
        if (openConnectionId === connectionId) { setOpenConnectionId(null); return; }
        setOpenConnectionId(connectionId);
        setRemoteBusy(true);
        try {
            setRemoteAccounts(await bankApi.remoteAccounts(connectionId));
        } catch (e: any) {
            setError(e.message);
            setOpenConnectionId(null);
        } finally {
            setRemoteBusy(false);
        }
    };

    const runPreview = async () => {
        if (!importAccountId) return;
        setImportBusy(true);
        setImportMessage(null);
        try {
            const rows = await bankApi.preview(importAccountId, dateFrom, dateTo);
            setPending(rows);
            // Pre-fill from the learned MCC mapping; the user still confirms.
            setChosen(Object.fromEntries(rows.map(r => [r.externalId, r.suggestedCategoryId ?? null])));
        } catch (e: any) {
            setImportMessage(e.message);
        } finally {
            setImportBusy(false);
        }
    };

    const runConfirm = async () => {
        if (!importAccountId || !pending) return;
        setImportBusy(true);
        try {
            const selected = Object.fromEntries(
                pending.filter(r => !r.alreadyImported).map(r => [r.externalId, chosen[r.externalId] ?? null]),
            );
            const result = await bankApi.confirm(importAccountId, dateFrom, dateTo, selected);
            setImportMessage(t('banks.imported', { imported: result.imported, skipped: result.skipped }));
            setPending(null);
            await reload();
        } catch (e: any) {
            setImportMessage(e.message);
        } finally {
            setImportBusy(false);
        }
    };

    const linkedAccounts = accounts.filter(a => a.bankConnectionId);
    const accountOf = (id?: number | null) => accounts.find(a => a.id === id);
    const freshCount = pending?.filter(r => !r.alreadyImported).length ?? 0;

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>{t('banks.title')}</h1>
                    <p className="text-muted text-sm">{t('banks.connectionCount', { count: connections.length })}</p>
                </div>
            </div>

            {error && (
                <div className="alert-error mb-5" role="alert">
                    <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                    <span>{error}</span>
                </div>
            )}

            {/* ── Providers ── */}
            <div className="card mb-6">
                <p className="section-title mb-4">{t('banks.addConnection')}</p>
                <div className="flex flex-wrap gap-3">
                    {providers.map(p => (
                        <button key={p.id} className="btn btn-secondary" onClick={() => openConnect(p)}>
                            <Plus size={14} /> {p.displayName}
                        </button>
                    ))}
                    {providers.length === 0 && <p className="text-muted text-sm">{t('banks.noProviders')}</p>}
                </div>
            </div>

            {/* ── Connections ── */}
            {connections.map(conn => (
                <div className="card mb-4" key={conn.id}>
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <div className="flex min-w-0 items-center gap-2.5">
                            <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-token bg-raised">
                                <Building2 size={17} className="text-ink-soft" />
                            </span>
                            <div className="min-w-0">
                                <p className="truncate text-[15px] font-bold">{conn.providerDisplayName}</p>
                                <p className="text-muted text-sm">
                                    {conn.label ?? t('common.none')}
                                    {conn.status === 'INVALID' && (
                                        <span className="text-red ml-2 inline-flex items-center gap-1">
                                            <AlertTriangle size={12} /> {t('banks.credentialsRejected')}
                                        </span>
                                    )}
                                </p>
                            </div>
                        </div>
                        <div className="flex flex-shrink-0 gap-1.5">
                            <button
                                className="btn btn-ghost btn-sm"
                                onClick={() => bankApi.verify(conn.id).then(reload).catch(e => setError(e.message))}
                            >
                                <RefreshCw size={13} /> {t('banks.verify')}
                            </button>
                            <button className="btn btn-ghost btn-sm" onClick={() => showAccounts(conn.id)}>
                                <Link2 size={13} /> {t('banks.accounts')}
                            </button>
                            <button
                                className="btn btn-ghost btn-icon btn-sm"
                                style={{ color: 'var(--red)' }}
                                title={t('banks.disconnect')}
                                aria-label={t('banks.disconnect')}
                                onClick={() => {
                                    if (window.confirm(t('banks.confirmDisconnect'))) {
                                        bankApi.disconnect(conn.id).then(reload);
                                    }
                                }}
                            >
                                <Trash2 size={13} />
                            </button>
                        </div>
                    </div>

                    {openConnectionId === conn.id && (
                        <div className="mt-4 border-t border-line pt-4">
                            {remoteBusy ? (
                                <p className="text-muted text-sm">{t('banks.loadingAccounts')}</p>
                            ) : (
                                <div className="table-wrap">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th>{t('banks.atTheBank')}</th>
                                                <th>{t('accounts.currency')}</th>
                                                <th>{t('banks.balance')}</th>
                                                <th>{t('banks.linkedTo')}</th>
                                                <th />
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {remoteAccounts.map(ra => (
                                                <tr key={ra.externalId}>
                                                    <td>{ra.displayName}</td>
                                                    <td>{ra.currencyCode}</td>
                                                    <td>{(ra.balanceMinor / 100).toFixed(2)}</td>
                                                    <td>
                                                        {accountOf(ra.linkedAccountId)?.name ?? (
                                                            <span className="text-muted">{t('common.none')}</span>
                                                        )}
                                                    </td>
                                                    <td className="text-right">
                                                        {ra.linkedAccountId ? (
                                                            <button
                                                                className="btn btn-ghost btn-sm"
                                                                onClick={() => bankApi.unlink(ra.linkedAccountId!).then(() => showAccounts(conn.id)).then(reload)}
                                                            >
                                                                <Unlink size={13} /> {t('banks.unlink')}
                                                            </button>
                                                        ) : (
                                                            <select
                                                                className="form-control"
                                                                defaultValue=""
                                                                onChange={e => {
                                                                    const accountId = Number(e.target.value);
                                                                    if (accountId) {
                                                                        bankApi.link(conn.id, accountId, ra.externalId)
                                                                            .then(() => showAccounts(conn.id)).then(reload)
                                                                            .catch(err => setError(err.message));
                                                                    }
                                                                }}
                                                            >
                                                                <option value="">{t('banks.linkTo')}</option>
                                                                {accounts
                                                                    .filter(a => !a.bankConnectionId && a.currency.code === ra.currencyCode)
                                                                    .map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                                                            </select>
                                                        )}
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            ))}

            {/* ── Import ── */}
            {linkedAccounts.length > 0 && (
                <div className="card">
                    <p className="section-title mb-4">{t('banks.importTitle')}</p>

                    <div className="flex flex-wrap items-end gap-3">
                        <div className="form-group min-w-[160px] flex-1">
                            <label className="form-label">{t('common.account')}</label>
                            <select
                                className="form-control"
                                value={importAccountId ?? ''}
                                onChange={e => { setImportAccountId(Number(e.target.value)); setPending(null); }}
                            >
                                <option value="">{t('common.choose')}</option>
                                {linkedAccounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                            </select>
                        </div>
                        <div className="form-group min-w-[140px]">
                            <label className="form-label">{t('common.from')}</label>
                            <input type="date" className="form-control" value={dateFrom} onChange={e => setDateFrom(e.target.value)} />
                        </div>
                        <div className="form-group min-w-[140px]">
                            <label className="form-label">{t('common.to')}</label>
                            <input type="date" className="form-control" value={dateTo} onChange={e => setDateTo(e.target.value)} />
                        </div>
                        <button className="btn btn-primary" disabled={!importAccountId || importBusy} onClick={runPreview}>
                            <Download size={14} /> {t('banks.preview')}
                        </button>
                    </div>

                    {importMessage && <p className="text-muted mt-4 text-sm">{importMessage}</p>}

                    {pending && (
                        <div className="mt-4 border-t border-line pt-4">
                            <p className="text-muted mb-3 text-sm">
                                {t('csv.summary', {
                                    fresh: freshCount,
                                    duplicates: pending.length - freshCount,
                                })}
                            </p>
                            <div className="table-wrap">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>{t('common.date')}</th>
                                            <th>{t('common.description')}</th>
                                            <th>{t('common.amount')}</th>
                                            <th>{t('common.category')}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {pending.map(r => {
                                            const account = accountOf(importAccountId);
                                            return (
                                                <tr key={r.externalId} style={{ opacity: r.alreadyImported ? 0.45 : 1 }}>
                                                    <td className="whitespace-nowrap">{r.date}</td>
                                                    <td>{r.description}</td>
                                                    <td
                                                        className="whitespace-nowrap font-semibold"
                                                        style={{ color: r.type === 'INCOME' ? 'var(--green)' : 'var(--red)' }}
                                                    >
                                                        {r.type === 'INCOME' ? '+' : '-'}
                                                        {account
                                                            ? formatAmount(r.amountMinor / 100, account.currency)
                                                            : (r.amountMinor / 100).toFixed(2)}
                                                    </td>
                                                    <td>
                                                        {r.alreadyImported ? (
                                                            <span className="text-muted">{t('csv.alreadyImported')}</span>
                                                        ) : (
                                                            <select
                                                                className="form-control"
                                                                value={chosen[r.externalId] ?? ''}
                                                                onChange={e => setChosen(c => ({
                                                                    ...c,
                                                                    [r.externalId]: e.target.value ? Number(e.target.value) : null,
                                                                }))}
                                                            >
                                                                <option value="">{t('common.uncategorised')}</option>
                                                                {categories
                                                                    .filter(c => c.type === (r.type === 'INCOME' ? 'INCOME' : 'EXPENSE'))
                                                                    .map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                                            </select>
                                                        )}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                            <button className="btn btn-primary mt-4" disabled={importBusy || freshCount === 0} onClick={runConfirm}>
                                {t('banks.importCount', { count: freshCount })}
                            </button>
                        </div>
                    )}
                </div>
            )}

            {/* ── File import: works for banks with no usable API at all ── */}
            <div className="mt-6">
                <CsvImport accounts={accounts} categories={categories} onImported={reload} />
            </div>

            {/* ── Connect dialog: rendered from the provider's own field list ── */}
            {connectProvider && (
                <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setConnectProvider(null)}>
                    <div className="modal">
                        <div className="modal-header">
                            <h2 className="modal-title">
                                {t('banks.connectTitle', { provider: connectProvider.displayName })}
                            </h2>
                            <button className="btn btn-ghost btn-icon" onClick={() => setConnectProvider(null)} aria-label={t('common.close')}>
                                <X size={16} />
                            </button>
                        </div>
                        <div className="modal-body">
                            {connectProvider.credentialFields.map((f: CredentialField) => (
                                <div className="form-group" key={f.name}>
                                    <label className="form-label">{f.label}</label>
                                    <input
                                        className="form-control"
                                        type={f.type === 'SECRET' ? 'password' : 'text'}
                                        placeholder={f.placeholder ?? ''}
                                        value={credentials[f.name] ?? ''}
                                        onChange={e => setCredentials(c => ({ ...c, [f.name]: e.target.value }))}
                                    />
                                    {f.helpText && <p className="text-muted text-xs">{f.helpText}</p>}
                                </div>
                            ))}

                            {connectProvider.helpUrl && (
                                <p className="text-xs">
                                    <a
                                        href={connectProvider.helpUrl}
                                        target="_blank"
                                        rel="noreferrer"
                                        className="text-accent hover:underline"
                                    >
                                        {t('banks.whereFrom')}
                                    </a>
                                </p>
                            )}

                            {connectError && (
                                <div className="alert-error" role="alert">
                                    <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                                    <span>{connectError}</span>
                                </div>
                            )}
                        </div>
                        <div className="modal-footer">
                            <button className="btn btn-ghost" onClick={() => setConnectProvider(null)}>{t('common.cancel')}</button>
                            <button className="btn btn-primary" onClick={submitConnect} disabled={connecting}>
                                {connecting ? t('banks.checking') : t('banks.checkAndConnect')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Banks;
