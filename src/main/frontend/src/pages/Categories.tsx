import React, { useEffect, useState } from 'react';
import { categoryApi } from '../api';
import { Category, CategoryType, CreateCategoryRequest } from '../types';
import { Plus, Edit2, Trash2, X } from 'lucide-react';
import { useT } from '../i18n';

const ICONS = ['tag', 'briefcase', 'laptop', 'trending-up', 'home', 'shopping-cart', 'car', 'heart', 'film', 'shopping-bag', 'zap', 'book', 'coffee', 'map', 'more-horizontal', 'plus-circle', 'dollar-sign'];
const COLORS = ['#10B981', '#06B6D4', '#8B5CF6', '#F59E0B', '#6366F1', '#EF4444', '#F97316', '#EC4899', '#3B82F6', '#14B8A6', '#6B7280'];

const Categories: React.FC = () => {
    const t = useT();
    const [categories, setCategories] = useState<Category[]>([]);
    const [tab, setTab] = useState<CategoryType>('EXPENSE');
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState<Category | null>(null);
    const [form, setForm] = useState<CreateCategoryRequest>({ name: '', type: 'EXPENSE', icon: 'tag', color: '#6366F1' });
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    const load = () => categoryApi.getAll().then(setCategories).finally(() => setLoading(false));
    useEffect(() => { load(); }, []);

    const filtered = categories.filter(c => c.type === tab);

    const openCreate = () => {
        setEditing(null);
        setForm({ name: '', type: tab, icon: 'tag', color: '#6366F1' });
        setError(''); setShowModal(true);
    };
    const openEdit = (c: Category) => {
        setEditing(c);
        setForm({ name: c.name, type: c.type, icon: c.icon, color: c.color, description: c.description });
        setError(''); setShowModal(true);
    };

    const save = async () => {
        if (!form.name.trim()) { setError(t('categories.nameRequired')); return; }
        setSaving(true); setError('');
        try {
            if (editing) { await categoryApi.update(editing.id, { name: form.name, icon: form.icon, color: form.color, description: form.description }); }
            else { await categoryApi.create(form); }
            setShowModal(false); await load();
        } catch (e: any) { setError(e.message); }
        finally { setSaving(false); }
    };

    const remove = async (id: number) => {
        if (!window.confirm(t('categories.confirmDelete'))) return;
        try { await categoryApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>{t('categories.title')}</h1>
                    <p className="text-muted text-sm">{t('categories.subtitle')}</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> {t('categories.new')}</button>
            </div>

            {/* Tabs */}
            <div className="mb-5 flex w-fit gap-1 rounded-lg bg-card p-1" role="tablist">
                {(['EXPENSE', 'INCOME'] as CategoryType[]).map(type => (
                    <button
                        key={type}
                        role="tab"
                        aria-selected={tab === type}
                        onClick={() => setTab(type)}
                        className={`flex items-center gap-2 rounded-md px-5 py-1.5 text-[13px] font-semibold transition-all ${
                            tab === type ? 'bg-brand text-white' : 'text-ink-soft hover:text-ink'
                        }`}
                    >
                        <span
                            className="h-2 w-2 rounded-full"
                            style={{ background: type === 'EXPENSE' ? 'var(--red)' : 'var(--green)' }}
                        />
                        {type === 'EXPENSE' ? t('common.expense') : t('common.income')}
                    </button>
                ))}
            </div>

            {filtered.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-state-icon">🏷️</div>
                    <p className="empty-state-text">
                        {tab === 'EXPENSE' ? t('categories.emptyExpense') : t('categories.emptyIncome')}
                    </p>
                    <p className="empty-state-sub">{t('categories.emptyBody')}</p>
                </div>
            ) : (
                <div className="grid-3">
                    {filtered.map(c => (
                        <div key={c.id} className="card flex items-center gap-3.5" style={{ padding: '16px 20px' }}>
                            <div
                                className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-token"
                                style={{ background: `${c.color}22` }}
                            >
                                <span className="h-4 w-4 rounded-full" style={{ background: c.color }} />
                            </div>
                            <div className="min-w-0 flex-1">
                                <p className="truncate text-sm font-semibold">{c.name}</p>
                                <p className="text-muted text-sm truncate">{c.icon}</p>
                            </div>
                            <div className="flex flex-shrink-0 gap-1">
                                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(c)} aria-label={t('common.edit')}><Edit2 size={14} /></button>
                                <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(c.id)} aria-label={t('common.delete')}><Trash2 size={14} /></button>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {showModal && (
                <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
                    <div className="modal">
                        <div className="modal-header">
                            <h2 className="modal-title">{editing ? t('categories.editTitle') : t('categories.new')}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)} aria-label={t('common.close')}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <div className="alert-error" role="alert">{error}</div>}
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">{t('common.name')} *</label>
                                    <input className="form-control" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder={t('categories.namePlaceholder')} />
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('common.type')}</label>
                                    <select className="form-control" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as CategoryType }))} disabled={!!editing}>
                                        <option value="EXPENSE">{t('common.expense')}</option>
                                        <option value="INCOME">{t('common.income')}</option>
                                    </select>
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('categories.iconKey')}</label>
                                <div className="flex flex-wrap gap-1.5">
                                    {ICONS.map(ic => (
                                        <button
                                            key={ic}
                                            type="button"
                                            aria-pressed={form.icon === ic}
                                            onClick={() => setForm(f => ({ ...f, icon: ic }))}
                                            className={`rounded-md border px-2.5 py-1 text-[11px] transition-colors ${
                                                form.icon === ic
                                                    ? 'border-brand bg-brand/15 text-brand-light'
                                                    : 'border-line text-ink-soft hover:border-line-strong hover:text-ink'
                                            }`}
                                        >
                                            {ic}
                                        </button>
                                    ))}
                                </div>
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
                                                boxShadow: form.color === c ? `0 0 0 2px var(--bg-card), 0 0 0 4px ${c}` : undefined,
                                            }}
                                        />
                                    ))}
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">{t('common.description')}</label>
                                <input className="form-control" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder={t('common.optional')} />
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

export default Categories;
