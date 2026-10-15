import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { ThemeProvider } from '../theme';
import { en } from '../i18n/locales/en';
import Register from './Register';

const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const closedInstance = {
    name: 'PersonalMoney', version: '1.0.0', apiVersion: 1, setupRequired: false,
    registrationOpen: false, mailEnabled: false, bankProviders: [],
};

const renderRegister = (url: string) =>
    render(
        <MemoryRouter initialEntries={[url]}>
            <ThemeProvider>
                <I18nProvider>
                    <Routes>
                        <Route path="/register" element={<Register />} />
                        <Route path="/join" element={<div>join page</div>} />
                        <Route path="/app" element={<div>app</div>} />
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

it('shows the closed notice on a closed instance without an invitation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(200, closedInstance)));
    renderRegister('/register');
    expect(await screen.findByText(en['register.closed'])).toBeInTheDocument();
});

it('registers with the invitation token even when registration is closed, and lands in the app', async () => {
    const fetchMock = vi.fn((url: string) =>
        Promise.resolve(
            url === '/api/instance' ? json(200, closedInstance)
                : url === '/api/auth/register' ? json(200, {})
                    : url === '/api/auth/login' ? json(200, { token: 'a', refreshToken: 'r', email: 'e@x' })
                        : json(500, {}),
        ));
    vi.stubGlobal('fetch', fetchMock);
    renderRegister('/register?invite=tok_en-1');
    expect(await screen.findByText(en['join.registerSubtitle'])).toBeInTheDocument();
    expect(screen.queryByText(en['register.closed'])).toBeNull();

    await userEvent.type(screen.getByLabelText(en['auth.firstName']), 'Bo');
    await userEvent.type(screen.getByLabelText(en['auth.lastName']), 'Li');
    await userEvent.type(screen.getByLabelText(en['auth.email']), 'bo@x.org');
    await userEvent.type(screen.getByLabelText(en['auth.password']), 'password123');
    await userEvent.click(screen.getByRole('button', { name: en['register.submit'] }));

    expect(await screen.findByText('app')).toBeInTheDocument();
    const call = (fetchMock.mock.calls as unknown as [string, RequestInit][]).find(([u]) => u === '/api/auth/register')!;
    expect(JSON.parse(call[1].body as string)).toMatchObject({
        email: 'bo@x.org', inviteToken: 'tok_en-1',
    });
});
