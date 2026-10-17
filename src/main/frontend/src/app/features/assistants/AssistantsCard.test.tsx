import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import { AssistantsCard } from './AssistantsCard';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const TOKEN = 'pm_AbCdEf0123456789AbCdEf0123456789AbCdEf01';
const mine = { id: '7', kind: 'PERSONAL', name: 'Laptop', prefix: 'pm_AbCd', createdAt: '2026-10-01T10:00:00Z', lastUsedAt: null, expiresAt: null };
const grant = { id: 'b1d3-uuid', kind: 'OAUTH', name: 'Claude', prefix: null, createdAt: '2026-10-02T10:00:00Z', lastUsedAt: '2026-10-03T10:00:00Z', expiresAt: '2027-01-01T00:00:00Z' };

function stubServer() {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/assistants/tokens' && init?.method === 'POST')
            return Promise.resolve(json({ token: TOKEN, item: { ...mine, id: '8', name: 'Phone' } }));
        if (url === '/api/assistants/tokens') return Promise.resolve(json([mine, grant]));
        if (url.startsWith('/api/assistants/tokens/') && init?.method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }));
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const renderCard = (oauthAvailable = true) =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <I18nProvider>
                <AssistantsCard oauthAvailable={oauthAvailable} />
            </I18nProvider>
        </QueryClientProvider>,
    );

afterEach(() => vi.unstubAllGlobals());

const endpointUrl = `${window.location.origin}/mcp`;

it('shows the endpoint and lists tokens, marking OAuth connections', async () => {
    stubServer();
    renderCard();
    expect(screen.getByDisplayValue(endpointUrl)).toBeInTheDocument();
    expect(await screen.findByText('Laptop')).toBeInTheDocument();
    expect(screen.getByText(/pm_AbCd/)).toBeInTheDocument();
    const row = screen.getByText('Claude').closest('li')!;
    expect(within(row).getByText(appEn.assistants_oauth_badge)).toBeInTheDocument();
});

it('creates a token, shows it once with its snippets, and forgets it on close', async () => {
    const fetchMock = stubServer();
    renderCard();
    fireEvent.click(await screen.findByRole('button', { name: appEn.assistants_create }));
    fireEvent.change(screen.getByLabelText(appEn.assistants_name), { target: { value: 'Phone' } });
    fireEvent.change(screen.getByLabelText(appEn.assistants_expiry), { target: { value: '90' } });
    fireEvent.click(screen.getByRole('button', { name: appEn.assistants_create_confirm }));

    expect(await screen.findByDisplayValue(TOKEN)).toBeInTheDocument();
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!;
    expect(JSON.parse(String(post[1]!.body))).toEqual({ name: 'Phone', expiresInDays: 90 });
    expect(document.body.textContent).toContain(
        `claude mcp add --transport http personalmoney ${endpointUrl} --header "Authorization: Bearer ${TOKEN}"`,
    );
    expect(document.body.textContent).toContain(`Add a custom connector with this URL: ${endpointUrl}`);

    fireEvent.click(screen.getByRole('button', { name: appEn.assistants_done }));
    await waitFor(() => expect(screen.queryByDisplayValue(TOKEN)).toBeNull());
    expect(document.body.textContent).not.toContain(TOKEN);
});

it('says web connectors need the public address when OAuth is unavailable', async () => {
    stubServer();
    renderCard(false);
    fireEvent.click(await screen.findByRole('button', { name: appEn.assistants_create }));
    fireEvent.click(screen.getByRole('button', { name: appEn.assistants_create_confirm }));
    await screen.findByDisplayValue(TOKEN);
    expect(document.body.textContent).toContain(appEn.assistants_web_needs_https);
    expect(document.body.textContent).not.toContain('Add a custom connector');
});

it('asks before revoking and then calls DELETE', async () => {
    const fetchMock = stubServer();
    renderCard();
    const row = (await screen.findByText('Laptop')).closest('li')!;
    fireEvent.click(within(row).getByRole('button', { name: appEn.assistants_revoke }));
    const dialog = await screen.findByRole('dialog');
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
    fireEvent.click(within(dialog).getByRole('button', { name: appEn.assistants_revoke }));
    await waitFor(() =>
        expect(fetchMock.mock.calls.some(([u, init]) => u === '/api/assistants/tokens/7' && init?.method === 'DELETE')).toBe(true),
    );
});
