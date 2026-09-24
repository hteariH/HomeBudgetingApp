import React, { useSyncExternalStore } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { session } from '../api/session';

export const useSession = () => useSyncExternalStore(session.subscribe, session.get, () => null);

/**
 * Nothing in the app can be shown before there is an account. A browser without a session
 * - never signed in, signed out, or a refresh token the server stopped honouring - goes to
 * the sign-in page, carrying the screen it was on so it comes back to it.
 */
export const AuthGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const current = useSession();
    const location = useLocation();
    if (!current) {
        const next = encodeURIComponent(location.pathname + location.search);
        return <Navigate to={`/login?next=${next}`} replace />;
    }
    return <>{children}</>;
};
