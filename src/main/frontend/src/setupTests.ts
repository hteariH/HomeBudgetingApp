// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';
import { TextDecoder, TextEncoder } from 'util';

/**
 * The jsdom build that ships with react-scripts 5 (jest 27) exposes neither
 * TextEncoder nor TextDecoder, and react-router 7 reaches for TextEncoder as soon as
 * it is imported - which made every test that touches a router fail to even load.
 * Node's own implementations are the same thing the browser provides.
 */
if (typeof global.TextEncoder === 'undefined') {
    global.TextEncoder = TextEncoder as unknown as typeof global.TextEncoder;
}
if (typeof global.TextDecoder === 'undefined') {
    global.TextDecoder = TextDecoder as unknown as typeof global.TextDecoder;
}
