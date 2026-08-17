import React, { useEffect, useState } from 'react';
import { accountApi, currencyApi } from '../api';
import { Account, AccountType, Currency, CreateAccountRequest, UpdateAccountRequest } from '../types';
import { Plus, Edit2, Trash2, X, Wallet } from 'lucide-react';
import { formatAmount } from '../money';
import { useT } from '../i18n';

const ACCOUNT_TYPES: AccountType[] = ['CHECKING', 'SAVINGS', 'CASH', 'CREDIT', 'INVESTMENT'];
const COLORS = ['#4F46E5', '#10B981', '#F59E0B', '#EF4444', '#06B6D4', '#8B5CF6', '#EC4899', '#14B8A6', '#F97316', '#6B7280'];


// Per-currency formatting lives in ../money. This local helper is only for
// aggregates that are not tied to a single currency (totals, chart axes).
const fmt = (n: number, sym = '') =>
    `${sym}${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const Accounts: React.FC = () => {
    const t = useT();
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
        if (!form.name.trim()) { setError(t('accounts.nameRequired')); return; }
        if (!form.currencyId) { setError(t('accounts.currencyRequired')); return; }
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
        if (!window.confirm(t('accounts.confirmDelete'))) return;
        try { await accountApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    const totalBalance = accounts.reduce((s, a) => s + Number(a.balance), 0);

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>{t('accounts.title')}</h1>
                    <p className="text-muted text-sm">{t('accounts.subtitle')}</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> {t('accounts.new')}</button>
            </div>

            {/* Summary bar */}
            <div className="card mb-6 flex items-center gap-4" style={{ padding: '16px 24px' }}>
                <span className="rounded-lg p-2" style={{ background: 'rgb(var(--accent-rgb) / 0.15)' }}>
                    <Wallet size={20} color="var(--accent-light)" />
                </span>
                <div>
                    <p className="text-muted text-sm">{t('accounts.totalBalance')}</p>
                    <p style={{ fontSize: 20, fontWeight: 700 }}>${fmt(totalBalance)}</p>
                </div>
            </div>

            {accounts.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-state-icon">🏦</div>
                    <p className="empty-state-text">{t('accounts.emptyTitle')}</p>
                    <p className="empty-state-sub">{t('accounts.emptyBody')}</p>
                </div>
            ) : (
                <div className="grid-3">
                    {accounts.map(a => (
                        <div key={a.id} className="card" style={{ borderTop: `3px solid ${a.color}`, padding: '20px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                                <div className="min-w-0">
                                    <p className="truncate" style={{ fontWeight: 700, fontSize: 16, marginBottom: 2 }}>{a.name}</p>
                                    <p className="text-muted text-sm">{t(`accountType.${a.type}`)} · {a.currency.code}</p>
                                </div>
                                <div className="flex flex-shrink-0 gap-1.5">
                                    <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(a)} aria-label={t('common.edit')}><Edit2 size={14} /></button>
                                    <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(a.id)} aria-label={t('common.delete')}><Trash2 size={14} /></button>
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
                            <h2 className="modal-title">{editing ? t('accounts.edit') : t('accounts.new')}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)} aria-label={t('common.close')}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <div className="alert-error" role="alert">{error}</div>}
                            <div className="form-group">
                                <label className="form-label">{t('accounts.nameLabel')} *</label>
                                <input className="form-control" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder={t('accounts.namePlaceholder')} />
                            </div>
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">{t('common.type')}</label>
                                    <select className="form-control" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as AccountType }))}>
                                        {ACCOUNT_TYPES.map(type => <option key={type} value={type}>{t(`accountType.${type}`)}</option>)}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('accounts.currency')}</label>
                                    <select className="form-control" value={form.currencyId} onChange={e => setForm(f => ({ ...f, currencyId: Number(e.target.value) }))} disabled={!!editing}>
                                        {currencies.map(c => <option key={c.id} value={c.id}>{c.code} — {c.name}</option>)}
                                    </select>
                                </div>
                            </div>
                            {!editing && (
                                <div className="form-group">
                                    <label className="form-label">{t('accounts.initialBalance')}</label>
                                    <input type="number" step="0.01" className="form-control" value={form.initialBalance} onChange={e => setForm(f => ({ ...f, initialBalance: Number(e.target.value) }))} />
                                </div>
                            )}
                            <div className="form-group">
                                <label className="form-label">{t('common.description')}</label>
                                <input className="form-control" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder={t('common.optional')} />
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('common.color')}</label>
                                <div className="flex flex-wrap gap-2">
                                    {COLORS.map(c => (
                                        <button
                                            key={c}
                                            type="button"
                                            aria-label={c}
                                            aria-pressed={form.color === c}
                                            onClick={() => setForm(f => ({ ...f, color: c }))}
                                            className="h-7 w-7 rounded-full transition-transform hover:scale-110"
                                            style={{
                                                background: c,
                                                // Ring instead of a white border: white is invisible in light mode.
                                                boxShadow: form.color === c ? `0 0 0 2px var(--bg-card), 0 0 0 4px ${c}` : undefined,
                                            }}
                                        />
                                    ))}
                                </div>
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

export default Accounts;
