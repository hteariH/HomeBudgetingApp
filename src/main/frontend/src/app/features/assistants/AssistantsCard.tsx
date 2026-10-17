import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { queryClient, qk } from '../../api/queryClient';
import type { TokenView } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorBanner, errorText } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { CopyButton } from './CopyButton';
import { CreateTokenDialog } from './CreateTokenDialog';

const date = (iso: string) => new Date(iso).toLocaleDateString();

/**
 * Settings → Assistants: the address to connect to, and the tokens and OAuth connections that
 * work today. The address is the instance's public one when it has one: the browser may have
 * reached it some other way (a LAN address, a tunnel) that an assistant cannot use.
 */
export const AssistantsCard: React.FC<{ oauthAvailable: boolean; baseUrl?: string }> = ({ oauthAvailable, baseUrl }) => {
    const t = useT();
    const url = `${baseUrl || window.location.origin}/mcp`;
    const tokens = useQuery({ queryKey: qk.assistantTokens, queryFn: endpoints.assistantTokens });
    const [creating, setCreating] = useState(false);
    const [revoking, setRevoking] = useState<TokenView | null>(null);
    const [revokeError, setRevokeError] = useState<string | null>(null);

    const revoke = useMutation({
        mutationFn: (token: TokenView) => endpoints.revokeAssistantToken(token.id),
        onSuccess: () => {
            setRevoking(null);
            queryClient.invalidateQueries({ queryKey: qk.assistantTokens });
        },
        onError: (e) => setRevokeError(errorText(e, t)),
    });

    return (
        <Card
            title={t('assistants_title')}
            action={
                <button type="button" className="btn btn-secondary" onClick={() => setCreating(true)}>
                    {t('assistants_create')}
                </button>
            }
        >
            <div className="flex flex-col gap-3 px-5 pb-4">
                <p className="text-xs text-ink-soft">{t('assistants_hint')}</p>
                <div className="flex items-center gap-2">
                    <input readOnly aria-label={t('assistants_url')} value={url} className="form-control flex-1" onFocus={(e) => e.currentTarget.select()} />
                    <CopyButton text={url} label={t('assistants_url')} />
                </div>
                <ErrorBanner error={tokens.error} fallbackKey="common_load_failed" />
            </div>
            {tokens.data && tokens.data.length === 0 && <p className="px-5 pb-4 text-sm text-ink-soft">{t('assistants_empty')}</p>}
            <ul className="divide-y divide-line">
                {(tokens.data ?? []).map((token) => (
                    <li key={token.id} className="flex items-center gap-3 px-5 py-3">
                        <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                                <span className="truncate text-sm font-medium text-ink">{token.name || token.prefix}</span>
                                {token.kind === 'OAUTH' && (
                                    <span className="shrink-0 rounded-full bg-brand/15 px-2 py-0.5 text-xs font-medium text-brand-light">
                                        {t('assistants_oauth_badge')}
                                    </span>
                                )}
                            </div>
                            <div className="text-xs text-ink-soft">
                                {token.prefix && <span className="font-mono">{token.prefix}… · </span>}
                                {t('assistants_created', { 0: date(token.createdAt) })} ·{' '}
                                {token.lastUsedAt ? t('assistants_last_used', { 0: date(token.lastUsedAt) }) : t('assistants_never_used')} ·{' '}
                                {token.expiresAt ? t('assistants_expires', { 0: date(token.expiresAt) }) : t('assistants_no_expiry')}
                            </div>
                        </div>
                        <button
                            type="button"
                            className="btn btn-ghost text-bad"
                            onClick={() => {
                                setRevokeError(null);
                                setRevoking(token);
                            }}
                        >
                            {t('assistants_revoke')}
                        </button>
                    </li>
                ))}
            </ul>

            <CreateTokenDialog open={creating} onClose={() => setCreating(false)} url={url} oauthAvailable={oauthAvailable} />
            <ConfirmDialog
                open={revoking !== null}
                title={t('assistants_revoke')}
                destructive
                busy={revoke.isPending}
                confirmLabel={t('assistants_revoke')}
                message={
                    <>
                        <p>{t('assistants_revoke_confirm', { 0: revoking?.name || revoking?.prefix || '' })}</p>
                        {revokeError && <p className="mt-3 text-bad">{revokeError}</p>}
                    </>
                }
                onConfirm={() => revoking && revoke.mutate(revoking)}
                onCancel={() => !revoke.isPending && setRevoking(null)}
            />
        </Card>
    );
};
