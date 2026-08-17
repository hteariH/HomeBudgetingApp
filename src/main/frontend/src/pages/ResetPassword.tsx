import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api';
import AuthShell from './AuthShell';
import { CheckCircle2 } from 'lucide-react';

const MIN_LENGTH = 10;

/**
 * Lands here from the reset email.
 *
 * The token is redeemed on submit, not on load: it is single use, so spending it just
 * by opening the page would break the link for the person who then types a password.
 * Confirmation is a second field rather than a reveal toggle, because a typo here locks
 * the account until the whole flow is repeated.
 */
const ResetPassword: React.FC = () => {
    const [params] = useSearchParams();
    const navigate = useNavigate();
    const token = params.get('token');

    const [password, setPassword] = useState('');
    const [confirm, setConfirm] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [done, setDone] = useState(false);

    const tooShort = password.length > 0 && password.length < MIN_LENGTH;
    const mismatch = confirm.length > 0 && password !== confirm;
    const canSubmit = !busy && !!token && password.length >= MIN_LENGTH && password === confirm;

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!canSubmit || !token) return;
        setBusy(true);
        setError(null);
        try {
            await authApi.resetPassword(token, password);
            setDone(true);
            // Every session was revoked server-side, so signing in again is the only way
            // forward - send them there rather than leaving a dead end.
            setTimeout(() => navigate('/login'), 2500);
        } catch (e: any) {
            setError(e.message || 'This link is not valid.');
        } finally {
            setBusy(false);
        }
    };

    if (!token) {
        return (
            <AuthShell centered>
                <h1>Link is incomplete</h1>
                <p style={{ color: 'var(--text-muted)' }}>This link is missing its token.</p>
                <Link to="/forgot-password" className="btn btn-primary" style={{ marginTop: 16 }}>
                    Request a new link
                </Link>
            </AuthShell>
        );
    }

    if (done) {
        return (
            <AuthShell centered>
                <CheckCircle2 size={40} color="var(--green)" />
                <h1>Password changed</h1>
                <p style={{ color: 'var(--text-muted)' }}>
                    You have been signed out everywhere. Sign in with the new password.
                </p>
                <Link to="/login" className="btn btn-primary" style={{ marginTop: 16 }}>Sign in</Link>
            </AuthShell>
        );
    }

    return (
        <AuthShell>
            <h1>Choose a new password</h1>
            <form onSubmit={submit} style={{ marginTop: 16 }}>
                <div className="form-group">
                    <label className="form-label">New password</label>
                    <input
                        type="password" className="form-control" required
                        value={password} onChange={e => setPassword(e.target.value)}
                    />
                    {tooShort && (
                        <p style={{ fontSize: 12, color: 'var(--red)' }}>At least {MIN_LENGTH} characters.</p>
                    )}
                </div>
                <div className="form-group">
                    <label className="form-label">Repeat it</label>
                    <input
                        type="password" className="form-control" required
                        value={confirm} onChange={e => setConfirm(e.target.value)}
                    />
                    {mismatch && <p style={{ fontSize: 12, color: 'var(--red)' }}>These do not match.</p>}
                </div>
                {error && <div className="alert alert-error">{error}</div>}
                <button type="submit" className="btn btn-primary" disabled={!canSubmit} style={{ width: '100%' }}>
                    {busy ? 'Saving…' : 'Set new password'}
                </button>
            </form>
            <p style={{ marginTop: 16, textAlign: 'center', fontSize: 13, color: 'var(--text-muted)' }}>
                Links expire after an hour and work once.{' '}
                <Link to="/forgot-password">Request another</Link>
            </p>
        </AuthShell>
    );
};

export default ResetPassword;
