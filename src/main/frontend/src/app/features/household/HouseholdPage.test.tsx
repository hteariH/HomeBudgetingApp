import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import HouseholdPage from './HouseholdPage';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const members = [
    { userId: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', role: 'OWNER', joinedAt: '2026-10-01T10:00:00Z' },
    { userId: 2, firstName: 'Kid', lastName: 'Lovelace', email: 'kid@example.com', role: 'MEMBER', joinedAt: '2026-10-02T10:00:00Z' },
];
const household = { id: 5, name: 'Lovelaces', baseCurrency: { id: 1, code: 'USD', name: 'US Dollar', symbol: '$' }, members };
const invites = [{ id: 7, createdAt: '2026-10-10T10:00:00Z', expiresAt: '2026-10-17T10:00:00Z', createdBy: 'Ada Lovelace' }];

function stubServer(role: 'OWNER' | 'MEMBER', roster = members) {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        const method = init?.method ?? 'GET';
        if (url === '/api/users/me') return Promise.resolve(json({ id: role === 'OWNER' ? 1 : 2, email: 'x@example.com', householdId: 5, householdName: 'Lovelaces', householdRole: role }));
        if (url === '/api/household' && method === 'GET') return Promise.resolve(json({ ...household, members: roster }));
        if (url === '/api/household' && method === 'PUT') return Promise.resolve(json({ ...household, name: 'Babbages' }));
        if (url === '/api/household/invites' && method === 'POST') return Promise.resolve(json({ id: 8, path: '/join?token=abc', expiresAt: '2026-10-22T10:00:00Z', qrRows: ['101', '010', '101'] }));
        if (url === '/api/household/invites' && method === 'GET') return Promise.resolve(json(invites));
        if (url === '/api/household/invites/7' && method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }));
        if (url === '/api/household/members/2' && method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }));
        if (url === '/api/household/owner' && method === 'POST') return Promise.resolve(new Response(null, { status: 204 }));
        if (url === '/api/household/leave' && method === 'POST') return Promise.resolve(new Response(null, { status: 204 }));
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const called = (fetchMock: ReturnType<typeof stubServer>, url: string, method: string) =>
    fetchMock.mock.calls.find(([u, init]) => u === url && (init?.method ?? 'GET') === method);

const renderPage = () =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter initialEntries={['/app/household']}>
                <I18nProvider>
                    <Routes>
                        <Route path="/app/household" element={<HouseholdPage />} />
                        <Route path="/app" element={<div>overview</div>} />
                    </Routes>
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

afterEach(() => vi.unstubAllGlobals());

it('lets the owner invite someone and revoke an open invitation', async () => {
    const fetchMock = stubServer('OWNER');
    renderPage();
    expect(await screen.findByText('ada@example.com')).toBeInTheDocument();
    expect(screen.getByText('kid@example.com')).toBeInTheDocument();

    fireEvent.click(await screen.findByRole('button', { name: appEn.household_invite }));
    expect(await screen.findByDisplayValue(`${window.location.origin}/join?token=abc`)).toHaveAttribute('readonly');
    expect(screen.getByText(appEn.household_invite_ready)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'QR' })).toBeInTheDocument();
    const call = called(fetchMock, '/api/household/invites', 'POST')!;
    expect(JSON.parse(String(call[1]?.body))).toEqual({ origin: window.location.origin });

    fireEvent.click(await screen.findByRole('button', { name: appEn.household_revoke }));
    await waitFor(() => expect(called(fetchMock, '/api/household/invites/7', 'DELETE')).toBeTruthy());
});

it('lets the owner rename the household', async () => {
    const fetchMock = stubServer('OWNER');
    renderPage();
    const input = await screen.findByDisplayValue('Lovelaces');
    fireEvent.change(input, { target: { value: 'Babbages' } });
    fireEvent.click(screen.getByRole('button', { name: appEn.household_rename }));
    await waitFor(() => expect(called(fetchMock, '/api/household', 'PUT')).toBeTruthy());
    expect(JSON.parse(String(called(fetchMock, '/api/household', 'PUT')![1]?.body))).toEqual({ name: 'Babbages' });
});

it('asks before removing a member or handing over ownership', async () => {
    const fetchMock = stubServer('OWNER');
    renderPage();
    await screen.findByText('kid@example.com');

    fireEvent.click(screen.getByRole('button', { name: appEn.household_remove }));
    expect(called(fetchMock, '/api/household/members/2', 'DELETE')).toBeUndefined();
    const removeDialog = await screen.findByRole('dialog');
    expect(within(removeDialog).getByText(appEn.household_remove_confirm.replace('{{name}}', 'Kid Lovelace'))).toBeInTheDocument();
    fireEvent.click(within(removeDialog).getByRole('button', { name: appEn.household_remove }));
    await waitFor(() => expect(called(fetchMock, '/api/household/members/2', 'DELETE')).toBeTruthy());

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    fireEvent.click(screen.getByRole('button', { name: appEn.household_make_owner }));
    const ownerDialog = await screen.findByRole('dialog');
    fireEvent.click(within(ownerDialog).getByRole('button', { name: appEn.household_make_owner }));
    await waitFor(() => expect(called(fetchMock, '/api/household/owner', 'POST')).toBeTruthy());
    expect(JSON.parse(String(called(fetchMock, '/api/household/owner', 'POST')![1]?.body))).toEqual({ userId: 2 });
});

it('offers a member nothing but leaving, behind a confirmation', async () => {
    const fetchMock = stubServer('MEMBER');
    renderPage();
    await screen.findByText('kid@example.com');
    await screen.findByRole('button', { name: appEn.household_leave });
    expect(screen.queryByRole('button', { name: appEn.household_invite })).toBeNull();
    expect(screen.queryByRole('button', { name: appEn.household_remove })).toBeNull();
    expect(screen.queryByRole('button', { name: appEn.household_make_owner })).toBeNull();
    expect(called(fetchMock, '/api/household/invites', 'GET')).toBeUndefined();

    fireEvent.click(screen.getByRole('button', { name: appEn.household_leave }));
    expect(called(fetchMock, '/api/household/leave', 'POST')).toBeUndefined();
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: appEn.household_leave }));
    await waitFor(() => expect(called(fetchMock, '/api/household/leave', 'POST')).toBeTruthy());
    expect(await screen.findByText('overview')).toBeInTheDocument();
});

it('tells an owner who is not alone to hand ownership over before leaving', async () => {
    stubServer('OWNER');
    renderPage();
    expect(await screen.findByText(appEn.household_leave_owner_hint)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: appEn.household_leave })).toBeNull();
});

it('has nothing to hand over or leave for an owner on their own', async () => {
    stubServer('OWNER', members.slice(0, 1));
    renderPage();
    await screen.findByText('ada@example.com');
    await screen.findByRole('button', { name: appEn.household_invite });
    expect(screen.queryByText(appEn.household_leave_owner_hint)).toBeNull();
    expect(screen.queryByRole('button', { name: appEn.household_leave })).toBeNull();
});
