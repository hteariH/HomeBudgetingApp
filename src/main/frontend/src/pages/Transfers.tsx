import React, { useState, useEffect } from 'react';
import { accountApi, transferApi } from '../api';
import { Account, Transfer } from '../types';
import { ArrowLeftRight, Trash2, Plus, X, AlertTriangle } from 'lucide-react';
import { formatAmount } from '../money';
import { useT } from '../i18n';

const Transfers: React.FC = () => {
    const t = useT();
    const [transfers, setTransfers] = useState<Transfer[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [formData, setFormData] = useState({
        fromAccountId: '',
        toAccountId: '',
        fromAmount: '',
        toAmount: '',
        date: new Date().toISOString().split('T')[0],
        description: '',
    });

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            const [transfersRes, accountsRes] = await Promise.all([
                transferApi.getAll(),
                accountApi.getAll(),
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
            description: '',
        });
        setError(null);
        setShowModal(true);
    };

    const handleSubmit = async () => {
        // Validation now reports inside the dialog rather than through alert(), which
        // dropped the message the moment it was dismissed.
        if (!formData.fromAccountId || !formData.toAccountId) {
            setError(t('transfers.needBothAccounts'));
            return;
        }
        if (!formData.fromAmount || !formData.toAmount) {
            setError(t('transfers.needAmounts'));
            return;
        }

        setSaving(true);
        setError(null);
        try {
            await transferApi.create({
                fromAccountId: Number(formData.fromAccountId),
                toAccountId: Number(formData.toAccountId),
                fromAmount: Number(formData.fromAmount),
                toAmount: Number(formData.toAmount),
                date: formData.date,
                description: formData.description,
            });
            setShowModal(false);
            await loadData();
        } catch (err: any) {
            setError(err.message);
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id: number) => {
        if (!window.confirm(t('transfers.confirmDelete'))) return;
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
                    <h1 className="section-title" style={{ fontSize: 22 }}>{t('transfers.title')}</h1>
                    <p className="text-muted text-sm">{t('transfers.subtitle')}</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}>
                    <Plus size={15} /> {t('transfers.new')}
                </button>
            </div>

            <div className="card overflow-hidden" style={{ padding: 0 }}>
                {transfers.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-state-icon">
                            <ArrowLeftRight size={40} className="mx-auto block opacity-50" />
                        </div>
                        <p className="empty-state-text">{t('transfers.emptyTitle')}</p>
                        <p className="empty-state-sub">{t('transfers.emptyBody')}</p>
                    </div>
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>{t('common.date')}</th>
                                    <th>{t('transfers.detail')}</th>
                                    <th>{t('common.amount')}</th>
                                    <th>{t('transfers.rate')}</th>
                                    <th className="text-right">{t('common.actions')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {transfers.map(transfer => (
                                    <tr key={transfer.id}>
                                        <td className="whitespace-nowrap">{transfer.date}</td>
                                        <td>
                                            <div className="flex flex-col">
                                                <div className="flex items-center gap-2 font-medium text-ink">
                                                    <span>{transfer.fromAccount.name}</span>
                                                    <ArrowLeftRight size={14} className="flex-shrink-0 text-ink-dim" />
                                                    <span>{transfer.toAccount.name}</span>
                                                </div>
                                                {transfer.description && (
                                                    <span className="text-muted text-sm">{transfer.description}</span>
                                                )}
                                            </div>
                                        </td>
                                        <td>
                                            <div className="flex flex-col whitespace-nowrap text-[13px] font-semibold">
                                                <span className="text-red">
                                                    -{transfer.fromAccount.currency.symbol}
                                                    {formatAmount(transfer.fromAmount, transfer.fromAccount.currency)}
                                                </span>
                                                {transfer.toAccount && (
                                                    <span className="text-green">
                                                        +{transfer.toAccount.currency.symbol}
                                                        {formatAmount(transfer.toAmount, transfer.toAccount.currency)}
                                                    </span>
                                                )}
                                            </div>
                                        </td>
                                        <td>
                                            {transfer.fromAccount.currency.id !== transfer.toAccount.currency.id ? (
                                                <span className="badge whitespace-nowrap border border-line bg-raised text-ink-dim">
                                                    1 {transfer.fromAccount.currency.code} = {Number(transfer.exchangeRate).toFixed(4)}{' '}
                                                    {transfer.toAccount.currency.code}
                                                </span>
                                            ) : (
                                                <span className="badge bg-raised text-ink-dim">1:1</span>
                                            )}
                                        </td>
                                        <td className="text-right">
                                            <button
                                                className="btn btn-ghost btn-icon btn-sm"
                                                style={{ color: 'var(--red)' }}
                                                onClick={() => handleDelete(transfer.id)}
                                                title={t('transfers.deleteTitle')}
                                                aria-label={t('transfers.deleteTitle')}
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
                            <h2 className="modal-title">{t('transfers.new')}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)} aria-label={t('common.close')}><X size={16} /></button>
                        </div>

                        <div className="modal-body">
                            {error && (
                                <div className="alert-error" role="alert">
                                    <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                                    <span>{error}</span>
                                </div>
                            )}

                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">{t('transfers.fromAccount')}</label>
                                    <select
                                        className="form-control"
                                        value={formData.fromAccountId}
                                        onChange={e => setFormData({ ...formData, fromAccountId: e.target.value })}
                                    >
                                        <option value="">{t('transfers.selectAccount')}</option>
                                        {accounts.map(a => (
                                            <option key={a.id} value={a.id}>
                                                {a.name} ({a.currency.symbol}{formatAmount(a.balance, a.currency)} {a.currency.code})
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('transfers.toAccount')}</label>
                                    <select
                                        className="form-control"
                                        value={formData.toAccountId}
                                        onChange={e => setFormData({ ...formData, toAccountId: e.target.value })}
                                    >
                                        <option value="">{t('transfers.selectAccount')}</option>
                                        {accounts.map(a => (
                                            <option key={a.id} value={a.id}>
                                                {a.name} ({a.currency.symbol}{formatAmount(a.balance, a.currency)} {a.currency.code})
                                            </option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">{t('transfers.amountFrom')}</label>
                                    <input
                                        type="number" step="0.01" className="form-control"
                                        value={formData.fromAmount}
                                        onChange={e => setFormData({ ...formData, fromAmount: e.target.value })}
                                        placeholder="0.00"
                                    />
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('transfers.amountTo')}</label>
                                    <input
                                        type="number" step="0.01" className="form-control"
                                        value={formData.toAmount}
                                        onChange={e => setFormData({ ...formData, toAmount: e.target.value })}
                                        placeholder="0.00"
                                    />
                                </div>
                            </div>

                            <div className="form-group">
                                <label className="form-label">{t('common.date')}</label>
                                <input
                                    type="date" className="form-control"
                                    value={formData.date}
                                    onChange={e => setFormData({ ...formData, date: e.target.value })}
                                />
                            </div>

                            <div className="form-group">
                                <label className="form-label">{t('common.description')} ({t('common.optional')})</label>
                                <input
                                    type="text" className="form-control"
                                    value={formData.description}
                                    onChange={e => setFormData({ ...formData, description: e.target.value })}
                                    placeholder={t('transfers.descriptionPlaceholder')}
                                />
                            </div>
                        </div>

                        <div className="modal-footer">
                            <button className="btn btn-ghost" onClick={() => setShowModal(false)}>{t('common.cancel')}</button>
                            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>
                                {saving ? t('transfers.creating') : t('transfers.create')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Transfers;
