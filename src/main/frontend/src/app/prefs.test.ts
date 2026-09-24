import { beforeEach, expect, it } from 'vitest';
import { readThemeMode } from '../theme';
import { prefs } from './prefs';

beforeEach(() => localStorage.clear());

it('reads what the Compose client stored', () => {
    localStorage.setItem('onboarded', 'true');
    localStorage.setItem('default_account', '42');
    expect(prefs.onboarded).toBe(true);
    expect(prefs.defaultAccountId).toBe(42);
});

it('reads the Compose theme values as well as its own', () => {
    localStorage.setItem('theme', 'LIGHT');
    expect(readThemeMode()).toBe('light');
    localStorage.setItem('theme', 'DARK');
    expect(readThemeMode()).toBe('dark');
    localStorage.setItem('theme', 'SYSTEM');
    expect(readThemeMode()).toBe('system');
    localStorage.removeItem('theme');
    expect(readThemeMode()).toBe('system');
});

it('sign-out clears what belongs to the account and keeps what belongs to the browser', () => {
    localStorage.setItem('onboarded', 'true');
    localStorage.setItem('theme', 'light');
    localStorage.setItem('lang', 'uk');
    localStorage.setItem('premium', 'true');
    prefs.setDefaultAccountId(3);
    prefs.setBaseCurrency('EUR');
    prefs.setHomeMoneyAuthState('s');
    prefs.clearAccountScoped();
    expect(prefs.defaultAccountId).toBeNull();
    expect(prefs.baseCurrency).toBe('USD');
    expect(prefs.homeMoneyAuthState).toBeNull();
    expect(localStorage.getItem('premium')).toBeNull();
    expect(localStorage.getItem('onboarded')).toBe('true');
    expect(localStorage.getItem('theme')).toBe('light');
    expect(localStorage.getItem('lang')).toBe('uk');
});
