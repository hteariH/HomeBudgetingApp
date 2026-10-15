import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { POLL_INTERVAL_MS, useReceiptScan } from './useReceiptScan';

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const read = (status: string, extra: object = {}) => ({ uuid: 'r1', status, totalMismatch: false, items: [], ...extra });
const photo = () => new File(['x'], 'receipt.jpg', { type: 'image/jpeg' });

type Init = { method?: string; body?: FormData };

/** An upload answer, then the given receipt states in order. */
function stub(states: object[]) {
    let next = 0;
    const fetchMock = vi.fn(async (url: string, init?: Init) => {
        if (url === '/api/receipts' && init?.method === 'POST') return json({ uuid: 'r1', status: 'PENDING' }, 202);
        return json(states[Math.min(next++, states.length - 1)]);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}
const gets = (fetchMock: ReturnType<typeof stub>) => fetchMock.mock.calls.filter(([, init]) => !init?.method || init.method === 'GET');

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

it('polls until the receipt is read, with exactly three GETs for PENDING, PENDING, READY', async () => {
    const fetchMock = stub([read('PENDING'), read('PENDING'), read('READY', { merchant: 'Corner Shop', total: '12.50' })]);
    const { result } = renderHook(() => useReceiptScan());

    let draft: unknown;
    await act(async () => {
        const done = result.current.start(photo()).then(r => (draft = r));
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);
        await done;
    });

    expect(draft).toMatchObject({ merchant: 'Corner Shop', total: '12.50' });
    expect(result.current.phase).toBe('ready');
    expect(gets(fetchMock)).toHaveLength(3);
    expect(gets(fetchMock).every(([url]) => url === '/api/receipts/r1')).toBe(true);
});

it('uploads the photo as the multipart part named image', async () => {
    const fetchMock = stub([read('READY')]);
    const { result } = renderHook(() => useReceiptScan());
    await act(async () => {
        await result.current.start(photo());
    });
    const upload = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!;
    expect((upload[1]!.body!.get('image') as File).name).toBe('receipt.jpg');
});

it('exposes the reason when the model could not read it', async () => {
    stub([read('FAILED', { failureReason: 'This model cannot read images' })]);
    const { result } = renderHook(() => useReceiptScan());
    let draft: unknown = 'unset';
    await act(async () => {
        draft = await result.current.start(photo());
    });
    expect(draft).toBeNull();
    expect(result.current.phase).toBe('failed');
    expect(result.current.failureReason).toBe('This model cannot read images');
});

it('exposes a refused upload as an error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'No model is set up' }, 409)));
    const { result } = renderHook(() => useReceiptScan());
    await act(async () => {
        await result.current.start(photo());
    });
    expect(result.current.phase).toBe('failed');
    expect(result.current.error).toMatchObject({ status: 409, serverMessage: 'No model is set up' });
});

it('stops polling when cancelled', async () => {
    const fetchMock = stub([read('PENDING')]);
    const { result } = renderHook(() => useReceiptScan());
    let draft: unknown = 'unset';
    await act(async () => {
        const done = result.current.start(photo()).then(r => (draft = r));
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS / 2);
        result.current.cancel();
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 5);
        await done;
    });
    expect(draft).toBeNull();
    expect(result.current.phase).toBe('idle');
    expect(gets(fetchMock)).toHaveLength(1);
});
