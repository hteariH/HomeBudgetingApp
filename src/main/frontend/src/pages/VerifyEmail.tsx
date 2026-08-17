import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../api';
import AuthShell from './AuthShell';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';
import { useT } from '../i18n';

/**
 * Lands here from the confirmation email.
 *
 * The token is redeemed on load, so the user only has to click the link. All three
 * states are shown explicitly because failure is common and recoverable: a link works
 * once, so a second click - or a forwarded mail - lands on "already used", and saying
 * so beats leaving someone staring at a spinner.
 */
const VerifyEmail: React.FC = () => {
    const t = useT();
    const [params] = useSearchParams();
    const token = params.get('token');
    const [state, setState] = useState<'working' | 'done' | 'failed'>('working');
    const [message, setMessage] = useState('');

    // React runs effects twice in development. Without this guard the second run
    // redeems the already-spent token and reports failure on a successful confirmation.
    const started = useRef(false);

    useEffect(() => {
        if (started.current) return;
        started.current = true;

        if (!token) {
            setState('failed');
            setMessage(t('verify.noToken'));
            return;
        }
        authApi.verifyEmail(token)
            .then(() => { setState('done'); setMessage(t('verify.doneBody')); })
            .catch((e: any) => { setState('failed'); setMessage(e.message || t('verify.failedBody')); });
        // Runs once by design - re-running on a language change would spend the token again.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [token]);

    if (state === 'working') {
        return (
            <AuthShell
                centered
                icon={<Loader2 size={34} className="animate-spin text-brand-light" />}
                title={t('verify.working')}
            />
        );
    }

    if (state === 'done') {
        return (
            <AuthShell
                centered
                icon={
                    <span className="flex h-14 w-14 items-center justify-center rounded-full bg-ok/15">
                        <CheckCircle2 size={26} className="text-ok" />
                    </span>
                }
                title={t('verify.doneTitle')}
                subtitle={message}
            >
                <Link to="/login" className="btn btn-primary">{t('login.submit')}</Link>
            </AuthShell>
        );
    }

    return (
        <AuthShell
            centered
            icon={
                <span className="flex h-14 w-14 items-center justify-center rounded-full bg-bad/15">
                    <XCircle size={26} className="text-bad" />
                </span>
            }
            title={t('verify.failedTitle')}
            subtitle={message}
        >
            <p className="text-[13px] text-ink-dim">{t('verify.failedNote')}</p>
            <Link to="/login" className="btn btn-secondary mt-5">{t('forgot.backToSignIn')}</Link>
        </AuthShell>
    );
};

export default VerifyEmail;
