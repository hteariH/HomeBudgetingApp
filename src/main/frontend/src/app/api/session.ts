/**
 * The browser session.
 *
 * These are the keys the Compose web client read, and the ones the sign-in page has always
 * written. Keeping them means everyone signed in before the switch to React is still signed
 * in after it; renaming one sends them to a sign-in screen with no error to explain why.
 */
const ACCESS = 'pm_access_token';
const REFRESH = 'pm_refresh_token';
const EMAIL = 'pm_email';

export interface AuthResponse {
    token: string;
    refreshToken: string;
    email?: string | null;
}

export interface Session {
    access: string;
    refresh: string;
    email: string | null;
}

type Listener = () => void;
const listeners = new Set<Listener>();

const read = (key: string): string | null => {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
};

// useSyncExternalStore compares snapshots by identity, so the same session must come back
// as the same object until it actually changes.
let cached: Session | null = null;
let cachedKey = '';

export const session = {
    get(): Session | null {
        const access = read(ACCESS);
        if (!access) {
            cached = null;
            cachedKey = '';
            return null;
        }
        const refresh = read(REFRESH) ?? '';
        const email = read(EMAIL);
        const key = `${access}|${refresh}|${email}`;
        if (key !== cachedKey) {
            cached = { access, refresh, email };
            cachedKey = key;
        }
        return cached;
    },

    save(res: AuthResponse): void {
        localStorage.setItem(ACCESS, res.token);
        localStorage.setItem(REFRESH, res.refreshToken);
        if (res.email) localStorage.setItem(EMAIL, res.email);
        else localStorage.removeItem(EMAIL);
        listeners.forEach(l => l());
    },

    clear(): void {
        [ACCESS, REFRESH, EMAIL].forEach(k => localStorage.removeItem(k));
        listeners.forEach(l => l());
    },

    subscribe(listener: Listener): () => void {
        listeners.add(listener);
        return () => {
            listeners.delete(listener);
        };
    },
};
