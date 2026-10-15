import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../../i18n';
import { appEn } from '../../../i18n/locales/app.en';
import { GIVE_UP_AFTER_MS, POLL_INTERVAL_MS, useReceiptScan } from './useReceiptScan';

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

const renderScan = () => renderHook(() => useReceiptScan(), { wrapper: I18nProvider });

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

it('polls until the receipt is read, with exactly three GETs for PENDING, PENDING, READY', async () => {
    const fetchMock = stub([read('PENDING'), read('PENDING'), read('READY', { merchant: 'Corner Shop', total: '12.50' })]);
    const { result } = renderScan();

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
    const { result } = renderScan();
    await act(async () => {
        await result.current.start(photo());
    });
    const upload = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!;
    expect((upload[1]!.body!.get('image') as File).name).toBe('receipt.jpg');
});

it('exposes the reason when the model could not read it', async () => {
    stub([read('FAILED', { failureReason: 'This model cannot read images' })]);
    const { result } = renderScan();
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
    const { result } = renderScan();
    await act(async () => {
        await result.current.start(photo());
    });
    expect(result.current.phase).toBe('failed');
    expect(result.current.error).toMatchObject({ status: 409, serverMessage: 'No model is set up' });
});

it('stops polling when cancelled', async () => {
    const fetchMock = stub([read('PENDING')]);
    const { result } = renderScan();
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

/** An upload answer, then GET answers from [answers] in order: a state, or an Error to throw as a dropped connection. */
function stubPolls(answers: (object | Error)[]) {
    let next = 0;
    const fetchMock = vi.fn(async (url: string, init?: Init) => {
        if (url === '/api/receipts' && init?.method === 'POST') return json({ uuid: 'r1', status: 'PENDING' }, 202);
        const answer = answers[Math.min(next++, answers.length - 1)];
        if (answer instanceof Error) throw answer;
        return json(answer);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

it('rides out two dropped polls in a row', async () => {
    const dropped = new TypeError('Failed to fetch');
    const fetchMock = stubPolls([dropped, dropped, read('PENDING'), dropped, dropped, read('READY', { merchant: 'Shop' })]);
    const { result } = renderScan();
    let draft: unknown;
    await act(async () => {
        const done = result.current.start(photo()).then(r => (draft = r));
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 6);
        await done;
    });
    expect(draft).toMatchObject({ merchant: 'Shop' });
    expect(result.current.phase).toBe('ready');
    expect(gets(fetchMock)).toHaveLength(6);
});

it('fails on the third dropped poll in a row', async () => {
    const fetchMock = stubPolls([read('PENDING'), new TypeError('Failed to fetch')]);
    const { result } = renderScan();
    let draft: unknown = 'unset';
    await act(async () => {
        const done = result.current.start(photo()).then(r => (draft = r));
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 10);
        await done;
    });
    expect(draft).toBeNull();
    expect(result.current.phase).toBe('failed');
    expect(result.current.error).toBeInstanceOf(TypeError);
    expect(gets(fetchMock)).toHaveLength(4);
});

it('does not ride out a receipt the server says is gone', async () => {
    const fetchMock = vi.fn(async (url: string, init?: Init) => {
        if (url === '/api/receipts' && init?.method === 'POST') return json({ uuid: 'r1', status: 'PENDING' }, 202);
        return json({ message: 'Receipt not found' }, 404);
    });
    vi.stubGlobal('fetch', fetchMock);
    const { result } = renderScan();
    await act(async () => {
        await result.current.start(photo());
    });
    expect(result.current.phase).toBe('failed');
    expect(gets(fetchMock)).toHaveLength(1);
});

it('gives up after six minutes without an answer', async () => {
    const fetchMock = stub([read('PENDING')]);
    const { result } = renderScan();
    let draft: unknown = 'unset';
    await act(async () => {
        const done = result.current.start(photo()).then(r => (draft = r));
        await vi.advanceTimersByTimeAsync(GIVE_UP_AFTER_MS + POLL_INTERVAL_MS);
        await done;
    });
    expect(draft).toBeNull();
    expect(result.current.phase).toBe('failed');
    expect(result.current.failureReason).toBe(appEn.receipt_too_slow);
    const polls = gets(fetchMock).length;
    await act(async () => {
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 5);
    });
    expect(gets(fetchMock)).toHaveLength(polls);
});
