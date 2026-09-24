import { expect, it } from 'vitest';
import { ApiError, apiErrorFrom } from './http';

// Ported from ApiErrorsTest.kt. The bodies are the running server's own, because the point
// is that the server's sentence reaches the user and only its real wire format proves that.
const res = (status: number, body: string) =>
    new Response(body, { status, headers: { 'Content-Type': 'application/json' } });

it("a rejected bank token reaches the user in the bank's own words", async () => {
    const e = await apiErrorFrom(
        res(
            422,
            '{"timestamp":"2026-08-19T21:55:27.9500155","status":422,"error":"Bank Rejected Credentials","message":"Monobank rejected the token"}',
        ),
    );
    expect(e.serverMessage).toBe('Monobank rejected the token');
});

it('a not-found says what was not found', async () => {
    const e = await apiErrorFrom(
        res(404, '{"timestamp":"2026-08-19T21:55:27.5375132","status":404,"error":"Not Found","message":"Bank connection not found: 999999"}'),
    );
    expect(e.serverMessage).toBe('Bank connection not found: 999999');
});

it('an internal error is not repeated to the user', async () => {
    const e = await apiErrorFrom(
        res(500, '{"status":500,"error":"Internal Server Error","message":"could not execute statement"}'),
    );
    expect(e.serverMessage).toBeNull();
});

it("a body that is not the server's error shape yields nothing", async () => {
    expect((await apiErrorFrom(res(400, '<html>gateway error</html>'))).serverMessage).toBeNull();
});

it('a non-HTTP failure has no server message', () => {
    expect(new ApiError(0, null).serverMessage).toBeNull();
});

it("the free plan's refusal is recognised as one, not just read", async () => {
    const body = '{"status":402,"error":"Plan Limit Reached","message":"The free plan covers 5 accounts. Subscribe to add more."}';
    expect((await apiErrorFrom(res(402, body))).isPlanLimit).toBe(true);
    expect((await apiErrorFrom(res(404, body))).isPlanLimit).toBe(false);
});
