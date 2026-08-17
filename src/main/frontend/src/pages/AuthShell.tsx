import React from 'react';

/**
 * The page frame shared by every signed-out screen.
 *
 * One component rather than the same wrapper markup copied into each page: these
 * screens are the first thing a new user sees, and having them drift apart visually is
 * both easy and noticeable.
 */
const AuthShell: React.FC<{ children: React.ReactNode; centered?: boolean }> = ({ children, centered }) => (
    <div className="min-h-screen flex items-center justify-center bg-[#0a0c12]">
        <div className="w-full max-w-[420px] z-10 p-6">
            <div
                className="card shadow-2xl shadow-black/50 border-white/5 bg-[#161A23]/90 backdrop-blur-xl"
                style={{ padding: 32, textAlign: centered ? 'center' : undefined }}
            >
                {children}
            </div>
        </div>
    </div>
);

export default AuthShell;
