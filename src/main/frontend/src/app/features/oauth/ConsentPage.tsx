import React from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useMe } from '../../api/hooks';
import { qk } from '../../api/queryClient';
import { Card } from '../../components/forms';
import { PageLoading } from '../../shell/Page';

const CLIENT = '\u0001';
const HOUSEHOLD = '\u0002';

/** The sentence with the two names set in bold wherever the translation puts them. */
function Sentence({ template, client, household }: { template: string; client: string; household: string }) {
    return (
        <>
            {template.split(/([\u0001\u0002])/).map((part, i) =>
                part === CLIENT ? (
                    <strong key={i} className="text-ink">{client}</strong>
                ) : part === HOUSEHOLD ? (
                    <strong key={i} className="text-ink">{household}</strong>
                ) : (
                    <React.Fragment key={i}>{part}</React.Fragment>
                ),
            )}
        </>
    );
}

/**
 * Where a signed-in person approves an assistant that asked to connect (the OAuth consent
 * step). The address carries the assistant's request; the server vouches for none of it until
 * asked, so the client and its redirect address are checked before anything can be allowed -
 * an invalid request gets a message and no buttons.
 *
 * Served with frame-ancestors 'none' (SpaFallbackController): this page asks for a decision,
 * so no other site may draw it.
 */
const ConsentPage: React.FC = () => {
    const t = useT();
    const [params] = useSearchParams();
    const clientId = params.get('client_id');
    const redirectUri = params.get('redirect_uri');
    const codeChallenge = params.get('code_challenge');
    const complete = !!clientId && !!redirectUri && !!codeChallenge;

    const me = useMe();
    const client = useQuery({
        queryKey: qk.oauthClient(clientId ?? '', redirectUri ?? ''),
        queryFn: () => endpoints.oauthClient(clientId!, redirectUri!),
        enabled: complete,
        retry: false,
    });

    const decide = useMutation({
        mutationFn: (approve: boolean) =>
            endpoints.oauthAuthorize({
                clientId: clientId!,
                redirectUri: redirectUri!,
                codeChallenge: codeChallenge!,
                state: params.get('state') ?? undefined,
                scope: params.get('scope') ?? undefined,
                resource: params.get('resource') ?? undefined,
                approve,
            }),
        onSuccess: ({ redirect }) => window.location.assign(redirect),
    });

    const invalid = !complete || client.isError;
    const ready = !invalid && client.data && me.data;

    return (
        <div className="flex min-h-screen items-center justify-center bg-base p-4">
            <Card title={t('consent_title')} className="w-full max-w-md">
                <div className="flex flex-col gap-4 px-5 pb-5 pt-2 text-sm text-ink-soft">
                    {invalid ? (
                        <p className="text-bad">{t('consent_invalid')}</p>
                    ) : !ready ? (
                        me.isError ? <p className="text-bad">{t('consent_invalid')}</p> : <PageLoading />
                    ) : (
                        <>
                            <p>
                                <Sentence
                                    template={t('consent_body', { 0: CLIENT, 1: HOUSEHOLD })}
                                    client={client.data.clientName}
                                    household={me.data.householdName ?? ''}
                                />
                            </p>
                            <p className="text-xs">{t('consent_redirect', { 0: client.data.redirectHost })}</p>
                            {decide.isError && <p className="text-bad">{t('consent_failed')}</p>}
                            <div className="flex justify-end gap-2">
                                <button type="button" className="btn btn-ghost" disabled={decide.isPending || decide.isSuccess} onClick={() => decide.mutate(false)}>
                                    {t('consent_deny')}
                                </button>
                                <button type="button" className="btn btn-primary" disabled={decide.isPending || decide.isSuccess} onClick={() => decide.mutate(true)}>
                                    {t('consent_allow')}
                                </button>
                            </div>
                        </>
                    )}
                </div>
            </Card>
        </div>
    );
};

export default ConsentPage;
