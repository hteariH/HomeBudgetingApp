import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import { EntryForm } from './EntryForm';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const currency = (code: string) => ({ id: 1, code, name: code, symbol: code === 'USD' ? '$' : code, decimals: 2 });
const account = { id: 1, uuid: 'a', name: 'Cash', type: 'CASH', balance: '0', initialBalance: '0', currency: currency('USD') };
const groceries = { id: 7, uuid: 'c', name: 'Groceries', type: 'EXPENSE', icon: 'shopping_cart', color: '#10B981' };

const read = (extra: object = {}) => ({
    uuid: 'r1',
    status: 'READY',
    merchant: 'Corner Shop',
    date: '2026-03-04',
    currency: 'USD',
    total: '12.50',
    totalMismatch: false,
    suggestedCategoryId: 7,
    items: [{ name: 'Milk', quantity: '2', price: '3.00' }],
    ...extra,
});

type Init = { method?: string; body?: unknown };

function stubServer(source: string, receipt: object = read()) {
    const fetchMock = vi.fn(async (url: string, init?: Init) => {
        if (url === '/api/accounts') return json([account]);
        if (url.startsWith('/api/categories')) return json([groceries]);
        if (url === '/api/household/ai-provider') return json({ source, hasKey: source !== 'NONE' });
        if (url === '/api/receipts' && init?.method === 'POST') return json({ uuid: 'r1', status: 'PENDING' }, 202);
        if (url === '/api/receipts/r1') return json(receipt);
        if (url === '/api/transactions' && init?.method === 'POST') return json({ id: 9 }, 201);
        if (url.startsWith('/api/transactions')) return json({ content: [] });
        return json([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const renderForm = () =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter>
                <I18nProvider>
                    <EntryForm variant="panel" />
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

const pickPhoto = async () => {
    const input = await screen.findByTestId('receipt-file');
    fireEvent.change(input, { target: { files: [new File(['x'], 'r.jpg', { type: 'image/jpeg' })] } });
};

afterEach(() => vi.unstubAllGlobals());

it('fills the fields from a read receipt and saves it with the entry', async () => {
    const fetchMock = stubServer('HOUSEHOLD');
    renderForm();
    await pickPhoto();

    await waitFor(() => expect(screen.getByDisplayValue('12.50')).toBeInTheDocument());
    expect(screen.getByDisplayValue('2026-03-04')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Corner Shop')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveValue('7');
    expect(screen.getByText(appEn.receipt_items)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: appEn.action_save }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([u, i]) => u === '/api/transactions' && i?.method === 'POST')).toBe(true));
    const post = fetchMock.mock.calls.find(([u, i]) => u === '/api/transactions' && i?.method === 'POST')!;
    expect(JSON.parse(post[1]!.body as string)).toMatchObject({
        amount: '12.50',
        categoryId: 7,
        description: 'Corner Shop',
        receiptUuid: 'r1',
    });
});

it('leaves the amount alone and says so when the receipt is in another currency', async () => {
    stubServer('HOUSEHOLD', read({ currency: 'EUR' }));
    renderForm();
    await pickPhoto();

    expect(await screen.findByText(/EUR, but this account is in USD/)).toBeInTheDocument();
    expect(screen.queryByDisplayValue('12.50')).not.toBeInTheDocument();
    expect(screen.getByDisplayValue('Corner Shop')).toBeInTheDocument();
});

it('warns when the items do not add up to the total', async () => {
    stubServer('HOUSEHOLD', read({ totalMismatch: true }));
    renderForm();
    await pickPhoto();
    expect(await screen.findByText(appEn.receipt_mismatch)).toBeInTheDocument();
});

it('shows why a receipt could not be read', async () => {
    stubServer('HOUSEHOLD', read({ status: 'FAILED', failureReason: 'This model cannot read images' }));
    renderForm();
    await pickPhoto();
    expect(await screen.findByText(/This model cannot read images/)).toBeInTheDocument();
});

it('offers no scan button when no model is set up', async () => {
    const fetchMock = stubServer('NONE');
    renderForm();
    await waitFor(() => expect(fetchMock.mock.calls.some(([u]) => u === '/api/household/ai-provider')).toBe(true));
    await screen.findByRole('combobox');
    expect(screen.queryByText(appEn.quickadd_scan_receipt)).not.toBeInTheDocument();
    expect(screen.queryByTestId('receipt-file')).not.toBeInTheDocument();
});
