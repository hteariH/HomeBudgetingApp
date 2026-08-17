import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../api';
import AuthShell from './AuthShell';
import { MailCheck } from 'lucide-react';

/**
 * Asks for a reset link.
 *
 * The confirmation is deliberately vague about whether the address is registered,
 * matching what the server does. Saying "no such account" would turn this form into a
 * way to test which addresses have accounts here, and a finance app is a pointed thing
 * to be on such a list for.
 */
const ForgotPassword: React.FC = () => {
    const [email, setEmail] = useState('');
    const [sent, setSent] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            await authApi.forgotPassword(email.trim());
            setSent(true);
        } catch (e: any) {
            // Only transport or rate-limit failures reach here; the server never
            // reports whether the address exists.
            setError(e.message || 'Could not send the link. Try again shortly.');
        } finally {
            setBusy(false);
        }
    };

    if (sent) {
        return (
            <AuthShell centered>
                <MailCheck size={40} color="var(--green)" />
                <h1>Check your inbox</h1>
                <p style={{ color: 'var(--text-muted)' }}>
                    If that address has an account, a reset link is on its way.
                </p>
                <p style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 8 }}>
                    The link works once and expires in an hour.
                </p>
                <Link to="/login" className="btn btn-secondary" style={{ marginTop: 16 }}>Back to sign in</Link>
            </AuthShell>
        );
    }

    return (
        <AuthShell>
            <h1>Reset your password</h1>
            <p style={{ color: 'var(--text-muted)', marginBottom: 16 }}>
                Enter your address and we will send a link to choose a new password.
            </p>
            <form onSubmit={submit}>
                <div className="form-group">
                    <label className="form-label">Email Address</label>
                    <input
                        type="email" className="form-control" required
                        placeholder="you@example.com"
                        value={email} onChange={e => setEmail(e.target.value)}
                    />
                </div>
                {error && <div className="alert alert-error">{error}</div>}
                <button type="submit" className="btn btn-primary" disabled={busy || !email} style={{ width: '100%' }}>
                    {busy ? 'Sending…' : 'Send reset link'}
                </button>
            </form>
            <p style={{ marginTop: 16, textAlign: 'center' }}>
                <Link to="/login">Back to sign in</Link>
            </p>
        </AuthShell>
    );
};

export default ForgotPassword;
