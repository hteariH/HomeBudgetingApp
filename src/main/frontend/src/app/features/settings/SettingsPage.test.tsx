import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import { ThemeProvider } from '../../../theme';
import SettingsPage from './SettingsPage';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

function stubServer(role: 'OWNER' | 'MEMBER', provider: unknown) {
    vi.stubGlobal(
        'fetch',
        vi.fn((url: string) => {
            if (url === '/api/users/me') return Promise.resolve(json({ id: 1, email: 'x@example.com', householdId: 5, householdRole: role }));
            if (url === '/api/household/ai-provider') return Promise.resolve(json(provider));
            if (url === '/api/instance') return Promise.resolve(json({ version: '1.0' }));
            return Promise.resolve(json([]));
        }),
    );
}

const renderPage = () =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter>
                <I18nProvider>
                    <ThemeProvider>
                        <SettingsPage />
                    </ThemeProvider>
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

afterEach(() => vi.unstubAllGlobals());

const instanceModel = { source: 'INSTANCE', type: 'OPENAI_COMPATIBLE', model: 'gpt-4o', hasKey: true, host: 'api.openai.com' };

it('tells a member where photos go, without a form', async () => {
    stubServer('MEMBER', instanceModel);
    renderPage();
    expect(await screen.findByText(/Photos are sent to api\.openai\.com/)).toBeInTheDocument();
    expect(screen.queryByLabelText(appEn.ai_model)).not.toBeInTheDocument();
});

it('shows the owner the form', async () => {
    stubServer('OWNER', instanceModel);
    renderPage();
    // Where photos go now, and - in the form's hint - where they would go with the form's settings.
    expect(await screen.findByText(new RegExp(appEn.ai_source_instance))).toHaveTextContent('Photos are sent to api.openai.com');
    expect(await screen.findByLabelText(appEn.ai_model)).toBeInTheDocument();
    expect(screen.getByText(new RegExp(appEn.ai_household_hint))).toBeInTheDocument();
});

it('tells a member to ask the owner when nothing is set up', async () => {
    stubServer('MEMBER', { source: 'NONE', hasKey: false });
    renderPage();
    expect(await screen.findByText(appEn.ai_none_member)).toBeInTheDocument();
});
