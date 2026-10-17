import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { queryClient, qk } from '../../api/queryClient';
import type { CreatedToken } from '../../api/types';
import { Dialog } from '../../components/Dialog';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Field } from '../../components/forms';
import { CopyButton } from './CopyButton';
import { claudeCodeCommand, mcpJson } from './snippets';

const EXPIRIES = [
    { days: 30, key: 'assistants_expiry_30' },
    { days: 90, key: 'assistants_expiry_90' },
    { days: 365, key: 'assistants_expiry_365' },
    { days: null, key: 'assistants_expiry_never' },
] as const;

const Snippet: React.FC<{ title: string; text: string }> = ({ title, text }) => (
    <div className="flex flex-col gap-1">
        <div className="form-label">{title}</div>
        <div className="flex items-start gap-2">
            <pre className="min-w-0 flex-1 overflow-x-auto whitespace-pre-wrap break-all rounded-token bg-raised px-3 py-2 text-xs text-ink">{text}</pre>
            <CopyButton text={text} label={title} />
        </div>
    </div>
);

/**
 * Makes a personal token. The raw token lives in this component's state alone: closing the
 * dialog clears it, and the server cannot show it again.
 */
export const CreateTokenDialog: React.FC<{ open: boolean; onClose: () => void; url: string; oauthAvailable: boolean }> = ({
    open,
    onClose,
    url,
    oauthAvailable,
}) => {
    const t = useT();
    const [name, setName] = useState('');
    const [expiry, setExpiry] = useState('90');
    const [created, setCreated] = useState<CreatedToken | null>(null);

    const create = useMutation({
        mutationFn: () => endpoints.createAssistantToken({ name: name.trim(), expiresInDays: expiry === 'never' ? null : Number(expiry) }),
        onSuccess: (data) => {
            setCreated(data);
            queryClient.invalidateQueries({ queryKey: qk.assistantTokens });
        },
    });

    const close = () => {
        setCreated(null);
        setName('');
        setExpiry('90');
        create.reset();
        onClose();
    };

    return (
        <Dialog
            open={open}
            onClose={close}
            width={640}
            title={t('assistants_create_title')}
            footer={
                created ? (
                    <button type="button" className="btn btn-primary" onClick={close}>
                        {t('assistants_done')}
                    </button>
                ) : (
                    <>
                        <button type="button" className="btn btn-ghost" onClick={close}>
                            {t('action_cancel')}
                        </button>
                        <button type="button" className="btn btn-primary" disabled={create.isPending} onClick={() => create.mutate()}>
                            {t('assistants_create_confirm')}
                        </button>
                    </>
                )
            }
        >
            {created ? (
                <div className="flex flex-col gap-4">
                    <p className="font-medium text-ink">{t('assistants_token_once')}</p>
                    <div className="flex items-center gap-2">
                        <input readOnly aria-label={t('assistants_token')} value={created.token} className="form-control flex-1 font-mono text-xs" onFocus={(e) => e.currentTarget.select()} />
                        <CopyButton text={created.token} label={t('assistants_token')} />
                    </div>
                    <Snippet title={t('assistants_snippet_claude_code')} text={claudeCodeCommand(url, created.token)} />
                    <Snippet title={t('assistants_snippet_json')} text={mcpJson(url, created.token)} />
                    <div className="flex flex-col gap-1">
                        <div className="form-label">{t('assistants_snippet_web')}</div>
                        <p className="text-sm text-ink">{oauthAvailable ? t('assistants_web_connector', { 0: url }) : t('assistants_web_needs_https')}</p>
                    </div>
                </div>
            ) : (
                <form
                    className="flex flex-col gap-4"
                    onSubmit={(e) => {
                        e.preventDefault();
                        if (!create.isPending) create.mutate();
                    }}
                >
                    <Field label={t('assistants_name')}>
                        <input className="form-control" placeholder={t('assistants_name_hint')} value={name} maxLength={100} onChange={(e) => setName(e.target.value)} data-autofocus />
                    </Field>
                    <Field label={t('assistants_expiry')}>
                        <select className="form-control" value={expiry} onChange={(e) => setExpiry(e.target.value)}>
                            {EXPIRIES.map((o) => (
                                <option key={o.key} value={o.days ?? 'never'}>
                                    {t(o.key)}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <ErrorBanner error={create.error} />
                </form>
            )}
        </Dialog>
    );
};
