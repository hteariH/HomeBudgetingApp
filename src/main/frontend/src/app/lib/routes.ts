export const APP = '/app';

/** Every screen of the app, as a path. The one place a screen's address is spelled. */
export const AppRoutes = {
    dashboard: APP,
    transactions: `${APP}/transactions`,
    transactionEdit: (id: number | string) => `${APP}/transactions/${id}`,
    quickAdd: (type = 'EXPENSE', categoryId?: number) =>
        `${APP}/quickadd?type=${type}${categoryId != null ? `&categoryId=${categoryId}` : ''}`,
    accounts: `${APP}/accounts`,
    accountEdit: (id?: number) => `${APP}/accounts/edit${id != null ? `?accountId=${id}` : ''}`,
    categories: `${APP}/categories`,
    categoryEdit: (id?: number, type = 'EXPENSE') =>
        `${APP}/categories/edit?type=${type}${id != null ? `&categoryId=${id}` : ''}`,
    subscriptions: `${APP}/subscriptions`,
    subscriptionEdit: (id?: number) => `${APP}/subscriptions/edit${id != null ? `?subscriptionId=${id}` : ''}`,
    currencies: `${APP}/currencies`,
    currencyEdit: (code?: string) => `${APP}/currencies/edit${code ? `?code=${encodeURIComponent(code)}` : ''}`,
    analytics: `${APP}/analytics`,
    banks: `${APP}/banks`,
    homeMoney: `${APP}/import/homemoney`,
    household: `${APP}/household`,
    admin: `${APP}/admin`,
    settings: `${APP}/settings`,
    onboarding: `${APP}/onboarding`,
} as const;

/**
 * Routes.kt's isKnown: whether an address has the shape of a route. A segment in braces
 * stands for any one segment, and the query is ignored on both sides because arguments
 * there are optional.
 */
export function isKnownRoute(route: string, patterns: string[]): boolean {
    const trim = (s: string) => s.split('?')[0].replace(/^\/+|\/+$/g, '');
    const path = trim(route);
    if (!path) return false;
    const segs = path.split('/');
    return patterns.some(p => {
        const expected = trim(p).split('/');
        return expected.length === segs.length && expected.every((e, i) => e.startsWith('{') || e === segs[i]);
    });
}

/**
 * The wasm client's routes as its address-bar fragments spelled them, and where each one
 * lives now. More is absent on purpose: the browser never registered it.
 */
const LEGACY: [pattern: string, to: (segments: string[], query: string) => string][] = [
    ['dashboard', () => AppRoutes.dashboard],
    ['quickadd', (_, q) => `${APP}/quickadd${q}`],
    ['transactions', () => AppRoutes.transactions],
    ['transaction/edit/{id}', s => AppRoutes.transactionEdit(s[2])],
    ['accounts', () => AppRoutes.accounts],
    ['account/edit', (_, q) => `${APP}/accounts/edit${q}`],
    ['categories', () => AppRoutes.categories],
    ['category/edit', (_, q) => `${APP}/categories/edit${q}`],
    ['subscriptions', () => AppRoutes.subscriptions],
    ['subscription/edit', (_, q) => `${APP}/subscriptions/edit${q}`],
    ['settings', () => AppRoutes.settings],
    ['currencies', () => AppRoutes.currencies],
    ['currency/edit', (_, q) => `${APP}/currencies/edit${q}`],
    ['banks', () => AppRoutes.banks],
    ['import/homemoney', () => AppRoutes.homeMoney],
    ['analytics', () => AppRoutes.analytics],
    ['onboarding', () => AppRoutes.onboarding],
];

/** A wasm-era fragment (`accounts`, `transaction/edit/12`) as today's path, or null. */
export function legacyFragmentToPath(fragment: string): string | null {
    for (const [pattern, to] of LEGACY) {
        if (isKnownRoute(fragment, [pattern])) {
            const [path, query] = fragment.split('?');
            return to(path.replace(/^\/+/, '').split('/'), query ? `?${query}` : '');
        }
    }
    return null;
}
