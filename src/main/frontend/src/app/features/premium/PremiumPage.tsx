import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Check, Crown, Smartphone } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { qk, queryClient } from '../../api/queryClient';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { formatMediumDate } from '../../lib/format';
import { isoDate } from '../../lib/dates';
import { Page, PageLoading } from '../../shell/Page';
import { premiumStatusKey } from './premium.logic';

/**
 * What premium is and what this account has. Premium is bought through Google Play, so a
 * browser has nothing to sell; it says where to buy instead of offering a button that
 * cannot work, and keeps "I have already paid" for somebody who bought on their phone.
 */
const PremiumPage: React.FC = () => {
    const t = useT();
    const { data: status, isLoading, error } = useQuery({ queryKey: qk.billing, queryFn: endpoints.billingStatus });
    const [message, setMessage] = useState<string | null>(null);

    const restore = useMutation({
        mutationFn: endpoints.refreshPurchases,
        onSuccess: result => {
            queryClient.setQueryData(qk.billing, result);
            setMessage(result.premium ? null : t('premium_restore_none'));
        },
    });

    if (isLoading) return <PageLoading />;
    const premium = !!status?.premium;
    const expires = status?.expiresAt ? formatMediumDate(isoDate(new Date(status.expiresAt))) : '';

    return (
        <Page title={t('premium_title')} width="form">
            <ErrorBanner error={error ?? restore.error} fallbackKey="common_load_failed" className="mb-4" />
            <div className="flex flex-col gap-4">
                {premium ? (
                    <section className="rounded-token-lg bg-gradient-to-br from-[#4F46E5] to-[#A855F7] p-5 text-white">
                        <div className="flex items-center gap-2.5 text-lg font-semibold">
                            <Crown size={20} /> {t('premium_active')}
                        </div>
                        <div className="mt-1 text-sm opacity-90">{t(premiumStatusKey(status!), { 0: expires })}</div>
                    </section>
                ) : (
                    <section>
                        <h2 className="text-xl font-bold text-ink">{t('premium_pitch_title')}</h2>
                        <p className="mt-1 text-sm text-ink-soft">{t('premium_pitch_body')}</p>
                    </section>
                )}

                <Card className="flex flex-col gap-3 p-5">
                    {['premium_benefit_accounts', 'premium_benefit_no_ads', 'premium_benefit_everywhere', 'premium_benefit_support'].map(k => (
                        <div key={k} className="flex items-start gap-3 text-sm text-ink">
                            <Check size={18} className="mt-0.5 shrink-0 text-ok" />
                            {t(k)}
                        </div>
                    ))}
                </Card>

                {!premium && (
                    <Card className="flex gap-3 p-5">
                        <Smartphone size={22} className="mt-0.5 shrink-0 text-brand-light" />
                        <div>
                            <div className="font-semibold text-ink">{t('premium_buy_elsewhere_title')}</div>
                            <div className="mt-1 text-sm text-ink-soft">{t('premium_buy_elsewhere_body')}</div>
                        </div>
                    </Card>
                )}

                <div className="flex flex-col items-start gap-2">
                    {/* Offered whatever the status says: somebody who paid on another device sees
                        "free" here, and this is the button they are looking for. */}
                    <button type="button" className="btn btn-ghost" disabled={restore.isPending} onClick={() => restore.mutate()}>
                        {t('premium_restore')}
                    </button>
                    {message && <div className="text-sm text-ink-soft">{message}</div>}
                </div>
            </div>
        </Page>
    );
};

export default PremiumPage;
