import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { qk } from '../../api/queryClient';
import type { AiProviderType, ProviderRequest, ProviderView } from '../../api/types';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Field } from '../../components/forms';

/** The addresses a household may use for an OpenAI-compatible model; the server refuses any other. */
export const HOUSEHOLD_PRESETS = [
    { url: 'https://api.openai.com/v1', labelKey: 'ai_preset_openai' },
    { url: 'https://openrouter.ai/api/v1', labelKey: 'ai_preset_openrouter' },
] as const;

const TYPES: { value: AiProviderType; labelKey: string }[] = [
    { value: 'OPENAI_COMPATIBLE', labelKey: 'ai_type_openai' },
    { value: 'ANTHROPIC', labelKey: 'ai_type_anthropic' },
    { value: 'GEMINI', labelKey: 'ai_type_gemini' },
];

/** The administrator types any address; a household picks from the presets. */
export type ProviderFormMode = 'admin' | 'household';

const API = {
    admin: {
        key: qk.adminAiProvider,
        own: 'INSTANCE',
        save: endpoints.setAdminAiProvider,
        remove: endpoints.deleteAdminAiProvider,
        test: endpoints.testAdminAiProvider,
    },
    household: {
        key: qk.aiProvider,
        own: 'HOUSEHOLD',
        save: endpoints.setHouseholdAiProvider,
        remove: endpoints.deleteHouseholdAiProvider,
        test: endpoints.testHouseholdAiProvider,
    },
} as const;

/**
 * The form for choosing the model that reads receipts, shared by the administrator's page
 * (any address) and the household owner's settings (two presets). The saved key never comes
 * back from the server, so leaving the field empty keeps it.
 */
export const ProviderForm: React.FC<{ mode: ProviderFormMode; view: ProviderView }> = ({ mode, view }) => {
    const t = useT();
    const api = API[mode];
    const client = useQueryClient();
    // The view may describe a level above this one (the instance's model, seen from a household).
    const own = view.source === api.own;
    const defaultUrl = mode === 'household' ? HOUSEHOLD_PRESETS[0].url : '';
    const [type, setType] = useState<AiProviderType>((own && view.type) || 'OPENAI_COMPATIBLE');
    const [baseUrl, setBaseUrl] = useState((own && view.baseUrl) || defaultUrl);
    const [model, setModel] = useState((own && view.model) || '');
    const [apiKey, setApiKey] = useState('');

    const body = (): ProviderRequest => ({
        type,
        ...(type === 'OPENAI_COMPATIBLE' ? { baseUrl: baseUrl.trim() } : {}),
        model: model.trim(),
        ...(apiKey ? { apiKey } : {}),
    });

    const save = useMutation({
        mutationFn: () => api.save(body()),
        onSuccess: data => {
            setApiKey('');
            client.setQueryData(api.key, data);
        },
    });
    const test = useMutation({ mutationFn: () => api.test(body()) });
    const remove = useMutation({
        mutationFn: () => api.remove(),
        onSuccess: () => client.invalidateQueries({ queryKey: api.key }),
    });

    const keepKey = own && view.hasKey;
    const busy = save.isPending || test.isPending || remove.isPending;
    const valid = model.trim() !== '' && (type !== 'OPENAI_COMPATIBLE' || baseUrl.trim() !== '');
    const edited = () => {
        save.reset();
        test.reset();
    };

    return (
        <form
            className="flex flex-col gap-3 px-5 pb-4"
            onSubmit={e => {
                e.preventDefault();
                if (valid && !busy) save.mutate();
            }}
        >
            <Field label={t('ai_provider')}>
                <select
                    className="form-control"
                    value={type}
                    onChange={e => {
                        setType(e.target.value as AiProviderType);
                        edited();
                    }}
                >
                    {TYPES.map(o => (
                        <option key={o.value} value={o.value}>
                            {t(o.labelKey)}
                        </option>
                    ))}
                </select>
            </Field>

            {type === 'OPENAI_COMPATIBLE' && (
                <Field label={t('ai_base_url')}>
                    {mode === 'household' ? (
                        <select
                            className="form-control"
                            value={baseUrl}
                            onChange={e => {
                                setBaseUrl(e.target.value);
                                edited();
                            }}
                        >
                            {HOUSEHOLD_PRESETS.map(p => (
                                <option key={p.url} value={p.url}>
                                    {t(p.labelKey)} ({p.url})
                                </option>
                            ))}
                        </select>
                    ) : (
                        <input
                            className="form-control"
                            type="url"
                            value={baseUrl}
                            placeholder="https://api.openai.com/v1"
                            onChange={e => {
                                setBaseUrl(e.target.value);
                                edited();
                            }}
                        />
                    )}
                </Field>
            )}

            <Field label={t('ai_model')}>
                <input
                    className="form-control"
                    value={model}
                    maxLength={200}
                    autoComplete="off"
                    onChange={e => {
                        setModel(e.target.value);
                        edited();
                    }}
                />
            </Field>

            <Field label={t('ai_key')}>
                <input
                    className="form-control"
                    type="password"
                    value={apiKey}
                    maxLength={1500}
                    autoComplete="new-password"
                    placeholder={keepKey ? t('ai_key_keep') : ''}
                    onChange={e => {
                        setApiKey(e.target.value);
                        edited();
                    }}
                />
            </Field>

            {test.data && (
                <p className={`text-sm ${test.data.ok ? 'text-ok' : 'text-bad'}`} role="status">
                    {test.data.ok ? t('ai_test_ok') : test.data.reason}
                </p>
            )}
            <ErrorBanner error={save.error ?? test.error ?? remove.error} />

            <div className="flex flex-wrap items-center gap-2">
                <button type="button" className="btn btn-secondary" disabled={!valid || busy} onClick={() => test.mutate()}>
                    {t(test.isPending ? 'ai_testing' : 'ai_test')}
                </button>
                <button type="submit" className="btn btn-primary" disabled={!valid || busy}>
                    {t(save.isPending ? 'common_saving' : 'common_save')}
                </button>
                {own && (
                    <button type="button" className="btn btn-ghost ml-auto text-bad" disabled={busy} onClick={() => remove.mutate()}>
                        {t(remove.isPending ? 'ai_removing' : 'ai_remove')}
                    </button>
                )}
            </div>
        </form>
    );
};
