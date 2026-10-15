import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { ThemeProvider } from '../theme';
import { en } from '../i18n/locales/en';
import { session } from '../app/api/session';
import Join from './Join';

const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const preview = { householdName: 'The Smiths', invitedBy: 'Ann', expiresAt: '2026-10-22T00:00:00Z' };

const renderJoin = () =>
    render(
        <MemoryRouter initialEntries={['/join?token=t1']}>
            <ThemeProvider>
                <I18nProvider>
                    <Routes>
                        <Route path="/join" element={<Join />} />
                        <Route path="/app" element={<div>app</div>} />
                        <Route path="/register" element={<div>register</div>} />
                        <Route path="/login" element={<div>login</div>} />
                    </Routes>
                </I18nProvider>
            </ThemeProvider>
        </MemoryRouter>,
    );

beforeEach(() => localStorage.clear());
afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
});

it('offers a signed-out visitor to create an account or sign in, carrying the invitation along', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(200, preview)));
    renderJoin();
    expect(await screen.findByText(/The Smiths/)).toBeInTheDocument();
    expect(screen.getByText(/Ann/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: en['join.register'] })).toHaveAttribute('href', '/register?invite=t1');
    expect(screen.getByRole('link', { name: en['join.signIn'] }))
        .toHaveAttribute('href', '/login?next=%2Fjoin%3Ftoken%3Dt1');
    expect(screen.queryByRole('button', { name: en['join.accept'] })).toBeNull();
});

it('lets a signed-in visitor accept and lands in the app', async () => {
    session.save({ token: 'a', refreshToken: 'r', email: 'e@x' });
    const fetchMock = vi.fn((url: string, init?: RequestInit) =>
        Promise.resolve(
            url === '/api/invites/t1' ? json(200, preview)
                : url === '/api/invites/t1/accept' && init?.method === 'POST' ? json(200, { householdId: 7, householdName: 'The Smiths' })
                    : json(500, {}),
        ));
    vi.stubGlobal('fetch', fetchMock);
    renderJoin();
    expect(await screen.findByText(en['join.moveWarning'])).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: en['join.accept'] }));
    expect(await screen.findByText('app')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/invites/t1/accept', expect.objectContaining({ method: 'POST' }));
});

it('shows the server message when the owner of a shared household cannot move', async () => {
    session.save({ token: 'a', refreshToken: 'r', email: 'e@x' });
    vi.stubGlobal('fetch', vi.fn((url: string) =>
        Promise.resolve(
            url === '/api/invites/t1' ? json(200, preview)
                : json(400, { message: 'Remove the other members before joining another household' }),
        )));
    renderJoin();
    await userEvent.click(await screen.findByRole('button', { name: en['join.accept'] }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Remove the other members before joining another household');
    expect(screen.queryByText('app')).toBeNull();
    await waitFor(() => expect(screen.getByRole('button', { name: en['join.accept'] })).toBeEnabled());
});

it('shows why an expired invitation does not work, without a join button', async () => {
    session.save({ token: 'a', refreshToken: 'r', email: 'e@x' });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(410, { message: 'This invitation has expired' })));
    renderJoin();
    expect(await screen.findByText('This invitation has expired')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: en['join.accept'] })).toBeNull();
});

it('says so when the invitation is unknown', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(404, { message: 'Not found' })));
    renderJoin();
    expect(await screen.findByText(en['join.notFound'])).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: en['join.accept'] })).toBeNull();
});
