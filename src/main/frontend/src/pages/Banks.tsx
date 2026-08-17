import React, { useCallback, useEffect, useState } from 'react';
import { accountApi, bankApi, categoryApi } from '../api';
import {
    Account, BankConnection, BankProvider, Category, CredentialField, PendingImport, RemoteAccount,
} from '../types';
import { formatAmount } from '../money';
import CsvImport from './CsvImport';
import { Plus, Link2, Unlink, RefreshCw, Trash2, X, Download, AlertTriangle, Building2 } from 'lucide-react';

const today = () => new Date().toISOString().slice(0, 10);
const monthAgo = () => new Date(Date.now() - 30 * 864e5).toISOString().slice(0, 10);

/**
 * Bank connections and statement import.
 *
 * Nothing on this page knows which banks exist. The provider list and each provider's
 * connect form come from the server, so adding a bank is a server-side change only -
 * which is the whole reason the credential form is rendered from metadata instead of
 * being written per bank.
 */
const Banks: React.FC = () => {
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
                pending.filter(r => !r.alreadyImported).map(r => [r.externalId, chosen[r.externalId] ?? null])
            );
            const result = await bankApi.confirm(importAccountId, dateFrom, dateTo, selected);
            setImportMessage(`Imported ${result.imported}, skipped ${result.skipped}`);
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

    if (loading) return <div className="page"><p>Loading…</p></div>;

    return (
        <div className="page">
            <div className="page-header">
                <div>
                    <h1>Banks</h1>
                    <p className="page-subtitle">{connections.length} connection(s)</p>
                </div>
            </div>

            {error && <div className="alert alert-error">{error}</div>}

            {/* ── Providers ── */}
            <div className="card" style={{ marginBottom: 24 }}>
                <div className="card-header"><h2>Add a connection</h2></div>
                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', padding: 16 }}>
                    {providers.map(p => (
                        <button key={p.id} className="btn btn-secondary" onClick={() => openConnect(p)}>
                            <Plus size={14} /> {p.displayName}
                        </button>
                    ))}
                    {providers.length === 0 && <p style={{ color: 'var(--text-muted)' }}>No providers available</p>}
                </div>
            </div>

            {/* ── Connections ── */}
            {connections.map(conn => (
                <div className="card" key={conn.id} style={{ marginBottom: 16 }}>
                    <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <Building2 size={18} />
                            <div>
                                <h2 style={{ margin: 0 }}>{conn.providerDisplayName}</h2>
                                <p style={{ margin: 0, fontSize: 13, color: 'var(--text-muted)' }}>
                                    {conn.label ?? '—'}
                                    {conn.status === 'INVALID' && (
                                        <span className="text-red" style={{ marginLeft: 8 }}>
                                            <AlertTriangle size={12} /> credentials rejected
                                        </span>
                                    )}
                                </p>
                            </div>
                        </div>
                        <div style={{ display: 'flex', gap: 6 }}>
                            <button className="btn btn-ghost btn-sm" onClick={() => bankApi.verify(conn.id).then(reload).catch(e => setError(e.message))}>
                                <RefreshCw size={13} /> Verify
                            </button>
                            <button className="btn btn-ghost btn-sm" onClick={() => showAccounts(conn.id)}>
                                <Link2 size={13} /> Accounts
                            </button>
                            <button className="btn btn-ghost btn-sm" style={{ color: 'var(--red)' }}
                                    onClick={() => { if (window.confirm('Disconnect? Imported transactions stay.')) bankApi.disconnect(conn.id).then(reload); }}>
                                <Trash2 size={13} />
                            </button>
                        </div>
                    </div>

                    {openConnectionId === conn.id && (
                        <div style={{ padding: 16 }}>
                            {remoteBusy ? <p>Loading accounts…</p> : (
                                <table className="table">
                                    <thead><tr><th>AT THE BANK</th><th>CURRENCY</th><th>BALANCE</th><th>LINKED TO</th><th /></tr></thead>
                                    <tbody>
                                    {remoteAccounts.map(ra => (
                                        <tr key={ra.externalId}>
                                            <td>{ra.displayName}</td>
                                            <td>{ra.currencyCode}</td>
                                            <td>{(ra.balanceMinor / 100).toFixed(2)}</td>
                                            <td>{accountOf(ra.linkedAccountId)?.name ?? <span style={{ color: 'var(--text-muted)' }}>—</span>}</td>
                                            <td style={{ textAlign: 'right' }}>
                                                {ra.linkedAccountId ? (
                                                    <button className="btn btn-ghost btn-sm"
                                                            onClick={() => bankApi.unlink(ra.linkedAccountId!).then(() => showAccounts(conn.id)).then(reload)}>
                                                        <Unlink size={13} /> Unlink
                                                    </button>
                                                ) : (
                                                    <select className="form-control" defaultValue=""
                                                            onChange={e => {
                                                                const accountId = Number(e.target.value);
                                                                if (accountId) {
                                                                    bankApi.link(conn.id, accountId, ra.externalId)
                                                                        .then(() => showAccounts(conn.id)).then(reload)
                                                                        .catch(err => setError(err.message));
                                                                }
                                                            }}>
                                                        <option value="">Link to…</option>
                                                        {accounts.filter(a => !a.bankConnectionId && a.currency.code === ra.currencyCode)
                                                            .map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                                                    </select>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            )}
                        </div>
                    )}
                </div>
            ))}

            {/* ── Import ── */}
            {linkedAccounts.length > 0 && (
                <div className="card">
                    <div className="card-header"><h2>Import a statement</h2></div>
                    <div style={{ padding: 16, display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
                        <div>
                            <label className="form-label">Account</label>
                            <select className="form-control" value={importAccountId ?? ''}
                                    onChange={e => { setImportAccountId(Number(e.target.value)); setPending(null); }}>
                                <option value="">Choose…</option>
                                {linkedAccounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                            </select>
                        </div>
                        <div>
                            <label className="form-label">From</label>
                            <input type="date" className="form-control" value={dateFrom} onChange={e => setDateFrom(e.target.value)} />
                        </div>
                        <div>
                            <label className="form-label">To</label>
                            <input type="date" className="form-control" value={dateTo} onChange={e => setDateTo(e.target.value)} />
                        </div>
                        <button className="btn btn-primary" disabled={!importAccountId || importBusy} onClick={runPreview}>
                            <Download size={14} /> Preview
                        </button>
                    </div>

                    {importMessage && <div style={{ padding: '0 16px 16px' }}>{importMessage}</div>}

                    {pending && (
                        <div style={{ padding: 16 }}>
                            <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                                {pending.filter(r => !r.alreadyImported).length} new,
                                {' '}{pending.filter(r => r.alreadyImported).length} already imported
                            </p>
                            <table className="table">
                                <thead><tr><th>DATE</th><th>DESCRIPTION</th><th>AMOUNT</th><th>CATEGORY</th></tr></thead>
                                <tbody>
                                {pending.map(r => {
                                    const account = accountOf(importAccountId);
                                    return (
                                        <tr key={r.externalId} style={{ opacity: r.alreadyImported ? 0.45 : 1 }}>
                                            <td>{r.date}</td>
                                            <td>{r.description}</td>
                                            <td style={{ color: r.type === 'INCOME' ? 'var(--green)' : 'var(--red)' }}>
                                                {r.type === 'INCOME' ? '+' : '-'}
                                                {account ? formatAmount(r.amountMinor / 100, account.currency) : (r.amountMinor / 100).toFixed(2)}
                                            </td>
                                            <td>
                                                {r.alreadyImported ? <span style={{ color: 'var(--text-muted)' }}>imported</span> : (
                                                    <select className="form-control" value={chosen[r.externalId] ?? ''}
                                                            onChange={e => setChosen(c => ({ ...c, [r.externalId]: e.target.value ? Number(e.target.value) : null }))}>
                                                        <option value="">Uncategorised</option>
                                                        {categories.filter(c => c.type === (r.type === 'INCOME' ? 'INCOME' : 'EXPENSE'))
                                                            .map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                                    </select>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                            <button className="btn btn-primary" disabled={importBusy} onClick={runConfirm} style={{ marginTop: 12 }}>
                                Import {pending.filter(r => !r.alreadyImported).length}
                            </button>
                        </div>
                    )}
                </div>
            )}

            {/* ── File import: works for banks with no usable API at all ── */}
            <div style={{ marginTop: 24 }}>
                <CsvImport accounts={accounts} categories={categories} onImported={reload} />
            </div>

            {/* ── Connect dialog: rendered from the provider's own field list ── */}
            {connectProvider && (
                <div className="modal-overlay" onClick={() => setConnectProvider(null)}>
                    <div className="modal" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2 className="modal-title">Connect {connectProvider.displayName}</h2>
                            <button className="btn btn-ghost btn-icon" onClick={() => setConnectProvider(null)}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {connectProvider.credentialFields.map((f: CredentialField) => (
                                <div key={f.name} style={{ marginBottom: 12 }}>
                                    <label className="form-label">{f.label}</label>
                                    <input
                                        className="form-control"
                                        type={f.type === 'SECRET' ? 'password' : 'text'}
                                        placeholder={f.placeholder ?? ''}
                                        value={credentials[f.name] ?? ''}
                                        onChange={e => setCredentials(c => ({ ...c, [f.name]: e.target.value }))}
                                    />
                                    {f.helpText && <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>{f.helpText}</p>}
                                </div>
                            ))}
                            {connectProvider.helpUrl && (
                                <p style={{ fontSize: 12 }}>
                                    <a href={connectProvider.helpUrl} target="_blank" rel="noreferrer">
                                        Where do I get this?
                                    </a>
                                </p>
                            )}
                            {connectError && <div className="alert alert-error">{connectError}</div>}
                        </div>
                        <div className="modal-footer">
                            <button className="btn btn-secondary" onClick={() => setConnectProvider(null)}>Cancel</button>
                            <button className="btn btn-primary" onClick={submitConnect} disabled={connecting}>
                                {connecting ? 'Checking…' : 'Check and connect'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Banks;
