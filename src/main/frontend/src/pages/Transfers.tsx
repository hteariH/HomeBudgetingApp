import React, { useState, useEffect } from 'react';
import { accountApi, transferApi } from '../api';
import { Account, Transfer } from '../types';
import { ArrowLeftRight, Trash2, Plus, X } from 'lucide-react';

const fmt = (n: number, sym = '') =>
    `${sym}${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const Transfers: React.FC = () => {
    const [transfers, setTransfers] = useState<Transfer[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [saving, setSaving] = useState(false);

    const [formData, setFormData] = useState({
        fromAccountId: '',
        toAccountId: '',
        fromAmount: '',
        toAmount: '',
        date: new Date().toISOString().split('T')[0],
        description: ''
    });

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            const [transfersRes, accountsRes] = await Promise.all([
                transferApi.getAll(),
                accountApi.getAll()
            ]);
            setTransfers(transfersRes.content);
            setAccounts(accountsRes);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const openCreate = () => {
        setFormData({
            fromAccountId: '',
            toAccountId: '',
            fromAmount: '',
            toAmount: '',
            date: new Date().toISOString().split('T')[0],
            description: ''
        });
        setShowModal(true);
    };

    const handleSubmit = async () => {
        if (!formData.fromAccountId || !formData.toAccountId) {
            alert('Please select both from and to accounts');
            return;
        }
        if (!formData.fromAmount || !formData.toAmount) {
            alert('Please enter amounts');
            return;
        }

        setSaving(true);
        try {
            await transferApi.create({
                fromAccountId: Number(formData.fromAccountId),
                toAccountId: Number(formData.toAccountId),
                fromAmount: Number(formData.fromAmount),
                toAmount: Number(formData.toAmount),
                date: formData.date,
                description: formData.description
            });
            setShowModal(false);
            await loadData();
        } catch (err: any) {
            alert(err.message);
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id: number) => {
        if (!window.confirm('Delete this transfer?')) return;
        try {
            await transferApi.delete(id);
            await loadData();
        } catch (err: any) {
            alert(err.message);
        }
    };

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>Transfers</h1>
                    <p className="text-muted text-sm">Move money between your accounts</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}>
                    <Plus size={15} /> New Transfer
                </button>
            </div>

            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
                {transfers.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">
                            <ArrowLeftRight size={40} style={{ opacity: 0.5, margin: '0 auto', display: 'block' }} />
                        </div>
                        <p className="empty-state-text">No transfers yet</p>
                        <p className="empty-state-sub">Click "New Transfer" to move money between accounts.</p>
                    </div>
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Date</th>
                                    <th>Transfer Detail</th>
                                    <th>Amount</th>
                                    <th>Rate</th>
                                    <th style={{ textAlign: 'right' }}>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {transfers.map(t => (
                                    <tr key={t.id}>
                                        <td>{t.date}</td>
                                        <td>
                                            <div className="flex flex-col">
                                                <div className="flex items-center gap-2" style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
                                                    <span>{t.fromAccount.name}</span>
                                                    <ArrowLeftRight size={14} style={{ color: 'var(--text-muted)' }} />
                                                    <span>{t.toAccount.name}</span>
                                                </div>
                                                {t.description && <span className="text-muted text-sm" style={{ marginTop: 2 }}>{t.description}</span>}
                                            </div>
                                        </td>
                                        <td>
                                            <div className="flex flex-col font-semibold" style={{ fontSize: 13 }}>
                                                <span className="text-red">-{t.fromAccount.currency.symbol}{fmt(t.fromAmount)}</span>
                                                {t.toAccount && <span className="text-green">+{t.toAccount.currency.symbol}{fmt(t.toAmount)}</span>}
                                            </div>
                                        </td>
                                        <td>
                                            {t.fromAccount.currency.id !== t.toAccount.currency.id ? (
                                                <span className="badge badge-transfer" style={{ background: 'var(--bg-hover)', color: 'var(--text-muted)', border: '1px solid var(--border)' }}>
                                                    1 {t.fromAccount.currency.code} = {t.exchangeRate.toFixed(4)} {t.toAccount.currency.code}
                                                </span>
                                            ) : (
                                                <span className="badge" style={{ background: 'var(--bg-hover)', color: 'var(--text-muted)' }}>1:1</span>
                                            )}
                                        </td>
                                        <td style={{ textAlign: 'right' }}>
                                            <button
                                                className="btn btn-ghost btn-icon btn-sm"
                                                style={{ color: 'var(--red)' }}
                                                onClick={() => handleDelete(t.id)}
                                                title="Delete Transfer"
                                            >
                                                <Trash2 size={16} />
                                            </button>
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
                            <h2 className="modal-title">New Transfer</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)}><X size={16} /></button>
                        </div>

                        <div className="modal-body">
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">From Account</label>
                                    <select
                                        className="form-control"
                                        value={formData.fromAccountId}
                                        onChange={e => setFormData({ ...formData, fromAccountId: e.target.value })}
                                    >
                                        <option value="">Select Account</option>
                                        {accounts.map(a => (
                                            <option key={a.id} value={a.id}>{a.name} ({fmt(a.balance, a.currency.symbol)} {a.currency.code})</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">To Account</label>
                                    <select
                                        className="form-control"
                                        value={formData.toAccountId}
                                        onChange={e => setFormData({ ...formData, toAccountId: e.target.value })}
                                    >
                                        <option value="">Select Account</option>
                                        {accounts.map(a => (
                                            <option key={a.id} value={a.id}>{a.name} ({fmt(a.balance, a.currency.symbol)} {a.currency.code})</option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">Amount (From)</label>
                                    <input
                                        type="number" step="0.01" className="form-control"
                                        value={formData.fromAmount}
                                        onChange={e => setFormData({ ...formData, fromAmount: e.target.value })}
                                        placeholder="0.00"
                                    />
                                </div>
                                <div className="form-group">
                                    <label className="form-label">Amount (To)</label>
                                    <input
                                        type="number" step="0.01" className="form-control"
                                        value={formData.toAmount}
                                        onChange={e => setFormData({ ...formData, toAmount: e.target.value })}
                                        placeholder="0.00"
                                    />
                                </div>
                            </div>

                            <div className="form-group">
                                <label className="form-label">Date</label>
                                <input
                                    type="date" className="form-control"
                                    value={formData.date}
                                    onChange={e => setFormData({ ...formData, date: e.target.value })}
                                />
                            </div>

                            <div className="form-group">
                                <label className="form-label">Description (Optional)</label>
                                <input
                                    type="text" className="form-control"
                                    value={formData.description}
                                    onChange={e => setFormData({ ...formData, description: e.target.value })}
                                    placeholder="e.g. Monthly Savings"
                                />
                            </div>
                        </div>

                        <div className="modal-footer">
                            <button className="btn btn-ghost" onClick={() => setShowModal(false)}>Cancel</button>
                            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>
                                {saving ? 'Creating…' : 'Create Transfer'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Transfers;
