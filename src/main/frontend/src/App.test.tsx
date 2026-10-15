import React from 'react';
import { render, screen } from '@testing-library/react';
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
    localStorage.clear();
    window.history.pushState({}, '', '/');
});

test('sends a visitor at / to sign-in, since there is no landing page in the app any more', async () => {
    render(<App />);

    expect(await screen.findByText(en['login.title'])).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
});

test('resolves the interface language from the browser', () => {
    // Detection reads navigator.languages first, so a Ukrainian browser should land
    // on Ukrainian without any stored preference.
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['uk-UA', 'en']);

    render(<App />);

    expect(document.documentElement.getAttribute('lang')).toBe('uk');
});
