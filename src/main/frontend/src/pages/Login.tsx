import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api';
import { AlertTriangle, LogIn, Mail, Lock } from 'lucide-react';
import { useT } from '../i18n';
import AuthShell from './AuthShell';

const Login: React.FC = () => {
    const t = useT();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const res = await authApi.login({ email, password });
            localStorage.setItem('token', res.token);
            localStorage.setItem('user', JSON.stringify(res));
            navigate('/');
        } catch (err: any) {
            setError(err.message || t('login.failed'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthShell
            title={t('login.title')}
            subtitle={t('login.subtitle')}
            footer={
                <>
                    <p className="text-ink-dim">
                        {t('login.noAccount')}{' '}
                        <Link to="/register" className="font-semibold text-brand-light transition-colors hover:text-brand">
                            {t('login.createOne')}
                        </Link>
                    </p>
                    <p className="mt-3">
                        <Link to="/forgot-password" className="text-ink-dim transition-colors hover:text-ink-soft">
                            {t('login.forgot')}
                        </Link>
                    </p>
                </>
            }
        >
            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
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
                            value={email}
                            onChange={e => setEmail(e.target.value)}
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
                            autoComplete="current-password"
                            placeholder="••••••••"
                            className="form-control form-control-icon"
                            value={password}
                            onChange={e => setPassword(e.target.value)}
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

                <button type="submit" disabled={loading} className="btn btn-primary btn-lg mt-1 w-full">
                    {loading ? (
                        <>
                            <span className="spinner spinner-sm" />
                            {t('login.submitting')}
                        </>
                    ) : (
                        <>
                            <LogIn size={18} />
                            {t('login.submit')}
                        </>
                    )}
                </button>
            </form>
        </AuthShell>
    );
};

export default Login;
