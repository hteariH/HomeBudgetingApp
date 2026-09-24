import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { TrendingUp } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useBaseCurrency, useCurrencies } from '../../api/hooks';
import { invalidateLedger } from '../../api/queryClient';
import { CurrencySelect } from '../../components/CurrencySelect';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Field } from '../../components/forms';
import { AppRoutes } from '../../lib/routes';
import { prefs } from '../../prefs';
import { canProceed, firstAccountRequest, ONBOARDING_STEPS } from './onboarding.logic';

/**
 * Three steps before the app: welcome, the base currency, the first account. Port of
 * OnboardingScreen.kt. The currency step is a search field rather than the phone's grid of
 * chips, since in a browser every currency the server has is on offer. Skipping finishes
 * with whatever has been chosen so far.
 */
const OnboardingPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const { data: currencies = [] } = useCurrencies();
    const [step, setStep] = useState(0);
    const [currency, setCurrency] = useState(useBaseCurrency());
    const [name, setName] = useState('');

    const finish = useMutation({
        mutationFn: async () => {
            prefs.setBaseCurrency(currency);
            const request = firstAccountRequest(name, currency, currencies);
            if (request) {
                const created = await endpoints.createAccount(request);
                prefs.setDefaultAccountId(created.id);
            }
            prefs.setOnboarded(true);
        },
        onSuccess: async () => {
            await invalidateLedger();
            navigate(AppRoutes.dashboard, { replace: true });
        },
    });

    const last = step === ONBOARDING_STEPS - 1;
    const next = () => {
        if (!canProceed(step, currency, name)) return;
        if (step === 1) prefs.setBaseCurrency(currency);
        if (last) finish.mutate();
        else setStep(s => s + 1);
    };

    return (
        <div className="flex min-h-screen items-center justify-center bg-base px-4 py-10">
            <div className="flex w-full max-w-[480px] flex-col gap-6 rounded-token-xl border border-line bg-card p-6 shadow-token">
                <div className="text-xs font-medium text-ink-dim">{t('onboarding_step_of', { 0: step + 1, 1: ONBOARDING_STEPS })}</div>

                {step === 0 && (
                    <div className="flex flex-col items-center gap-3 py-6 text-center">
                        <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-[#6366F1] to-[#06B6D4] text-white">
                            <TrendingUp size={28} />
                        </span>
                        <h1 className="text-2xl font-bold text-ink">{t('onboarding_welcome')}</h1>
                        <p className="text-sm text-ink-soft">{t('onboarding_subtitle')}</p>
                    </div>
                )}

                {step === 1 && (
                    <div className="flex flex-col gap-4">
                        <h1 className="text-xl font-bold text-ink">{t('onboarding_pick_currency')}</h1>
                        <CurrencySelect selected={currency} currencies={currencies} onSelect={setCurrency} />
                    </div>
                )}

                {step === 2 && (
                    <div className="flex flex-col gap-4">
                        <h1 className="text-xl font-bold text-ink">{t('onboarding_first_account')}</h1>
                        <Field label={t('onboarding_account_name')}>
                            <input
                                className="form-control"
                                value={name}
                                placeholder={t('onboarding_account_placeholder')}
                                onChange={e => setName(e.target.value)}
                                onKeyDown={e => e.key === 'Enter' && next()}
                                autoFocus
                            />
                        </Field>
                    </div>
                )}

                <ErrorBanner error={finish.error} />

                <div className="flex items-center justify-between gap-3">
                    <button type="button" className="btn btn-ghost" disabled={finish.isPending} onClick={() => finish.mutate()}>
                        {t('onboarding_skip')}
                    </button>
                    <button type="button" className="btn btn-primary" disabled={!canProceed(step, currency, name) || finish.isPending} onClick={next}>
                        {last ? t('onboarding_start') : t('onboarding_next')}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default OnboardingPage;
