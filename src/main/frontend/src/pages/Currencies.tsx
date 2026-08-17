import React, { useEffect, useState } from 'react';
import { currencyApi } from '../api';
import { CreateCurrencyRequest, Currency, UpdateCurrencyRequest } from '../types';
import { Plus, Edit2, Trash2, X, Star } from 'lucide-react';
import { useT } from '../i18n';

const Currencies: React.FC = () => {
    const t = useT();
    const [currencies, setCurrencies] = useState<Currency[]>([]);
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState<Currency | null>(null);
    const [form, setForm] = useState<CreateCurrencyRequest>({ code: '', name: '', symbol: '', exchangeRateToBase: 1, isBase: false });
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    const load = () => currencyApi.getAll().then(setCurrencies).finally(() => setLoading(false));
    useEffect(() => { load(); }, []);

    const openCreate = () => {
        setEditing(null);
        setForm({ code: '', name: '', symbol: '', exchangeRateToBase: 1, isBase: false });
        setError(''); setShowModal(true);
    };
    const openEdit = (c: Currency) => {
        setEditing(c);
        setForm({ code: c.code, name: c.name, symbol: c.symbol, exchangeRateToBase: Number(c.exchangeRateToBase), isBase: c.isBase });
        setError(''); setShowModal(true);
    };

    const save = async () => {
        if (!form.code.trim() || !form.name.trim() || !form.symbol.trim()) { setError(t('currencies.fieldsRequired')); return; }
        if (form.code.length !== 3) { setError(t('currencies.codeLength')); return; }
        setSaving(true); setError('');
        try {
            if (editing) {
                const upd: UpdateCurrencyRequest = { name: form.name, symbol: form.symbol, exchangeRateToBase: form.exchangeRateToBase };
                await currencyApi.update(editing.id, upd);
            } else {
                await currencyApi.create(form);
            }
            setShowModal(false); await load();
        } catch (e: any) { setError(e.message); }
        finally { setSaving(false); }
    };

    const remove = async (id: number) => {
        if (!window.confirm(t('currencies.confirmDelete'))) return;
        try { await currencyApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>{t('currencies.title')}</h1>
                    <p className="text-muted text-sm">{t('currencies.subtitle')}</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> {t('currencies.add')}</button>
            </div>

            <div className="card overflow-hidden" style={{ padding: 0 }}>
                {currencies.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">💱</div>
                        <p className="empty-state-text">{t('currencies.empty')}</p>
                    </div>
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>{t('currencies.code')}</th>
                                    <th>{t('common.name')}</th>
                                    <th>{t('currencies.symbol')}</th>
                                    <th>{t('currencies.rateToBase')}</th>
                                    <th>{t('currencies.base')}</th>
                                    <th />
                                </tr>
                            </thead>
                            <tbody>
                                {currencies.map(c => (
                                    <tr key={c.id}>
                                        <td>
                                            <span className="font-mono text-[15px] font-bold text-accent">{c.code}</span>
                                        </td>
                                        <td>{c.name}</td>
                                        <td className="font-semibold">{c.symbol}</td>
                                        <td className="font-mono">{Number(c.exchangeRateToBase).toFixed(6)}</td>
                                        <td>
                                            {c.isBase && (
                                                <Star size={14} color="var(--yellow)" fill="var(--yellow)" aria-label={t('currencies.base')} />
                                            )}
                                        </td>
                                        <td>
                                            <div className="flex justify-end gap-1">
                                                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(c)} aria-label={t('common.edit')}><Edit2 size={13} /></button>
                                                {!c.isBase && (
                                                    <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(c.id)} aria-label={t('common.delete')}><Trash2 size={13} /></button>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {showModal && (
                <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
                    <div className="modal">
                        <div className="modal-header">
                            <h2 className="modal-title">{editing ? t('currencies.editTitle') : t('currencies.add')}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)} aria-label={t('common.close')}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <div className="alert-error" role="alert">{error}</div>}
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">{t('currencies.codeLabel')} *</label>
                                    <input
                                        className="form-control font-mono tracking-[0.15em]"
                                        value={form.code}
                                        maxLength={3}
                                        onChange={e => setForm(f => ({ ...f, code: e.target.value.toUpperCase() }))}
                                        placeholder="EUR"
                                        disabled={!!editing}
                                    />
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('currencies.symbol')} *</label>
                                    <input className="form-control" value={form.symbol} maxLength={10} onChange={e => setForm(f => ({ ...f, symbol: e.target.value }))} placeholder="€" />
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('common.name')} *</label>
                                <input className="form-control" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="Euro" />
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('currencies.rateToBase')}</label>
                                <input type="number" step="0.000001" min="0.000001" className="form-control" value={form.exchangeRateToBase} onChange={e => setForm(f => ({ ...f, exchangeRateToBase: Number(e.target.value) }))} />
                                <p className="text-muted text-sm">{t('currencies.rateHint')}</p>
                            </div>
                            {!editing && (
                                <label className="flex cursor-pointer items-center gap-2 text-sm text-ink-soft">
                                    <input type="checkbox" checked={form.isBase} onChange={e => setForm(f => ({ ...f, isBase: e.target.checked }))} />
                                    {t('currencies.markAsBase')}
                                </label>
                            )}
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

export default Currencies;
