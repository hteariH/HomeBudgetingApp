import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import type { RestTransaction } from '../../api/types';
import TransactionsPage from './TransactionsPage';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const usd = { id: 1, code: 'USD', name: 'US dollar', symbol: '$', decimals: 2 };
const account = (id: number, name: string) => ({ id, uuid: '', name, type: 'CHECKING', balance: '0', initialBalance: '0', currency: usd });

const row = (id: number, description: string, createdByUserId: number | null, extra: Partial<RestTransaction> = {}) =>
    ({
        id,
        uuid: '',
        account: account(1, 'Cash'),
        amount: '-5.00',
        currencyCode: 'USD',
        accountAmount: '-5.00',
        type: 'EXPENSE',
        date: '2026-03-10',
        occurredAt: '2026-03-10T12:00:00Z',
        description,
        createdByUserId,
        ...extra,
    }) as RestTransaction;

const ada = { userId: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', role: 'OWNER', joinedAt: '2026-10-01T10:00:00Z' };
const kid = { userId: 2, firstName: 'Kid', lastName: 'Byron', email: 'kid@example.com', role: 'MEMBER', joinedAt: '2026-10-02T10:00:00Z' };

function stubServer(members: object[], rows: RestTransaction[]) {
    const fetchMock = vi.fn((url: string) => {
        if (url.startsWith('/api/transactions')) return Promise.resolve(json({ content: rows, page: 0, size: 500, totalElements: rows.length, totalPages: 1 }));
        if (url === '/api/household') return Promise.resolve(json({ id: 5, name: 'Home', baseCurrency: 'USD', members }));
        if (url.startsWith('/api/accounts')) return Promise.resolve(json([account(1, 'Cash')]));
        return Promise.resolve(json({}, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const renderPage = (entry = '/app/transactions') =>
    render(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <MemoryRouter initialEntries={[entry]}>
                <I18nProvider>
                    <TransactionsPage />
                </I18nProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );

beforeEach(() => {
    // jsdom has no matchMedia; a narrow window keeps the quick-entry panel out of the page.
    window.matchMedia = ((query: string) =>
        ({ matches: false, media: query, addEventListener: () => {}, removeEventListener: () => {} }) as unknown as MediaQueryList);
});

afterEach(() => vi.unstubAllGlobals());

it('shows who wrote each row and offers a Who filter when the household has several members', async () => {
    const fetchMock = stubServer([ada, kid], [
        row(1, 'Groceries', 1),
        row(2, 'Comics', 2),
        row(3, 'Old import', null),
    ]);
    renderPage();
    expect(await screen.findByText('Groceries')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTitle('Ada Lovelace')).toHaveTextContent('AL'));
    expect(screen.getByTitle('Kid Byron')).toHaveTextContent('KB');
    expect(screen.getAllByTitle(/Lovelace|Byron/)).toHaveLength(2);

    fireEvent.change(screen.getByLabelText(appEn.filter_who), { target: { value: '2' } });
    await waitFor(() => expect(fetchMock.mock.calls.some(([u]) => String(u).includes('createdBy=2'))).toBe(true));
    expect(screen.getByRole('button', { name: appEn.action_clear_filters })).toBeInTheDocument();
});

it('reads the Who filter from the address', async () => {
    const fetchMock = stubServer([ada, kid], [row(2, 'Comics', 2)]);
    renderPage('/app/transactions?who=2');
    expect(await screen.findByText('Comics')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('createdBy=2'))).toBe(true);
    expect(screen.getByLabelText(appEn.filter_who)).toHaveValue('2');
});

it('shows a transfer under the author of its debit leg', async () => {
    const debit = row(10, '', 1, { type: 'TRANSFER', accountAmount: '-5.00', transferGroupId: 'g', account: account(1, 'Cash') });
    const credit = row(11, '', 2, { type: 'TRANSFER', accountAmount: '5.00', transferGroupId: 'g', account: account(2, 'Bank') });
    stubServer([ada, kid], [credit, debit]);
    renderPage();
    expect(await screen.findByText('Cash → Bank')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTitle('Ada Lovelace')).toBeInTheDocument());
    expect(screen.queryByTitle('Kid Byron')).toBeNull();
});

it('shows neither initials nor the filter for a household of one', async () => {
    stubServer([ada], [row(1, 'Groceries', 1)]);
    renderPage();
    expect(await screen.findByText('Groceries')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText(appEn.filter_account)).toBeInTheDocument());
    expect(screen.queryByLabelText(appEn.filter_who)).toBeNull();
    expect(screen.queryByTitle('Ada Lovelace')).toBeNull();
});
