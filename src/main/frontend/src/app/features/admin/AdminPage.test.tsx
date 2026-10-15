import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import AdminPage from './AdminPage';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const users = [
    { id: 1, email: 'admin@example.com', firstName: 'Ada', lastName: 'A', role: 'ADMIN', createdAt: '2026-10-01T10:00:00', lastSignInAt: null, householdId: 1 },
    { id: 2, email: 'kid@example.com', firstName: 'Kid', lastName: 'A', role: 'USER', createdAt: '2026-10-02T10:00:00', lastSignInAt: '2026-10-03T10:00:00Z', householdId: 2 },
];

function stubServer(registration = { open: false, lockedByEnvironment: false }) {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url.startsWith('/api/admin/users/2/reset-link')) return Promise.resolve(json({ path: '/reset-password?token=abc', expiresAt: '2026-10-04T10:00:00Z' }));
        if (url.startsWith('/api/admin/users')) return Promise.resolve(json(users));
        if (url.startsWith('/api/admin/registration') && init?.method === 'PUT') return Promise.resolve(json({ open: true, lockedByEnvironment: false }));
        if (url.startsWith('/api/admin/registration')) return Promise.resolve(json(registration));
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const renderPage = () =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter>
                <I18nProvider>
                    <AdminPage />
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

afterEach(() => vi.unstubAllGlobals());

it('lists the users of the instance', async () => {
    stubServer();
    renderPage();
    expect(await screen.findByText('kid@example.com')).toBeInTheDocument();
    expect(screen.getByText('admin@example.com')).toBeInTheDocument();
});

it('issues a reset link the administrator can pass on', async () => {
    stubServer();
    renderPage();
    const buttons = await screen.findAllByRole('button', { name: appEn.admin_reset_link });
    fireEvent.click(buttons[1]);
    expect(await screen.findByDisplayValue(`${window.location.origin}/reset-password?token=abc`)).toBeInTheDocument();
});

it('opens registration', async () => {
    const fetchMock = stubServer();
    renderPage();
    const toggle = await screen.findByRole('switch', { name: new RegExp(appEn.admin_registration_open) });
    await waitFor(() => expect(toggle).toBeEnabled());
    fireEvent.click(toggle);
    await screen.findByText('kid@example.com');
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).startsWith('/api/admin/registration') && init?.method === 'PUT')).toBe(true);
});

const registrationSwitch = () => screen.findByRole('switch', { name: new RegExp(appEn.admin_registration_open) });

it('leaves registration alone when the environment decides it', async () => {
    const fetchMock = stubServer({ open: false, lockedByEnvironment: true });
    renderPage();
    await screen.findByText(appEn.admin_registration_locked);
    const toggle = await registrationSwitch();
    expect(toggle).toBeDisabled();
    fireEvent.click(toggle);
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
});

it('does not offer the switch before it knows the state', async () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
    renderPage();
    expect(await registrationSwitch()).toBeDisabled();
});
