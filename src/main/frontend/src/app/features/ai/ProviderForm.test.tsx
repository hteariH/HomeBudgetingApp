import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import type { ProviderView } from '../../api/types';
import { qk } from '../../api/queryClient';
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

const renderForm = (mode: ProviderFormMode, view: ProviderView = none, client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
    render(
        <QueryClientProvider client={client}>
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

it('tells the owner where photos will go before anything is saved', () => {
    renderForm('household');
    const hint = () => screen.getByText(new RegExp(appEn.ai_household_hint));
    expect(hint()).toHaveTextContent('Photos are sent to api.openai.com');
    fireEvent.change(screen.getByLabelText(appEn.ai_base_url), { target: { value: 'https://openrouter.ai/api/v1' } });
    expect(hint()).toHaveTextContent('Photos are sent to openrouter.ai');
    fireEvent.change(screen.getByLabelText(appEn.ai_provider), { target: { value: 'ANTHROPIC' } });
    expect(hint()).toHaveTextContent('Photos are sent to api.anthropic.com');
    fireEvent.change(screen.getByLabelText(appEn.ai_provider), { target: { value: 'GEMINI' } });
    expect(hint()).toHaveTextContent('Photos are sent to generativelanguage.googleapis.com');
});

it('does not show the household hint to the administrator', () => {
    renderForm('admin');
    expect(screen.queryByText(new RegExp(appEn.ai_household_hint))).not.toBeInTheDocument();
});

it('asks before removing, then shows the fields for what is left', async () => {
    const mine: ProviderView = { source: 'HOUSEHOLD', type: 'GEMINI', model: 'gemini-old', hasKey: true, host: 'generativelanguage.googleapis.com' };
    const instance: ProviderView = { source: 'INSTANCE', type: 'ANTHROPIC', model: 'claude-x', hasKey: true, host: 'api.anthropic.com' };
    let removed = false;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/household/ai-provider' && init?.method === 'DELETE') {
            removed = true;
            return Promise.resolve(new Response(null, { status: 204 }));
        }
        if (url === '/api/household/ai-provider') return Promise.resolve(json(removed ? instance : mine));
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(qk.aiProvider, mine);
    renderForm('household', mine, client);
    fireEvent.change(screen.getByLabelText(appEn.ai_key), { target: { value: 'sk-typed' } });
    const deletes = () => fetchMock.mock.calls.filter(([, i]) => i?.method === 'DELETE').length;

    fireEvent.click(screen.getByRole('button', { name: appEn.ai_remove }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(appEn.ai_remove_confirm)).toBeInTheDocument();
    expect(deletes()).toBe(0);

    fireEvent.click(within(dialog).getByRole('button', { name: appEn.ai_remove }));
    await waitFor(() => expect(screen.getByLabelText(appEn.ai_provider)).toHaveValue('OPENAI_COMPATIBLE'));
    expect(deletes()).toBe(1);
    // The instance's model is not this household's to edit: the fields start empty.
    expect(screen.getByLabelText(appEn.ai_model)).toHaveValue('');
    expect(screen.getByLabelText(appEn.ai_key)).toHaveValue('');
    expect(screen.getByLabelText(appEn.ai_base_url)).toHaveValue('https://api.openai.com/v1');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
});

it('cancelling the question removes nothing', async () => {
    const fetchMock = stubServer();
    renderForm('household', { source: 'HOUSEHOLD', type: 'GEMINI', model: 'm', hasKey: true, host: 'x' });
    fireEvent.click(screen.getByRole('button', { name: appEn.ai_remove }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: appEn.action_cancel }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(fetchMock.mock.calls.some(([, i]) => i?.method === 'DELETE')).toBe(false);
    expect(screen.getByLabelText(appEn.ai_model)).toHaveValue('m');
});

it('an administrator save or removal refreshes what households and the instance report', async () => {
    vi.stubGlobal(
        'fetch',
        vi.fn((_url: string, init?: RequestInit) => {
            if (init?.method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }));
            if (init?.method === 'PUT') return Promise.resolve(json({ source: 'INSTANCE', type: 'GEMINI', model: 'g', hasKey: false, host: 'h' }));
            return Promise.resolve(json({ source: 'NONE', hasKey: false }));
        }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    renderForm('admin', { source: 'INSTANCE', type: 'GEMINI', model: 'g', hasKey: false, host: 'h' }, client);
    const invalidated = () => invalidate.mock.calls.map(([filters]) => JSON.stringify(filters?.queryKey));

    fireEvent.click(screen.getByRole('button', { name: appEn.common_save }));
    await waitFor(() => expect(invalidated()).toEqual(expect.arrayContaining([JSON.stringify(qk.aiProvider), JSON.stringify(qk.instance)])));

    invalidate.mockClear();
    fireEvent.click(screen.getByRole('button', { name: appEn.ai_remove }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: appEn.ai_remove }));
    await waitFor(() => expect(invalidated()).toEqual(expect.arrayContaining([JSON.stringify(qk.aiProvider), JSON.stringify(qk.instance)])));
    // And the administrator's own view is read again, to reset the fields from.
    await waitFor(() => expect(screen.getByLabelText(appEn.ai_model)).toHaveValue(''));
});
