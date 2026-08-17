import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../api';
import AuthShell from './AuthShell';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';

/**
 * Lands here from the confirmation email.
 *
 * The token is redeemed on load, so the user only has to click the link. All three
 * states are shown explicitly because failure is common and recoverable: a link works
 * once, so a second click - or a forwarded mail - lands on "already used", and saying
 * so beats leaving someone staring at a spinner.
 */
const VerifyEmail: React.FC = () => {
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
            setMessage('This link is missing its token.');
            return;
        }
        authApi.verifyEmail(token)
            .then(() => { setState('done'); setMessage('Your address is confirmed.'); })
            .catch((e: any) => { setState('failed'); setMessage(e.message || 'This link is not valid.'); });
    }, [token]);

    return (
        <AuthShell centered>
            {state === 'working' && (
                <>
                    <Loader2 size={40} />
                    <h1>Confirming…</h1>
                </>
            )}

            {state === 'done' && (
                <>
                    <CheckCircle2 size={40} color="var(--green)" />
                    <h1>Address confirmed</h1>
                    <p style={{ color: 'var(--text-muted)' }}>{message}</p>
                    <Link to="/login" className="btn btn-primary" style={{ marginTop: 16 }}>Sign in</Link>
                </>
            )}

            {state === 'failed' && (
                <>
                    <XCircle size={40} color="var(--red)" />
                    <h1>Could not confirm</h1>
                    <p style={{ color: 'var(--text-muted)' }}>{message}</p>
                    <p style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 8 }}>
                        Confirmation links expire after 24 hours and work once. Sign in and ask for a new one.
                    </p>
                    <Link to="/login" className="btn btn-secondary" style={{ marginTop: 16 }}>Back to sign in</Link>
                </>
            )}
        </AuthShell>
    );
};

export default VerifyEmail;
