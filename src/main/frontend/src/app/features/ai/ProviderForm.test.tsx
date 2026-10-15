import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import type { ProviderView } from '../../api/types';
import { ProviderForm, type ProviderFormMode } from './ProviderForm';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const none: ProviderView = { source: 'NONE', hasKey: false };

function stubServer(test: unknown = { ok: true }) {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url.endsWith('/ai-provider/test')) return Promise.resolve(json(test));
        if (url.endsWith('/ai-provider') && init?.method === 'PUT') {
            return Promise.resolve(json({ source: 'HOUSEHOLD', type: 'GEMINI', model: 'm', hasKey: true, host: 'generativelanguage.googleapis.com' }));
        }
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const renderForm = (mode: ProviderFormMode, view: ProviderView = none) =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <I18nProvider>
                <ProviderForm mode={mode} view={view} />
            </I18nProvider>
        </QueryClientProvider>,
    );

const bodyOf = (fetchMock: ReturnType<typeof stubServer>, urlEnd: string) => {
    const call = fetchMock.mock.calls.find(([u]) => u.endsWith(urlEnd));
    return call ? JSON.parse(call[1]?.body as string) : undefined;
};

afterEach(() => vi.unstubAllGlobals());

it('offers a household only the two presets for an OpenAI-compatible model', () => {
    renderForm('household');
    const urls = within(screen.getByLabelText(appEn.ai_base_url)).getAllByRole('option').map(o => (o as HTMLOptionElement).value);
    expect(urls).toEqual(['https://api.openai.com/v1', 'https://openrouter.ai/api/v1']);
});

it('lets the administrator type any address', () => {
    renderForm('admin');
    const field = screen.getByLabelText(appEn.ai_base_url);
    expect(field.tagName).toBe('INPUT');
});

it('shows the reason a test failed', async () => {
    stubServer({ ok: false, reason: 'This model cannot read images' });
    renderForm('household');
    fireEvent.change(screen.getByLabelText(appEn.ai_model), { target: { value: 'gpt-4o' } });
    fireEvent.click(screen.getByRole('button', { name: appEn.ai_test }));
    expect(await screen.findByText('This model cannot read images')).toBeInTheDocument();
});

it('saves without an apiKey when the field is empty, and without a base URL for Gemini', async () => {
    const fetchMock = stubServer();
    renderForm('household', { source: 'HOUSEHOLD', type: 'GEMINI', model: 'old', hasKey: true, host: 'x' });
    expect(screen.getByLabelText(appEn.ai_key)).toHaveAttribute('placeholder', appEn.ai_key_keep);
    fireEvent.change(screen.getByLabelText(appEn.ai_model), { target: { value: 'gemini-2.0-flash' } });
    fireEvent.click(screen.getByRole('button', { name: appEn.common_save }));
    await waitFor(() => expect(bodyOf(fetchMock, '/household/ai-provider')).toBeDefined());
    expect(bodyOf(fetchMock, '/household/ai-provider')).toEqual({ type: 'GEMINI', model: 'gemini-2.0-flash' });
});

it('sends the key when one is typed', async () => {
    const fetchMock = stubServer();
    renderForm('admin');
    fireEvent.change(screen.getByLabelText(appEn.ai_base_url), { target: { value: 'http://localhost:11434/v1' } });
    fireEvent.change(screen.getByLabelText(appEn.ai_model), { target: { value: 'llava' } });
    fireEvent.change(screen.getByLabelText(appEn.ai_key), { target: { value: 'sk-1' } });
    fireEvent.click(screen.getByRole('button', { name: appEn.common_save }));
    await waitFor(() => expect(bodyOf(fetchMock, '/admin/ai-provider')).toBeDefined());
    expect(bodyOf(fetchMock, '/admin/ai-provider')).toEqual({
        type: 'OPENAI_COMPATIBLE',
        baseUrl: 'http://localhost:11434/v1',
        model: 'llava',
        apiKey: 'sk-1',
    });
});
