import React from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '../../api/queryClient';
import { AuthGuard } from '../../shell/AuthGuard';
import ConsentPage from './ConsentPage';

/**
 * /oauth/consent outside the app shell: the same sign-in guard as /app, which sends a
 * signed-out visitor to /login carrying this address (query included) so sign-in returns
 * here with the assistant's request intact.
 */
const ConsentRoute: React.FC = () => (
    <QueryClientProvider client={queryClient}>
        <AuthGuard>
            <ConsentPage />
        </AuthGuard>
    </QueryClientProvider>
);

export default ConsentRoute;
