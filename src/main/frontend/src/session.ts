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

/** Hands the tab to the application. */
export function enterApp(): void {
    window.location.assign(APP_PATH);
}
