import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Trash2 } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useBaseCurrency, useCategories } from '../../api/hooks';
import { qk, queryClient } from '../../api/queryClient';
import type { CategoryType } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, ColorPicker, Field, IconPicker, NEUTRAL_COLOR, Segmented } from '../../components/forms';
import { sanitizeExpression } from '../../lib/expressionInput';
import { AppRoutes } from '../../lib/routes';
import { BackButton, Page, PageLoading } from '../../shell/Page';
import { budgetForRequest, parseCategoryType } from './categories.logic';

interface Form {
    name: string;
    type: CategoryType;
    icon: string;
    color: string;
    budget: string;
}

/** Create or edit a category. Port of CategoryEditScreen.kt. */
const CategoryEditPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const id = Number(params.get('categoryId')) || null;
    const base = useBaseCurrency();
    const { data: categories, isLoading } = useCategories();
    const existing = id ? categories?.find(c => c.id === id) : undefined;
    const [form, setForm] = useState<Form>({
        name: '',
        type: parseCategoryType(params.get('type')),
        icon: 'more_horiz',
        color: NEUTRAL_COLOR,
        budget: '',
    });
    const [loaded, setLoaded] = useState(!id);
    const [confirmDelete, setConfirmDelete] = useState(false);

    useEffect(() => {
        if (existing && !loaded) {
            setForm({
                name: existing.name,
                type: existing.type,
                icon: existing.icon,
                color: existing.color,
                budget: existing.budgetLimit ? String(Number(existing.budgetLimit)) : '',
            });
            setLoaded(true);
        }
    }, [existing, loaded]);

    const back = () => navigate(`${AppRoutes.categories}?type=${form.type}`);
    const done = async () => {
        await queryClient.invalidateQueries({ queryKey: qk.categories });
        back();
    };

    const save = useMutation({
        mutationFn: () => {
            const body = {
                name: form.name.trim(),
                type: form.type,
                icon: form.icon,
                color: form.color,
                parentId: existing?.parentId ?? null,
                sortOrder: existing?.sortOrder ?? 0,
                budgetLimit: budgetForRequest(form.type, form.budget),
            };
            return id ? endpoints.updateCategory(id, body) : endpoints.createCategory(body);
        },
        onSuccess: done,
    });

    // The server keeps the row and its history when a used category is deleted, which is
    // what archiving meant in the Compose client - so there is one action, not two.
    const remove = useMutation({ mutationFn: () => endpoints.deleteCategory(id!), onSuccess: done });

    if (id && isLoading) return <PageLoading />;

    const canSave = form.name.trim() !== '' && !save.isPending;
    const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm(f => ({ ...f, [key]: value }));

    return (
        <Page
            width="form"
            leading={<BackButton fallback={AppRoutes.categories} label={t('action_back')} />}
            title={id ? t('action_edit') : t('categories_new')}
            actions={
                <>
                    {id && (
                        <button
                            type="button"
                            className="btn btn-ghost btn-icon"
                            aria-label={t('action_delete')}
                            title={t('action_delete')}
                            onClick={() => setConfirmDelete(true)}
                        >
                            <Trash2 size={18} />
                        </button>
                    )}
                    <button type="button" className="btn btn-primary" disabled={!canSave} onClick={() => save.mutate()}>
                        {save.isPending ? t('common_saving') : t('action_save')}
                    </button>
                </>
            }
        >
            <ErrorBanner error={save.error ?? remove.error} className="mb-4" />
            <Card className="flex flex-col gap-5 p-5">
                <Field label={t('categories_name')}>
                    <input
                        className="form-control"
                        value={form.name}
                        placeholder={t('categories_name_placeholder')}
                        onChange={e => set('name', e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && canSave && save.mutate()}
                        autoFocus={!id}
                    />
                </Field>
                <Segmented<CategoryType>
                    value={form.type}
                    onChange={v => set('type', v)}
                    options={[
                        { value: 'EXPENSE', label: t('categories_expense') },
                        { value: 'INCOME', label: t('categories_income') },
                    ]}
                />
                {form.type === 'EXPENSE' && (
                    <Field label={`${t('category_budget')} (${base})`} hint={t('category_budget_hint')}>
                        <input
                            className="form-control"
                            inputMode="decimal"
                            value={form.budget}
                            onChange={e => set('budget', sanitizeExpression(e.target.value))}
                        />
                    </Field>
                )}
                <div>
                    <div className="form-label mb-2">{t('accounts_icon')}</div>
                    <IconPicker value={form.icon} color={form.color} onChange={v => set('icon', v)} />
                </div>
                <div>
                    <div className="form-label mb-2">{t('accounts_color')}</div>
                    <ColorPicker value={form.color} onChange={v => set('color', v)} />
                </div>
            </Card>
            <ConfirmDialog
                open={confirmDelete}
                title={t('action_delete')}
                message={t('category_delete_confirm')}
                destructive
                busy={remove.isPending}
                onConfirm={() => remove.mutate()}
                onCancel={() => setConfirmDelete(false)}
            />
        </Page>
    );
};

export default CategoryEditPage;
