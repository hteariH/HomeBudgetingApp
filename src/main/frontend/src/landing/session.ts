/**
 * The browser session, in the keys the app reads.
 *
 * The React side no longer runs the application - it is the marketing page and the pages a
 * visitor reaches before there is an account, and the app itself lives at {@link APP_PATH},
 * served from the same origin as a Compose/Wasm bundle. So signing in here is a handover:
 * this side does the POST, writes the tokens, and hands the tab over.
 *
 * The names below are the contract for that handover. They are exactly what
 * `client/.../core/auth/BrowserTokenStore.kt` reads out of localStorage; renaming one on
 * either side sends a signed-in visitor to a sign-in screen with no error anywhere to
 * explain it. The old React-only `token` / `user` pair is gone, and with it the reason a
 * session used to die after a day: React never stored a refresh token, while the app
 * renews one under a mutex.
 */
const ACCESS_TOKEN = 'pm_access_token';
const REFRESH_TOKEN = 'pm_refresh_token';
const EMAIL = 'pm_email';

/** Where the application lives. A full page load - it is a different document. */
export const APP_PATH = '/app/';

export interface AuthResponse {
    token: string;
    refreshToken: string;
    email: string;
}

export function saveSession(res: AuthResponse): void {
    localStorage.setItem(ACCESS_TOKEN, res.token);
    localStorage.setItem(REFRESH_TOKEN, res.refreshToken);
    localStorage.setItem(EMAIL, res.email);
}

/** Whether this browser already has an account signed in - the landing page asks. */
export function hasSession(): boolean {
    return !!localStorage.getItem(ACCESS_TOKEN);
}

/**
 * Where a visitor was heading before they were asked to sign in.
 *
 * The app puts the screen it was showing into `?next=` when it sends a signed-out browser
 * here (see `navigation/SignIn.wasmJs.kt`), because the fragment does not survive the trip
 * on its own - and without it a bookmark on `/app/#accounts` whose session had expired came
 * back to the overview, every time.
 *
 * Only a fragment of our own page is ever accepted. Nothing here can leave the origin -
 * whatever the value, it ends up after a `#` on `/app/` - but a value carrying its own `#`
 * or a control character would produce an address that means something other than it looks
 * like, so anything outside the shape a route has is dropped rather than repaired.
 */
const ROUTE = /^[A-Za-z0-9/?&=_.,:-]{1,200}$/;

function nextFragment(search: string): string {
    const next = new URLSearchParams(search).get('next');
    return next && ROUTE.test(next) ? next : '';
}

/**
 * Hands the tab to the application, on the screen the visitor was trying to reach.
 *
 * `replace`, not `assign`: this runs on a sign-in that has just succeeded, so the form
 * behind it is done with. Leaving it on the stack made Back from the app land on a sign-in
 * page belonging to a session that already exists.
 */
export function enterApp(): void {
    const fragment = nextFragment(window.location.search);
    window.location.replace(fragment ? `${APP_PATH}#${fragment}` : APP_PATH);
}
