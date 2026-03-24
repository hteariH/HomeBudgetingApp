import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { accountApi, categoryApi, monobankApi, transactionApi } from '../api';
import {
    Account, Category, MonobankConfirmItem, MonobankPendingTransaction,
    Transaction, MonobankAccountInfo,
} from '../types';
import { Link2, Link2Off, RefreshCw, Download, CornerLeftUp, Trash2, Check } from 'lucide-react';

// ─── helpers ──────────────────────────────────────────────────────────────────
const toEpoch = (d: string) => Math.floor(new Date(d).getTime() / 1000);
const isoDay  = (d: string) => d.slice(0, 10);
const today   = () => new Date().toISOString().slice(0, 10);
const monthAgo = () => { const d = new Date(); d.setMonth(d.getMonth() - 1); return d.toISOString().slice(0, 10); };
const fmt = (n: number) => Math.abs(n).toLocaleString('uk-UA', { minimumFractionDigits: 2 });
const fmtSigned = (n: number, type: string) => (type === 'INCOME' ? '+' : '-') + fmt(n);

// ─── style helpers ────────────────────────────────────────────────────────────
const thStyle: React.CSSProperties = {
    padding: '0.4rem 0.6rem',
    fontWeight: 600,
    fontSize: '0.73rem',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    color: 'var(--text-muted)',
    borderBottom: '2px solid var(--border)',
    whiteSpace: 'nowrap',
    background: 'var(--bg-surface)',
};

const tdStyle: React.CSSProperties = {
    padding: '0.38rem 0.6rem',
    verticalAlign: 'middle',
};

const tagStyle = (bg: string, color: string): React.CSSProperties => ({
    display: 'inline-flex', alignItems: 'center', gap: '0.2rem',
    padding: '0.1rem 0.4rem', borderRadius: '999px',
    fontSize: '0.7rem', fontWeight: 600, background: bg, color, whiteSpace: 'nowrap',
});

const iconBtn = (color?: string): React.CSSProperties => ({
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    padding: '4px 6px', borderRadius: '6px', cursor: 'pointer', border: 'none',
    background: 'transparent', color: color || 'var(--text-secondary)',
    transition: 'background 150ms, color 150ms',
});

// ─── component ────────────────────────────────────────────────────────────────
const MonobankImport: React.FC = () => {
    const [accounts, setAccounts]   = useState<Account[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [loading, setLoading]     = useState(true);

    // ── Link panel
    const [token, setToken]                           = useState('');
    const [clientInfo, setClientInfo]                 = useState<{ clientId: string; name: string; accounts: MonobankAccountInfo[] } | null>(null);
    const [selectedMonoAccountId, setSelectedMonoAccountId] = useState('');
    const [linkAccountId, setLinkAccountId]           = useState('');
    const [fetchingInfo, setFetchingInfo]             = useState(false);
    const [linking, setLinking]                       = useState(false);
    const [linkError, setLinkError]                   = useState('');
    const [linkSuccess, setLinkSuccess]               = useState('');

    // ── Import panel config
    const [importAccountId, setImportAccountId]   = useState('');
    const [defaultCategoryId, setDefaultCategoryId] = useState('');
    const [dateFrom, setDateFrom]                 = useState(monthAgo());
    const [dateTo, setDateTo]                     = useState(today());

    // ── Preview state
    const [previewing, setPreviewing]             = useState(false);
    const [monoTxs, setMonoTxs]                   = useState<MonobankPendingTransaction[] | null>(null);
    const [existingTxs, setExistingTxs]           = useState<Transaction[]>([]);
    const [previewEpochFrom, setPreviewEpochFrom] = useState(0);
    const [previewEpochTo, setPreviewEpochTo]     = useState(0);
    const [previewError, setPreviewError]         = useState('');

    // per-row (new rows): monobankId -> { checked, categoryId }
    const [rowState, setRowState] = useState<Record<string, { checked: boolean; categoryId: string }>>({});

    // per-row (imported rows): existingTxId -> { editCatId, saving, deleting }
    const [importedEdits, setImportedEdits] = useState<Record<number, { editCatId: string; saving: boolean; deleting: boolean }>>({});

    // ── Confirm state
    const [confirming, setConfirming]     = useState(false);
    const [importResult, setImportResult] = useState<{ imported: number; skipped: number } | null>(null);
    const [confirmError, setConfirmError] = useState('');

    // ── Load
    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [accs, cats] = await Promise.all([accountApi.getAll(), categoryApi.getAll()]);
            setAccounts(accs);
            setCategories(cats);
            const linked = accs.find(a => a.isMonobankLinked);
            if (linked) setImportAccountId(String(linked.id));
        } finally { setLoading(false); }
    }, []);
    useEffect(() => { load(); }, [load]);

    const linkedAccounts   = accounts.filter(a => a.isMonobankLinked);
    const unlinkedAccounts = accounts.filter(a => !a.isMonobankLinked);

    // ── Link handlers
    const fetchClientInfo = async () => {
        if (!token.trim()) { setLinkError('Enter your Monobank token'); return; }
        setFetchingInfo(true); setLinkError(''); setClientInfo(null); setSelectedMonoAccountId('');
        try {
            const info = await monobankApi.getClientInfo(token.trim());
            setClientInfo(info);
            if (info.accounts.length > 0) setSelectedMonoAccountId(info.accounts[0].id);
        } catch (e: any) {
            setLinkError(e.message || 'Failed to fetch Monobank info');
        } finally { setFetchingInfo(false); }
    };

    const linkAccount = async () => {
        if (!linkAccountId || !selectedMonoAccountId) { setLinkError('Select both accounts'); return; }
        setLinking(true); setLinkError(''); setLinkSuccess('');
        try {
            await monobankApi.linkAccount(Number(linkAccountId), token.trim(), selectedMonoAccountId);
            setLinkSuccess('Account linked!');
            setToken(''); setClientInfo(null); setSelectedMonoAccountId(''); setLinkAccountId('');
            await load();
        } catch (e: any) { setLinkError(e.message || 'Failed to link'); }
        finally { setLinking(false); }
    };

    const unlinkAccount = async (id: number) => {
        try { await monobankApi.unlinkAccount(id); await load(); }
        catch (e: any) { setLinkError(e.message || 'Failed to unlink'); }
    };

    // ── Preview
    const runPreview = async () => {
        if (!importAccountId) { setPreviewError('Select an account'); return; }
        setPreviewing(true); setPreviewError(''); setMonoTxs(null); setImportResult(null);
        const epochFrom = toEpoch(dateFrom);
        const epochTo   = toEpoch(dateTo + 'T23:59:59');
        setPreviewEpochFrom(epochFrom);
        setPreviewEpochTo(epochTo);
        try {
            const [preview, paged] = await Promise.all([
                monobankApi.preview(Number(importAccountId), epochFrom, epochTo),
                transactionApi.getAll({ accountId: Number(importAccountId), dateFrom, dateTo, size: 500 }),
            ]);
            setMonoTxs(preview.transactions);
            setExistingTxs(paged.content);

            // new-row checkboxes initial state
            const expCat = String(categories.find(c => c.type === 'EXPENSE')?.id ?? '');
            const incCat = String(categories.find(c => c.type === 'INCOME')?.id ?? '');
            const rs: Record<string, { checked: boolean; categoryId: string }> = {};
            for (const tx of preview.transactions) {
                rs[tx.monobankId] = {
                    checked: !tx.alreadyExists,
                    categoryId: defaultCategoryId || (tx.type === 'EXPENSE' ? expCat : incCat),
                };
            }
            setRowState(rs);

            // imported-rows edit state
            const ie: Record<number, { editCatId: string; saving: boolean; deleting: boolean }> = {};
            for (const tx of preview.transactions) {
                if (tx.existingTransactionId) {
                    // find existing transaction to prefill its current category
                    const existing = paged.content.find(t => t.id === tx.existingTransactionId);
                    ie[tx.existingTransactionId] = {
                        editCatId: String(existing?.category.id ?? ''),
                        saving: false, deleting: false,
                    };
                }
            }
            setImportedEdits(ie);
        } catch (e: any) {
            setPreviewError(e.message || 'Preview failed');
        } finally { setPreviewing(false); }
    };

    const applyDefaultCategory = (catId: string) => {
        setDefaultCategoryId(catId);
        if (!monoTxs) return;
        setRowState(prev => {
            const next = { ...prev };
            for (const tx of monoTxs) {
                if (!tx.alreadyExists) next[tx.monobankId] = { ...next[tx.monobankId], categoryId: catId };
            }
            return next;
        });
    };

    // ── Confirm import (new rows)
    const confirmImport = async () => {
        const items: MonobankConfirmItem[] = Object.entries(rowState)
            .filter(([, v]) => v.checked && v.categoryId)
            .map(([monobankId, v]) => ({ monobankId, categoryId: Number(v.categoryId) }));
        if (items.length === 0) { setConfirmError('Select at least one transaction with a category'); return; }
        setConfirming(true); setConfirmError(''); setImportResult(null);
        try {
            const result = await monobankApi.confirmImport(Number(importAccountId), previewEpochFrom, previewEpochTo, items);
            setImportResult(result);
            const paged = await transactionApi.getAll({ accountId: Number(importAccountId), dateFrom, dateTo, size: 500 });
            setExistingTxs(paged.content);
            setMonoTxs(prev => prev ? prev.map(t =>
                items.some(i => i.monobankId === t.monobankId)
                    ? { ...t, alreadyExists: true, existingTransactionId: paged.content.find(e => e.id > 0)?.id }
                    : t
            ) : prev);
            setRowState(prev => {
                const next = { ...prev };
                for (const item of items) next[item.monobankId] = { ...next[item.monobankId], checked: false };
                return next;
            });
        } catch (e: any) { setConfirmError(e.message || 'Import failed'); }
        finally { setConfirming(false); }
    };

    // ── Edit category for imported row
    const saveEditedCategory = async (txId: number) => {
        const catId = importedEdits[txId]?.editCatId;
        if (!catId) return;
        setImportedEdits(prev => ({ ...prev, [txId]: { ...prev[txId], saving: true } }));
        try {
            await transactionApi.update(txId, { categoryId: Number(catId) });
            const paged = await transactionApi.getAll({ accountId: Number(importAccountId), dateFrom, dateTo, size: 500 });
            setExistingTxs(paged.content);
        } catch { /* swallow */ }
        finally { setImportedEdits(prev => ({ ...prev, [txId]: { ...prev[txId], saving: false } })); }
    };

    // ── Delete imported row
    const deleteImported = async (monobankId: string, txId: number) => {
        setImportedEdits(prev => ({ ...prev, [txId]: { ...prev[txId], deleting: true } }));
        try {
            await transactionApi.delete(txId);
            // remove from monoTxs alreadyExists flag so it becomes importable again
            setMonoTxs(prev => prev ? prev.map(t =>
                t.monobankId === monobankId
                    ? { ...t, alreadyExists: false, existingTransactionId: undefined }
                    : t
            ) : prev);
            // re-init that row in rowState
            setMonoTxs(prev => {
                if (!prev) return prev;
                const tx = prev.find(t => t.monobankId === monobankId);
                if (tx) {
                    const expCat = String(categories.find(c => c.type === 'EXPENSE')?.id ?? '');
                    const incCat = String(categories.find(c => c.type === 'INCOME')?.id ?? '');
                    setRowState(rs => ({
                        ...rs,
                        [monobankId]: {
                            checked: true,
                            categoryId: defaultCategoryId || (tx.type === 'EXPENSE' ? expCat : incCat),
                        },
                    }));
                }
                return prev;
            });
            setImportedEdits(prev => {
                const next = { ...prev };
                delete next[txId];
                return next;
            });
            const paged = await transactionApi.getAll({ accountId: Number(importAccountId), dateFrom, dateTo, size: 500 });
            setExistingTxs(paged.content);
        } catch { setImportedEdits(prev => ({ ...prev, [txId]: { ...prev[txId], deleting: false } })); }
    };

    // ── Date groups
    const dateGroups = useMemo(() => {
        if (!monoTxs) return [];
        const allDates = new Set<string>();
        monoTxs.forEach(t => allDates.add(isoDay(t.date)));
        existingTxs.forEach(t => allDates.add(isoDay(t.date)));
        return Array.from(allDates).sort((a, b) => b.localeCompare(a)).map(day => ({
            day,
            mono: monoTxs.filter(t => isoDay(t.date) === day),
            existing: existingTxs.filter(t => isoDay(t.date) === day),
        }));
    }, [monoTxs, existingTxs]);

    const selectedCount = Object.values(rowState).filter(v => v.checked).length;

    if (loading) return <div className="page-container"><p>Loading…</p></div>;

    return (
        <div className="page-container">
            <div className="page-header">
                <div>
                    <h1 className="page-title">Monobank Import</h1>
                    <p className="page-subtitle">Link accounts and import transactions</p>
                </div>
            </div>

            {/* ── Linked accounts ── */}
            <div className="card" style={{ marginBottom: '1.5rem' }}>
                <div className="card-header"><h2 className="card-title">Linked Accounts</h2></div>
                {linkedAccounts.length === 0 ? (
                    <p style={{ color: 'var(--text-secondary)', padding: '0.5rem 0' }}>No accounts linked yet.</p>
                ) : (
                    <table className="table">
                        <thead><tr><th>Account</th><th>Monobank ID</th><th>Balance</th><th /></tr></thead>
                        <tbody>
                            {linkedAccounts.map(a => (
                                <tr key={a.id}>
                                    <td><span className="account-dot" style={{ background: a.color }} />{a.name}</td>
                                    <td><code style={{ fontSize: '0.8rem' }}>{a.monobankAccountId}</code></td>
                                    <td>{a.currency.symbol}{fmt(a.balance)}</td>
                                    <td>
                                        <button className="btn btn-danger btn-sm" onClick={() => unlinkAccount(a.id)}>
                                            <Link2Off size={14} /> Unlink
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* ── Link new account ── */}
            <div className="card" style={{ marginBottom: '1.5rem' }}>
                <div className="card-header"><h2 className="card-title">Link Account to Monobank</h2></div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                    <div className="form-group">
                        <label className="form-label">Monobank API Token</label>
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                            <input className="form-control" type="text"
                                placeholder="Paste your token from monobank.ua/api"
                                value={token} onChange={e => setToken(e.target.value)} style={{ flex: 1 }} />
                            <button className="btn btn-secondary" onClick={fetchClientInfo} disabled={fetchingInfo}>
                                <RefreshCw size={14} className={fetchingInfo ? 'spin' : ''} />
                                {fetchingInfo ? 'Loading…' : 'Fetch Info'}
                            </button>
                        </div>
                        <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
                            Get your token at <a href="https://api.monobank.ua" target="_blank" rel="noreferrer">api.monobank.ua</a>
                        </p>
                    </div>
                    {clientInfo && (
                        <>
                            <div style={{ padding: '0.75rem', background: 'var(--bg-surface)', borderRadius: '8px', border: '1px solid var(--border)' }}>
                                <strong>{clientInfo.name}</strong>
                                <span style={{ color: 'var(--text-secondary)', marginLeft: '0.5rem', fontSize: '0.85rem' }}>
                                    ({clientInfo.accounts.length} account{clientInfo.accounts.length !== 1 ? 's' : ''})
                                </span>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Monobank Account</label>
                                <select className="form-control" value={selectedMonoAccountId}
                                    onChange={e => setSelectedMonoAccountId(e.target.value)}>
                                    {clientInfo.accounts.map((ma: MonobankAccountInfo) => (
                                        <option key={ma.id} value={ma.id}>
                                            {ma.type} — {ma.maskedPan.join(', ') || ma.id} ({(ma.balance / 100).toFixed(2)} / code {ma.currencyCode})
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Link to App Account</label>
                                <select className="form-control" value={linkAccountId}
                                    onChange={e => setLinkAccountId(e.target.value)}>
                                    <option value="">— select account —</option>
                                    {unlinkedAccounts.map(a => (
                                        <option key={a.id} value={a.id}>{a.name} ({a.currency.code})</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <button className="btn btn-primary" onClick={linkAccount} disabled={linking}>
                                    <Link2 size={14} /> {linking ? 'Linking…' : 'Link Account'}
                                </button>
                            </div>
                        </>
                    )}
                    {linkError && <p className="form-error">{linkError}</p>}
                    {linkSuccess && <p style={{ color: 'var(--green)', fontWeight: 500 }}>{linkSuccess}</p>}
                </div>
            </div>

            {/* ── Import panel ── */}
            <div className="card">
                <div className="card-header"><h2 className="card-title">Import Transactions</h2></div>

                {linkedAccounts.length === 0 ? (
                    <p style={{ color: 'var(--text-secondary)' }}>Link at least one account first.</p>
                ) : (
                    <>
                        {/* Config bar */}
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.6rem', marginBottom: '1rem', alignItems: 'flex-end' }}>
                            <div className="form-group" style={{ flex: '1 1 140px', margin: 0 }}>
                                <label className="form-label" style={{ fontSize: '0.73rem' }}>Account</label>
                                <select className="form-control" value={importAccountId}
                                    onChange={e => { setImportAccountId(e.target.value); setMonoTxs(null); }}>
                                    <option value="">— account —</option>
                                    {linkedAccounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                                </select>
                            </div>
                            <div className="form-group" style={{ flex: '0 1 128px', margin: 0 }}>
                                <label className="form-label" style={{ fontSize: '0.73rem' }}>From</label>
                                <input className="form-control" type="date" value={dateFrom}
                                    onChange={e => { setDateFrom(e.target.value); setMonoTxs(null); }} />
                            </div>
                            <div className="form-group" style={{ flex: '0 1 128px', margin: 0 }}>
                                <label className="form-label" style={{ fontSize: '0.73rem' }}>To</label>
                                <input className="form-control" type="date" value={dateTo}
                                    onChange={e => { setDateTo(e.target.value); setMonoTxs(null); }} />
                            </div>
                            <div className="form-group" style={{ flex: '1 1 160px', margin: 0 }}>
                                <label className="form-label" style={{ fontSize: '0.73rem' }}>Default Category</label>
                                <select className="form-control" value={defaultCategoryId}
                                    onChange={e => applyDefaultCategory(e.target.value)}>
                                    <option value="">— auto by type —</option>
                                    {categories.map(c => <option key={c.id} value={c.id}>{c.name} ({c.type})</option>)}
                                </select>
                            </div>
                            <button className="btn btn-secondary" onClick={runPreview} disabled={previewing}
                                style={{ flexShrink: 0, alignSelf: 'flex-end' }}>
                                <RefreshCw size={14} /> {previewing ? 'Loading…' : 'Preview'}
                            </button>
                        </div>

                        {previewError && <p className="form-error">{previewError}</p>}

                        {/* Transaction table */}
                        {monoTxs && (
                            <>
                                <div style={{
                                    border: '1px solid var(--border)',
                                    borderRadius: '8px',
                                    overflowX: 'auto',
                                    overflowY: 'auto',
                                    maxHeight: '65vh',
                                }}>
                                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.845rem' }}>
                                        <thead>
                                            <tr style={{ position: 'sticky', top: 0, zIndex: 2 }}>
                                                <th style={{ ...thStyle, width: '32px' }}></th>
                                                <th style={{ ...thStyle, textAlign: 'right', width: '96px' }}>Amount</th>
                                                <th style={thStyle}>Description</th>
                                                <th style={{ ...thStyle, width: '190px' }}>Category</th>
                                                <th style={{ ...thStyle, textAlign: 'center', width: '80px' }}>Status</th>
                                                <th style={{ ...thStyle, width: '66px' }}></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {dateGroups.length === 0 ? (
                                                <tr>
                                                    <td colSpan={6} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                                                        No transactions found for this period.
                                                    </td>
                                                </tr>
                                            ) : dateGroups.map(({ day, mono, existing }) => {
                                                const monoTotal    = mono.reduce((s, t) => s + (t.type === 'INCOME' ? t.amount : -t.amount), 0);
                                                const newCount     = mono.filter(t => !t.alreadyExists).length;
                                                const importedCount = mono.filter(t => t.alreadyExists).length;

                                                return (
                                                    <React.Fragment key={day}>
                                                        {/* Date group header */}
                                                        <tr>
                                                            <td colSpan={6} style={{
                                                                padding: '0.32rem 0.6rem',
                                                                background: 'var(--bg-surface)',
                                                                borderTop: '1px solid var(--border)',
                                                                borderBottom: '1px solid var(--border)',
                                                            }}>
                                                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.55rem', flexWrap: 'wrap' }}>
                                                                    <span style={{ fontWeight: 700, fontSize: '0.8rem', color: 'var(--text-primary)' }}>{day}</span>
                                                                    <span style={{ fontWeight: 600, fontSize: '0.8rem', color: monoTotal >= 0 ? 'var(--green)' : 'var(--red)' }}>
                                                                        {monoTotal >= 0 ? '+' : ''}{fmt(monoTotal)}
                                                                    </span>
                                                                    <span style={{ display: 'flex', gap: '0.3rem' }}>
                                                                        {newCount > 0 && <span style={tagStyle('rgba(99,102,241,0.18)', 'var(--accent-light)')}>{newCount} new</span>}
                                                                        {importedCount > 0 && <span style={tagStyle('rgba(255,255,255,0.06)', 'var(--text-muted)')}>{importedCount} imported</span>}
                                                                        {existing.length > 0 && <span style={tagStyle('rgba(16,185,129,0.15)', 'var(--green)')}>{existing.length} in budget</span>}
                                                                    </span>
                                                                </div>
                                                            </td>
                                                        </tr>

                                                        {/* Rows */}
                                                        {mono.map(mn => {
                                                            const rs = rowState[mn.monobankId];
                                                            const txId = mn.existingTransactionId;
                                                            const ie = txId ? importedEdits[txId] : undefined;
                                                            const isImported = mn.alreadyExists && !!txId;

                                                            return (
                                                                <tr key={mn.monobankId} style={{
                                                                    opacity: isImported ? 0.6 : 1,
                                                                    background: isImported ? 'rgba(255,255,255,0.02)' : undefined,
                                                                    borderBottom: '1px solid var(--border)',
                                                                    transition: 'opacity 0.15s, background 0.15s',
                                                                }}>
                                                                    {/* Checkbox */}
                                                                    <td style={{ ...tdStyle, textAlign: 'center', width: '32px' }}>
                                                                        {!isImported ? (
                                                                            <input type="checkbox"
                                                                                checked={rs?.checked ?? false}
                                                                                onChange={e => setRowState(prev => ({
                                                                                    ...prev,
                                                                                    [mn.monobankId]: { ...prev[mn.monobankId], checked: e.target.checked }
                                                                                }))}
                                                                                style={{ width: '14px', height: '14px', cursor: 'pointer' }}
                                                                            />
                                                                        ) : (
                                                                            <CornerLeftUp size={13} style={{ color: 'var(--text-muted)', display: 'block', margin: '0 auto' }} />
                                                                        )}
                                                                    </td>
                                                                    {/* Amount */}
                                                                    <td style={{ ...tdStyle, textAlign: 'right', fontWeight: 700, whiteSpace: 'nowrap', color: mn.type === 'INCOME' ? 'var(--green)' : 'var(--red)' }}>
                                                                        {fmtSigned(mn.amount, mn.type)}
                                                                    </td>
                                                                    {/* Description */}
                                                                    <td style={{ ...tdStyle, maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-primary)' }}>
                                                                        {mn.description}
                                                                    </td>
                                                                    {/* Category */}
                                                                    <td style={{ ...tdStyle, width: '190px' }}>
                                                                        {isImported && ie ? (
                                                                            <select
                                                                                className="form-control"
                                                                                style={{ fontSize: '0.78rem', padding: '0.15rem 0.3rem', height: 'auto' }}
                                                                                value={ie.editCatId}
                                                                                onChange={e => setImportedEdits(prev => ({
                                                                                    ...prev,
                                                                                    [txId!]: { ...prev[txId!], editCatId: e.target.value }
                                                                                }))}
                                                                            >
                                                                                <option value="">— category —</option>
                                                                                {categories.filter(c => c.type === mn.type).map(c =>
                                                                                    <option key={c.id} value={c.id}>{c.name}</option>
                                                                                )}
                                                                            </select>
                                                                        ) : !isImported && rs ? (
                                                                            <select
                                                                                className="form-control"
                                                                                style={{ fontSize: '0.78rem', padding: '0.15rem 0.3rem', height: 'auto' }}
                                                                                value={rs.categoryId}
                                                                                onChange={e => setRowState(prev => ({
                                                                                    ...prev,
                                                                                    [mn.monobankId]: { ...prev[mn.monobankId], categoryId: e.target.value }
                                                                                }))}
                                                                            >
                                                                                <option value="">— category —</option>
                                                                                {categories.filter(c => c.type === mn.type).map(c =>
                                                                                    <option key={c.id} value={c.id}>{c.name}</option>
                                                                                )}
                                                                            </select>
                                                                        ) : (
                                                                            <span style={{ color: 'var(--text-muted)', fontSize: '0.78rem' }}>—</span>
                                                                        )}
                                                                    </td>
                                                                    {/* Status */}
                                                                    <td style={{ ...tdStyle, textAlign: 'center' }}>
                                                                        {isImported ? (
                                                                            <span style={tagStyle('rgba(255,255,255,0.06)', 'var(--text-muted)')}>done</span>
                                                                        ) : (
                                                                            <span style={tagStyle('rgba(99,102,241,0.18)', 'var(--accent-light)')}>new</span>
                                                                        )}
                                                                    </td>
                                                                    {/* Actions */}
                                                                    <td style={{ ...tdStyle, width: '66px' }}>
                                                                        {isImported && ie && txId ? (
                                                                            <div style={{ display: 'flex', gap: '2px', justifyContent: 'center' }}>
                                                                                <button
                                                                                    title="Save category"
                                                                                    style={{ ...iconBtn('var(--green)') }}
                                                                                    disabled={ie.saving}
                                                                                    onClick={() => saveEditedCategory(txId)}
                                                                                    onMouseEnter={e => (e.currentTarget.style.background = 'rgba(16,185,129,0.15)')}
                                                                                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                                                                                >
                                                                                    <Check size={14} />
                                                                                </button>
                                                                                <button
                                                                                    title="Delete transaction"
                                                                                    style={{ ...iconBtn('var(--red)') }}
                                                                                    disabled={ie.deleting}
                                                                                    onClick={() => deleteImported(mn.monobankId, txId)}
                                                                                    onMouseEnter={e => (e.currentTarget.style.background = 'rgba(239,68,68,0.15)')}
                                                                                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                                                                                >
                                                                                    <Trash2 size={14} />
                                                                                </button>
                                                                            </div>
                                                                        ) : null}
                                                                    </td>
                                                                </tr>
                                                            );
                                                        })}
                                                    </React.Fragment>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>

                                {/* Bottom bar */}
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.75rem', flexWrap: 'wrap', gap: '0.6rem' }}>
                                    <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                                        {selectedCount} selected for import
                                    </span>
                                    <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', flexWrap: 'wrap' }}>
                                        {confirmError && <span className="form-error" style={{ margin: 0 }}>{confirmError}</span>}
                                        {importResult && (
                                            <span style={{ padding: '0.35rem 0.65rem', background: 'var(--bg-surface)', borderRadius: '6px', borderLeft: '3px solid var(--accent)', fontSize: '0.85rem' }}>
                                                ✓ {importResult.imported} imported, {importResult.skipped} skipped
                                            </span>
                                        )}
                                        <button className="btn btn-primary" onClick={confirmImport}
                                            disabled={confirming || selectedCount === 0}>
                                            <Download size={14} /> {confirming ? 'Saving…' : `Save (${selectedCount})`}
                                        </button>
                                    </div>
                                </div>
                            </>
                        )}
                    </>
                )}
            </div>
        </div>
    );
};

export default MonobankImport;
