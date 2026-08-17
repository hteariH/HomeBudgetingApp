import React from 'react';
import { Link } from 'react-router-dom';
import {
    ArrowLeftRight, ArrowRight, Check, ChevronDown, FileSpreadsheet,
    Languages, LineChart, Receipt, ShieldCheck, TrendingUp, Wallet,
} from 'lucide-react';
import { useT } from '../i18n';
import LanguageSwitcher from '../components/LanguageSwitcher';
import ThemeToggle from '../components/ThemeToggle';

/**
 * The public front page - what a first-time visitor lands on at `/`.
 *
 * Deliberately self-contained: no API calls, no auth state, no charting library.
 * The product preview is a hand-built mock rather than a screenshot so it follows
 * the live theme tokens and stays correct in both light and dark mode, and so it
 * never goes stale against a UI screenshot nobody remembers to retake.
 */

/**
 * Bank connections are intentionally absent: the integration is experimental and
 * currently supports a single Ukrainian bank, which is not something to put on a
 * front page as though it were a finished feature.
 */
const FEATURE_ICONS = [Wallet, Receipt, ArrowLeftRight, FileSpreadsheet, LineChart, Languages] as const;

const FEATURE_KEYS = ['accounts', 'transactions', 'transfers', 'csv', 'insights', 'interface'] as const;

/** Bars for the mock chart. Fixed values - it illustrates the layout, not real data. */
const MOCK_BARS = [
    { income: 62, expense: 44 },
    { income: 71, expense: 52 },
    { income: 58, expense: 61 },
    { income: 80, expense: 49 },
    { income: 74, expense: 57 },
    { income: 92, expense: 63 },
];

const MOCK_CATEGORIES = [
    { key: 'groceries', share: 34, color: 'var(--accent)' },
    { key: 'rent', share: 26, color: 'var(--cyan)' },
    { key: 'transport', share: 18, color: 'var(--green)' },
    { key: 'other', share: 22, color: 'var(--yellow)' },
] as const;

const Landing: React.FC = () => {
    const t = useT();

    return (
        <div className="min-h-screen bg-base text-ink overflow-x-hidden [scroll-behavior:smooth]">

            {/* ─── Nav ─────────────────────────────────────────────────── */}
            <header className="sticky top-0 z-50 border-b border-line bg-surface/80 backdrop-blur-xl">
                <div className="mx-auto flex h-16 max-w-content items-center justify-between gap-4 px-5">
                    <Link to="/" className="flex items-center gap-2.5" aria-label="PersonalMoney">
                        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-brand to-info">
                            <TrendingUp size={18} color="#fff" strokeWidth={2.5} />
                        </span>
                        <span className="text-[15px] font-bold leading-tight">
                            Personal<span className="text-brand-light">Money</span>
                        </span>
                    </Link>

                    <nav className="hidden items-center gap-7 text-sm font-medium text-ink-soft md:flex">
                        <a href="#features" className="transition-colors hover:text-ink">{t('landing.nav.features')}</a>
                        <a href="#how" className="transition-colors hover:text-ink">{t('landing.nav.how')}</a>
                        <a href="#faq" className="transition-colors hover:text-ink">{t('landing.nav.faq')}</a>
                    </nav>

                    <div className="flex items-center gap-2">
                        <LanguageSwitcher />
                        <ThemeToggle />
                        <Link to="/login" className="btn btn-ghost btn-sm hidden sm:inline-flex">
                            {t('landing.nav.signIn')}
                        </Link>
                        <Link to="/register" className="btn btn-primary btn-sm">
                            {t('landing.nav.getStarted')}
                        </Link>
                    </div>
                </div>
            </header>

            {/* ─── Hero ────────────────────────────────────────────────── */}
            <section className="relative isolate overflow-hidden px-5 pb-20 pt-16 sm:pt-24">
                {/* Ambient glow. aria-hidden and pointer-events-none: decorative only. */}
                <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
                    <div className="animate-drift absolute -left-32 -top-40 h-[520px] w-[520px] rounded-full bg-brand/20 blur-[130px]" />
                    <div className="absolute -right-40 top-10 h-[460px] w-[460px] rounded-full bg-info/15 blur-[130px]" />
                    <div className="absolute left-1/3 top-1/2 h-[380px] w-[380px] rounded-full bg-violet/10 blur-[120px]" />
                </div>

                <div className="mx-auto max-w-content">
                    <div className="mx-auto max-w-3xl text-center">
                        <span className="inline-flex items-center gap-2 rounded-full border border-line bg-card/70 px-3.5 py-1.5 text-xs font-semibold text-ink-soft backdrop-blur">
                            <span className="h-1.5 w-1.5 rounded-full bg-ok" />
                            {t('landing.hero.badge')}
                        </span>

                        <h1 className="mt-6 text-4xl font-extrabold leading-[1.1] tracking-tight sm:text-5xl lg:text-6xl">
                            {t('landing.hero.titleLead')}{' '}
                            <span className="bg-gradient-to-r from-brand-light via-violet to-info bg-clip-text text-transparent">
                                {t('landing.hero.titleAccent')}
                            </span>
                        </h1>

                        <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-ink-soft sm:text-lg">
                            {t('landing.hero.subtitle')}
                        </p>

                        <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
                            <Link to="/register" className="btn btn-primary btn-lg w-full shadow-glow sm:w-auto">
                                {t('landing.hero.ctaPrimary')}
                                <ArrowRight size={17} />
                            </Link>
                            <Link to="/login" className="btn btn-ghost btn-lg w-full sm:w-auto">
                                {t('landing.hero.ctaSecondary')}
                            </Link>
                        </div>

                        <p className="mt-5 text-[13px] text-ink-dim">{t('landing.hero.note')}</p>
                    </div>

                    {/* ─── Product preview ─────────────────────────────── */}
                    <div className="animate-fade-up mx-auto mt-16 max-w-5xl">
                        <div className="rounded-token-xl border border-line bg-card/80 p-3 shadow-token-lg backdrop-blur sm:p-4">
                            {/* Window chrome, so the mock reads as an application screen. */}
                            <div className="mb-3 flex items-center gap-1.5 px-1.5">
                                <span className="h-2.5 w-2.5 rounded-full bg-bad/70" />
                                <span className="h-2.5 w-2.5 rounded-full bg-warn/70" />
                                <span className="h-2.5 w-2.5 rounded-full bg-ok/70" />
                            </div>

                            <div className="rounded-token-lg border border-line bg-base p-4 sm:p-6">
                                {/* Stat row */}
                                <div className="grid gap-3 sm:grid-cols-3">
                                    <MockStat
                                        label={t('landing.preview.netWorth')}
                                        value="$48,120.75"
                                        accent="text-ink"
                                        icon={<Wallet size={16} className="text-brand-light" />}
                                    />
                                    <MockStat
                                        label={t('landing.preview.income')}
                                        value="+$6,420.00"
                                        accent="text-ok"
                                        icon={<TrendingUp size={16} className="text-ok" />}
                                    />
                                    <MockStat
                                        label={t('landing.preview.expenses')}
                                        value="−$4,058.30"
                                        accent="text-bad"
                                        icon={<Receipt size={16} className="text-bad" />}
                                    />
                                </div>

                                <div className="mt-4 grid gap-4 lg:grid-cols-5">
                                    {/* Bar chart */}
                                    <div className="rounded-token border border-line bg-card p-4 lg:col-span-3">
                                        <div className="mb-4 flex items-center justify-between">
                                            <p className="text-[13px] font-bold">{t('landing.preview.thisMonth')}</p>
                                            <div className="flex items-center gap-3 text-[11px] text-ink-dim">
                                                <span className="flex items-center gap-1.5">
                                                    <span className="h-2 w-2 rounded-full bg-ok" />
                                                    {t('landing.preview.income')}
                                                </span>
                                                <span className="flex items-center gap-1.5">
                                                    <span className="h-2 w-2 rounded-full bg-bad" />
                                                    {t('landing.preview.expenses')}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="flex h-32 items-end justify-between gap-2.5 sm:gap-4">
                                            {MOCK_BARS.map((bar, i) => (
                                                <div key={i} className="flex h-full flex-1 items-end gap-1">
                                                    <div
                                                        className="w-full rounded-t bg-ok/80"
                                                        style={{ height: `${bar.income}%` }}
                                                    />
                                                    <div
                                                        className="w-full rounded-t bg-bad/70"
                                                        style={{ height: `${bar.expense}%` }}
                                                    />
                                                </div>
                                            ))}
                                        </div>
                                    </div>

                                    {/* Category split */}
                                    <div className="rounded-token border border-line bg-card p-4 lg:col-span-2">
                                        <p className="mb-4 text-[13px] font-bold">{t('dashboard.pieTitle')}</p>
                                        <div className="flex flex-col gap-3">
                                            {MOCK_CATEGORIES.map(cat => (
                                                <div key={cat.key}>
                                                    <div className="mb-1.5 flex items-center justify-between text-[12px]">
                                                        <span className="text-ink-soft">{t(`landing.preview.cat.${cat.key}`)}</span>
                                                        <span className="font-semibold">{cat.share}%</span>
                                                    </div>
                                                    <div className="h-1.5 overflow-hidden rounded-full bg-raised">
                                                        <div
                                                            className="h-full rounded-full"
                                                            style={{ width: `${cat.share}%`, background: cat.color }}
                                                        />
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* ─── Features ────────────────────────────────────────────── */}
            <section id="features" className="scroll-mt-20 border-t border-line px-5 py-20">
                <div className="mx-auto max-w-content">
                    <SectionHeading title={t('landing.features.title')} subtitle={t('landing.features.subtitle')} />

                    <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                        {FEATURE_KEYS.map((key, i) => {
                            const Icon = FEATURE_ICONS[i];
                            return (
                                <article
                                    key={key}
                                    className="group rounded-token-lg border border-line bg-card p-6 transition-all duration-200 hover:-translate-y-1 hover:border-line-strong hover:shadow-token"
                                >
                                    <span className="mb-4 inline-flex h-11 w-11 items-center justify-center rounded-token bg-brand/10 transition-colors group-hover:bg-brand/20">
                                        <Icon size={20} className="text-brand-light" />
                                    </span>
                                    <h3 className="mb-2 text-[15px] font-bold">{t(`landing.features.${key}.title`)}</h3>
                                    <p className="text-sm leading-relaxed text-ink-soft">
                                        {t(`landing.features.${key}.body`)}
                                    </p>
                                </article>
                            );
                        })}
                    </div>
                </div>
            </section>

            {/* ─── How it works ────────────────────────────────────────── */}
            <section id="how" className="scroll-mt-20 border-t border-line bg-surface/40 px-5 py-20">
                <div className="mx-auto max-w-content">
                    <SectionHeading title={t('landing.how.title')} />

                    <ol className="mt-12 grid gap-6 md:grid-cols-3">
                        {([1, 2, 3] as const).map(step => (
                            <li key={step} className="relative rounded-token-lg border border-line bg-card p-6">
                                <span className="mb-4 flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-brand to-info text-sm font-bold text-white">
                                    {step}
                                </span>
                                <h3 className="mb-2 text-[15px] font-bold">{t(`landing.how.step${step}.title`)}</h3>
                                <p className="text-sm leading-relaxed text-ink-soft">{t(`landing.how.step${step}.body`)}</p>
                            </li>
                        ))}
                    </ol>
                </div>
            </section>

            {/* ─── Trust ───────────────────────────────────────────────── */}
            <section className="border-t border-line px-5 py-20">
                <div className="mx-auto grid max-w-content items-center gap-12 lg:grid-cols-2">
                    <div>
                        <span className="mb-5 inline-flex h-12 w-12 items-center justify-center rounded-token bg-ok/10">
                            <ShieldCheck size={22} className="text-ok" />
                        </span>
                        <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{t('landing.trust.title')}</h2>
                        <p className="mt-4 text-base leading-relaxed text-ink-soft">{t('landing.trust.body')}</p>
                    </div>

                    <ul className="flex flex-col gap-4">
                        {(['point1', 'point2', 'point3'] as const).map(point => (
                            <li
                                key={point}
                                className="flex gap-3.5 rounded-token-lg border border-line bg-card p-5"
                            >
                                <span className="mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-ok/15">
                                    <Check size={12} className="text-ok" strokeWidth={3} />
                                </span>
                                <p className="text-sm leading-relaxed text-ink-soft">{t(`landing.trust.${point}`)}</p>
                            </li>
                        ))}
                    </ul>
                </div>
            </section>

            {/* ─── FAQ ─────────────────────────────────────────────────── */}
            <section id="faq" className="scroll-mt-20 border-t border-line bg-surface/40 px-5 py-20">
                <div className="mx-auto max-w-3xl">
                    <SectionHeading title={t('landing.faq.title')} />

                    <div className="mt-10 flex flex-col gap-3">
                        {([1, 2, 3, 4] as const).map(n => (
                            /* <details> keeps the accordion working without any state. */
                            <details
                                key={n}
                                className="group rounded-token-lg border border-line bg-card px-5 open:border-line-strong"
                            >
                                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 py-4 text-[15px] font-semibold [&::-webkit-details-marker]:hidden">
                                    {t(`landing.faq.q${n}`)}
                                    <ChevronDown
                                        size={17}
                                        className="flex-shrink-0 text-ink-dim transition-transform duration-200 group-open:rotate-180"
                                    />
                                </summary>
                                <p className="pb-5 text-sm leading-relaxed text-ink-soft">{t(`landing.faq.a${n}`)}</p>
                            </details>
                        ))}
                    </div>
                </div>
            </section>

            {/* ─── Closing CTA ─────────────────────────────────────────── */}
            <section className="border-t border-line px-5 py-20">
                <div className="relative isolate mx-auto max-w-content overflow-hidden rounded-token-xl border border-line bg-card px-6 py-14 text-center">
                    <div
                        aria-hidden
                        className="pointer-events-none absolute inset-0 -z-10 bg-gradient-to-br from-brand/15 via-transparent to-info/15"
                    />
                    <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{t('landing.cta.title')}</h2>
                    <p className="mx-auto mt-4 max-w-xl text-base text-ink-soft">{t('landing.cta.body')}</p>
                    <Link to="/register" className="btn btn-primary btn-lg mt-8 shadow-glow">
                        {t('landing.cta.button')}
                        <ArrowRight size={17} />
                    </Link>
                </div>
            </section>

            {/* ─── Footer ──────────────────────────────────────────────── */}
            <footer className="border-t border-line px-5 py-12">
                <div className="mx-auto flex max-w-content flex-col gap-10 sm:flex-row sm:justify-between">
                    <div className="max-w-xs">
                        <div className="flex items-center gap-2.5">
                            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand to-info">
                                <TrendingUp size={16} color="#fff" strokeWidth={2.5} />
                            </span>
                            <span className="text-sm font-bold">
                                Personal<span className="text-brand-light">Money</span>
                            </span>
                        </div>
                        <p className="mt-3 text-[13px] text-ink-dim">{t('landing.footer.tagline')}</p>
                    </div>

                    <div className="flex gap-14">
                        <nav className="flex flex-col gap-2.5">
                            <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-dim">
                                {t('landing.footer.product')}
                            </p>
                            <a href="#features" className="text-sm text-ink-soft transition-colors hover:text-ink">
                                {t('landing.nav.features')}
                            </a>
                            <a href="#how" className="text-sm text-ink-soft transition-colors hover:text-ink">
                                {t('landing.nav.how')}
                            </a>
                            <a href="#faq" className="text-sm text-ink-soft transition-colors hover:text-ink">
                                {t('landing.nav.faq')}
                            </a>
                        </nav>

                        <nav className="flex flex-col gap-2.5">
                            <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-dim">
                                {t('landing.footer.account')}
                            </p>
                            <Link to="/login" className="text-sm text-ink-soft transition-colors hover:text-ink">
                                {t('landing.nav.signIn')}
                            </Link>
                            <Link to="/register" className="text-sm text-ink-soft transition-colors hover:text-ink">
                                {t('landing.nav.getStarted')}
                            </Link>
                        </nav>
                    </div>
                </div>

                <div className="mx-auto mt-10 max-w-content border-t border-line pt-6 text-[13px] text-ink-dim">
                    {t('landing.footer.rights', { year: new Date().getFullYear() })}
                </div>
            </footer>
        </div>
    );
};

const SectionHeading: React.FC<{ title: string; subtitle?: string }> = ({ title, subtitle }) => (
    <div className="mx-auto max-w-2xl text-center">
        <h2 className="text-2xl font-extrabold tracking-tight sm:text-[32px] sm:leading-tight">{title}</h2>
        {subtitle && <p className="mt-3 text-base text-ink-soft">{subtitle}</p>}
    </div>
);

const MockStat: React.FC<{
    label: string;
    value: string;
    accent: string;
    icon: React.ReactNode;
}> = ({ label, value, accent, icon }) => (
    <div className="rounded-token border border-line bg-card p-4">
        <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
                <p className="text-[10px] font-semibold uppercase tracking-wider text-ink-soft">{label}</p>
                <p className={`mt-1.5 truncate text-lg font-bold ${accent}`}>{value}</p>
            </div>
            <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-raised">{icon}</span>
        </div>
    </div>
);

export default Landing;
