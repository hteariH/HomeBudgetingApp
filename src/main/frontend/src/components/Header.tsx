import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, User as UserIcon, Sun, Moon } from 'lucide-react';

const Header: React.FC = () => {
    const navigate = useNavigate();
    const [userName, setUserName] = useState<string>('User');
    const [theme, setTheme] = useState<'dark' | 'light'>('dark');

    useEffect(() => {
        // Theme init
        const savedTheme = localStorage.getItem('theme') as 'dark' | 'light';
        const initialTheme = savedTheme || 'dark';
        setTheme(initialTheme);
        document.documentElement.setAttribute('data-theme', initialTheme);

        // User info init
        const userStr = localStorage.getItem('user');
        if (userStr) {
            try {
                const user = JSON.parse(userStr);
                if (user.firstName && user.lastName) {
                    setUserName(`${user.firstName} ${user.lastName}`);
                } else if (user.firstName) {
                    setUserName(user.firstName);
                } else if (user.email) {
                    setUserName(user.email.split('@')[0]);
                }
            } catch (e) {
                console.error("Failed to parse user details", e);
            }
        }
    }, []);

    const toggleTheme = () => {
        const newTheme = theme === 'dark' ? 'light' : 'dark';
        setTheme(newTheme);
        localStorage.setItem('theme', newTheme);
        document.documentElement.setAttribute('data-theme', newTheme);
    };

    const handleLogout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        navigate('/login');
    };

    return (
        <header className="header">
            <div>
                {/* Left side can remain empty or hold a dynamic title later */}
            </div>

            <div className="flex items-center gap-4">
                <button
                    onClick={toggleTheme}
                    className="btn btn-ghost !p-2 transition-all"
                    title={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
                    style={{ color: 'var(--text-secondary)' }}
                >
                    {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
                </button>

                <div className="flex items-center gap-3 px-3 py-1.5 rounded-lg" style={{ background: 'var(--bg-hover)', border: '1px solid var(--border)' }}>
                    <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center text-white shadow-inner">
                        <UserIcon size={16} />
                    </div>
                    <span className="text-sm font-medium pr-2" style={{ color: 'var(--text-primary)' }}>
                        {userName}
                    </span>
                </div>

                <button
                    onClick={handleLogout}
                    className="btn btn-ghost !p-2 hover:text-red-400 transition-all border-transparent"
                    title="Sign Out"
                    style={{ color: 'var(--text-secondary)' }}
                    onMouseEnter={e => { e.currentTarget.style.color = 'var(--red)'; e.currentTarget.style.background = 'rgba(239, 68, 68, 0.1)'; }}
                    onMouseLeave={e => { e.currentTarget.style.color = 'var(--text-secondary)'; e.currentTarget.style.background = 'transparent'; }}
                >
                    <LogOut size={18} />
                </button>
            </div>
        </header>
    );
};

export default Header;
