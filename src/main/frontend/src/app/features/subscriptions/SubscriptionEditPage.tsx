import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Trash2 } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useAccounts, useCategories } from '../../api/hooks';
import { qk, queryClient } from '../../api/queryClient';
import type { SubscriptionFrequency } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, Field, Switch } from '../../components/forms';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import { todayIso } from '../../lib/dates';
import { sanitizeExpression } from '../../lib/expressionInput';
import { toTrimmedString, parseAmount } from '../../lib/money';
import { AppRoutes } from '../../lib/routes';
import { BackButton, Page, PageLoading } from '../../shell/Page';
import { splitAccounts } from '../accounts/accounts.logic';
import { positiveAmount } from '../quickadd/quickadd.logic';

const FREQUENCIES: SubscriptionFrequency[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'];

interface Form {
    name: string;
    amount: string;
    accountId: number | null;
    categoryId: number | null;
    frequency: SubscriptionFrequency;
    interval: number;
    nextChargeDate: string;
    active: boolean;
}

/**
 * Create or edit a subscription. Port of SubscriptionEditScreen.kt. The account is fixed
 * once one exists: the server pins a subscription's currency to its account's, and its
 * update request has no account.
 */
const SubscriptionEditPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const id = Number(params.get('subscriptionId')) || null;
    const { data: existing, isLoading } = useQuery({
        queryKey: [...qk.subscriptions, id],
        queryFn: () => endpoints.subscription(id!),
        enabled: !!id,
    });
    const { data: accounts = [] } = useAccounts();
    const active = useMemo(() => splitAccounts(accounts).active, [accounts]);
    const { data: categories = [] } = useCategories('EXPENSE');
    const [form, setForm] = useState<Form>({
        name: '',
        amount: '',
        accountId: null,
        categoryId: null,
        frequency: 'MONTHLY',
        interval: 1,
        nextChargeDate: todayIso(),
        active: true,
    });
    const [loaded, setLoaded] = useState(!id);
    const [confirmDelete, setConfirmDelete] = useState(false);

    useEffect(() => {
        if (existing && !loaded) {
            setForm({
                name: existing.name,
                amount: toTrimmedString(parseAmount(existing.amount)),
                accountId: existing.account.id,
                categoryId: existing.category?.id ?? null,
                frequency: existing.frequency,
                interval: existing.intervalCount ?? 1,
                nextChargeDate: existing.nextChargeDate,
                active: existing.active !== false,
            });
            setLoaded(true);
        }
    }, [existing, loaded]);

    const account = active.find(a => a.id === form.accountId) ?? (id ? existing?.account : active[0]);
    const code = account?.currency.code ?? 'USD';
    const amount = positiveAmount(form.amount, code);

    const done = async () => {
        await queryClient.invalidateQueries({ queryKey: qk.subscriptions });
        navigate(AppRoutes.subscriptions);
    };

    const save = useMutation({
        mutationFn: async () => {
            const common = {
                name: form.name.trim(),
                categoryId: form.categoryId,
                amount: amount!.toFixed(CurrencyRegistry.decimalsOrFallback(code)),
                frequency: form.frequency,
                intervalCount: form.interval,
                nextChargeDate: form.nextChargeDate,
            };
            if (id) {
                await endpoints.updateSubscription(id, { ...common, active: form.active });
            } else {
                const created = await endpoints.createSubscription({
                    ...common,
                    accountId: account!.id,
                    startDate: todayIso(),
                    reminderMinutesBefore: 24 * 60,
                });
                if (!form.active) await endpoints.updateSubscription(created.id, { active: false });
            }
        },
        onSuccess: done,
    });

    const remove = useMutation({ mutationFn: () => endpoints.deleteSubscription(id!), onSuccess: done });

    if (id && isLoading) return <PageLoading />;

    const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm(f => ({ ...f, [key]: value }));
    const canSave = form.name.trim() !== '' && amount !== null && !!account && !save.isPending;

    return (
        <Page
            width="form"
            leading={<BackButton fallback={AppRoutes.subscriptions} label={t('action_back')} />}
            title={id ? t('action_edit') : t('subscriptions_new')}
            actions={
                <>
                    {id && (
                        <button type="button" className="btn btn-ghost btn-icon" aria-label={t('action_delete')} title={t('action_delete')} onClick={() => setConfirmDelete(true)}>
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
                <Field label={t('subscriptions_name')}>
                    <input className="form-control" value={form.name} onChange={e => set('name', e.target.value)} autoFocus={!id} />
                </Field>
                <Field
                    label={t('subscriptions_amount')}
                    error={form.amount.trim() && amount === null ? t('subscriptions_invalid_amount') : null}
                >
                    <div className="relative">
                        <input
                            className="form-control pr-14"
                            inputMode="decimal"
                            value={form.amount}
                            onChange={e => set('amount', sanitizeExpression(e.target.value))}
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-ink-dim">{code}</span>
                    </div>
                </Field>
                <Field label={t('common_account')}>
                    <select
                        className="form-control"
                        value={account?.id ?? ''}
                        disabled={!!id}
                        onChange={e => set('accountId', Number(e.target.value))}
                    >
                        {(id && existing && !active.some(a => a.id === existing.account.id) ? [existing.account, ...active] : active).map(a => (
                            <option key={a.id} value={a.id}>
                                {a.name} · {a.currency.code}
                            </option>
                        ))}
                    </select>
                </Field>
                <Field label={t('common_category')}>
                    <select className="form-control" value={form.categoryId ?? ''} onChange={e => set('categoryId', Number(e.target.value) || null)}>
                        <option value="">{t('common_uncategorised')}</option>
                        {categories
                            .filter(c => !c.archived)
                            .map(c => (
                                <option key={c.id} value={c.id}>
                                    {c.name}
                                </option>
                            ))}
                    </select>
                </Field>
                <div className="grid grid-cols-2 gap-3">
                    <Field label={t('subscriptions_frequency')}>
                        <select className="form-control" value={form.frequency} onChange={e => set('frequency', e.target.value as SubscriptionFrequency)}>
                            {FREQUENCIES.map(f => (
                                <option key={f} value={f}>
                                    {t(`freq_${f.toLowerCase()}`)}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label={t('subscriptions_interval')}>
                        <input
                            type="number"
                            min={1}
                            className="form-control"
                            value={form.interval}
                            onChange={e => set('interval', Math.max(1, Math.floor(Number(e.target.value) || 1)))}
                        />
                    </Field>
                </div>
                <Field label={t('subscriptions_next_charge')}>
                    <input type="date" className="form-control" value={form.nextChargeDate} onChange={e => e.target.value && set('nextChargeDate', e.target.value)} />
                </Field>
                <Switch checked={form.active} onChange={v => set('active', v)} label={t('subscriptions_active')} />
            </Card>
            <ConfirmDialog
                open={confirmDelete}
                title={t('action_delete')}
                message={t('subscription_delete_confirm')}
                destructive
                busy={remove.isPending}
                onConfirm={() => remove.mutate()}
                onCancel={() => setConfirmDelete(false)}
            />
        </Page>
    );
};

export default SubscriptionEditPage;
