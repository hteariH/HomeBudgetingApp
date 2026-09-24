import { beforeEach, expect, it } from 'vitest';
import { bootLocation, takeHomeMoneyRedirect } from './boot';

beforeEach(() => {
    window.history.replaceState(null, '', '/');
    takeHomeMoneyRedirect();
});

const here = () => location.pathname + location.search + location.hash;

it('rewrites a known wasm bookmark to its path', () => {
    window.history.replaceState(null, '', '/app/#account/edit?accountId=3');
    bootLocation();
    expect(here()).toBe('/app/accounts/edit?accountId=3');
});

it('drops an unknown fragment', () => {
    window.history.replaceState(null, '', '/app/#more');
    bootLocation();
    expect(here()).toBe('/app');
});

it('captures the HomeMoney redirect once and cleans the address bar', () => {
    window.history.replaceState(null, '', '/app/?homemoney=pending&code=abc');
    bootLocation();
    expect(here()).toBe('/app/import/homemoney');
    expect(takeHomeMoneyRedirect()).toEqual({ kind: 'pending', code: 'abc' });
    expect(takeHomeMoneyRedirect()).toBeNull();
});

it('treats anything but a pending code as denied', () => {
    window.history.replaceState(null, '', '/app/?homemoney=error');
    bootLocation();
    expect(takeHomeMoneyRedirect()).toEqual({ kind: 'denied' });
});

it('leaves pages outside the app alone', () => {
    window.history.replaceState(null, '', '/login#x');
    bootLocation();
    expect(here()).toBe('/login#x');
});
