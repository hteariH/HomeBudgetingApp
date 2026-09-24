import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useCurrencies, useFxRates } from '../../api/hooks';
import { qk, queryClient } from '../../api/queryClient';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, Field } from '../../components/forms';
import { AppRoutes } from '../../lib/routes';
import { BackButton, Page, PageLoading } from '../../shell/Page';
import { formatRate, handRateFor, handRateOffered, parseRate } from './currencies.logic';

/**
 * One currency: what the catalogue says about it, read-only, and the rate the user may set
 * by hand for a currency no rates feed carries. Emptying the field hands the currency back
 * to the daily rate.
 */
const CurrencyEditPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const code = (params.get('code') ?? '').toUpperCase();
    const { data: currencies, isLoading } = useCurrencies();
    const { data: rates } = useFxRates();
    const currency = currencies?.find(c => c.code === code);
    const [rate, setRate] = useState('');
    const [touched, setTouched] = useState(false);

    useEffect(() => {
        if (rates && !touched) {
            const current = handRateFor(rates, code);
            setRate(current === null ? '' : formatRate(current));
        }
    }, [rates, code, touched]);

    const offered = handRateOffered(code);
    const valid = rate.trim() === '' || parseRate(rate) !== null;

    const save = useMutation({
        mutationFn: async () => {
            const parsed = parseRate(rate);
            if (parsed === null) await endpoints.clearFxOverride(code);
            else await endpoints.setFxOverride(code, String(parsed));
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: qk.fx });
            navigate(AppRoutes.currencies);
        },
    });

    if (isLoading) return <PageLoading />;

    return (
        <Page
            width="form"
            leading={<BackButton fallback={AppRoutes.currencies} label={t('action_back')} />}
            title={currency ? currency.name || currency.code : code}
            actions={
                offered && (
                    <button type="button" className="btn btn-primary" disabled={!valid || save.isPending} onClick={() => save.mutate()}>
                        {save.isPending ? t('common_saving') : t('action_save')}
                    </button>
                )
            }
        >
            <ErrorBanner error={save.error} className="mb-4" />
            <Card className="p-5">
                <div className="grid grid-cols-2 gap-4">
                    <Field label={t('currencies_code')}>
                        <input className="form-control" value={code} readOnly disabled />
                    </Field>
                    <Field label={t('currencies_symbol')}>
                        <input className="form-control" value={currency?.symbol ?? ''} readOnly disabled />
                    </Field>
                    <div className="col-span-2">
                        <Field label={t('currencies_name')}>
                            <input className="form-control" value={currency?.name ?? ''} readOnly disabled />
                        </Field>
                    </div>
                    <Field label={t('currencies_decimals')}>
                        <input className="form-control" value={currency?.decimals ?? ''} readOnly disabled />
                    </Field>
                </div>
                {offered && (
                    <div className="mt-5">
                        <Field
                            label={t('currencies_rate', { 0: code })}
                            error={valid ? null : t('currencies_rate_invalid')}
                            hint={t('currencies_rate_hint')}
                        >
                            <input
                                className="form-control"
                                inputMode="decimal"
                                value={rate}
                                onChange={e => {
                                    setTouched(true);
                                    setRate(e.target.value);
                                }}
                                onKeyDown={e => e.key === 'Enter' && valid && save.mutate()}
                            />
                        </Field>
                    </div>
                )}
            </Card>
        </Page>
    );
};

export default CurrencyEditPage;
