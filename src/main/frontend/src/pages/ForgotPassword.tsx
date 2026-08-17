import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../api';
import AuthShell from './AuthShell';
import { AlertTriangle, Mail, MailCheck } from 'lucide-react';
import { useT } from '../i18n';

/**
 * Asks for a reset link.
 *
 * The confirmation is deliberately vague about whether the address is registered,
 * matching what the server does. Saying "no such account" would turn this form into a
 * way to test which addresses have accounts here, and a finance app is a pointed thing
 * to be on such a list for.
 */
const ForgotPassword: React.FC = () => {
    const t = useT();
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
            setError(e.message || t('forgot.failed'));
        } finally {
            setBusy(false);
        }
    };

    if (sent) {
        return (
            <AuthShell
                centered
                icon={
                    <span className="flex h-14 w-14 items-center justify-center rounded-full bg-ok/15">
                        <MailCheck size={26} className="text-ok" />
                    </span>
                }
                title={t('forgot.sentTitle')}
                subtitle={t('forgot.sentBody')}
            >
                <p className="text-[13px] text-ink-dim">{t('forgot.sentNote')}</p>
                <Link to="/login" className="btn btn-secondary mt-5">{t('forgot.backToSignIn')}</Link>
            </AuthShell>
        );
    }

    return (
        <AuthShell title={t('forgot.title')} subtitle={t('forgot.subtitle')}>
            <form onSubmit={submit} className="flex flex-col gap-5">
                <div className="form-group">
                    <label className="form-label" htmlFor="email">{t('auth.email')}</label>
                    <div className="relative">
                        <Mail size={17} className="input-icon" />
                        <input
                            type="email"
                            id="email"
                            autoComplete="email"
                            className="form-control form-control-icon"
                            required
                            placeholder={t('auth.emailPlaceholder')}
                            value={email}
                            onChange={e => setEmail(e.target.value)}
                        />
                    </div>
                </div>

                {error && (
                    <div className="alert-error" role="alert">
                        <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                        <span>{error}</span>
                    </div>
                )}

                <button type="submit" className="btn btn-primary btn-lg w-full" disabled={busy || !email}>
                    {busy ? (
                        <>
                            <span className="spinner spinner-sm" />
                            {t('forgot.submitting')}
                        </>
                    ) : (
                        t('forgot.submit')
                    )}
                </button>

                <p className="text-center text-sm">
                    <Link to="/login" className="text-ink-dim transition-colors hover:text-ink-soft">
                        {t('forgot.backToSignIn')}
                    </Link>
                </p>
            </form>
        </AuthShell>
    );
};

export default ForgotPassword;
