import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Plus, Repeat } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useBaseCurrency, useFxSnapshot } from '../../api/hooks';
import { qk, queryClient } from '../../api/queryClient';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, Switch } from '../../components/forms';
import { IconCircle } from '../../components/IconCircle';
import { MoneyText } from '../../components/MoneyText';
import { formatMediumDate } from '../../lib/format';
import { money } from '../../lib/money';
import { AppRoutes } from '../../lib/routes';
import { Page, PageLoading } from '../../shell/Page';
import { frequencyLabel, subscriptionTotals } from './subscriptions.logic';

/** Recurring charges, what they add up to, and a switch to pause each. Port of SubscriptionsScreen.kt. */
const SubscriptionsPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const base = useBaseCurrency();
    const fx = useFxSnapshot();
    const { data = [], isLoading, error } = useQuery({ queryKey: qk.subscriptions, queryFn: endpoints.subscriptions });
    const totals = subscriptionTotals(data, fx, base);

    const toggle = useMutation({
        mutationFn: ({ id, active }: { id: number; active: boolean }) => endpoints.updateSubscription(id, { active }),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: qk.subscriptions }),
    });

    return (
        <Page
            title={t('subscriptions_title')}
            width="form"
            actions={
                <button type="button" className="btn btn-primary" onClick={() => navigate(AppRoutes.subscriptionEdit())}>
                    <Plus size={16} /> {t('subscriptions_new')}
                </button>
            }
        >
            <ErrorBanner error={error ?? toggle.error} fallbackKey="common_load_failed" className="mb-4" />
            {isLoading ? (
                <PageLoading />
            ) : data.length === 0 ? (
                <EmptyState
                    icon={Repeat}
                    title={t('subscriptions_new')}
                    action={{ label: t('action_add'), onClick: () => navigate(AppRoutes.subscriptionEdit()) }}
                />
            ) : (
                <div className="flex flex-col gap-4">
                    <div className="grid grid-cols-2 gap-3">
                        <div className="rounded-token-lg border border-line bg-card px-4 py-3">
                            <div className="text-xs text-ink-soft">{t('subscriptions_total_month')}</div>
                            <MoneyText money={totals.perMonth} className="text-lg font-bold text-ink" />
                        </div>
                        <div className="rounded-token-lg border border-line bg-card px-4 py-3">
                            <div className="text-xs text-ink-soft">{t('subscriptions_total_year')}</div>
                            <MoneyText money={totals.perYear} className="text-lg font-bold text-ink" />
                        </div>
                    </div>
                    <Card>
                        {data.map(s => (
                            <div key={s.id} className="flex items-center gap-3 border-t border-line px-5 py-3 first:border-t-0">
                                <Link to={AppRoutes.subscriptionEdit(s.id)} className="flex min-w-0 flex-1 items-center gap-3">
                                    <IconCircle icon={s.icon ?? 'subscriptions'} color={s.color ?? '#008080'} size={40} />
                                    <span className="min-w-0 flex-1">
                                        <span className={`block truncate font-medium ${s.active === false ? 'text-ink-soft' : 'text-ink'}`}>{s.name}</span>
                                        <span className="block truncate text-xs text-ink-soft">
                                            {frequencyLabel(t, s.frequency, s.intervalCount ?? 1)} · {formatMediumDate(s.nextChargeDate)}
                                        </span>
                                    </span>
                                    <MoneyText money={money(s.amount, s.currencyCode)} className="text-sm font-semibold text-ink" />
                                </Link>
                                <Switch
                                    compact
                                    checked={s.active !== false}
                                    onChange={active => toggle.mutate({ id: s.id, active })}
                                    label={t('subscriptions_active')}
                                />
                            </div>
                        ))}
                    </Card>
                </div>
            )}
        </Page>
    );
};

export default SubscriptionsPage;
