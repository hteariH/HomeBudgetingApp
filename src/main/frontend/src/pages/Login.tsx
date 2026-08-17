import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api';
import { TrendingUp, LogIn, Mail, Lock } from 'lucide-react';

const Login: React.FC = () => {
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
            setError(err.message || 'Failed to login');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center relative overflow-hidden bg-[#0a0c12]">
            {/* Immersive animated background blobs */}
            <div className="absolute top-[-10%] left-[-10%] w-[500px] h-[500px] bg-indigo-600/20 rounded-full blur-[120px] mix-blend-screen pointer-events-none animate-pulse"></div>
            <div className="absolute bottom-[-10%] right-[-10%] w-[600px] h-[600px] bg-cyan-600/20 rounded-full blur-[150px] mix-blend-screen pointer-events-none" style={{ animation: 'pulse 3s alternate infinite' }}></div>
            <div className="absolute top-[20%] right-[10%] w-[300px] h-[300px] bg-purple-600/20 rounded-full blur-[100px] mix-blend-screen pointer-events-none animate-pulse"></div>

            <div className="w-full max-w-[420px] z-10 p-6">

                <div className="flex flex-col items-center mb-8">
                    <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-indigo-500 to-cyan-400 flex items-center justify-center shadow-[0_0_40px_rgba(99,102,241,0.4)] mb-6">
                        <TrendingUp size={32} color="#fff" strokeWidth={2.5} />
                    </div>
                    <h1 className="text-3xl font-extrabold text-white tracking-tight mb-2">Welcome back</h1>
                    <p className="text-gray-400 font-medium">Log in to manage your finances</p>
                </div>

                <div className="card shadow-2xl shadow-black/50 border-white/5 bg-[#161A23]/90 backdrop-blur-xl">
                    <form onSubmit={handleSubmit} className="space-y-6">

                        <div className="form-group">
                            <label className="form-label" htmlFor="email">Email Address</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Mail size={18} className="text-gray-500" />
                                </div>
                                <input
                                    type="email"
                                    id="email"
                                    placeholder="you@example.com"
                                    className="form-control pl-10 bg-black/20 border-white/5 focus:border-indigo-500"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    required
                                />
                            </div>
                        </div>

                        <div className="form-group">
                            <label className="form-label" htmlFor="password">Password</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Lock size={18} className="text-gray-500" />
                                </div>
                                <input
                                    type="password"
                                    id="password"
                                    placeholder="••••••••"
                                    className="form-control pl-10 bg-black/20 border-white/5 focus:border-indigo-500"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    required
                                />
                            </div>
                        </div>

                        {error && (
                            <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-sm flex items-center">
                                <div className="mr-2">⚠️</div>
                                <span>{error}</span>
                            </div>
                        )}

                        <button
                            type="submit"
                            disabled={loading}
                            className="btn btn-primary w-full flex justify-center py-3 text-base shadow-lg shadow-indigo-500/25 mt-2"
                        >
                            {loading ? (
                                <span className="flex items-center gap-2">
                                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                                    Signing in...
                                </span>
                            ) : (
                                <>
                                    <LogIn size={20} className="mr-2" />
                                    Sign In
                                </>
                            )}
                        </button>
                    </form>
                </div>

                <p className="text-center mt-8 text-sm text-gray-500">
                    Don't have an account?{' '}
                    <Link to="/register" className="text-indigo-400 hover:text-indigo-300 font-semibold transition-colors">
                        Create one now
                    </Link>
                </p>

                <p className="text-center mt-3 text-sm">
                    <Link to="/forgot-password" className="text-gray-500 hover:text-gray-300 transition-colors">
                        Forgot your password?
                    </Link>
                </p>

            </div>
        </div>
    );
};

export default Login;
