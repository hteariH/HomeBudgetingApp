import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api';
import { TrendingUp, UserPlus, Mail, Lock, User } from 'lucide-react';

const Register: React.FC = () => {
    const [formData, setFormData] = useState({
        email: '',
        password: '',
        firstName: '',
        lastName: ''
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            await authApi.register(formData);
            navigate('/login');
        } catch (err: any) {
            setError(err.message || 'Failed to register account');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center relative overflow-hidden bg-[#0a0c12]">
            {/* Immersive animated background blobs */}
            <div className="absolute top-[-10%] right-[-10%] w-[500px] h-[500px] bg-purple-600/20 rounded-full blur-[120px] mix-blend-screen pointer-events-none animate-pulse"></div>
            <div className="absolute bottom-[-10%] left-[-10%] w-[600px] h-[600px] bg-indigo-600/20 rounded-full blur-[150px] mix-blend-screen pointer-events-none" style={{ animation: 'pulse 3s alternate infinite' }}></div>
            <div className="absolute top-[30%] left-[20%] w-[300px] h-[300px] bg-cyan-600/20 rounded-full blur-[100px] mix-blend-screen pointer-events-none animate-pulse"></div>

            <div className="w-full max-w-[480px] z-10 p-6">

                <div className="flex flex-col items-center mb-8">
                    <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-purple-500 to-indigo-400 flex items-center justify-center shadow-[0_0_40px_rgba(168,85,247,0.4)] mb-6">
                        <TrendingUp size={32} color="#fff" strokeWidth={2.5} />
                    </div>
                    <h1 className="text-3xl font-extrabold text-white tracking-tight mb-2">Create an account</h1>
                    <p className="text-gray-400 font-medium">Join HomeBudget to manage your finances</p>
                </div>

                <div className="card shadow-2xl shadow-black/50 border-white/5 bg-[#161A23]/90 backdrop-blur-xl">
                    <form onSubmit={handleSubmit} className="space-y-5">

                        <div className="grid grid-cols-2 gap-4">
                            <div className="form-group">
                                <label className="form-label">First Name</label>
                                <div className="relative">
                                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                        <User size={18} className="text-gray-500" />
                                    </div>
                                    <input
                                        type="text"
                                        placeholder="John"
                                        className="form-control pl-10 bg-black/20 border-white/5 focus:border-purple-500"
                                        value={formData.firstName}
                                        onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                                        required
                                    />
                                </div>
                            </div>
                            <div className="form-group">
                                <label className="form-label">Last Name</label>
                                <input
                                    type="text"
                                    placeholder="Doe"
                                    className="form-control bg-black/20 border-white/5 focus:border-purple-500"
                                    value={formData.lastName}
                                    onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                                    required
                                />
                            </div>
                        </div>

                        <div className="form-group">
                            <label className="form-label">Email Address</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Mail size={18} className="text-gray-500" />
                                </div>
                                <input
                                    type="email"
                                    placeholder="you@example.com"
                                    className="form-control pl-10 bg-black/20 border-white/5 focus:border-purple-500"
                                    value={formData.email}
                                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                                    required
                                />
                            </div>
                        </div>

                        <div className="form-group">
                            <label className="form-label">Password</label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Lock size={18} className="text-gray-500" />
                                </div>
                                <input
                                    type="password"
                                    placeholder="••••••••"
                                    className="form-control pl-10 bg-black/20 border-white/5 focus:border-purple-500"
                                    value={formData.password}
                                    onChange={(e) => setFormData({ ...formData, password: e.target.value })}
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
                            className="btn btn-primary w-full flex justify-center py-3 text-base shadow-lg shadow-purple-500/25 mt-4"
                            style={{ background: 'linear-gradient(to right, var(--purple), var(--indigo))', borderColor: 'transparent' }}
                        >
                            {loading ? (
                                <span className="flex items-center gap-2">
                                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                                    Creating account...
                                </span>
                            ) : (
                                <>
                                    <UserPlus size={20} className="mr-2" />
                                    Register Account
                                </>
                            )}
                        </button>
                    </form>
                </div>

                <p className="text-center mt-8 text-sm text-gray-500">
                    Already have an account?{' '}
                    <Link to="/login" className="text-purple-400 hover:text-purple-300 font-semibold transition-colors">
                        Sign in instead
                    </Link>
                </p>

            </div>
        </div>
    );
};

export default Register;
