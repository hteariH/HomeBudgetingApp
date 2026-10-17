import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './http';

export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            // A 4xx will not improve by asking again; a dropped connection might.
            retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
            refetchOnWindowFocus: false,
        },
    },
});

export const qk = {
    accounts: ['accounts'] as const,
    categories: ['categories'] as const,
    currencies: ['currencies'] as const,
    fx: ['fx'] as const,
    transactions: (filter: object = {}) => ['transactions', filter] as const,
    transaction: (id: number) => ['transactions', 'one', id] as const,
    transfers: ['transfers'] as const,
    subscriptions: ['subscriptions'] as const,
    receipt: (uuid: string) => ['receipts', uuid] as const,
    me: ['me'] as const,
    bank: ['bank'] as const,
    homeMoney: ['homemoney'] as const,
    instance: ['instance'] as const,
    household: ['household'] as const,
    invites: ['household', 'invites'] as const,
    adminUsers: ['admin', 'users'] as const,
    aiProvider: ['household', 'ai-provider'] as const,
    adminAiProvider: ['admin', 'ai-provider'] as const,
    adminRegistration: ['admin', 'registration'] as const,
    adminMcp: ['admin', 'mcp'] as const,
    oauthClient: (clientId: string, redirectUri: string) => ['oauth', 'client', clientId, redirectUri] as const,
    assistantTokens: ['assistants', 'tokens'] as const,
};

/**
 * A change to the ledger moves balances, the overview, the analytics and the subscription
 * schedule together - the invalidation the Compose repositories did by bumping a revision.
 */
export const invalidateLedger = () =>
    Promise.all(
        ['transactions', 'transfers', 'accounts', 'subscriptions'].map(key =>
            queryClient.invalidateQueries({ queryKey: [key] }),
        ),
    );
