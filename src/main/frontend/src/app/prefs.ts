/**
 * The app's own preferences, in localStorage under the keys the Compose web client used
 * (UserPreferences.kt), so nobody has to redo onboarding or pick their default account
 * again after the switch. Theme and language live with their providers.
 */
const KEYS = {
    onboarded: 'onboarded',
    defaultAccount: 'default_account',
    baseCurrency: 'base_currency',
    homeMoneyAuthState: 'homemoney_auth_state',
} as const;

// Written by the Compose client and meaningless to this one, but they belong to the
// signed-in account, so sign-out clears them all the same.
const LEGACY_ACCOUNT_SCOPED = ['premium', 'premium_expires_at_ms', 'sync_cursor', 'sync_last_ok_ms'];

const get = (key: string): string | null => {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
};

const listeners = new Set<() => void>();

const put = (key: string, value: string | null) => {
    try {
        if (value === null) localStorage.removeItem(key);
        else localStorage.setItem(key, value);
    } catch {
        /* Preference simply does not persist. */
    }
    listeners.forEach(l => l());
};

export const prefs = {
    /** For useSyncExternalStore: fires after any preference is written. */
    subscribe(listener: () => void): () => void {
        listeners.add(listener);
        return () => {
            listeners.delete(listener);
        };
    },

    get onboarded(): boolean {
        return get(KEYS.onboarded) === 'true';
    },
    setOnboarded(value: boolean) {
        put(KEYS.onboarded, String(value));
    },

    get defaultAccountId(): number | null {
        const v = Number(get(KEYS.defaultAccount));
        return Number.isFinite(v) && v > 0 ? v : null;
    },
    setDefaultAccountId(id: number | null) {
        put(KEYS.defaultAccount, id === null ? null : String(id));
    },

    /**
     * The currency totals are shown in. A preference of this browser, as it was in the
     * Compose web client, which never read the server's copy.
     */
    get baseCurrency(): string {
        return get(KEYS.baseCurrency) ?? 'USD';
    },
    setBaseCurrency(code: string) {
        put(KEYS.baseCurrency, code);
    },

    get homeMoneyAuthState(): string | null {
        return get(KEYS.homeMoneyAuthState);
    },
    setHomeMoneyAuthState(state: string | null) {
        put(KEYS.homeMoneyAuthState, state);
    },

    /** What belongs to the account rather than to the browser; cleared on sign-out. */
    clearAccountScoped() {
        [KEYS.defaultAccount, KEYS.baseCurrency, KEYS.homeMoneyAuthState, ...LEGACY_ACCOUNT_SCOPED].forEach(k =>
            put(k, null),
        );
    },
};
