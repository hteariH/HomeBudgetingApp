import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from './authApi';
import { AlertTriangle, UserPlus, Mail, Lock, User } from 'lucide-react';
import { useT } from '../i18n';
import AuthShell from './AuthShell';
import { nextPath, saveSession } from './session';
import { useInstance } from './instance';

const Register: React.FC<{ setup?: boolean }> = ({ setup = false }) => {
    const t = useT();
    const navigate = useNavigate();
    const location = useLocation();
    const inviteToken = useSearchParams()[0].get('invite');
    const [formData, setFormData] = useState({
        email: '',
        password: '',
        firstName: '',
        lastName: '',
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const instance = useInstance();

    useEffect(() => {
        if (!setup && instance?.setupRequired) navigate('/setup' + location.search, { replace: true });
    }, [setup, instance, navigate, location.search]);
    // An invitation is let through on a closed instance, so there is nothing to refuse.
    const closed = !setup && !inviteToken && instance !== null && !instance.registrationOpen;

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            await authApi.register(inviteToken ? { ...formData, inviteToken } : formData);
            // Registration does not return tokens, so sign in straight away rather than
            // sending someone who has just typed their password to a form asking for it
            // again. This is what the Compose client's AuthRepository.register already did;
            // the web was the odd one out.
            const res = await authApi.login({ email: formData.email, password: formData.password });
            saveSession(res);
            // The invitation was accepted by registering; the household is already theirs.
            navigate(inviteToken ? '/app' : nextPath(location.search), { replace: true });
        } catch (err: any) {
            setError(err.message || t('register.failed'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthShell
            wide
            title={t(setup ? 'setup.title' : 'register.title')}
            subtitle={t(setup ? 'setup.subtitle' : inviteToken ? 'join.registerSubtitle' : 'register.subtitle')}
            footer={
                <p className="text-ink-dim">
                    {t('register.hasAccount')}{' '}
                    <Link
                        to={inviteToken
                            ? `/login?next=${encodeURIComponent(`/join?token=${encodeURIComponent(inviteToken)}`)}`
                            : '/login'}
                        className="font-semibold text-brand-light transition-colors hover:text-brand">
                        {t('register.signIn')}
                    </Link>
                </p>
            }
        >
            {closed ? (
                <div className="alert-error" role="alert">
                    <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                    <span>{t('register.closed')}</span>
                </div>
            ) : (
                <form onSubmit={handleSubmit} className="flex flex-col gap-5">
                    <div className="grid gap-4 sm:grid-cols-2">
                        <div className="form-group">
                            <label className="form-label" htmlFor="firstName">{t('auth.firstName')}</label>
                            <div className="relative">
                                <User size={17} className="input-icon" />
                                <input
                                    type="text"
                                    id="firstName"
                                    autoComplete="given-name"
                                    className="form-control form-control-icon"
                                    value={formData.firstName}
                                    onChange={e => setFormData({ ...formData, firstName: e.target.value })}
                                    required
                                />
                            </div>
                        </div>
                        <div className="form-group">
                            <label className="form-label" htmlFor="lastName">{t('auth.lastName')}</label>
                            <input
                                type="text"
                                id="lastName"
                                autoComplete="family-name"
                                className="form-control"
                                value={formData.lastName}
                                onChange={e => setFormData({ ...formData, lastName: e.target.value })}
                                required
                            />
                        </div>
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="email">{t('auth.email')}</label>
                        <div className="relative">
                            <Mail size={17} className="input-icon" />
                            <input
                                type="email"
                                id="email"
                                autoComplete="email"
                                placeholder={t('auth.emailPlaceholder')}
                                className="form-control form-control-icon"
                                value={formData.email}
                                onChange={e => setFormData({ ...formData, email: e.target.value })}
                                required
                            />
                        </div>
                    </div>

                    <div className="form-group">
                        <label className="form-label" htmlFor="password">{t('auth.password')}</label>
                        <div className="relative">
                            <Lock size={17} className="input-icon" />
                            <input
                                type="password"
                                id="password"
                                autoComplete="new-password"
                                placeholder="••••••••"
                                className="form-control form-control-icon"
                                value={formData.password}
                                onChange={e => setFormData({ ...formData, password: e.target.value })}
                                required
                            />
                        </div>
                    </div>

                    {error && (
                        <div className="alert-error" role="alert">
                            <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                            <span>{error}</span>
                        </div>
                    )}

                    {/* Gradient set through the tokens; the old inline style referenced
                        var(--purple)/var(--indigo), neither of which existed, which left the
                        button with no background at all. */}
                    <button
                        type="submit"
                        disabled={loading}
                        className="btn btn-primary btn-lg mt-1 w-full bg-gradient-to-r from-violet to-brand hover:from-violet hover:to-brand-light"
                    >
                        {loading ? (
                            <>
                                <span className="spinner spinner-sm" />
                                {t('register.submitting')}
                            </>
                        ) : (
                            <>
                                <UserPlus size={18} />
                                {t('register.submit')}
                            </>
                        )}
                    </button>
                </form>
            )}
        </AuthShell>
    );
};

export default Register;
