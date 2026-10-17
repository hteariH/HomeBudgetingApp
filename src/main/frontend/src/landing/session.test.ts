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
it('returns to an invitation link', () => {
    expect(nextPath('?next=%2Fjoin%3Ftoken%3Dabc_DEF-1')).toBe('/join?token=abc_DEF-1');
});
it('refuses an invitation path that is not just a token', () => {
    expect(nextPath('?next=%2Fjoin%3Ftoken%3Da%2F..')).toBe('/app');
    expect(nextPath('?next=%2Fjoin%3Ftoken%3Dabc_DEF-1%26x%3D1')).toBe('/app');
    expect(nextPath('?next=%2Fjoin%3Ftoken%3D')).toBe('/app');
});

it('returns to the consent page with everything it needs', () => {
    const consent = '/oauth/consent?response_type=code&client_id=abc&redirect_uri=https%3A%2F%2Fclaude.ai%2Fcb&code_challenge=CH&state=xyz&scope=ledger%3Awrite&resource=https%3A%2F%2Fm.example.com%2Fmcp&code_challenge_method=S256';
    expect(nextPath(`?next=${encodeURIComponent(consent)}`)).toBe(consent);
});
it("keeps a state carrying the characters encodeURIComponent leaves alone: * ! ' ( )", () => {
    const consent = "/oauth/consent?client_id=abc&code_challenge=CH&state=a*b!c'd(e)f";
    expect(nextPath(`?next=${encodeURIComponent(consent)}`)).toBe(consent);
});
it('refuses a consent query carrying a slash, backslash, fragment, at sign or whitespace', () => {
    for (const bad of ['state=a/b', 'state=a\\b', 'state=a#b', 'state=a@b', 'state=a b', 'state=a\tb']) {
        expect(nextPath(`?next=${encodeURIComponent(`/oauth/consent?client_id=abc&${bad}`)}`)).toBe('/app');
    }
});
it('refuses a consent path that is not the consent page', () => {
    expect(nextPath('?next=%2Foauth%2Fconsent%2F..%2F..%2Fevil')).toBe('/app');
    expect(nextPath('?next=%2Foauth%2Ftoken%3Fa%3D1')).toBe('/app');
    expect(nextPath('?next=%2Foauth%2Fconsent%3Fx%3D%3Cscript%3E')).toBe('/app');
});
