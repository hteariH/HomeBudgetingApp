import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangle, Users } from 'lucide-react';
import { useT } from '../i18n';
import AuthShell from './AuthShell';
import { AuthApiError, authApi, type InvitePreview } from './authApi';
import { ApiError } from '../app/api/http';
import { endpoints } from '../app/api/endpoints';
import { queryClient } from '../app/api/queryClient';
import { session } from '../app/api/session';

/**
 * What an invitation link opens. Anyone can read what the link is for; joining needs a
 * session, so a visitor without one is sent to create an account or sign in, and the
 * invitation travels along (`?invite=` to registration, `?next=` to sign-in).
 */
const Join: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const token = params.get('token') ?? '';
    const signedIn = session.get() !== null;

    const [preview, setPreview] = useState<InvitePreview | null>(null);
    // Why the invitation cannot be used; set instead of a preview.
    const [unusable, setUnusable] = useState('');
    const [error, setError] = useState('');
    const [joining, setJoining] = useState(false);

    useEffect(() => {
        if (!token) {
            setUnusable(t('join.notFound'));
            return;
        }
        let live = true;
        authApi.invitePreview(token)
            .then(p => live && setPreview(p))
            .catch((e: unknown) => {
                if (!live) return;
                // 410 carries the reason (expired, used, revoked); anything else, including an
                // unknown token, is simply a link that does not work.
                setUnusable(e instanceof AuthApiError && e.status === 410 && e.message ? e.message : t('join.notFound'));
            });
        return () => {
            live = false;
        };
    }, [token, t]);

    const accept = async () => {
        setError('');
        setJoining(true);
        try {
            await endpoints.acceptInvite(token);
            // Everything cached belonged to the household the caller has just left.
            queryClient.clear();
            navigate('/app', { replace: true });
        } catch (e) {
            setError(e instanceof ApiError && e.serverMessage ? e.serverMessage : t('join.failed'));
            setJoining(false);
        }
    };

    if (unusable) {
        return (
            <AuthShell title={t('join.failed')} icon={<Users size={40} />}>
                <div className="alert-error" role="alert">
                    <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                    <span>{unusable}</span>
                </div>
            </AuthShell>
        );
    }
    if (!preview) {
        return (
            <div className="loading">
                <span className="spinner" />
            </div>
        );
    }

    const linkClass = 'btn btn-lg w-full';
    return (
        <AuthShell
            title={t('join.title', { name: preview.householdName })}
            subtitle={preview.invitedBy ? t('join.invitedBy', { name: preview.invitedBy }) : undefined}
        >
            {signedIn ? (
                <div className="flex flex-col gap-5">
                    <p className="text-sm text-ink-soft">{t('join.moveWarning')}</p>
                    {error && (
                        <div className="alert-error" role="alert">
                            <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                            <span>{error}</span>
                        </div>
                    )}
                    <button type="button" disabled={joining} onClick={accept} className="btn btn-primary btn-lg w-full">
                        {joining ? (
                            <>
                                <span className="spinner spinner-sm" />
                                {t('join.accepting')}
                            </>
                        ) : (
                            t('join.accept')
                        )}
                    </button>
                </div>
            ) : (
                <div className="flex flex-col gap-3">
                    <Link to={`/register?invite=${encodeURIComponent(token)}`} className={`${linkClass} btn-primary`}>
                        {t('join.register')}
                    </Link>
                    <Link
                        to={`/login?next=${encodeURIComponent(`/join?token=${encodeURIComponent(token)}`)}`}
                        className={linkClass}
                    >
                        {t('join.signIn')}
                    </Link>
                </div>
            )}
        </AuthShell>
    );
};

export default Join;
