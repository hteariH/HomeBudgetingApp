import React, { useMemo } from 'react';
import Decimal from 'decimal.js';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Minus, Plus } from 'lucide-react';
import { useT } from '../../../i18n';
import { useChartColors } from '../../../theme';
import { endpoints } from '../../api/endpoints';
import { useAccounts, useBaseCurrency, useCategories, useFxSnapshot } from '../../api/hooks';
import { qk } from '../../api/queryClient';
import { CategoryBreakdownChart } from '../../components/CategoryBreakdownChart';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { IconCircle, safeColor } from '../../components/IconCircle';
import { MoneyText } from '../../components/MoneyText';
import { formatMoney, shortMonthName } from '../../lib/format';
import { AppRoutes } from '../../lib/routes';
import { Page, PageLoading } from '../../shell/Page';
import { BREAKPOINTS, useMinWidth } from '../../shell/useMediaQuery';
import { accountBalance, splitAccounts, totalBalance } from '../accounts/accounts.logic';
import { QuickEntryPanel } from '../quickadd/QuickEntryPanel';
import { collapseTransfers, counterparts, fetchLedger } from '../transactions/ledger.logic';
import { TransactionRow } from '../transactions/TransactionRow';
import { sixMonths, summarize, type DashboardSummary } from './dashboard.logic';

const StatCard: React.FC<{ label: string; sub: string; children: React.ReactNode }> = ({ label, sub, children }) => (
    <div className="flex-1 rounded-token-lg border border-line bg-card px-4 py-3">
        <div className="text-xs text-ink-soft">{label}</div>
        <div className="mt-1 text-lg font-bold">{children}</div>
        <div className="text-xs text-ink-dim">{sub}</div>
    </div>
);

const FlowChart: React.FC<{ summary: DashboardSummary }> = ({ summary }) => {
    const t = useT();
    const colors = useChartColors();
    const data = summary.flow.map(f => ({
        label: shortMonthName(f.month1),
        income: f.income.toNumber(),
        expense: f.expense.toNumber(),
    }));
    return (
        <ResponsiveContainer width="100%" height={220}>
            <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid vertical={false} stroke={colors.grid} strokeDasharray="3 3" />
                <XAxis dataKey="label" stroke={colors.axis} tickLine={false} axisLine={false} fontSize={12} />
                <YAxis stroke={colors.axis} tickLine={false} axisLine={false} fontSize={11} width={48} />
                <Tooltip
                    cursor={{ fill: colors.grid, opacity: 0.3 }}
                    contentStyle={{ background: colors.tooltipBg, border: `1px solid ${colors.tooltipBorder}`, borderRadius: 10, color: colors.text }}
                    formatter={(v: number, name: string) => [
                        formatMoney({ amount: new Decimal(v), currency: summary.monthIncome.currency }),
                        name === 'income' ? t('common_income') : t('common_expense'),
                    ]}
                />
                <Bar dataKey="income" fill={colors.income} radius={[4, 4, 0, 0]} />
                <Bar dataKey="expense" fill={colors.expense} radius={[4, 4, 0, 0]} />
            </BarChart>
        </ResponsiveContainer>
    );
};

/** Net worth over the six months, drawn on the balance card - so always white. */
const Sparkline: React.FC<{ values: number[] }> = ({ values }) => {
    if (values.length < 2) return null;
    return (
        <ResponsiveContainer width="100%" height={40}>
            <LineChart data={values.map((v, i) => ({ i, v }))}>
                <YAxis hide domain={['dataMin', 'dataMax']} />
                <Line type="monotone" dataKey="v" stroke="#FFFFFF" strokeWidth={2} dot={false} isAnimationActive={false} />
            </LineChart>
        </ResponsiveContainer>
    );
};

/**
 * The overview. Port of DashboardScreen.kt: on a wide window two columns - balances down the
 * left, and everything you do down the right; narrower, one column in the same order.
 */
const DashboardPage: React.FC = () => {
    const t = useT();
    const wide = useMinWidth(BREAKPOINTS.inlineEntry);
    const base = useBaseCurrency();
    const fx = useFxSnapshot();
    const { data: accounts = [], isLoading, error } = useAccounts();
    const { data: categories = [] } = useCategories();
    const span = useMemo(() => sixMonths(), []);
    const { data: rows = [], error: ledgerError } = useQuery({
        queryKey: qk.transactions({ dashboard: span.starts[0].toISOString() }),
        queryFn: () => fetchLedger({ from: span.starts[0], to: span.end }),
    });
    const { data: recentPage } = useQuery({
        queryKey: qk.transactions({ recent: 20 }),
        queryFn: async () => (await endpoints.transactions({ size: 20 })).content,
    });

    const active = splitAccounts(accounts).active;
    const total = totalBalance(accounts, fx, base);
    const summary = useMemo(() => summarize({ accounts, rows, categories, fx, base }), [accounts, rows, categories, fx, base]);
    const recent = useMemo(() => {
        const all = recentPage ?? [];
        return { rows: collapseTransfers(all).slice(0, 10), others: counterparts(all) };
    }, [recentPage]);

    if (isLoading) return <PageLoading />;

    const balanceCard = (
        <section className="rounded-token-lg bg-gradient-to-br from-[#4F46E5] to-[#6366F1] p-5 text-white shadow-token">
            <div className="text-sm font-medium opacity-90">{t('dashboard_total_balance')}</div>
            <div className="mt-1 text-3xl font-bold tabular-nums">{formatMoney(total.total)}</div>
            <div className="mt-1 text-xs opacity-80">{t('dashboard_account_count', { count: total.counted })}</div>
            {total.missingRate > 0 && (
                // Left out of the sum, and said to be: a total over 24 of 26 accounts must not
                // read as a total over all of them.
                <div className="text-xs opacity-80">{t('dashboard_total_missing_rate', { count: total.missingRate })}</div>
            )}
            <div className="mt-3 opacity-80">
                <Sparkline values={summary.trend.map(v => v.toNumber())} />
            </div>
        </section>
    );

    const accountsCard = active.length > 0 && (
        <Card title={t('dashboard_accounts')}>
            <div className="pb-2 pt-1">
                {active.map(a => (
                    // A balance invites "made of what?", and the answer is the ledger for it.
                    <Link
                        key={a.id}
                        to={`${AppRoutes.transactions}?accountId=${a.id}`}
                        className="flex items-center justify-between gap-3 px-5 py-2.5 transition-colors hover:bg-raised"
                    >
                        <span className="flex min-w-0 items-center gap-2.5">
                            <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: safeColor(a.color) }} />
                            <span className="truncate text-sm text-ink">{a.name}</span>
                        </span>
                        <MoneyText money={accountBalance(a)} className="text-sm font-bold text-ink" />
                    </Link>
                ))}
            </div>
        </Card>
    );

    const stats = (
        <div className="flex flex-col gap-3 sm:flex-row">
            <StatCard label={t('dashboard_month_income')} sub={t('dashboard_current_month')}>
                <MoneyText money={summary.monthIncome} type="INCOME" />
            </StatCard>
            <StatCard label={t('dashboard_month_expenses')} sub={t('dashboard_current_month')}>
                <MoneyText money={summary.monthExpense} type="EXPENSE" />
            </StatCard>
            <StatCard label={t('dashboard_net_this_month')} sub={t('dashboard_income_minus_expenses')}>
                <MoneyText money={summary.monthNet} type={summary.monthNet.amount.isNegative() ? 'EXPENSE' : 'INCOME'} />
            </StatCard>
        </div>
    );

    const recentCard = (
        <Card
            title={t('dashboard_recent')}
            action={
                <Link to={AppRoutes.transactions} className="text-xs font-medium text-brand-light">
                    {t('nav_transactions')}
                </Link>
            }
        >
            <div className="pb-2 pt-1">
                {recent.rows.length === 0 ? (
                    <div className="px-5 py-4 text-sm text-ink-soft">{t('dashboard_no_transactions')}</div>
                ) : (
                    recent.rows.map(tx => <TransactionRow key={tx.id} tx={tx} counterpart={recent.others.get(tx.id)} />)
                )}
            </div>
        </Card>
    );

    const breakdownCard = summary.slices.length > 0 && (
        <Card title={t('chart_expenses_by_category')}>
            <div className="px-5 pb-5 pt-2">
                <CategoryBreakdownChart
                    slices={summary.slices.map(s => ({
                        key: String(s.categoryId),
                        label: s.name ?? t('common_uncategorised'),
                        icon: s.icon,
                        color: s.color,
                        amount: { amount: s.total, currency: base },
                    }))}
                />
                {summary.moreSlices > 0 && (
                    <div className="mt-3 text-xs text-ink-soft">{t('dashboard_more_categories', { 0: summary.moreSlices })}</div>
                )}
            </div>
        </Card>
    );

    const budgetsCard = summary.budgets.length > 0 && (
        <Card title={t('dashboard_budgets')}>
            <div className="flex flex-col gap-3 px-5 pb-5 pt-2">
                {summary.budgets.map(b => (
                    <div key={b.category.id} className="flex flex-col gap-1">
                        <div className="flex items-center gap-2 text-sm">
                            <IconCircle icon={b.category.icon} color={b.category.color} size={24} />
                            <span className="flex-1 truncate text-ink">{b.category.name}</span>
                            <span className={`tabular-nums ${b.over ? 'text-bad' : 'text-ink-soft'}`}>
                                {formatMoney({ amount: b.spent, currency: base })} / {formatMoney({ amount: b.limit, currency: base })}
                            </span>
                        </div>
                        <div className="h-2 overflow-hidden rounded-full bg-raised">
                            <div
                                className={`h-full rounded-full ${b.over ? 'bg-bad' : ''}`}
                                style={{ width: `${b.fraction * 100}%`, background: b.over ? undefined : safeColor(b.category.color) }}
                            />
                        </div>
                    </div>
                ))}
            </div>
        </Card>
    );

    const flowCard = (
        <Card title={t('dashboard_chart_title')}>
            <div className="px-3 pb-4 pt-2">
                <FlowChart summary={summary} />
            </div>
        </Card>
    );

    return (
        <Page title={t('dashboard_title')}>
            <ErrorBanner error={error ?? ledgerError} fallbackKey="common_load_failed" className="mb-4" />
            {wide ? (
                <div className="grid grid-cols-[minmax(0,36fr)_minmax(0,64fr)] items-start gap-4">
                    {/* Stays in view while the right column scrolls, so a long ledger does not
                        take the balances with it. */}
                    <div className="sticky top-[76px] flex flex-col gap-3">
                        {balanceCard}
                        {accountsCard}
                    </div>
                    <div className="flex flex-col gap-3">
                        {stats}
                        <QuickEntryPanel />
                        {recentCard}
                        {breakdownCard}
                        {budgetsCard}
                        {flowCard}
                    </div>
                </div>
            ) : (
                <div className="flex flex-col gap-3">
                    {balanceCard}
                    {/* Below the inline-entry width the header's "+" opens the form; these two
                        are the one-tap way to the two most common kinds of entry. */}
                    <div className="grid grid-cols-2 gap-3">
                        <Link to={AppRoutes.quickAdd('EXPENSE')} className="btn btn-secondary">
                            <Minus size={16} /> {t('dashboard_add_expense')}
                        </Link>
                        <Link to={AppRoutes.quickAdd('INCOME')} className="btn btn-secondary">
                            <Plus size={16} /> {t('dashboard_add_income')}
                        </Link>
                    </div>
                    {stats}
                    {accountsCard}
                    {recentCard}
                    {breakdownCard}
                    {budgetsCard}
                    {flowCard}
                </div>
            )}
        </Page>
    );
};

export default DashboardPage;
