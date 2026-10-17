import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import ConsentPage from './ConsentPage';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const PARAMS =
    'response_type=code&client_id=abc&redirect_uri=https%3A%2F%2Fclaude.ai%2Fcb&code_challenge=CH&code_challenge_method=S256&state=xyz&scope=ledger%3Awrite&resource=https%3A%2F%2Fm.example.com%2Fmcp';

function stubServer(client: () => Response = () => json({ clientName: 'Claude', redirectHost: 'claude.ai' }), authorize: () => Response = () => json({ redirect: 'https://claude.ai/cb?code=1&state=xyz' })) {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url.startsWith('/api/oauth/clients/')) return Promise.resolve(client());
        if (url === '/api/oauth/authorize' && init?.method === 'POST') return Promise.resolve(authorize());
        if (url === '/api/users/me') return Promise.resolve(json({ id: 1, email: 'a@b.c', householdName: 'The Smiths' }));
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const assign = vi.fn();
beforeEach(() => {
    assign.mockReset();
    vi.stubGlobal('location', { ...window.location, assign, origin: window.location.origin });
});
afterEach(() => vi.unstubAllGlobals());

const renderPage = (search = PARAMS) =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter initialEntries={[`/oauth/consent?${search}`]}>
                <I18nProvider>
                    <ConsentPage />
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

it('names the client and the household and checks the redirect with the server', async () => {
    const fetchMock = stubServer();
    renderPage();
    expect(await screen.findByText('Claude')).toBeInTheDocument();
    expect(await screen.findByText('The Smiths')).toBeInTheDocument();
    expect(document.body.textContent).toContain('(unverified) wants to read your finances and add entries in');
    expect(document.body.textContent).toContain('It cannot edit or delete anything.');
    expect(fetchMock.mock.calls.some(([u]) => u === '/api/oauth/clients/abc?redirectUri=https%3A%2F%2Fclaude.ai%2Fcb')).toBe(true);
});

it('Allow posts approve:true with everything the request carried and follows the redirect', async () => {
    const fetchMock = stubServer();
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: appEn.consent_allow }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://claude.ai/cb?code=1&state=xyz'));
    const post = fetchMock.mock.calls.find(([u]) => u === '/api/oauth/authorize')!;
    expect(JSON.parse(String(post[1]!.body))).toEqual({
        clientId: 'abc',
        redirectUri: 'https://claude.ai/cb',
        codeChallenge: 'CH',
        state: 'xyz',
        scope: 'ledger:write',
        resource: 'https://m.example.com/mcp',
        approve: true,
    });
});

it('Deny posts approve:false', async () => {
    const fetchMock = stubServer();
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: appEn.consent_deny }));
    await waitFor(() => expect(assign).toHaveBeenCalled());
    const post = fetchMock.mock.calls.find(([u]) => u === '/api/oauth/authorize')!;
    expect(JSON.parse(String(post[1]!.body)).approve).toBe(false);
});

it('shows an error and no buttons for an unknown client or an unregistered redirect', async () => {
    stubServer(() => json({ message: 'nope' }, 400));
    renderPage();
    expect(await screen.findByText(appEn.consent_invalid)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: appEn.consent_allow })).toBeNull();
    expect(screen.queryByRole('button', { name: appEn.consent_deny })).toBeNull();
});

it('shows an error and no buttons when the parameters are missing', async () => {
    const fetchMock = stubServer();
    renderPage('client_id=abc');
    expect(await screen.findByText(appEn.consent_invalid)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: appEn.consent_allow })).toBeNull();
    expect(fetchMock.mock.calls.some(([u]) => String(u).startsWith('/api/oauth'))).toBe(false);
});

it('shows a generic error when the server refuses the decision', async () => {
    stubServer(undefined, () => json({ error: 'invalid_target' }, 400));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: appEn.consent_allow }));
    expect(await screen.findByText(appEn.consent_failed)).toBeInTheDocument();
    expect(assign).not.toHaveBeenCalled();
});
