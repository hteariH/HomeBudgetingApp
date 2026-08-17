import React, { useCallback, useEffect, useState } from 'react';
import { accountApi, categoryApi, transactionApi } from '../api';
import {
    Account, Category, CreateTransactionRequest, PagedResponse, Transaction, TransactionType
} from '../types';
import { Plus, Trash2, Edit2, X, ChevronLeft, ChevronRight } from 'lucide-react';
import { formatAmount } from '../money';


/**
 * A transfer leg already carries its sign - negative on the account the money leaves,
 * positive on the one it arrives in. Forcing a minus onto everything that is not
 * INCOME showed money arriving as a loss.
 */
const isCredit = (t: Transaction) =>
    t.type === 'INCOME' || (t.type === 'TRANSFER' && Number(t.amount) >= 0);

const signOf = (t: Transaction) => (isCredit(t) ? '+' : '-');

const today = () => new Date().toISOString().slice(0, 10);

const Transactions: React.FC = () => {
    const [data, setData] = useState<PagedResponse<Transaction>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [loading, setLoading] = useState(true);

    const [filterAccount, setFilterAccount] = useState('');
    const [filterType, setFilterType] = useState('');
    const [filterDateFrom, setFilterDateFrom] = useState('');
    const [filterDateTo, setFilterDateTo] = useState('');
    const [page, setPage] = useState(0);

    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState<Transaction | null>(null);
    const [form, setForm] = useState<CreateTransactionRequest>({
        accountId: 0, categoryId: 0, amount: 0, type: 'EXPENSE', date: today()
    });
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    const filteredCats = categories.filter(c => !form.type || c.type === form.type);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [txns, accs, cats] = await Promise.all([
                transactionApi.getAll({
                    accountId: filterAccount ? Number(filterAccount) : undefined,
                    type: filterType ? filterType as TransactionType : undefined,
                    dateFrom: filterDateFrom || undefined,
                    dateTo: filterDateTo || undefined,
                    page, size: 20
                }),
                accountApi.getAll(),
                categoryApi.getAll(),
            ]);
            setData(txns); setAccounts(accs); setCategories(cats);
        } finally { setLoading(false); }
    }, [filterAccount, filterType, filterDateFrom, filterDateTo, page]);

    useEffect(() => { load(); }, [load]);

    const openCreate = () => {
        setEditing(null);
        setForm({ accountId: accounts[0]?.id ?? 0, categoryId: 0, amount: 0, type: 'EXPENSE', date: today() });
        setError(''); setShowModal(true);
    };
    const openEdit = (t: Transaction) => {
        setEditing(t);
        setForm({ accountId: t.account.id, categoryId: t.category?.id ?? 0, amount: Number(t.amount), type: t.type, date: t.date, description: t.description });
        setError(''); setShowModal(true);
    };

    const save = async () => {
        if (!form.accountId) { setError('Select an account'); return; }
        if (!form.categoryId) { setError('Select a category'); return; }
        if (!form.amount || form.amount <= 0) { setError('Enter a valid amount'); return; }
        setSaving(true); setError('');
        try {
            if (editing) { await transactionApi.update(editing.id, { categoryId: form.categoryId, amount: form.amount, date: form.date, description: form.description }); }
            else { await transactionApi.create(form); }
            setShowModal(false); await load();
        } catch (e: any) { setError(e.message); }
        finally { setSaving(false); }
    };

    const remove = async (id: number) => {
        if (!window.confirm('Delete this transaction?')) return;
        try { await transactionApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>Transactions</h1>
                    <p className="text-muted text-sm">{data.totalElements} total transactions</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> Add Transaction</button>
            </div>

            {/* Filters */}
            <div className="card" style={{ marginBottom: 20, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', padding: '16px 20px' }}>
                <div className="form-group" style={{ minWidth: 160 }}>
                    <label className="form-label">Account</label>
                    <select className="form-control" value={filterAccount} onChange={e => { setFilterAccount(e.target.value); setPage(0); }}>
                        <option value="">All Accounts</option>
                        {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                    </select>
                </div>
                <div className="form-group" style={{ minWidth: 120 }}>
                    <label className="form-label">Type</label>
                    <select className="form-control" value={filterType} onChange={e => { setFilterType(e.target.value); setPage(0); }}>
                        <option value="">All Types</option>
                        <option value="INCOME">Income</option>
                        <option value="EXPENSE">Expense</option>
                    </select>
                </div>
                <div className="form-group" style={{ minWidth: 140 }}>
                    <label className="form-label">From</label>
                    <input type="date" className="form-control" value={filterDateFrom} onChange={e => { setFilterDateFrom(e.target.value); setPage(0); }} />
                </div>
                <div className="form-group" style={{ minWidth: 140 }}>
                    <label className="form-label">To</label>
                    <input type="date" className="form-control" value={filterDateTo} onChange={e => { setFilterDateTo(e.target.value); setPage(0); }} />
                </div>
            </div>

            {/* Table */}
            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
                {loading ? (
                    <div className="loading"><div className="spinner" /></div>
                ) : data.content.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">💸</div>
                        <p className="empty-state-text">No transactions found</p>
                    </div>
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Date</th><th>Account</th><th>Category</th><th>Description</th><th>Type</th><th style={{ textAlign: 'right' }}>Amount</th><th></th>
                                </tr>
                            </thead>
                            <tbody>
                                {data.content.map(t => (
                                    <tr key={t.id}>
                                        <td>{t.date}</td>
                                        <td>
                                            <div className="account-chip">
                                                <div className="account-chip-dot" style={{ background: t.account.color }} />
                                                {t.account.name}
                                            </div>
                                        </td>
                                        <td>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                                <div style={{ width: 8, height: 8, borderRadius: '50%', background: t.category?.color ?? '#9CA3AF' }} />
                                                {t.category?.name ?? 'Uncategorised'}
                                            </div>
                                        </td>
                                        <td style={{ color: 'var(--text-muted)' }}>{t.description || '—'}</td>
                                        <td>
                                            <span className={`badge badge-${t.type.toLowerCase()}`}>{t.type}</span>
                                        </td>
                                        <td style={{ textAlign: 'right', fontWeight: 700, color: isCredit(t) ? 'var(--green)' : 'var(--red)' }}>
                                            {signOf(t)}{t.account.currency.symbol}{formatAmount(Math.abs(Number(t.amount)), t.account.currency)}
                                        </td>
                                        <td>
                                            <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                                                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(t)}><Edit2 size={13} /></button>
                                                <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(t.id)}><Trash2 size={13} /></button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Pagination */}
                {data.totalPages > 1 && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8, padding: '12px 20px', borderTop: '1px solid var(--border)' }}>
                        <span className="text-muted text-sm">Page {page + 1} of {data.totalPages}</span>
                        <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setPage(p => p - 1)} disabled={page === 0}><ChevronLeft size={14} /></button>
                        <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setPage(p => p + 1)} disabled={page + 1 >= data.totalPages}><ChevronRight size={14} /></button>
                    </div>
                )}
            </div>

            {/* Modal */}
            {showModal && (
                <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
                    <div className="modal">
                        <div className="modal-header">
                            <h2 className="modal-title">{editing ? 'Edit Transaction' : 'Add Transaction'}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <p style={{ color: 'var(--red)', fontSize: 13 }}>{error}</p>}
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">Type</label>
                                    <select className="form-control" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as TransactionType, categoryId: 0 }))} disabled={!!editing}>
                                        <option value="EXPENSE">Expense</option>
                                        <option value="INCOME">Income</option>
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">Date</label>
                                    <input type="date" className="form-control" value={form.date} onChange={e => setForm(f => ({ ...f, date: e.target.value }))} />
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Account *</label>
                                <select className="form-control" value={form.accountId} onChange={e => setForm(f => ({ ...f, accountId: Number(e.target.value) }))} disabled={!!editing}>
                                    <option value={0}>Select account…</option>
                                    {accounts.map(a => <option key={a.id} value={a.id}>{a.name} ({a.currency.code})</option>)}
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Category *</label>
                                <select className="form-control" value={form.categoryId} onChange={e => setForm(f => ({ ...f, categoryId: Number(e.target.value) }))}>
                                    <option value={0}>Select category…</option>
                                    {filteredCats.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Amount *</label>
                                <input type="number" step="0.01" min="0.01" className="form-control" value={form.amount} onChange={e => setForm(f => ({ ...f, amount: Number(e.target.value) }))} />
                            </div>
                            <div className="form-group">
                                <label className="form-label">Description</label>
                                <input className="form-control" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="Optional note" />
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

export default Transactions;
