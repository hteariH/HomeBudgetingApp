import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from './http';
import { session } from './session';

const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

type Init = { headers: Record<string, string> };

beforeEach(() => {
    localStorage.clear();
    session.save({ token: 'old', refreshToken: 'r1', email: 'a@b.c' });
});

afterEach(() => vi.unstubAllGlobals());

it('sends the bearer token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, []));
    vi.stubGlobal('fetch', fetchMock);
    await api('/accounts');
    expect((fetchMock.mock.calls[0][1] as Init).headers.Authorization).toBe('Bearer old');
});

it('spends one refresh token for several concurrent 401s', async () => {
    let refreshes = 0;
    vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string, init: Init) => {
            if (url.endsWith('/auth/refresh')) {
                refreshes++;
                return json(200, { token: 'new', refreshToken: 'r2', email: 'a@b.c' });
            }
            return init.headers.Authorization === 'Bearer new' ? json(200, { ok: true }) : json(401, {});
        }),
    );
    const results = await Promise.all([api('/a'), api('/b'), api('/c')]);
    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }]);
    expect(refreshes).toBe(1);
    expect(session.get()?.refresh).toBe('r2');
});

it('clears the session when the refresh is refused', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(401, {})));
    await expect(api('/a')).rejects.toMatchObject({ status: 401 });
    expect(session.get()).toBeNull();
});

it('never sends the bearer to the endpoints that mint tokens', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, {}));
    vi.stubGlobal('fetch', fetchMock);
    await api('/auth/login', { method: 'POST', json: {} });
    expect((fetchMock.mock.calls[0][1] as Init).headers.Authorization).toBeUndefined();
});

it('builds the query string and leaves out empty values', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, {}));
    vi.stubGlobal('fetch', fetchMock);
    await api('/transactions', { query: { page: 0, accountId: undefined, type: 'EXPENSE', dateFrom: '' } });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/transactions?page=0&type=EXPENSE');
});

it('an empty 200 or a 204 resolves to undefined', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(api('/x', { method: 'DELETE' })).resolves.toBeUndefined();
});

it('renews on a 403 too, because that is what the server answers an expired token with', async () => {
    let refreshes = 0;
    vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string, init: Init) => {
            if (url.endsWith('/auth/refresh')) {
                refreshes++;
                return json(200, { token: 'new', refreshToken: 'r2', email: 'a@b.c' });
            }
            return init.headers.Authorization === 'Bearer new' ? json(200, [1]) : json(403, {});
        }),
    );
    await expect(api('/accounts')).resolves.toEqual([1]);
    expect(refreshes).toBe(1);
});

it('a genuine 403 still fails after one refresh', async () => {
    vi.stubGlobal(
        'fetch',
        vi.fn(async (url: string) =>
            url.endsWith('/auth/refresh') ? json(200, { token: 'new', refreshToken: 'r2', email: 'a@b.c' }) : json(403, {}),
        ),
    );
    await expect(api('/currencies', { method: 'POST', json: {} })).rejects.toMatchObject({ status: 403 });
    expect(session.get()?.access).toBe('new');
});
