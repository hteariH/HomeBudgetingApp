import React, { useEffect, useState } from 'react';
import { accountApi, transactionApi } from '../api';
import { Account, Transaction } from '../types';
import {
    AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell
} from 'recharts';
import { Wallet, TrendingUp, TrendingDown, ArrowLeftRight } from 'lucide-react';

const fmt = (n: number, sym = '') =>
    `${sym}${Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const Dashboard: React.FC = () => {
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
    const monthTxns = transactions.filter(t => t.date.startsWith(thisMonth));
    const monthIncome = monthTxns.filter(t => t.type === 'INCOME').reduce((s, t) => s + Number(t.amount), 0);
    const monthExpense = monthTxns.filter(t => t.type === 'EXPENSE').reduce((s, t) => s + Number(t.amount), 0);

    // Build last 6 months spending chart data
    const chartData: { month: string; income: number; expenses: number }[] = [];
    for (let i = 5; i >= 0; i--) {
        const d = new Date(); d.setMonth(d.getMonth() - i);
        const key = d.toISOString().slice(0, 7);
        const label = d.toLocaleString('default', { month: 'short' });
        const income = transactions.filter(t => t.type === 'INCOME' && t.date.startsWith(key)).reduce((s, t) => s + Number(t.amount), 0);
        const expenses = transactions.filter(t => t.type === 'EXPENSE' && t.date.startsWith(key)).reduce((s, t) => s + Number(t.amount), 0);
        chartData.push({ month: label, income, expenses });
    }

    // Category breakdown for current month
    const categoryMap: Record<string, number> = {};
    monthTxns.filter(t => t.type === 'EXPENSE').forEach(t => {
        categoryMap[t.category.name] = (categoryMap[t.category.name] || 0) + Number(t.amount);
    });
    const pieData = Object.entries(categoryMap).map(([name, value]) => ({ name, value }));
    const COLORS = ['#6366F1', '#10B981', '#F59E0B', '#EF4444', '#06B6D4', '#8B5CF6', '#EC4899', '#14B8A6'];

    const recent = transactions.slice(0, 6);

    if (loading) return <div className="loading"><div className="spinner" /></div>;

    return (
        <div className="page">
            <div className="section-header">
                <div>
                    <h1 className="section-title" style={{ fontSize: 22 }}>Dashboard</h1>
                    <p className="text-muted text-sm">Your financial overview</p>
                </div>
            </div>

            {/* Stat Cards */}
            <div className="grid-4" style={{ marginBottom: 24 }}>
                <div className="stat-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                            <p className="stat-label">Net Worth</p>
                            <p className="stat-value">${fmt(totalBalance)}</p>
                            <p className="stat-sub">{accounts.length} account(s)</p>
                        </div>
                        <div style={{ background: 'rgba(99,102,241,0.15)', borderRadius: 8, padding: 8 }}>
                            <Wallet size={20} color="var(--accent-light)" />
                        </div>
                    </div>
                </div>
                <div className="stat-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                            <p className="stat-label">This Month Income</p>
                            <p className="stat-value text-green">${fmt(monthIncome)}</p>
                            <p className="stat-sub">Current month</p>
                        </div>
                        <div style={{ background: 'rgba(16,185,129,0.15)', borderRadius: 8, padding: 8 }}>
                            <TrendingUp size={20} color="var(--green)" />
                        </div>
                    </div>
                </div>
                <div className="stat-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                            <p className="stat-label">This Month Expenses</p>
                            <p className="stat-value text-red">${fmt(monthExpense)}</p>
                            <p className="stat-sub">Current month</p>
                        </div>
                        <div style={{ background: 'rgba(239,68,68,0.15)', borderRadius: 8, padding: 8 }}>
                            <TrendingDown size={20} color="var(--red)" />
                        </div>
                    </div>
                </div>
                <div className="stat-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                            <p className="stat-label">Net This Month</p>
                            <p className={`stat-value ${monthIncome - monthExpense >= 0 ? 'text-green' : 'text-red'}`}>
                                ${fmt(monthIncome - monthExpense)}
                            </p>
                            <p className="stat-sub">Income − Expenses</p>
                        </div>
                        <div style={{ background: 'rgba(6,182,212,0.15)', borderRadius: 8, padding: 8 }}>
                            <ArrowLeftRight size={20} color="var(--cyan)" />
                        </div>
                    </div>
                </div>
            </div>

            <div className="grid-2" style={{ marginBottom: 24 }}>
                {/* Area Chart */}
                <div className="card">
                    <p className="section-title" style={{ marginBottom: 20 }}>Income vs Expenses (6 months)</p>
                    <ResponsiveContainer width="100%" height={220}>
                        <AreaChart data={chartData} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
                            <defs>
                                <linearGradient id="gi" x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0%" stopColor="#10B981" stopOpacity={0.4} />
                                    <stop offset="100%" stopColor="#10B981" stopOpacity={0} />
                                </linearGradient>
                                <linearGradient id="ge" x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0%" stopColor="#EF4444" stopOpacity={0.4} />
                                    <stop offset="100%" stopColor="#EF4444" stopOpacity={0} />
                                </linearGradient>
                            </defs>
                            <XAxis dataKey="month" tick={{ fill: '#555D7C', fontSize: 12 }} axisLine={false} tickLine={false} />
                            <YAxis tick={{ fill: '#555D7C', fontSize: 12 }} axisLine={false} tickLine={false} width={50} />
                            <Tooltip
                                contentStyle={{ background: '#1E2330', border: '1px solid #2D3448', borderRadius: 8, fontSize: 13 }}
                                labelStyle={{ color: '#8B93B0' }}
                            />
                            <Area type="monotone" dataKey="income" stroke="#10B981" fill="url(#gi)" strokeWidth={2} name="Income" />
                            <Area type="monotone" dataKey="expenses" stroke="#EF4444" fill="url(#ge)" strokeWidth={2} name="Expenses" />
                        </AreaChart>
                    </ResponsiveContainer>
                </div>

                {/* Pie Chart */}
                <div className="card">
                    <p className="section-title" style={{ marginBottom: 20 }}>Expense Categories (this month)</p>
                    {pieData.length === 0 ? (
                        <div className="empty-state"><p className="empty-state-sub">No expense data this month</p></div>
                    ) : (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                            <ResponsiveContainer width={160} height={160}>
                                <PieChart>
                                    <Pie data={pieData} dataKey="value" cx="50%" cy="50%" innerRadius={40} outerRadius={70} paddingAngle={3}>
                                        {pieData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                                    </Pie>
                                    <Tooltip
                                        contentStyle={{ background: '#1E2330', border: '1px solid #2D3448', borderRadius: 8, fontSize: 13 }}
                                        formatter={(v: any) => [`$${fmt(v)}`, '']}
                                    />
                                </PieChart>
                            </ResponsiveContainer>
                            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
                                {pieData.slice(0, 5).map((d, i) => (
                                    <div key={d.name} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                            <div style={{ width: 8, height: 8, borderRadius: '50%', background: COLORS[i % COLORS.length] }} />
                                            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{d.name}</span>
                                        </div>
                                        <span style={{ fontSize: 12, fontWeight: 600 }}>${fmt(d.value)}</span>
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
                    <p className="section-title" style={{ marginBottom: 16 }}>Accounts</p>
                    {accounts.length === 0 ? (
                        <p className="text-muted text-sm">No accounts yet</p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                            {accounts.map(a => (
                                <div key={a.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid var(--border)' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                        <div style={{ width: 10, height: 10, borderRadius: '50%', background: a.color }} />
                                        <div>
                                            <p style={{ fontSize: 14, fontWeight: 500 }}>{a.name}</p>
                                            <p className="text-muted text-sm">{a.type.toLowerCase().replace('_', ' ')} · {a.currency.code}</p>
                                        </div>
                                    </div>
                                    <p style={{ fontSize: 15, fontWeight: 700, color: Number(a.balance) < 0 ? 'var(--red)' : 'var(--text-primary)' }}>
                                        {a.currency.symbol}{fmt(Number(a.balance))}
                                    </p>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Recent Transactions */}
                <div className="card">
                    <p className="section-title" style={{ marginBottom: 16 }}>Recent Transactions</p>
                    {recent.length === 0 ? (
                        <p className="text-muted text-sm">No transactions yet</p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                            {recent.map(t => (
                                <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid var(--border)' }}>
                                    <div>
                                        <p style={{ fontSize: 14, fontWeight: 500 }}>{t.category.name}</p>
                                        <p className="text-muted text-sm">{t.account.name} · {t.date}</p>
                                    </div>
                                    <p style={{ fontSize: 14, fontWeight: 700, color: t.type === 'INCOME' ? 'var(--green)' : 'var(--red)' }}>
                                        {t.type === 'INCOME' ? '+' : '-'}{t.account.currency.symbol}{fmt(Number(t.amount))}
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

export default Dashboard;
