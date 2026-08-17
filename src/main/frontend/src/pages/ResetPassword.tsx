import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api';
import AuthShell from './AuthShell';
import { AlertTriangle, CheckCircle2, Lock, LinkIcon } from 'lucide-react';
import { useT } from '../i18n';

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
    const t = useT();
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
            setError(e.message || t('reset.failed'));
        } finally {
            setBusy(false);
        }
    };

    if (!token) {
        return (
            <AuthShell
                centered
                icon={
                    <span className="flex h-14 w-14 items-center justify-center rounded-full bg-warn/15">
                        <LinkIcon size={24} className="text-warn" />
                    </span>
                }
                title={t('reset.noTokenTitle')}
                subtitle={t('reset.noTokenBody')}
            >
                <Link to="/forgot-password" className="btn btn-primary">{t('reset.requestNew')}</Link>
            </AuthShell>
        );
    }

    if (done) {
        return (
            <AuthShell
                centered
                icon={
                    <span className="flex h-14 w-14 items-center justify-center rounded-full bg-ok/15">
                        <CheckCircle2 size={26} className="text-ok" />
                    </span>
                }
                title={t('reset.doneTitle')}
                subtitle={t('reset.doneBody')}
            >
                <Link to="/login" className="btn btn-primary">{t('login.submit')}</Link>
            </AuthShell>
        );
    }

    return (
        <AuthShell title={t('reset.title')}>
            <form onSubmit={submit} className="flex flex-col gap-5">
                <div className="form-group">
                    <label className="form-label" htmlFor="password">{t('reset.newPassword')}</label>
                    <div className="relative">
                        <Lock size={17} className="input-icon" />
                        <input
                            type="password"
                            id="password"
                            autoComplete="new-password"
                            className="form-control form-control-icon"
                            required
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                        />
                    </div>
                    {tooShort && (
                        <p className="text-xs text-red">{t('reset.tooShort', { count: MIN_LENGTH })}</p>
                    )}
                </div>

                <div className="form-group">
                    <label className="form-label" htmlFor="confirm">{t('reset.repeat')}</label>
                    <div className="relative">
                        <Lock size={17} className="input-icon" />
                        <input
                            type="password"
                            id="confirm"
                            autoComplete="new-password"
                            className="form-control form-control-icon"
                            required
                            value={confirm}
                            onChange={e => setConfirm(e.target.value)}
                        />
                    </div>
                    {mismatch && <p className="text-xs text-red">{t('reset.mismatch')}</p>}
                </div>

                {error && (
                    <div className="alert-error" role="alert">
                        <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                        <span>{error}</span>
                    </div>
                )}

                <button type="submit" className="btn btn-primary btn-lg w-full" disabled={!canSubmit}>
                    {busy ? (
                        <>
                            <span className="spinner spinner-sm" />
                            {t('common.saving')}
                        </>
                    ) : (
                        t('reset.submit')
                    )}
                </button>

                <p className="text-center text-[13px] text-ink-dim">
                    {t('reset.expiryNote')}{' '}
                    <Link to="/forgot-password" className="text-brand-light transition-colors hover:text-brand">
                        {t('reset.requestAnother')}
                    </Link>
                </p>
            </form>
        </AuthShell>
    );
};

export default ResetPassword;
