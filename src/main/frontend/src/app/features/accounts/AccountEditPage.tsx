import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Archive, ArchiveRestore, Trash2 } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useAccounts, useBaseCurrency, useCurrencies } from '../../api/hooks';
import { invalidateLedger } from '../../api/queryClient';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { CurrencySelect } from '../../components/CurrencySelect';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, ColorPicker, Field, IconPicker, Switch } from '../../components/forms';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import { AppRoutes } from '../../lib/routes';
import { prefs } from '../../prefs';
import { BackButton, Page, PageLoading } from '../../shell/Page';
import { initialBalanceForRequest } from './accounts.logic';

interface Form {
    name: string;
    currency: string;
    initialBalance: string;
    icon: string;
    color: string;
    includeInTotal: boolean;
    archived: boolean;
    note: string;
}

/** Create or edit an account. Port of AccountEditScreen.kt. */
const AccountEditPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const id = Number(params.get('accountId')) || null;
    const base = useBaseCurrency();
    const { data: accounts, isLoading } = useAccounts();
    const { data: currencies = [] } = useCurrencies();
    const existing = id ? accounts?.find(a => a.id === id) : undefined;
    const [form, setForm] = useState<Form>({
        name: '',
        currency: base,
        initialBalance: '0',
        icon: 'wallet',
        color: '#3D4499',
        includeInTotal: true,
        archived: false,
        note: '',
    });
    const [loaded, setLoaded] = useState(!id);
    const [confirmDelete, setConfirmDelete] = useState(false);

    // Counted with a one-row page of the ledger rather than by pulling every transaction.
    const { data: txCount = 0 } = useQuery({
        queryKey: ['transactions', 'count', id],
        queryFn: async () => (await endpoints.transactions({ accountId: id!, size: 1 })).totalElements,
        enabled: !!id,
    });

    useEffect(() => {
        if (existing && !loaded) {
            setForm({
                name: existing.name,
                currency: existing.currency.code,
                initialBalance: existing.initialBalance,
                icon: existing.icon ?? 'wallet',
                color: existing.color ?? '#3D4499',
                includeInTotal: existing.includeInTotal !== false,
                archived: !!existing.archived,
                note: existing.description ?? '',
            });
            setLoaded(true);
        }
    }, [existing, loaded]);

    const decimals = CurrencyRegistry.decimalsOrFallback(form.currency);
    const balance = initialBalanceForRequest(form.initialBalance, decimals);

    const done = async () => {
        await invalidateLedger();
        navigate(AppRoutes.accounts);
    };

    const save = useMutation({
        mutationFn: async () => {
            const common = {
                name: form.name.trim(),
                initialBalance: balance ?? '0',
                icon: form.icon,
                color: form.color,
                description: form.note.trim() || null,
                includeInTotal: form.includeInTotal,
            };
            if (id) {
                await endpoints.updateAccount(id, { ...common, archived: form.archived });
                return;
            }
            const currencyId = currencies.find(c => c.code === form.currency)?.id;
            if (!currencyId) throw new Error('unknown currency');
            const created = await endpoints.createAccount({ ...common, currencyId });
            // The first account becomes the one the entry form opens on.
            if (prefs.defaultAccountId === null) prefs.setDefaultAccountId(created.id);
        },
        onSuccess: done,
    });

    const remove = useMutation({
        mutationFn: () => endpoints.deleteAccount(id!),
        onSuccess: async () => {
            if (prefs.defaultAccountId === id) prefs.setDefaultAccountId(null);
            await done();
        },
    });

    if (id && isLoading) return <PageLoading />;

    const canSave = form.name.trim() !== '' && form.currency !== '' && balance !== null && !save.isPending;
    const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm(f => ({ ...f, [key]: value }));

    return (
        <Page
            width="form"
            leading={<BackButton fallback={AppRoutes.accounts} label={t('action_back')} />}
            title={id ? t('action_edit') : t('accounts_new')}
            actions={
                <>
                    {id && (
                        <>
                            <button
                                type="button"
                                className={`btn btn-ghost btn-icon ${form.archived ? 'text-brand-light' : ''}`}
                                aria-label={t('action_archive')}
                                aria-pressed={form.archived}
                                title={t('action_archive')}
                                onClick={() => set('archived', !form.archived)}
                            >
                                {form.archived ? <ArchiveRestore size={18} /> : <Archive size={18} />}
                            </button>
                            <button
                                type="button"
                                className="btn btn-ghost btn-icon"
                                aria-label={t('action_delete')}
                                title={t('action_delete')}
                                onClick={() => setConfirmDelete(true)}
                            >
                                <Trash2 size={18} />
                            </button>
                        </>
                    )}
                    <button type="button" className="btn btn-primary" disabled={!canSave} onClick={() => save.mutate()}>
                        {save.isPending ? t('common_saving') : t('action_save')}
                    </button>
                </>
            }
        >
            <ErrorBanner error={save.error ?? remove.error} className="mb-4" />
            <Card className="flex flex-col gap-5 p-5">
                <Field label={t('accounts_name')}>
                    <input
                        className="form-control"
                        value={form.name}
                        placeholder={t('accounts_name_placeholder')}
                        onChange={e => set('name', e.target.value)}
                        autoFocus={!id}
                    />
                </Field>
                {/* The server keys an account to its currency for good: the update request
                    has no currency, so the field is fixed once the account exists. */}
                <CurrencySelect
                    label={t('accounts_currency')}
                    selected={form.currency}
                    currencies={currencies}
                    onSelect={code => set('currency', code)}
                    disabled={!!id}
                />
                <Field label={t('accounts_initial_balance')}>
                    <input
                        className="form-control"
                        inputMode="decimal"
                        value={form.initialBalance}
                        onChange={e => set('initialBalance', e.target.value.replace(/[^0-9.,-]/g, ''))}
                    />
                </Field>
                <Field label={t('quickadd_note')}>
                    <input className="form-control" value={form.note} onChange={e => set('note', e.target.value)} />
                </Field>
                <div>
                    <div className="form-label mb-2">{t('accounts_icon')}</div>
                    <IconPicker value={form.icon} color={form.color} onChange={v => set('icon', v)} />
                </div>
                <div>
                    <div className="form-label mb-2">{t('accounts_color')}</div>
                    <ColorPicker value={form.color} onChange={v => set('color', v)} />
                </div>
                <Switch checked={form.includeInTotal} onChange={v => set('includeInTotal', v)} label={t('accounts_total')} />
            </Card>
            <ConfirmDialog
                open={confirmDelete}
                title={t('action_delete')}
                message={
                    txCount > 0
                        ? t('accounts_delete_warning', { 0: form.name, 1: txCount })
                        : `${t('accounts_name')}: ${form.name}`
                }
                destructive
                busy={remove.isPending}
                onConfirm={() => remove.mutate()}
                onCancel={() => setConfirmDelete(false)}
            />
        </Page>
    );
};

export default AccountEditPage;
