import { useState, type ReactNode } from 'react';
import {
    ArrowLeftRight, ArrowRight, Download, FileSpreadsheet, LineChart, Moon,
    Plug, Receipt, Sun, TrendingUp, Users, Wallet,
} from 'lucide-react';
import { I18nProvider, LanguageSwitcher, useT, type Key } from './i18n';
import {
    BACKUP_STEPS, COMPOSE_STEPS, PLATFORMS, RELEASES, REPO, RESET_PASSWORD, UPDATE_STEPS,
} from './install';

/**
 * The project's page: what PersonalMoney is and how to put it on a NAS or home server.
 *
 * Static and self-contained - no API calls, no account, nothing to sign in to. The product
 * preview is a hand-built mock rather than a screenshot so it follows the theme tokens in
 * both light and dark and never goes stale against a UI nobody remembers to re-shoot.
 */

const FEATURES = [
    { n: 1, Icon: Wallet, soon: false },
    { n: 2, Icon: ArrowLeftRight, soon: false },
    { n: 3, Icon: FileSpreadsheet, soon: false },
    { n: 4, Icon: LineChart, soon: false },
    { n: 5, Icon: Users, soon: true },
    { n: 6, Icon: Receipt, soon: false },
    { n: 7, Icon: Plug, soon: false },
] as const;

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

export default function App() {
    return (
        <I18nProvider>
            <Page />
        </I18nProvider>
    );
}

function Page() {
    const t = useT();

    return (
        <div className="min-h-screen overflow-x-hidden bg-base text-ink [scroll-behavior:smooth]">
            <header className="sticky top-0 z-50 border-b border-line bg-surface/80 backdrop-blur-xl">
                <div className="mx-auto flex h-16 max-w-content items-center justify-between gap-4 px-5">
                    <a href="#hero" className="flex items-center gap-2.5" aria-label="PersonalMoney">
                        <Logo size={36} />
                        <span className="text-[15px] font-bold leading-tight">
                            Personal<span className="text-brand-light">Money</span>
                        </span>
                    </a>

                    <nav className="hidden items-center gap-7 text-sm font-medium text-ink-soft md:flex">
                        <a href="#features" className="transition-colors hover:text-ink">{t('nav.features')}</a>
                        <a href="#install" className="transition-colors hover:text-ink">{t('nav.install')}</a>
                        <a href="#android" className="transition-colors hover:text-ink">{t('nav.android')}</a>
                    </nav>

                    <div className="flex items-center gap-2">
                        <LanguageSwitcher />
                        <ThemeToggle />
                    </div>
                </div>
            </header>

            <Hero />
            <Features />
            <Install />
            <Operate />
            <Android />
            <Ai />
            <Footer />
        </div>
    );
}

function Logo({ size }: { size: number }) {
    return (
        <span
            className="flex items-center justify-center rounded-lg bg-gradient-to-br from-brand to-info"
            style={{ width: size, height: size }}
        >
            <TrendingUp size={size / 2} color="#fff" strokeWidth={2.5} />
        </span>
    );
}

/** The inline script in index.html sets data-theme before first paint; this keeps it in step. */
function ThemeToggle() {
    const t = useT();
    const [theme, setTheme] = useState<'dark' | 'light'>(() =>
        document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark',
    );
    const label = theme === 'dark' ? t('header.toLight') : t('header.toDark');

    const toggle = () => {
        const next = theme === 'dark' ? 'light' : 'dark';
        setTheme(next);
        document.documentElement.setAttribute('data-theme', next);
        try {
            localStorage.setItem('theme', next);
        } catch {
            /* The choice simply does not persist. */
        }
    };

    return (
        <button type="button" className="btn btn-ghost btn-icon" onClick={toggle} title={label} aria-label={label}>
            {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
        </button>
    );
}

function Hero() {
    const t = useT();

    return (
        <section id="hero" className="relative isolate overflow-hidden px-5 pb-20 pt-16 sm:pt-24">
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
                        {t('hero.badge')}
                    </span>

                    <h1 className="mt-6 text-4xl font-extrabold leading-[1.1] tracking-tight sm:text-5xl lg:text-6xl">
                        {t('hero.title')}{' '}
                        <span className="bg-gradient-to-r from-brand-light via-violet to-info bg-clip-text text-transparent">
                            {t('hero.accent')}
                        </span>
                    </h1>

                    <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-ink-soft sm:text-lg">
                        {t('hero.subtitle')}
                    </p>

                    <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
                        <a href="#install" className="btn btn-primary btn-lg w-full shadow-glow sm:w-auto">
                            {t('hero.ctaInstall')}
                            <ArrowRight size={17} />
                        </a>
                        <a href={REPO} className="btn btn-ghost btn-lg w-full sm:w-auto">
                            {t('hero.ctaSource')}
                        </a>
                    </div>
                </div>

                <Preview />
            </div>
        </section>
    );
}

function Preview() {
    const t = useT();

    return (
        <div className="animate-fade-up mx-auto mt-16 max-w-5xl" aria-hidden>
            <div className="rounded-token-xl border border-line bg-card/80 p-3 shadow-token-lg backdrop-blur sm:p-4">
                {/* Window chrome, so the mock reads as an application screen. */}
                <div className="mb-3 flex items-center gap-1.5 px-1.5">
                    <span className="h-2.5 w-2.5 rounded-full bg-bad/70" />
                    <span className="h-2.5 w-2.5 rounded-full bg-warn/70" />
                    <span className="h-2.5 w-2.5 rounded-full bg-ok/70" />
                </div>

                <div className="rounded-token-lg border border-line bg-base p-4 sm:p-6">
                    <div className="grid gap-3 sm:grid-cols-3">
                        <MockStat
                            label={t('preview.netWorth')}
                            value="$48,120.75"
                            accent="text-ink"
                            icon={<Wallet size={16} className="text-brand-light" />}
                        />
                        <MockStat
                            label={t('preview.income')}
                            value="+$6,420.00"
                            accent="text-ok"
                            icon={<TrendingUp size={16} className="text-ok" />}
                        />
                        <MockStat
                            label={t('preview.expenses')}
                            value="−$4,058.30"
                            accent="text-bad"
                            icon={<Receipt size={16} className="text-bad" />}
                        />
                    </div>

                    <div className="mt-4 grid gap-4 lg:grid-cols-5">
                        <div className="rounded-token border border-line bg-card p-4 lg:col-span-3">
                            <div className="mb-4 flex items-center justify-between">
                                <p className="text-[13px] font-bold">{t('preview.thisMonth')}</p>
                                <div className="flex items-center gap-3 text-[11px] text-ink-dim">
                                    <span className="flex items-center gap-1.5">
                                        <span className="h-2 w-2 rounded-full bg-ok" />
                                        {t('preview.income')}
                                    </span>
                                    <span className="flex items-center gap-1.5">
                                        <span className="h-2 w-2 rounded-full bg-bad" />
                                        {t('preview.expenses')}
                                    </span>
                                </div>
                            </div>
                            <div className="flex h-32 items-end justify-between gap-2.5 sm:gap-4">
                                {MOCK_BARS.map((bar, i) => (
                                    <div key={i} className="flex h-full flex-1 items-end gap-1">
                                        <div className="w-full rounded-t bg-ok/80" style={{ height: `${bar.income}%` }} />
                                        <div className="w-full rounded-t bg-bad/70" style={{ height: `${bar.expense}%` }} />
                                    </div>
                                ))}
                            </div>
                        </div>

                        <div className="rounded-token border border-line bg-card p-4 lg:col-span-2">
                            <p className="mb-4 text-[13px] font-bold">{t('preview.pieTitle')}</p>
                            <div className="flex flex-col gap-3">
                                {MOCK_CATEGORIES.map(cat => (
                                    <div key={cat.key}>
                                        <div className="mb-1.5 flex items-center justify-between text-[12px]">
                                            <span className="text-ink-soft">{t(`preview.cat.${cat.key}`)}</span>
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
    );
}

function MockStat({ label, value, accent, icon }: { label: string; value: string; accent: string; icon: ReactNode }) {
    return (
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
}

function SectionHeading({ title, subtitle }: { title: string; subtitle?: string }) {
    return (
        <div className="mx-auto max-w-2xl text-center">
            <h2 className="text-2xl font-extrabold tracking-tight sm:text-[32px] sm:leading-tight">{title}</h2>
            {subtitle && <p className="mt-3 text-base text-ink-soft">{subtitle}</p>}
        </div>
    );
}

/**
 * The last feature card widens to fill its row in both the two- and three-column layouts, so
 * a count that does not divide evenly leaves no empty cell. Literal class names, for Tailwind.
 */
function lastCardSpan(count: number): string {
    const sm = count % 2 === 1 ? 'sm:col-span-2' : '';
    const lg = ['lg:col-span-1', 'lg:col-span-3', 'lg:col-span-2'][count % 3];
    return sm ? `${sm} ${lg}` : count % 3 === 0 ? '' : lg;
}

function Features() {
    const t = useT();

    return (
        <section id="features" className="scroll-mt-20 border-t border-line px-5 py-20">
            <div className="mx-auto max-w-content">
                <SectionHeading title={t('features.title')} />

                <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                    {FEATURES.map(({ n, Icon, soon }, i) => (
                        <article
                            key={n}
                            className={`${i === FEATURES.length - 1 ? lastCardSpan(FEATURES.length) : ''} group rounded-token-lg border border-line bg-card p-6 transition-all duration-200 hover:-translate-y-1 hover:border-line-strong hover:shadow-token`}
                        >
                            <div className="mb-4 flex items-start justify-between gap-3">
                                <span className="inline-flex h-11 w-11 items-center justify-center rounded-token bg-brand/10 transition-colors group-hover:bg-brand/20">
                                    <Icon size={20} className="text-brand-light" />
                                </span>
                                {soon && (
                                    <span className="rounded-full border border-line px-2.5 py-0.5 text-[11px] font-semibold text-ink-soft">
                                        {t('features.soon')}
                                    </span>
                                )}
                            </div>
                            <h3 className="mb-2 text-[15px] font-bold">{t(`features.${n}.title`)}</h3>
                            <p className="text-sm leading-relaxed text-ink-soft">{t(`features.${n}.body`)}</p>
                        </article>
                    ))}
                </div>
            </div>
        </section>
    );
}

/**
 * Commands are the same in every language, so they come from install.ts and only the prose
 * goes through i18n. A comment line (`# ...`) is dimmed so it reads as a note, not a command.
 */
function CodeBlock({ lines, perLine = true }: { lines: readonly string[]; perLine?: boolean }) {
    return (
        <pre className="code-block">
            {perLine ? (
                <code>
                    {lines.map((line, i) => (
                        <span key={i} className={`block${line.startsWith('#') ? ' comment' : ''}`}>{line}</span>
                    ))}
                </code>
            ) : (
                // One text node: UPDATE_STEPS repeats a compose line (`docker compose up -d`), and a
                // line-per-element render would make that line ambiguous to anything looking it up.
                <code>{lines.join('\n')}</code>
            )}
        </pre>
    );
}

function Install() {
    const t = useT();
    const [platform, setPlatform] = useState<(typeof PLATFORMS)[number]['id']>('compose');
    const active = PLATFORMS.find(p => p.id === platform) ?? PLATFORMS[0];

    return (
        <section id="install" className="scroll-mt-20 border-t border-line bg-surface/40 px-5 py-20">
            <div className="mx-auto max-w-3xl">
                <SectionHeading title={t('install.title')} subtitle={t('install.lead')} />

                <div role="tablist" className="mt-10 flex flex-wrap justify-center gap-2">
                    {PLATFORMS.map(p => (
                        <button
                            key={p.id}
                            type="button"
                            role="tab"
                            id={`tab-${p.id}`}
                            aria-selected={p.id === platform}
                            aria-controls="install-panel"
                            className={`btn btn-sm ${p.id === platform ? 'btn-primary' : 'btn-ghost'}`}
                            onClick={() => setPlatform(p.id)}
                        >
                            {t(`install.tab.${p.id}`)}
                        </button>
                    ))}
                </div>

                <div
                    id="install-panel"
                    role="tabpanel"
                    aria-labelledby={`tab-${platform}`}
                    className="mt-6 rounded-token-lg border border-line bg-card p-6"
                >
                    <ol className="flex flex-col gap-3">
                        {Array.from({ length: active.steps }, (_, i) => i + 1).map(n => (
                            <li key={n} className="flex gap-3.5">
                                <span className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand to-info text-xs font-bold text-white">
                                    {n}
                                </span>
                                <p className="text-sm leading-relaxed text-ink-soft">
                                    {t(`install.${active.id}.${n}` as Key)}
                                </p>
                            </li>
                        ))}
                    </ol>

                    {active.id === 'compose' && (
                        <div className="mt-6">
                            <CodeBlock lines={COMPOSE_STEPS} />
                        </div>
                    )}
                </div>

                <div className="mt-6 rounded-token-lg border border-line bg-card p-6">
                    <h3 className="mb-2 text-[15px] font-bold">{t('install.https.title')}</h3>
                    <p className="text-sm leading-relaxed text-ink-soft">{t('install.https.body')}</p>
                </div>
            </div>
        </section>
    );
}

function Operate() {
    const t = useT();

    return (
        <section className="border-t border-line px-5 py-20">
            <div className="mx-auto grid max-w-content gap-5 lg:grid-cols-2">
                <div id="update" className="scroll-mt-20 rounded-token-lg border border-line bg-card p-6">
                    <h3 className="mb-4 text-[15px] font-bold">{t('install.update.title')}</h3>
                    <CodeBlock lines={UPDATE_STEPS} perLine={false} />
                </div>

                <div id="backup" className="scroll-mt-20 rounded-token-lg border border-line bg-card p-6">
                    <h3 className="mb-2 text-[15px] font-bold">{t('install.backup.title')}</h3>
                    <p className="mb-4 text-sm leading-relaxed text-ink-soft">{t('install.backup.body')}</p>
                    <CodeBlock lines={BACKUP_STEPS} />

                    <h3 className="mb-2 mt-6 text-[15px] font-bold">{t('install.reset.title')}</h3>
                    <p className="mb-4 text-sm leading-relaxed text-ink-soft">{t('install.reset.body')}</p>
                    <CodeBlock lines={[RESET_PASSWORD]} />
                </div>
            </div>
        </section>
    );
}

function Android() {
    const t = useT();

    return (
        <section id="android" className="scroll-mt-20 border-t border-line bg-surface/40 px-5 py-20">
            <div className="mx-auto max-w-2xl text-center">
                <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{t('android.title')}</h2>
                <p className="mt-4 text-base leading-relaxed text-ink-soft">{t('android.body')}</p>
                <a href={RELEASES} className="btn btn-primary btn-lg mt-8">
                    <Download size={17} />
                    {t('android.cta')}
                </a>
            </div>
        </section>
    );
}

function Ai() {
    const t = useT();

    return (
        <section id="ai" className="scroll-mt-20 border-t border-line px-5 py-20">
            <div className="relative isolate mx-auto max-w-content overflow-hidden rounded-token-xl border border-line bg-card px-6 py-14 text-center">
                <div
                    aria-hidden
                    className="pointer-events-none absolute inset-0 -z-10 bg-gradient-to-br from-brand/15 via-transparent to-info/15"
                />
                <span className="mb-4 inline-block rounded-full border border-line px-2.5 py-0.5 text-[11px] font-semibold text-ink-soft">
                    {t('features.soon')}
                </span>
                <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{t('ai.title')}</h2>
                <p className="mx-auto mt-4 max-w-2xl text-base leading-relaxed text-ink-soft">{t('ai.body')}</p>
            </div>
        </section>
    );
}

function Footer() {
    const t = useT();

    return (
        <footer className="border-t border-line px-5 py-12">
            <div className="mx-auto flex max-w-content flex-col gap-10 sm:flex-row sm:justify-between">
                <div className="max-w-xs">
                    <div className="flex items-center gap-2.5">
                        <Logo size={32} />
                        <span className="text-sm font-bold">
                            Personal<span className="text-brand-light">Money</span>
                        </span>
                    </div>
                    <p className="mt-3 text-[13px] text-ink-dim">{t('footer.tagline')}</p>
                </div>

                <nav className="flex flex-col gap-2.5">
                    <a href={REPO} className="text-sm text-ink-soft transition-colors hover:text-ink">
                        {t('footer.source')}
                    </a>
                    <a href={`${REPO}/blob/master/LICENSE`} className="text-sm text-ink-soft transition-colors hover:text-ink">
                        {t('footer.license')}
                    </a>
                    <a href="/privacy.html" className="text-sm text-ink-soft transition-colors hover:text-ink">
                        {t('footer.privacy')}
                    </a>
                </nav>
            </div>
        </footer>
    );
}
