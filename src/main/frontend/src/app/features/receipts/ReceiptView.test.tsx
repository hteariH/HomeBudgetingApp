import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import { ReceiptView } from './ReceiptView';

const receipt = {
    uuid: 'r1',
    status: 'READY',
    merchant: 'Corner Shop',
    currency: 'EUR',
    totalMismatch: false,
    items: [
        { name: 'Milk', quantity: '2', price: '3.00' },
        { name: 'Bread', price: '1.5' },
    ],
};

beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:photo');
    URL.revokeObjectURL = vi.fn();
    vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string) =>
            url === '/api/receipts/r1/image'
                ? new Response(new Blob(['jpeg'], { type: 'image/jpeg' }))
                : new Response(JSON.stringify(receipt), { headers: { 'Content-Type': 'application/json' } }),
        ),
    );
});
afterEach(() => vi.unstubAllGlobals());

const renderView = () =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <I18nProvider>
                <ReceiptView uuid="r1" />
            </I18nProvider>
        </QueryClientProvider>,
    );

it('lists the items, opens the photo full size and revokes the object URL', async () => {
    const { unmount } = renderView();
    expect(await screen.findByText(/Milk/)).toBeInTheDocument();
    expect(screen.getByText('Bread')).toBeInTheDocument();

    fireEvent.click(await screen.findByRole('button', { name: appEn.receipt_title }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getAllByRole('img').some(img => img.getAttribute('src') === 'blob:photo')).toBe(true);

    unmount();
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:photo'));
});

it('shows item prices as money in the receipt currency', async () => {
    renderView();
    const price = await screen.findByText(/1\.50/);
    expect(price.textContent).toMatch(/€|EUR/);
});
