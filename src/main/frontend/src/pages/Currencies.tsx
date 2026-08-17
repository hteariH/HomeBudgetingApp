import React, { useEffect, useState } from 'react';
import { currencyApi } from '../api';
import { CreateCurrencyRequest, Currency, UpdateCurrencyRequest } from '../types';
import { Plus, Edit2, Trash2, X, Star } from 'lucide-react';

const Currencies: React.FC = () => {
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
        if (!form.code.trim() || !form.name.trim() || !form.symbol.trim()) { setError('Code, name and symbol are required'); return; }
        if (form.code.length !== 3) { setError('Currency code must be exactly 3 characters'); return; }
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
        if (!window.confirm('Delete this currency?')) return;
        try { await currencyApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>Currencies</h1>
                    <p className="text-muted text-sm">Manage supported currencies and exchange rates</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> Add Currency</button>
            </div>

            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
                {currencies.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">💱</div>
                        <p className="empty-state-text">No currencies yet</p>
                    </div>
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead>
                                <tr><th>Code</th><th>Name</th><th>Symbol</th><th>Rate to USD</th><th>Base</th><th></th></tr>
                            </thead>
                            <tbody>
                                {currencies.map(c => (
                                    <tr key={c.id}>
                                        <td>
                                            <span style={{ fontWeight: 700, color: 'var(--accent-light)', fontFamily: 'monospace', fontSize: 15 }}>{c.code}</span>
                                        </td>
                                        <td>{c.name}</td>
                                        <td style={{ fontWeight: 600 }}>{c.symbol}</td>
                                        <td style={{ fontFamily: 'monospace' }}>{Number(c.exchangeRateToBase).toFixed(6)}</td>
                                        <td>
                                            {c.isBase && <Star size={14} color="var(--yellow)" fill="var(--yellow)" />}
                                        </td>
                                        <td>
                                            <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                                                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(c)}><Edit2 size={13} /></button>
                                                {!c.isBase && <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(c.id)}><Trash2 size={13} /></button>}
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
                            <h2 className="modal-title">{editing ? 'Edit Currency' : 'Add Currency'}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <p style={{ color: 'var(--red)', fontSize: 13 }}>{error}</p>}
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">Code (3 chars) *</label>
                                    <input className="form-control" value={form.code} maxLength={3} onChange={e => setForm(f => ({ ...f, code: e.target.value.toUpperCase() }))} placeholder="e.g. EUR" disabled={!!editing} style={{ fontFamily: 'monospace', letterSpacing: 2 }} />
                                </div>
                                <div className="form-group">
                                    <label className="form-label">Symbol *</label>
                                    <input className="form-control" value={form.symbol} maxLength={10} onChange={e => setForm(f => ({ ...f, symbol: e.target.value }))} placeholder="e.g. €" />
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Name *</label>
                                <input className="form-control" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Euro" />
                            </div>
                            <div className="form-group">
                                <label className="form-label">Exchange Rate to USD</label>
                                <input type="number" step="0.000001" min="0.000001" className="form-control" value={form.exchangeRateToBase} onChange={e => setForm(f => ({ ...f, exchangeRateToBase: Number(e.target.value) }))} />
                                <p className="text-muted text-sm" style={{ marginTop: 4 }}>How many units of this currency equal 1 USD</p>
                            </div>
                            {!editing && (
                                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: 'var(--text-secondary)', cursor: 'pointer' }}>
                                    <input type="checkbox" checked={form.isBase} onChange={e => setForm(f => ({ ...f, isBase: e.target.checked }))} />
                                    Mark as base currency
                                </label>
                            )}
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

export default Currencies;
