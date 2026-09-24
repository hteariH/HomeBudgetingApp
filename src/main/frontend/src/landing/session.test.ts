import { expect, it } from 'vitest';
import { nextPath } from './session';

it('returns to the app path the visitor was sent away from', () => {
    expect(nextPath('?next=/app/accounts')).toBe('/app/accounts');
    expect(nextPath('?next=%2Fapp%2Faccounts%2Fedit%3FaccountId%3D3')).toBe('/app/accounts/edit?accountId=3');
});
it('translates a fragment written by the wasm client', () => expect(nextPath('?next=accounts')).toBe('/app/accounts'));
it('never leaves the app', () => {
    expect(nextPath('?next=https://evil.example')).toBe('/app');
    expect(nextPath('?next=//evil.example')).toBe('/app');
    expect(nextPath('?next=/app/../../evil')).toBe('/app');
    expect(nextPath('?next=/apple')).toBe('/app');
    expect(nextPath('')).toBe('/app');
});
