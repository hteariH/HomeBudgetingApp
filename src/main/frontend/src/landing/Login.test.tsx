import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { ThemeProvider } from '../theme';
import { en } from '../i18n/locales/en';
import Login from './Login';

const instance = (over: Partial<Record<string, unknown>> = {}) =>
    new Response(
        JSON.stringify({
            name: 'PersonalMoney', version: '1.0.0', apiVersion: 1, setupRequired: false,
            registrationOpen: false, mailEnabled: false, bankProviders: [], ...over,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
    );

const renderLogin = () =>
    render(
        <MemoryRouter initialEntries={['/login']}>
            <ThemeProvider>
            <I18nProvider>
                <Routes>
                    <Route path="/login" element={<Login />} />
                    <Route path="/setup" element={<div>setup screen</div>} />
                </Routes>
            </I18nProvider>
            </ThemeProvider>
        </MemoryRouter>,
    );

afterEach(() => vi.unstubAllGlobals());

it('hides register and forgot-password on a closed instance without mail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(instance()));
    renderLogin();
    expect(await screen.findByText(en['login.title'])).toBeInTheDocument();
    await Promise.resolve();
    expect(screen.queryByText(en['login.createOne'])).toBeNull();
    expect(screen.queryByText(en['login.forgot'])).toBeNull();
});

it('offers both when the instance is open and can send mail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(instance({ registrationOpen: true, mailEnabled: true })));
    renderLogin();
    expect(await screen.findByText(en['login.createOne'])).toBeInTheDocument();
    expect(screen.getByText(en['login.forgot'])).toBeInTheDocument();
});

it('sends the first visitor of a new instance to setup', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(instance({ setupRequired: true, registrationOpen: true })));
    renderLogin();
    expect(await screen.findByText('setup screen')).toBeInTheDocument();
});
