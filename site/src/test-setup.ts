import '@testing-library/jest-dom/vitest';

// Node 25+ ships its own `localStorage` global, which is unusable unless Node is started
// with --localstorage-file, and it shadows the one jsdom provides. Pointing both storages
// back at jsdom keeps the tests independent of the Node version running them.
const dom = (globalThis as unknown as { jsdom?: { window: Window } }).jsdom;
if (dom) {
    for (const key of ['localStorage', 'sessionStorage'] as const) {
        Object.defineProperty(globalThis, key, { value: dom.window[key], configurable: true, writable: true });
    }
}
