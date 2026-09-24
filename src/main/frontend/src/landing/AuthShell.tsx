import React from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp } from 'lucide-react';
import LanguageSwitcher from '../components/LanguageSwitcher';
import ThemeToggle from '../components/ThemeToggle';

interface Props {
    title: string;
    subtitle?: string;
    /** Status glyph for the outcome screens (confirmed, link expired, and so on). */
    icon?: React.ReactNode;
    centered?: boolean;
    /** Sign-in / sign-up cross-links, rendered under the card. */
    footer?: React.ReactNode;
    /** Register needs the extra width for its two-column name row. */
    wide?: boolean;
    children?: React.ReactNode;
}

/**
 * The page frame shared by every signed-out screen.
 *
 * One component rather than the same wrapper markup copied into each page: these
 * screens are the first thing a new user sees, and having them drift apart visually is
 * both easy and noticeable. The heading and card now live here too - previously each
 * page brought its own, which is exactly how they drifted.
 *
 * Colours come from the theme tokens rather than fixed dark hex values, so these
 * screens follow the light/dark choice like the rest of the app.
 */
const AuthShell: React.FC<Props> = ({ title, subtitle, icon, centered, footer, wide, children }) => (
    <div className="relative isolate flex min-h-screen flex-col bg-base text-ink">
        {/* Decorative only - kept out of the accessibility tree and unclickable. */}
        <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
            <div className="animate-drift absolute -left-40 -top-40 h-[520px] w-[520px] rounded-full bg-brand/20 blur-[130px]" />
            <div className="absolute -right-40 bottom-0 h-[560px] w-[560px] rounded-full bg-info/15 blur-[140px]" />
            <div className="absolute left-1/2 top-1/3 h-[340px] w-[340px] rounded-full bg-violet/10 blur-[120px]" />
        </div>

        <div className="flex items-center justify-between px-5 py-4">
            <Link to="/" className="flex items-center gap-2.5" aria-label="PersonalMoney">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand to-info">
                    <TrendingUp size={16} color="#fff" strokeWidth={2.5} />
                </span>
                <span className="text-sm font-bold">
                    Personal<span className="text-brand-light">Money</span>
                </span>
            </Link>

            <div className="flex items-center gap-2">
                <LanguageSwitcher />
                <ThemeToggle />
            </div>
        </div>

        <div className="flex flex-1 items-center justify-center px-5 pb-16 pt-4">
            <div className={`w-full ${wide ? 'max-w-[480px]' : 'max-w-[420px]'}`}>
                <div className="mb-7 flex flex-col items-center text-center">
                    {icon ? (
                        <span className="mb-5">{icon}</span>
                    ) : (
                        <span className="mb-5 flex h-14 w-14 items-center justify-center rounded-token-lg bg-gradient-to-br from-brand to-info shadow-glow">
                            <TrendingUp size={28} color="#fff" strokeWidth={2.5} />
                        </span>
                    )}
                    <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{title}</h1>
                    {subtitle && <p className="mt-2.5 max-w-sm text-sm leading-relaxed text-ink-soft">{subtitle}</p>}
                </div>

                {children && (
                    <div
                        className="card border-line bg-card/85 shadow-token-lg backdrop-blur-xl"
                        style={{ padding: 28, textAlign: centered ? 'center' : undefined }}
                    >
                        {children}
                    </div>
                )}

                {footer && <div className="mt-7 text-center text-sm">{footer}</div>}
            </div>
        </div>
    </div>
);

export default AuthShell;
