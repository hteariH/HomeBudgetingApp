import React, { useCallback, useEffect, useState } from 'react';
import { accountApi, categoryApi, transactionApi } from '../api';
import {
    Account, Category, CreateTransactionRequest, PagedResponse, Transaction, TransactionType,
} from '../types';
import { Plus, Trash2, Edit2, X, ChevronLeft, ChevronRight } from 'lucide-react';
import { formatAmount } from '../money';
import { useT } from '../i18n';

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
    const t = useT();
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
        accountId: 0, categoryId: 0, amount: 0, type: 'EXPENSE', date: today(),
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
                    page, size: 20,
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
    const openEdit = (txn: Transaction) => {
        setEditing(txn);
        setForm({ accountId: txn.account.id, categoryId: txn.category?.id ?? 0, amount: Number(txn.amount), type: txn.type, date: txn.date, description: txn.description });
        setError(''); setShowModal(true);
    };

    const save = async () => {
        if (!form.accountId) { setError(t('transactions.accountRequired')); return; }
        if (!form.categoryId) { setError(t('transactions.categoryRequired')); return; }
        if (!form.amount || form.amount <= 0) { setError(t('transactions.amountRequired')); return; }
        setSaving(true); setError('');
        try {
            if (editing) { await transactionApi.update(editing.id, { categoryId: form.categoryId, amount: form.amount, date: form.date, description: form.description }); }
            else { await transactionApi.create(form); }
            setShowModal(false); await load();
        } catch (e: any) { setError(e.message); }
        finally { setSaving(false); }
    };

    const remove = async (id: number) => {
        if (!window.confirm(t('transactions.confirmDelete'))) return;
        try { await transactionApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    const typeLabel = (type: TransactionType) =>
        type === 'INCOME' ? t('common.income') : type === 'EXPENSE' ? t('common.expense') : t('common.transfer');

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>{t('transactions.title')}</h1>
                    <p className="text-muted text-sm">{t('transactions.totalCount', { count: data.totalElements })}</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> {t('transactions.add')}</button>
            </div>

            {/* Filters */}
            <div className="card mb-5 flex flex-wrap items-end gap-3" style={{ padding: '16px 20px' }}>
                <div className="form-group min-w-[160px] flex-1">
                    <label className="form-label">{t('common.account')}</label>
                    <select className="form-control" value={filterAccount} onChange={e => { setFilterAccount(e.target.value); setPage(0); }}>
                        <option value="">{t('transactions.allAccounts')}</option>
                        {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                    </select>
                </div>
                <div className="form-group min-w-[130px] flex-1">
                    <label className="form-label">{t('common.type')}</label>
                    <select className="form-control" value={filterType} onChange={e => { setFilterType(e.target.value); setPage(0); }}>
                        <option value="">{t('transactions.allTypes')}</option>
                        <option value="INCOME">{t('common.income')}</option>
                        <option value="EXPENSE">{t('common.expense')}</option>
                    </select>
                </div>
                <div className="form-group min-w-[140px] flex-1">
                    <label className="form-label">{t('common.from')}</label>
                    <input type="date" className="form-control" value={filterDateFrom} onChange={e => { setFilterDateFrom(e.target.value); setPage(0); }} />
                </div>
                <div className="form-group min-w-[140px] flex-1">
                    <label className="form-label">{t('common.to')}</label>
                    <input type="date" className="form-control" value={filterDateTo} onChange={e => { setFilterDateTo(e.target.value); setPage(0); }} />
                </div>
            </div>

            {/* Table */}
            <div className="card overflow-hidden" style={{ padding: 0 }}>
                {loading ? (
                    <div className="loading"><div className="spinner" /></div>
                ) : data.content.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">💸</div>
                        <p className="empty-state-text">{t('transactions.empty')}</p>
                        <p className="empty-state-sub">{t('transactions.emptyBody')}</p>
                    </div>
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>{t('common.date')}</th>
                                    <th>{t('common.account')}</th>
                                    <th>{t('common.category')}</th>
                                    <th>{t('common.description')}</th>
                                    <th>{t('common.type')}</th>
                                    <th className="text-right">{t('common.amount')}</th>
                                    <th />
                                </tr>
                            </thead>
                            <tbody>
                                {data.content.map(txn => (
                                    <tr key={txn.id}>
                                        <td className="whitespace-nowrap">{txn.date}</td>
                                        <td>
                                            <div className="account-chip">
                                                <div className="account-chip-dot" style={{ background: txn.account.color }} />
                                                {txn.account.name}
                                            </div>
                                        </td>
                                        <td>
                                            <div className="flex items-center gap-1.5">
                                                <span className="h-2 w-2 flex-shrink-0 rounded-full" style={{ background: txn.category?.color ?? '#9CA3AF' }} />
                                                {txn.category?.name ?? t('common.uncategorised')}
                                            </div>
                                        </td>
                                        <td className="text-muted">{txn.description || t('common.none')}</td>
                                        <td>
                                            <span className={`badge badge-${txn.type.toLowerCase()}`}>{typeLabel(txn.type)}</span>
                                        </td>
                                        <td
                                            className="whitespace-nowrap text-right font-bold"
                                            style={{ color: isCredit(txn) ? 'var(--green)' : 'var(--red)' }}
                                        >
                                            {signOf(txn)}{txn.account.currency.symbol}
                                            {formatAmount(Math.abs(Number(txn.amount)), txn.account.currency)}
                                        </td>
                                        <td>
                                            <div className="flex justify-end gap-1">
                                                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(txn)} aria-label={t('common.edit')}><Edit2 size={13} /></button>
                                                <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(txn.id)} aria-label={t('common.delete')}><Trash2 size={13} /></button>
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
                    <div className="flex items-center justify-end gap-2 border-t border-line" style={{ padding: '12px 20px' }}>
                        <span className="text-muted text-sm">
                            {t('transactions.page', { page: page + 1, total: data.totalPages })}
                        </span>
                        <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setPage(p => p - 1)} disabled={page === 0} aria-label="Previous page"><ChevronLeft size={14} /></button>
                        <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setPage(p => p + 1)} disabled={page + 1 >= data.totalPages} aria-label="Next page"><ChevronRight size={14} /></button>
                    </div>
                )}
            </div>

            {/* Modal */}
            {showModal && (
                <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
                    <div className="modal">
                        <div className="modal-header">
                            <h2 className="modal-title">{editing ? t('transactions.editTitle') : t('transactions.add')}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)} aria-label={t('common.close')}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <div className="alert-error" role="alert">{error}</div>}
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">{t('common.type')}</label>
                                    <select className="form-control" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as TransactionType, categoryId: 0 }))} disabled={!!editing}>
                                        <option value="EXPENSE">{t('common.expense')}</option>
                                        <option value="INCOME">{t('common.income')}</option>
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('common.date')}</label>
                                    <input type="date" className="form-control" value={form.date} onChange={e => setForm(f => ({ ...f, date: e.target.value }))} />
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('common.account')} *</label>
                                <select className="form-control" value={form.accountId} onChange={e => setForm(f => ({ ...f, accountId: Number(e.target.value) }))} disabled={!!editing}>
                                    <option value={0}>{t('transactions.selectAccount')}</option>
                                    {accounts.map(a => <option key={a.id} value={a.id}>{a.name} ({a.currency.code})</option>)}
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('common.category')} *</label>
                                <select className="form-control" value={form.categoryId} onChange={e => setForm(f => ({ ...f, categoryId: Number(e.target.value) }))}>
                                    <option value={0}>{t('transactions.selectCategory')}</option>
                                    {filteredCats.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('common.amount')} *</label>
                                <input type="number" step="0.01" min="0.01" className="form-control" value={form.amount} onChange={e => setForm(f => ({ ...f, amount: Number(e.target.value) }))} />
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('common.description')}</label>
                                <input className="form-control" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder={t('transactions.descriptionPlaceholder')} />
                            </div>
                        </div>
                        <div className="modal-footer">
                            <button className="btn btn-ghost" onClick={() => setShowModal(false)}>{t('common.cancel')}</button>
                            <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? t('common.saving') : t('common.save')}</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Transactions;
