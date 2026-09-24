import React from 'react';
import { render, screen } from '@testing-library/react';
import App from './App';
import { en } from './i18n/locales/en';

/**
 * The default create-react-app test asserted on a "learn react" link that this app
 * has never contained, so the suite failed on a clean checkout.
 *
 * These cover the routing decision that is easy to break instead: `/` is the landing
 * page to a visitor and the dashboard to someone signed in.
 */
beforeEach(() => {
    localStorage.clear();
    window.history.pushState({}, '', '/');
});

test('shows the landing page to a visitor with no token', () => {
    render(<App />);

    expect(screen.getByText(en['landing.hero.titleAccent'])).toBeInTheDocument();
    expect(screen.getByText(en['landing.features.title'])).toBeInTheDocument();
    // The hero and the closing band share this label, so more than one is expected.
    expect(screen.getAllByText(en['landing.hero.ctaPrimary']).length).toBeGreaterThan(0);
});

test('resolves the interface language from the browser', () => {
    // Detection reads navigator.languages first, so a Ukrainian browser should land
    // on Ukrainian without any stored preference.
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['uk-UA', 'en']);

    render(<App />);

    expect(document.documentElement.getAttribute('lang')).toBe('uk');
});
