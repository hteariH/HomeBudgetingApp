import { AppRoutes, legacyFragmentToPath } from './lib/routes';

export type HomeMoneyRedirect = { kind: 'pending'; code: string } | { kind: 'denied' };

let pending: HomeMoneyRedirect | null = null;

/**
 * Runs once, before the router reads the address.
 *
 * Two kinds of URL arrive at /app from outside: bookmarks from the Compose client, whose
 * screens were fragments (/app/#accounts), and HomeMoney's return trip after consent
 * (/app/?homemoney=pending&code=…). Both are rewritten with replaceState, so Back does not
 * land on them again - and the one-time code leaves the address bar before anything can
 * read it twice.
 */
export function bootLocation(): void {
    if (!location.pathname.startsWith('/app')) return;

    const params = new URLSearchParams(location.search);
    const outcome = params.get('homemoney');
    if (outcome !== null) {
        const code = params.get('code');
        pending = outcome === 'pending' && code ? { kind: 'pending', code } : { kind: 'denied' };
        history.replaceState(null, '', AppRoutes.homeMoney);
        return;
    }

    const fragment = location.hash.slice(1);
    if (fragment) history.replaceState(null, '', legacyFragmentToPath(fragment) ?? AppRoutes.dashboard);
}

/** The HomeMoney outcome, once. The import screen takes it and finishes the exchange. */
export function takeHomeMoneyRedirect(): HomeMoneyRedirect | null {
    const value = pending;
    pending = null;
    return value;
}
