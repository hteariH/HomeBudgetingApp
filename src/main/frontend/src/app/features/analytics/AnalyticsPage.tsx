import React, { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Decimal from 'decimal.js';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useT } from '../../../i18n';
import { useChartColors } from '../../../theme';
import { useBaseCurrency, useCategories, useFxSnapshot } from '../../api/hooks';
import { qk } from '../../api/queryClient';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, Segmented } from '../../components/forms';
import { IconCircle, safeColor } from '../../components/IconCircle';
import { MoneyText } from '../../components/MoneyText';
import { formatMoney, shortMonthName } from '../../lib/format';
import { Page, PageLoading } from '../../shell/Page';
import { fetchLedger } from '../transactions/ledger.logic';
import { buildAnalytics, monthStarts, type AnalyticsPeriod } from './analytics.logic';

/** Port of AnalyticsScreen.kt: a period, its months as bars, and one month taken apart by category. */
const AnalyticsPage: React.FC = () => {
    const t = useT();
    const colors = useChartColors();
    const [params, setParams] = useSearchParams();
    const period = ([3, 6, 12].includes(Number(params.get('months'))) ? Number(params.get('months')) : 6) as AnalyticsPeriod;
    const base = useBaseCurrency();
    const fx = useFxSnapshot();
    const { data: categories = [] } = useCategories();
    const span = useMemo(() => monthStarts(period), [period]);
    const { data: rows = [], isLoading, error } = useQuery({
        queryKey: qk.transactions({ analytics: span.starts[0].toISOString() }),
        queryFn: () => fetchLedger({ from: span.starts[0], to: span.end }),
    });
    const [selected, setSelected] = useState<number | null>(null);
    const [kind, setKind] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');

    const a = useMemo(() => buildAnalytics({ rows, categories, fx, base, months: period }), [rows, categories, fx, base, period]);
    const month = a.months[selected ?? a.months.length - 1];
    const chartData = useMemo(
        () => a.months.map((bar, i) => ({ i, label: shortMonthName(bar.month1), income: bar.income.toNumber(), expense: bar.expense.toNumber() })),
        [a],
    );
    const slices = kind === 'EXPENSE' ? month?.expenseCategories ?? [] : month?.incomeCategories ?? [];
    const m = (amount: Decimal) => ({ amount, currency: base });
    const monthName = (bar: { month1: number; year: number }) => `${shortMonthName(bar.month1)} ${bar.year}`;
    const empty = a.totalExpense.isZero() && a.totalIncome.isZero();

    return (
        <Page
            title={t('analytics_title')}
            actions={
                <Segmented<string>
                    value={String(period)}
                    onChange={v => {
                        setSelected(null);
                        setParams({ months: v }, { replace: true });
                    }}
                    options={[
                        { value: '3', label: t('analytics_period_3m') },
                        { value: '6', label: t('analytics_period_6m') },
                        { value: '12', label: t('analytics_period_12m') },
                    ]}
                />
            }
        >
            <ErrorBanner error={error} fallbackKey="common_load_failed" className="mb-4" />
            {isLoading ? (
                <PageLoading />
            ) : (
                <div className="flex flex-col gap-4">
                    <div className="grid grid-cols-2 gap-3">
                        <div className="rounded-token-lg border border-line bg-card px-4 py-3">
                            <div className="text-xs text-ink-soft">{t('dashboard_add_expense')}</div>
                            <MoneyText money={m(a.totalExpense)} type="EXPENSE" className="text-lg font-bold" />
                        </div>
                        <div className="rounded-token-lg border border-line bg-card px-4 py-3">
                            <div className="text-xs text-ink-soft">{t('dashboard_add_income')}</div>
                            <MoneyText money={m(a.totalIncome)} type="INCOME" className="text-lg font-bold" />
                        </div>
                    </div>

                    {(a.expenseChangePercent !== null || a.savingsRatePercent !== null) && (
                        <Card title={t('analytics_insights')}>
                            <div className="flex flex-col gap-1 px-5 pb-4 pt-1 text-sm text-ink-soft">
                                {a.expenseChangePercent !== null && (
                                    <div>
                                        {a.expenseChangePercent > 0
                                            ? t('analytics_expense_up', { 0: a.expenseChangePercent })
                                            : a.expenseChangePercent < 0
                                              ? t('analytics_expense_down', { 0: -a.expenseChangePercent })
                                              : t('analytics_expense_flat')}
                                    </div>
                                )}
                                {a.savingsRatePercent !== null && <div>{t('analytics_savings_rate', { 0: a.savingsRatePercent })}</div>}
                            </div>
                        </Card>
                    )}

                    {empty ? (
                        <EmptyState title={t('chart_no_data')} />
                    ) : (
                        <>
                            <Card title={t('analytics_by_month')} action={<span className="text-xs text-ink-dim">{t('analytics_last_months', { 0: period })}</span>}>
                                <div className="px-3 pb-4 pt-2">
                                    <ResponsiveContainer width="100%" height={240}>
                                        <BarChart
                                            data={chartData}
                                            onClick={e => typeof e?.activeTooltipIndex === 'number' && setSelected(e.activeTooltipIndex)}
                                            margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
                                        >
                                            <CartesianGrid vertical={false} stroke={colors.grid} strokeDasharray="3 3" />
                                            <XAxis dataKey="label" stroke={colors.axis} tickLine={false} axisLine={false} fontSize={12} />
                                            <YAxis stroke={colors.axis} tickLine={false} axisLine={false} fontSize={11} width={48} />
                                            <Tooltip
                                                cursor={{ fill: colors.grid, opacity: 0.3 }}
                                                contentStyle={{ background: colors.tooltipBg, border: `1px solid ${colors.tooltipBorder}`, borderRadius: 10, color: colors.text }}
                                                formatter={(v: number, name: string) => [
                                                    formatMoney(m(new Decimal(v))),
                                                    name === 'income' ? t('common_income') : t('common_expense'),
                                                ]}
                                            />
                                            <Bar dataKey="expense" radius={[4, 4, 0, 0]} cursor="pointer" isAnimationActive={false}>
                                                {a.months.map((_, i) => (
                                                    <Cell key={i} fill={colors.expense} fillOpacity={month === a.months[i] ? 1 : 0.45} />
                                                ))}
                                            </Bar>
                                            <Bar dataKey="income" radius={[4, 4, 0, 0]} cursor="pointer" isAnimationActive={false}>
                                                {a.months.map((_, i) => (
                                                    <Cell key={i} fill={colors.income} fillOpacity={month === a.months[i] ? 1 : 0.45} />
                                                ))}
                                            </Bar>
                                        </BarChart>
                                    </ResponsiveContainer>
                                </div>
                            </Card>

                            {month && (
                                <Card
                                    title={t('analytics_month_categories', { 0: monthName(month) })}
                                    action={
                                        <Segmented<'EXPENSE' | 'INCOME'>
                                            value={kind}
                                            onChange={setKind}
                                            options={[
                                                { value: 'EXPENSE', label: `${t('dashboard_add_expense')} (${month.expenseCategories.length})` },
                                                { value: 'INCOME', label: `${t('dashboard_add_income')} (${month.incomeCategories.length})` },
                                            ]}
                                        />
                                    }
                                >
                                    <div className="flex flex-col gap-3 px-5 pb-5 pt-3">
                                        {slices.length === 0 ? (
                                            <div className="text-sm text-ink-soft">{t('analytics_no_category_data')}</div>
                                        ) : (
                                            slices.map(s => (
                                                <div key={String(s.categoryId)} className="flex flex-col gap-1">
                                                    <div className="flex items-center gap-2 text-sm">
                                                        <IconCircle icon={s.icon} color={s.color} size={24} />
                                                        <span className="flex-1 truncate text-ink">{s.name ?? t('common_uncategorised')}</span>
                                                        <span className="text-xs text-ink-dim">{s.percentage}%</span>
                                                        <MoneyText money={m(s.amount)} className="text-sm font-semibold text-ink" />
                                                    </div>
                                                    <div className="h-2 overflow-hidden rounded-full bg-raised">
                                                        <div className="h-full rounded-full" style={{ width: `${s.percentage}%`, background: safeColor(s.color) }} />
                                                    </div>
                                                </div>
                                            ))
                                        )}
                                    </div>
                                </Card>
                            )}

                            <Card title={t('analytics_by_month')}>
                                <div className="pb-2 pt-1">
                                    {[...a.months].reverse().map(bar => (
                                        <button
                                            key={`${bar.year}-${bar.month1}`}
                                            type="button"
                                            onClick={() => setSelected(a.months.indexOf(bar))}
                                            className={`flex w-full items-center gap-3 px-5 py-2.5 text-left text-sm transition-colors hover:bg-raised ${
                                                bar === month ? 'bg-brand/10' : ''
                                            }`}
                                        >
                                            <span className="flex-1 text-ink">{monthName(bar)}</span>
                                            <MoneyText money={m(bar.income)} type="INCOME" className="w-32 text-right" />
                                            <MoneyText money={m(bar.expense)} type="EXPENSE" className="w-32 text-right" />
                                        </button>
                                    ))}
                                </div>
                            </Card>
                        </>
                    )}
                </div>
            )}
        </Page>
    );
};

export default AnalyticsPage;
