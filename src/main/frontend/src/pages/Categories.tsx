import React, { useEffect, useState } from 'react';
import { categoryApi } from '../api';
import { Category, CategoryType, CreateCategoryRequest } from '../types';
import { Plus, Edit2, Trash2, X } from 'lucide-react';

const ICONS = ['tag', 'briefcase', 'laptop', 'trending-up', 'home', 'shopping-cart', 'car', 'heart', 'film', 'shopping-bag', 'zap', 'book', 'coffee', 'map', 'more-horizontal', 'plus-circle', 'dollar-sign'];
const COLORS = ['#10B981', '#06B6D4', '#8B5CF6', '#F59E0B', '#6366F1', '#EF4444', '#F97316', '#EC4899', '#3B82F6', '#14B8A6', '#6B7280'];

const Categories: React.FC = () => {
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
        if (!form.name.trim()) { setError('Name is required'); return; }
        setSaving(true); setError('');
        try {
            if (editing) { await categoryApi.update(editing.id, { name: form.name, icon: form.icon, color: form.color, description: form.description }); }
            else { await categoryApi.create(form); }
            setShowModal(false); await load();
        } catch (e: any) { setError(e.message); }
        finally { setSaving(false); }
    };

    const remove = async (id: number) => {
        if (!window.confirm('Delete this category?')) return;
        try { await categoryApi.delete(id); await load(); }
        catch (e: any) { alert(e.message); }
    };

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>Categories</h1>
                    <p className="text-muted text-sm">Organize your income and expenses</p>
                </div>
                <button className="btn btn-primary" onClick={openCreate}><Plus size={15} /> New Category</button>
            </div>

            {/* Tabs */}
            <div style={{ display: 'flex', gap: 4, marginBottom: 20, background: 'var(--bg-card)', borderRadius: 8, padding: 4, width: 'fit-content' }}>
                {(['EXPENSE', 'INCOME'] as CategoryType[]).map(t => (
                    <button key={t} onClick={() => setTab(t)}
                        style={{ padding: '7px 20px', borderRadius: 6, fontSize: 13, fontWeight: 600, background: tab === t ? 'var(--accent)' : 'transparent', color: tab === t ? '#fff' : 'var(--text-secondary)', cursor: 'pointer', border: 'none', transition: 'all 150ms' }}>
                        {t === 'EXPENSE' ? '🔴 Expense' : '🟢 Income'}
                    </button>
                ))}
            </div>

            {filtered.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-state-icon">🏷️</div>
                    <p className="empty-state-text">No {tab.toLowerCase()} categories</p>
                    <p className="empty-state-sub">Click "New Category" to add one</p>
                </div>
            ) : (
                <div className="grid-3">
                    {filtered.map(c => (
                        <div key={c.id} className="card" style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '16px 20px' }}>
                            <div style={{ width: 40, height: 40, borderRadius: 10, background: `${c.color}22`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                                <div style={{ width: 16, height: 16, borderRadius: '50%', background: c.color }} />
                            </div>
                            <div style={{ flex: 1, minWidth: 0 }}>
                                <p style={{ fontWeight: 600, fontSize: 14 }}>{c.name}</p>
                                <p className="text-muted text-sm">{c.icon}</p>
                            </div>
                            <div style={{ display: 'flex', gap: 4 }}>
                                <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(c)}><Edit2 size={14} /></button>
                                <button className="btn btn-ghost btn-icon btn-sm" style={{ color: 'var(--red)' }} onClick={() => remove(c.id)}><Trash2 size={14} /></button>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {showModal && (
                <div className="modal-overlay" onClick={e => e.target === e.currentTarget && setShowModal(false)}>
                    <div className="modal">
                        <div className="modal-header">
                            <h2 className="modal-title">{editing ? 'Edit Category' : 'New Category'}</h2>
                            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setShowModal(false)}><X size={16} /></button>
                        </div>
                        <div className="modal-body">
                            {error && <p style={{ color: 'var(--red)', fontSize: 13 }}>{error}</p>}
                            <div className="grid-2">
                                <div className="form-group">
                                    <label className="form-label">Name *</label>
                                    <input className="form-control" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Groceries" />
                                </div>
                                <div className="form-group">
                                    <label className="form-label">Type</label>
                                    <select className="form-control" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as CategoryType }))} disabled={!!editing}>
                                        <option value="EXPENSE">Expense</option>
                                        <option value="INCOME">Income</option>
                                    </select>
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Icon key</label>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                                    {ICONS.map(ic => (
                                        <button key={ic} onClick={() => setForm(f => ({ ...f, icon: ic }))}
                                            style={{ padding: '4px 10px', borderRadius: 6, fontSize: 11, border: `1px solid ${form.icon === ic ? 'var(--accent)' : 'var(--border)'}`, background: form.icon === ic ? 'var(--accent-glow)' : 'transparent', color: form.icon === ic ? 'var(--accent-light)' : 'var(--text-secondary)', cursor: 'pointer' }}>
                                            {ic}
                                        </button>
                                    ))}
                                </div>
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
                            <div className="form-group">
                                <label className="form-label">Description</label>
                                <input className="form-control" value={form.description ?? ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="Optional" />
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

export default Categories;
