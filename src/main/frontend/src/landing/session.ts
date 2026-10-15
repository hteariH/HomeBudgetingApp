import { session, type AuthResponse } from '../app/api/session';
import { AppRoutes, legacyFragmentToPath } from '../app/lib/routes';

/**
 * The sign-in pages' view of the session. The session itself lives in app/api/session.ts,
 * which the application reads too - signing in here is no longer a handover to another
 * document, just a navigation within this one.
 */
export type { AuthResponse };

/** Where the application lives. */
export const APP_PATH = AppRoutes.dashboard;

export function saveSession(res: AuthResponse): void {
    session.save(res);
}

// The shape a path of this app can have. Anything else - another origin, a value carrying
// its own `#`, a control character - is dropped rather than repaired.
const APP_PATH_SHAPE = /^\/app(\/[A-Za-z0-9/?&=_.,:%-]*)?$/;

/**
 * Where to go after signing in: the screen the visitor was sent away from.
 *
 * The app writes `?next=/app/...` when it sends a signed-out browser to /login. The wasm
 * client wrote a bare fragment (`accounts`) instead, and a tab left open from before the
 * switch may still carry one, so those are translated. Nothing here can leave the origin.
 */
export function nextPath(search: string): string {
    const next = new URLSearchParams(search).get('next');
    if (!next || next.length > 200) return AppRoutes.dashboard;
    if (APP_PATH_SHAPE.test(next) && !next.includes('..')) return next;
    return legacyFragmentToPath(next) ?? AppRoutes.dashboard;
}
