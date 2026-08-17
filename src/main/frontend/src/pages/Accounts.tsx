import React, { useEffect, useState } from 'react';
import { accountApi, currencyApi } from '../api';
import { Account, AccountType, Currency, CreateAccountRequest, UpdateAccountRequest } from '../types';
import { Plus, Edit2, Trash2, X, Wallet } from 'lucide-react';
import { formatAmount } from '../money';

const ACCOUNT_TYPES: AccountType[] = ['CHECKING', 'SAVINGS', 'CASH', 'CREDIT', 'INVESTMENT'];
const COLORS = ['#4F46E5', '#10B981', '#F59E0B', '#EF4444', '#06B6D4', '#8B5CF6', '#EC4899', '#14B8A6', '#F97316', '#6B7280'];


// Per-currency formatting lives in ../money. This local helper is only for
// aggregates that are not tied to a single currency (totals, chart axes).
const fmt = (n: number, sym = '') =>
    `${sym}${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const Accounts: React.FC = () => {
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [currencies, setCurrencies] = useState<Currency[]>([]);
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState<Account | null>(null);
    const [form, setForm] = useState<CreateAccountRequest>({
        name: '', type: 'CHECKING', initialBalance: 0, currencyId: 0, description: '', color: '#4F46E5'
    });
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    const load = () => Promise.all([accountApi.getAll(), currencyApi.getAll()])
        .then(([accs, curs]) => { setAccounts(accs); setCurrencies(curs); })
        .finally(() => setLoading(false));

    useEffect(() => { load(); }, []);

    const openCreate = () => {
        setEditing(null);
        setForm({ name: '', type: 'CHECKING', initialBalance: 0, currencyId: currencies[0]?.id ?? 0, description: '', color: '#4F46E5' });
        setError(''); setShowModal(true);
    };
    const openEdit = (a: Account) => {
        setEditing(a);
        setForm({ name: a.name, type: a.type, initialBalance: Number(a.initialBalance), currencyId: a.currency.id, description: a.description ?? '', color: a.color });
        setError(''); setShowModal(true);
    };

    const save = async () => {
        if (!form.name.trim()) { setError('Name is required'); return; }
        if (!form.currencyId) { setError('Select a currency'); return; }
        setSaving(true); setError('');
        try {
            if (editing) {
                const upd: UpdateAccountRequest = { name: form.name, type: form.type, description: form.description, color: form.color };
                await accountApi.update(editing.id, upd);
            } else {
                await accountApi.create(form);
            }
            setShowModal(false); await load();
        } catch (e: any) { setError(e.message); }
        finally { setSaving(false); }
    };

    const remove = async (id: number) => {
        if (!window.confirm('Delete this account?')) return;
        try { await accountApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    const totalBalance = accounts.reduce((s, a) => s + Number(a.balance), 0);

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>Accounts</h1>
                    <p className="text-muted text-sm">Manage your bank accounts and wallets</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> New Account</button>
            </div>

            {/* Summary bar */}
            <div className="card" style={{ marginBottom: 24, display: 'flex', alignItems: 'center', gap: 16, padding: '16px 24px' }}>
                <div style={{ background: 'rgba(99,102,241,0.15)', borderRadius: 8, padding: 8 }}><Wallet size={20} color="var(--accent-light)" /></div>
                <div>
                    <p className="text-muted text-sm">Total Balance (all accounts)</p>
                    <p style={{ fontSize: 20, fontWeight: 700 }}>${fmt(totalBalance)}</p>
                </div>
            </div>

            {accounts.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-state-icon">🏦</div>
                    <p className="empty-state-text">No accounts yet</p>
                    <p className="empty-state-sub">Click "New Account" to add your first account</p>
                </div>
            ) : (
                <div className="grid-3">
                    {accounts.map(a => (
                        <div key={a.id} className="card" style={{ borderTop: `3px solid ${a.color}`, padding: '20px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                                <div>
                                    <p style={{ fontWeight: 700, fontSize: 16, marginBottom: 2 }}>{a.name}</p>
                                    <p className="text-muted text-sm">{a.type} · {a.currency.code}</p>
                                </div>
                                <div style={{ display: 'flex', gap: 6 }}>
                                    <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(a)}><Edit2 size={14} /></button>
                                    <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(a.id)}><Trash2 size={14} /></button>
                                </div>
                            </div>
                            <p style={{ fontSize: 24, fontWeight: 800, color: Number(a.balance) < 0 ? 'var(--red)' : 'var(--text-primary)' }}>
                                {a.currency.symbol}{formatAmount(Number(a.balance), a.currency)}
                            </p>
                            {a.description && <p className="text-muted text-sm" style={{ marginTop: 8 }}>{a.description}</p>}
                        </div>
                    ))}
                </div>
            )}

            {showModal && (
                <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
                    <div className="modal">
                        <div className="modal-header">
                            <h2 className="modal-title">{editing ? 'Edit Account' : 'New Account'}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <p style={{ color: 'var(--red)', fontSize: 13 }}>{error}</p>}
                            <div className="form-group">
                                <label className="form-label">Account Name *</label>
                                <input className="form-control" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Main Checking" />
                            </div>
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">Type</label>
                                    <select className="form-control" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as AccountType }))}>
                                        {ACCOUNT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">Currency</label>
                                    <select className="form-control" value={form.currencyId} onChange={e => setForm(f => ({ ...f, currencyId: Number(e.target.value) }))} disabled={!!editing}>
                                        {currencies.map(c => <option key={c.id} value={c.id}>{c.code} — {c.name}</option>)}
                                    </select>
                                </div>
                            </div>
                            {!editing && (
                                <div className="form-group">
                                    <label className="form-label">Initial Balance</label>
                                    <input type="number" step="0.01" className="form-control" value={form.initialBalance} onChange={e => setForm(f => ({ ...f, initialBalance: Number(e.target.value) }))} />
                                </div>
                            )}
                            <div className="form-group">
                                <label className="form-label">Description</label>
                                <input className="form-control" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="Optional" />
                            </div>
                            <div className="form-group">
                                <label className="form-label">Color</label>
                                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                                    {COLORS.map(c => (
                                        <button key={c} onClick={() => setForm(f => ({ ...f, color: c }))}
                                            style={{ width: 28, height: 28, borderRadius: '50%', background: c, border: form.color === c ? '3px solid white' : '3px solid transparent', cursor: 'pointer' }} />
                                    ))}
                                </div>
                            </div>
                        </div>
                        <div className="modal-footer">
                            <button className="btn btn-ghost" onClick={() => setShowModal(false)}>Cancel</button>
                            <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Accounts;
