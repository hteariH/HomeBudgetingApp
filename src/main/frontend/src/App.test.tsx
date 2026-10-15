import React from 'react';
import { act, render, screen } from '@testing-library/react';
import App from './App';
import { en } from './i18n/locales/en';

/**
 * The default create-react-app test asserted on a "learn react" link that this app
 * has never contained, so the suite failed on a clean checkout.
 *
 * These cover the routing decision that is easy to break instead: `/` is the app, which
 * sends a visitor with no session on to sign-in.
 */
beforeEach(() => {
    // The sign-in page reads /api/instance; answer as an established, closed instance.
    vi.stubGlobal(
        'fetch',
        vi.fn().mockImplementation(async () =>
            new Response(
                JSON.stringify({
                    name: 'PersonalMoney', version: '1.0.0', apiVersion: 1, setupRequired: false,
                    registrationOpen: false, mailEnabled: false, bankProviders: [],
                }),
                { status: 200, headers: { 'Content-Type': 'application/json' } },
            ),
        ),
    );
    localStorage.clear();
    window.history.pushState({}, '', '/');
});

afterEach(() => vi.unstubAllGlobals());

test('sends a visitor at / to sign-in, since there is no landing page in the app any more', async () => {
    render(<App />);

    expect(await screen.findByText(en['login.title'])).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
});

test('resolves the interface language from the browser', async () => {
    // Detection reads navigator.languages first, so a Ukrainian browser should land
    // on Ukrainian without any stored preference.
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['uk-UA', 'en']);

    render(<App />);
    // Let the instance lookup on the sign-in page settle before asserting.
    await act(async () => {});

    expect(document.documentElement.getAttribute('lang')).toBe('uk');
});
