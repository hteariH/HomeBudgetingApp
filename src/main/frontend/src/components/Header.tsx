import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, User as UserIcon } from 'lucide-react';

const Header: React.FC = () => {
    const navigate = useNavigate();
    const [userName, setUserName] = useState<string>('User');

    useEffect(() => {
        // Try to get user details from localStorage
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
                <div className="flex items-center gap-3 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10">
                    <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center text-white shadow-inner">
                        <UserIcon size={16} />
                    </div>
                    <span className="text-sm font-medium text-gray-200 pr-2">
                        {userName}
                    </span>
                </div>

                <button
                    onClick={handleLogout}
                    className="btn btn-ghost !p-2 text-gray-400 hover:text-red-400 hover:bg-red-500/10 border-transparent hover:border-red-500/20 transition-all"
                    title="Sign Out"
                >
                    <LogOut size={18} />
                </button>
            </div>
        </header>
    );
};

export default Header;
