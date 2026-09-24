import { expect, it } from 'vitest';
import { isKnownRoute, legacyFragmentToPath } from './routes';

// Ported from RoutesTest.kt, with the patterns the wasm client registered.
const registered = [
    'dashboard',
    'accounts',
    'account/edit?accountId={accountId}',
    'transaction/edit/{transactionId}',
    'quickadd?type={type}&categoryId={categoryId}',
    'import/homemoney',
];

it('a plain section is known', () => {
    expect(isKnownRoute('accounts', registered)).toBe(true);
    expect(isKnownRoute('import/homemoney', registered)).toBe(true);
});

it('arguments do not have to be spelled the way the pattern spells them', () => {
    expect(isKnownRoute('account/edit', registered)).toBe(true);
    expect(isKnownRoute('account/edit?accountId=33', registered)).toBe(true);
    expect(isKnownRoute('quickadd?type=EXPENSE&categoryId=', registered)).toBe(true);
});

it('a path argument matches whatever is in its place', () => {
    expect(isKnownRoute('transaction/edit/34', registered)).toBe(true);
    expect(isKnownRoute('transaction/edit/anything', registered)).toBe(true);
});

it('an address no screen answers to is not known', () => {
    expect(isKnownRoute('nosuchscreen', registered)).toBe(false);
    expect(isKnownRoute('account', registered)).toBe(false);
    expect(isKnownRoute('account/edit/33', registered)).toBe(false);
    expect(isKnownRoute('transaction/edit', registered)).toBe(false);
});

it('an empty address is not known', () => {
    expect(isKnownRoute('', registered)).toBe(false);
    expect(isKnownRoute('/', registered)).toBe(false);
});

it('old wasm fragments map onto the new paths', () => {
    expect(legacyFragmentToPath('accounts')).toBe('/app/accounts');
    expect(legacyFragmentToPath('dashboard')).toBe('/app');
    expect(legacyFragmentToPath('transaction/edit/12')).toBe('/app/transactions/12');
    expect(legacyFragmentToPath('account/edit?accountId=3')).toBe('/app/accounts/edit?accountId=3');
    expect(legacyFragmentToPath('category/edit?type=INCOME')).toBe('/app/categories/edit?type=INCOME');
    expect(legacyFragmentToPath('quickadd?type=TRANSFER')).toBe('/app/quickadd?type=TRANSFER');
    expect(legacyFragmentToPath('more')).toBeNull();
    expect(legacyFragmentToPath('bogus')).toBeNull();
});
