import React, { useEffect, useState } from 'react';
import { accountApi, transactionApi } from '../api';
import { Account, Transaction } from '../types';
import {
    AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell,
} from 'recharts';
import { Wallet, TrendingUp, TrendingDown, ArrowLeftRight } from 'lucide-react';
import { formatAmount } from '../money';
import { useI18n } from '../i18n';
import { CATEGORY_COLORS, useChartColors } from '../theme';

/** A transfer leg is already signed; see the note in Transactions.tsx. */
const isCredit = (t: Transaction) =>
    t.type === 'INCOME' || (t.type === 'TRANSFER' && Number(t.amount) >= 0);

// Per-currency formatting lives in ../money. This local helper is only for
// aggregates that are not tied to a single currency (totals, chart axes).
const fmt = (n: number, sym = '') =>
    `${sym}${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const Dashboard: React.FC = () => {
    const { t, lang } = useI18n();
    const chart = useChartColors();
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [transactions, setTransactions] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        Promise.all([
            accountApi.getAll(),
            transactionApi.getAll({ size: 100 }),
        ]).then(([accs, txns]) => {
            setAccounts(accs);
            setTransactions(txns.content);
        }).finally(() => setLoading(false));
    }, []);

    const totalBalance = accounts.reduce((s, a) => s + Number(a.balance), 0);
    const thisMonth = new Date().toISOString().slice(0, 7);
    // Named `txn` rather than `t` throughout: `t` is the translate function here.
    const monthTxns = transactions.filter(txn => txn.date.startsWith(thisMonth));
    const monthIncome = monthTxns.filter(txn => txn.type === 'INCOME').reduce((s, txn) => s + Number(txn.amount), 0);
    const monthExpense = monthTxns.filter(txn => txn.type === 'EXPENSE').reduce((s, txn) => s + Number(txn.amount), 0);

    // Build last 6 months spending chart data
    const chartData: { month: string; income: number; expenses: number }[] = [];
    for (let i = 5; i >= 0; i--) {
        const d = new Date(); d.setMonth(d.getMonth() - i);
        const key = d.toISOString().slice(0, 7);
        // Month names follow the interface language rather than the browser's.
        const label = d.toLocaleString(lang, { month: 'short' });
        const income = transactions.filter(txn => txn.type === 'INCOME' && txn.date.startsWith(key)).reduce((s, txn) => s + Number(txn.amount), 0);
        const expenses = transactions.filter(txn => txn.type === 'EXPENSE' && txn.date.startsWith(key)).reduce((s, txn) => s + Number(txn.amount), 0);
        chartData.push({ month: label, income, expenses });
    }

    // Category breakdown for current month
    const categoryMap: Record<string, number> = {};
    monthTxns.filter(txn => txn.type === 'EXPENSE').forEach(txn => {
        // Transfers have no category, and neither does history whose category was deleted.
        const label = txn.category?.name ?? t('common.uncategorised');
        categoryMap[label] = (categoryMap[label] || 0) + Number(txn.amount);
    });
    const pieData = Object.entries(categoryMap).map(([name, value]) => ({ name, value }));

    const recent = transactions.slice(0, 6);

    const tooltipStyle = {
        background: chart.tooltipBg,
        border: `1px solid ${chart.tooltipBorder}`,
        borderRadius: 8,
        fontSize: 13,
        color: chart.text,
    };

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>{t('dashboard.title')}</h1>
                    <p className="text-muted text-sm">{t('dashboard.subtitle')}</p>
                </div>
            </div>

            {/* Stat Cards */}
            <div className="grid-4" style={{ marginBottom: 24 }}>
                <StatCard
                    label={t('dashboard.netWorth')}
                    value={`$${fmt(totalBalance)}`}
                    sub={t('dashboard.accountCount', { count: accounts.length })}
                    tint="rgb(var(--accent-rgb) / 0.15)"
                    icon={<Wallet size={20} color="var(--accent-light)" />}
                />
                <StatCard
                    label={t('dashboard.monthIncome')}
                    value={`$${fmt(monthIncome)}`}
                    valueClass="text-green"
                    sub={t('dashboard.currentMonth')}
                    tint="rgb(var(--green-rgb) / 0.15)"
                    icon={<TrendingUp size={20} color="var(--green)" />}
                />
                <StatCard
                    label={t('dashboard.monthExpenses')}
                    value={`$${fmt(monthExpense)}`}
                    valueClass="text-red"
                    sub={t('dashboard.currentMonth')}
                    tint="rgb(var(--red-rgb) / 0.15)"
                    icon={<TrendingDown size={20} color="var(--red)" />}
                />
                <StatCard
                    label={t('dashboard.netThisMonth')}
                    value={`$${fmt(monthIncome - monthExpense)}`}
                    valueClass={monthIncome - monthExpense >= 0 ? 'text-green' : 'text-red'}
                    sub={t('dashboard.incomeMinusExpenses')}
                    tint="rgb(var(--cyan-rgb) / 0.15)"
                    icon={<ArrowLeftRight size={20} color="var(--cyan)" />}
                />
            </div>

            <div className="grid-2" style={{ marginBottom: 24 }}>
                {/* Area Chart */}
                <div className="card">
                    <p className="section-title" style={{ marginBottom: 20 }}>{t('dashboard.chartTitle')}</p>
                    <ResponsiveContainer width="100%" height={220}>
                        <AreaChart data={chartData} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
                            <defs>
                                <linearGradient id="gi" x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0%" stopColor={chart.income} stopOpacity={0.4} />
                                    <stop offset="100%" stopColor={chart.income} stopOpacity={0} />
                                </linearGradient>
                                <linearGradient id="ge" x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0%" stopColor={chart.expense} stopOpacity={0.4} />
                                    <stop offset="100%" stopColor={chart.expense} stopOpacity={0} />
                                </linearGradient>
                            </defs>
                            <XAxis dataKey="month" tick={{ fill: chart.axis, fontSize: 12 }} axisLine={false} tickLine={false} />
                            <YAxis tick={{ fill: chart.axis, fontSize: 12 }} axisLine={false} tickLine={false} width={50} />
                            <Tooltip contentStyle={tooltipStyle} labelStyle={{ color: chart.axis }} />
                            <Area type="monotone" dataKey="income" stroke={chart.income} fill="url(#gi)" strokeWidth={2} name={t('common.income')} />
                            <Area type="monotone" dataKey="expenses" stroke={chart.expense} fill="url(#ge)" strokeWidth={2} name={t('common.expense')} />
                        </AreaChart>
                    </ResponsiveContainer>
                </div>

                {/* Pie Chart */}
                <div className="card">
                    <p className="section-title" style={{ marginBottom: 20 }}>{t('dashboard.pieTitle')}</p>
                    {pieData.length === 0 ? (
                        <div className="empty-state"><p className="empty-state-sub">{t('dashboard.noExpenseData')}</p></div>
                    ) : (
                        <div className="flex flex-wrap items-center gap-4">
                            <ResponsiveContainer width={160} height={160}>
                                <PieChart>
                                    <Pie data={pieData} dataKey="value" cx="50%" cy="50%" innerRadius={40} outerRadius={70} paddingAngle={3}>
                                        {pieData.map((_, i) => <Cell key={i} fill={CATEGORY_COLORS[i % CATEGORY_COLORS.length]} />)}
                                    </Pie>
                                    <Tooltip contentStyle={tooltipStyle} formatter={(v: any) => [`$${fmt(v)}`, '']} />
                                </PieChart>
                            </ResponsiveContainer>
                            <div className="flex min-w-[160px] flex-1 flex-col gap-2">
                                {pieData.slice(0, 5).map((d, i) => (
                                    <div key={d.name} className="flex items-center justify-between gap-3">
                                        <div className="flex min-w-0 items-center gap-1.5">
                                            <span
                                                className="h-2 w-2 flex-shrink-0 rounded-full"
                                                style={{ background: CATEGORY_COLORS[i % CATEGORY_COLORS.length] }}
                                            />
                                            <span className="truncate text-xs text-ink-soft">{d.name}</span>
                                        </div>
                                        <span className="flex-shrink-0 text-xs font-semibold">${fmt(d.value)}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* Accounts Summary */}
            <div className="grid-2">
                <div className="card">
                    <p className="section-title" style={{ marginBottom: 16 }}>{t('dashboard.accounts')}</p>
                    {accounts.length === 0 ? (
                        <p className="text-muted text-sm">{t('dashboard.noAccounts')}</p>
                    ) : (
                        <div className="flex flex-col">
                            {accounts.map(a => (
                                <div key={a.id} className="flex items-center justify-between gap-3 border-b border-line py-2.5 last:border-0">
                                    <div className="flex min-w-0 items-center gap-2.5">
                                        <span className="h-2.5 w-2.5 flex-shrink-0 rounded-full" style={{ background: a.color }} />
                                        <div className="min-w-0">
                                            <p className="truncate text-sm font-medium">{a.name}</p>
                                            <p className="text-muted text-sm">
                                                {t(`accountType.${a.type}`)} · {a.currency.code}
                                            </p>
                                        </div>
                                    </div>
                                    <p
                                        className="flex-shrink-0 text-[15px] font-bold"
                                        style={{ color: Number(a.balance) < 0 ? 'var(--red)' : 'var(--text-primary)' }}
                                    >
                                        {a.currency.symbol}{formatAmount(Number(a.balance), a.currency)}
                                    </p>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Recent Transactions */}
                <div className="card">
                    <p className="section-title" style={{ marginBottom: 16 }}>{t('dashboard.recent')}</p>
                    {recent.length === 0 ? (
                        <p className="text-muted text-sm">{t('dashboard.noTransactions')}</p>
                    ) : (
                        <div className="flex flex-col">
                            {recent.map(txn => (
                                <div key={txn.id} className="flex items-center justify-between gap-3 border-b border-line py-2.5 last:border-0">
                                    <div className="min-w-0">
                                        <p className="truncate text-sm font-medium">
                                            {txn.category?.name ?? t('common.uncategorised')}
                                        </p>
                                        <p className="text-muted text-sm truncate">{txn.account.name} · {txn.date}</p>
                                    </div>
                                    <p
                                        className="flex-shrink-0 text-sm font-bold"
                                        style={{ color: isCredit(txn) ? 'var(--green)' : 'var(--red)' }}
                                    >
                                        {isCredit(txn) ? '+' : '-'}{txn.account.currency.symbol}
                                        {formatAmount(Math.abs(Number(txn.amount)), txn.account.currency)}
                                    </p>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

/** The four headline figures were four copies of the same markup; now they are one. */
const StatCard: React.FC<{
    label: string;
    value: string;
    valueClass?: string;
    sub: string;
    tint: string;
    icon: React.ReactNode;
}> = ({ label, value, valueClass, sub, tint, icon }) => (
    <div className="stat-card">
        <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
                <p className="stat-label">{label}</p>
                <p className={`stat-value ${valueClass ?? ''}`}>{value}</p>
                <p className="stat-sub">{sub}</p>
            </div>
            <span className="flex-shrink-0 rounded-lg p-2" style={{ background: tint }}>{icon}</span>
        </div>
    </div>
);

export default Dashboard;
