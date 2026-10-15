import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import TransactionEditPage from './TransactionEditPage';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const usd = { id: 1, code: 'USD', name: 'US dollar', symbol: '$', decimals: 2 };
const tx = (extra: object = {}) => ({
    id: 3,
    uuid: 't',
    account: { id: 1, uuid: 'a', name: 'Cash', type: 'CASH', balance: '0', initialBalance: '0', currency: usd },
    category: null,
    amount: '-5.00',
    currencyCode: 'USD',
    accountAmount: '-5.00',
    type: 'EXPENSE',
    date: '2026-03-10',
    occurredAt: '2026-03-10T12:00:00Z',
    description: 'Shop',
    ...extra,
});

const receipt = {
    uuid: 'r1',
    status: 'READY',
    totalMismatch: false,
    items: [{ name: 'Milk', price: '3.00' }],
};

function stubServer(transaction: object) {
    URL.createObjectURL = vi.fn(() => 'blob:photo');
    URL.revokeObjectURL = vi.fn();
    vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string) => {
            if (url === '/api/transactions/3') return json(transaction);
            if (url === '/api/receipts/r1') return json(receipt);
            if (url === '/api/receipts/r1/image') return new Response(new Blob(['jpeg'], { type: 'image/jpeg' }));
            return json([]);
        }),
    );
}

const renderPage = () =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter initialEntries={['/transactions/3']}>
                <I18nProvider>
                    <Routes>
                        <Route path="/transactions/:id" element={<TransactionEditPage />} />
                    </Routes>
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

beforeEach(() => localStorage.clear());
afterEach(() => vi.unstubAllGlobals());

it('shows the receipt items when the transaction was saved from a receipt', async () => {
    stubServer(tx({ receiptUuid: 'r1' }));
    renderPage();
    expect(await screen.findByText('Milk')).toBeInTheDocument();
    expect(screen.getByText(appEn.receipt_title)).toBeInTheDocument();
});

it('shows no receipt card for an ordinary transaction', async () => {
    stubServer(tx());
    renderPage();
    expect(await screen.findByDisplayValue('Shop')).toBeInTheDocument();
    expect(screen.queryByText(appEn.receipt_title)).not.toBeInTheDocument();
});
