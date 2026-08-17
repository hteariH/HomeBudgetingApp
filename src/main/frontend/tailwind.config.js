/**
 * react-scripts 5 turns Tailwind on purely by the presence of this file
 * (see `useTailwind` in react-scripts/config/webpack.config.js), which is why
 * Tailwind must stay on v3 - v4 moved to the separate @tailwindcss/postcss plugin
 * that CRA knows nothing about.
 *
 * Colours are declared as bare RGB channels in index.css rather than hex so that
 * `<alpha-value>` works here. Without that, opacity modifiers such as
 * `bg-card/90` silently produce no colour at all.
 *
 * Names deliberately avoid `accent` and `muted`: index.css still ships
 * `.text-accent` / `.text-muted`, and a same-named Tailwind colour would generate
 * a competing rule in the utilities layer and win.
 */
const token = (name) => `rgb(var(${name}) / <alpha-value>)`;

/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ['./src/**/*.{js,jsx,ts,tsx}'],

    // The app toggles themes with a data-theme attribute on <html>, not a class.
    darkMode: ['selector', '[data-theme="dark"]'],

    theme: {
        extend: {
            colors: {
                base: token('--bg-base-rgb'),
                surface: token('--bg-surface-rgb'),
                card: token('--bg-card-rgb'),
                raised: token('--bg-hover-rgb'),

                line: {
                    DEFAULT: token('--border-rgb'),
                    strong: token('--border-light-rgb'),
                },

                ink: {
                    DEFAULT: token('--text-primary-rgb'),
                    soft: token('--text-secondary-rgb'),
                    dim: token('--text-muted-rgb'),
                },

                brand: {
                    DEFAULT: token('--accent-rgb'),
                    light: token('--accent-light-rgb'),
                    dark: token('--accent-dark-rgb'),
                },

                ok: token('--green-rgb'),
                bad: token('--red-rgb'),
                warn: token('--yellow-rgb'),
                info: token('--cyan-rgb'),
                violet: token('--purple-rgb'),
            },

            fontFamily: {
                sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
            },

            borderRadius: {
                token: 'var(--radius)',
                'token-lg': 'var(--radius-lg)',
                'token-xl': 'var(--radius-xl)',
            },

            boxShadow: {
                token: 'var(--shadow)',
                'token-lg': 'var(--shadow-lg)',
                glow: '0 0 40px var(--accent-glow)',
            },

            maxWidth: {
                content: '1180px',
            },

            keyframes: {
                'fade-up': {
                    from: { opacity: '0', transform: 'translateY(14px)' },
                    to: { opacity: '1', transform: 'translateY(0)' },
                },
                drift: {
                    '0%, 100%': { transform: 'translate3d(0, 0, 0) scale(1)' },
                    '50%': { transform: 'translate3d(0, -24px, 0) scale(1.06)' },
                },
            },

            animation: {
                'fade-up': 'fade-up 0.5s cubic-bezier(0.16, 1, 0.3, 1) both',
                drift: 'drift 14s ease-in-out infinite',
            },
        },
    },

    plugins: [],
};
