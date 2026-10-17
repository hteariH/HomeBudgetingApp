import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight, ExternalLink } from 'lucide-react';
import { LANGUAGES, useI18n, useT, type Lang } from '../../../i18n';
import { useTheme, type ThemeMode } from '../../../theme';
import { endpoints } from '../../api/endpoints';
import { useBaseCurrency, useCurrencies, useMe } from '../../api/hooks';
import { queryClient, qk } from '../../api/queryClient';
import { session } from '../../api/session';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { CurrencySelect } from '../../components/CurrencySelect';
import { ErrorBanner, errorText } from '../../components/ErrorBanner';
import { Card, Segmented } from '../../components/forms';
import { AppRoutes } from '../../lib/routes';
import { prefs } from '../../prefs';
import { Page } from '../../shell/Page';
import { AssistantsCard } from '../assistants/AssistantsCard';
import { ProviderForm } from '../ai/ProviderForm';

/** The instance serves its own privacy page. */
const PRIVACY_POLICY_URL = '/privacy.html';

const SectionLabel: React.FC<{ children: React.ReactNode }> = ({ children }) => (
    <div className="px-5 pb-2 pt-4 text-xs font-semibold uppercase tracking-wide text-brand-light">{children}</div>
);

const SOURCE_KEYS = {
    HOUSEHOLD: 'ai_source_household',
    INSTANCE: 'ai_source_instance',
    ENVIRONMENT: 'ai_source_environment',
} as const;

/**
 * Which model reads receipts and where the photos go. The household owner chooses it;
 * everyone else just gets told.
 */
const ReceiptRecognitionCard: React.FC = () => {
    const t = useT();
    const me = useMe();
    const provider = useQuery({ queryKey: qk.aiProvider, queryFn: endpoints.householdAiProvider });
    const view = provider.data;
    const isOwner = me.data?.householdRole === 'OWNER';

    return (
        <Card>
            <SectionLabel>{t('ai_title')}</SectionLabel>
            <div className="px-5 pb-4">
                <ErrorBanner error={provider.error} fallbackKey="common_load_failed" />
                {view && (
                    <p className="text-sm text-ink-soft">
                        {view.source === 'NONE' ? (
                            t(isOwner ? 'ai_none_owner' : 'ai_none_member')
                        ) : (
                            <>
                                {t(SOURCE_KEYS[view.source])}
                                {view.host && <> {t('ai_sent_to', { host: view.host })}</>}
                            </>
                        )}
                    </p>
                )}
            </div>
            {view && isOwner && <ProviderForm mode="household" view={view} />}
        </Card>
    );
};

/**
 * Settings, as the Compose web client had them - base currency, theme, the privacy policy,
 * and deleting the account - plus a language picker, which the browser build never had but
 * the landing page always did. Backup, biometric lock and Material You are Android's alone.
 */
const SettingsPage: React.FC = () => {
    const { t, lang, setLang } = useI18n();
    const { mode, setMode } = useTheme();
    const navigate = useNavigate();
    const base = useBaseCurrency();
    const { data: currencies = [] } = useCurrencies();
    const { data: instance } = useQuery({ queryKey: qk.instance, queryFn: endpoints.instance, staleTime: Infinity });
    const [deleting, setDeleting] = useState<{ open: boolean; busy: boolean; error: string | null }>({
        open: false,
        busy: false,
        error: null,
    });

    /**
     * Not best-effort, unlike sign-out: if the server call fails the account still exists,
     * and clearing this browser anyway would tell the user their data is gone while it waits
     * on the server. So a failure is reported and nothing local is touched.
     */
    const deleteAccount = async () => {
        if (deleting.busy) return;
        setDeleting({ open: true, busy: true, error: null });
        try {
            await endpoints.deleteMe();
            prefs.clearAccountScoped();
            queryClient.clear();
            session.clear();
            navigate('/login', { replace: true });
        } catch (e) {
            setDeleting({ open: true, busy: false, error: errorText(e, t) });
        }
    };

    return (
        <Page title={t('settings_title')} width="form">
            <div className="flex flex-col gap-4">
                <Card>
                    <SectionLabel>{t('settings_base_currency')}</SectionLabel>
                    <div className="px-5">
                        <CurrencySelect selected={base} currencies={currencies} onSelect={code => prefs.setBaseCurrency(code)} />
                    </div>
                    <Link
                        to={AppRoutes.currencies}
                        className="mt-3 flex items-center gap-3 border-t border-line px-5 py-3.5 transition-colors hover:bg-raised"
                    >
                        <span className="flex-1">
                            <span className="block text-sm font-medium text-ink">{t('settings_manage_currencies')}</span>
                            <span className="block text-xs text-ink-soft">
                                {currencies.length} / {currencies.length}
                            </span>
                        </span>
                        <ChevronRight size={18} className="text-ink-dim" />
                    </Link>
                </Card>

                <Card>
                    <SectionLabel>{t('settings_theme')}</SectionLabel>
                    <div className="px-5 pb-5">
                        <Segmented<ThemeMode>
                            value={mode}
                            onChange={setMode}
                            options={[
                                { value: 'system', label: t('settings_theme_system') },
                                { value: 'light', label: t('settings_theme_light') },
                                { value: 'dark', label: t('settings_theme_dark') },
                            ]}
                        />
                    </div>
                    <SectionLabel>{t('settings_language')}</SectionLabel>
                    <div className="px-5 pb-5">
                        <Segmented<Lang>
                            value={lang}
                            onChange={setLang}
                            options={LANGUAGES.map(l => ({ value: l.code, label: l.label }))}
                        />
                    </div>
                </Card>

                <Card>
                    <a
                        href={PRIVACY_POLICY_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-3 px-5 py-3.5 transition-colors hover:bg-raised"
                    >
                        <span className="flex-1 text-sm font-medium text-ink">{t('settings_privacy_policy')}</span>
                        <ExternalLink size={16} className="text-ink-dim" />
                    </a>
                    <div className="border-t border-line px-5 py-3.5">
                        <span className="block text-sm font-medium text-ink">{t('settings_about')}</span>
                        <span className="block text-xs text-ink-soft">
                            {t('settings_version')} {instance?.version ?? '…'}
                        </span>
                    </div>
                </Card>

                <ReceiptRecognitionCard />

                {instance?.mcpEnabled && <AssistantsCard oauthAvailable={!!instance.oauthAvailable} />}

                {/* The way out of the product, at the bottom where it belongs - deliberately
                    awkward to trigger by accident, and not hidden behind a support email. */}
                <Card>
                    <SectionLabel>
                        <span className="text-bad">{t('settings_danger_zone')}</span>
                    </SectionLabel>
                    <button
                        type="button"
                        onClick={() => setDeleting({ open: true, busy: false, error: null })}
                        className="w-full px-5 pb-4 pt-1 text-left transition-colors hover:bg-raised"
                    >
                        <span className="block text-sm font-semibold text-bad">{t('settings_delete_account')}</span>
                        <span className="block text-xs text-ink-soft">{t('settings_delete_account_subtitle')}</span>
                    </button>
                </Card>
            </div>

            <ConfirmDialog
                open={deleting.open}
                title={t('settings_delete_account_title')}
                destructive
                busy={deleting.busy}
                confirmLabel={t(deleting.busy ? 'settings_delete_account_working' : 'settings_delete_account_confirm')}
                message={
                    <>
                        <p>{t('settings_delete_account_body')}</p>
                        {deleting.error && <p className="mt-3 text-bad">{deleting.error}</p>}
                    </>
                }
                onConfirm={deleteAccount}
                // Not while the request is in flight: the next thing the user would see is the
                // sign-in page with no explanation.
                onCancel={() => !deleting.busy && setDeleting({ open: false, busy: false, error: null })}
            />
        </Page>
    );
};

export default SettingsPage;
