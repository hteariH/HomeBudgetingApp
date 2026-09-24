import React from 'react';
import type { Money } from '../lib/money';
import { IconCircle, safeColor } from './IconCircle';
import { MoneyText } from './MoneyText';

export interface BreakdownSlice {
    key: string;
    label: string;
    icon: string;
    color: string;
    amount: Money;
}

/** Horizontal bars scaled to the largest entry. Port of CategoryBreakdownChart.kt. */
export const CategoryBreakdownChart: React.FC<{ slices: BreakdownSlice[] }> = ({ slices }) => {
    if (slices.length === 0) return null;
    const max = slices.reduce((m, s) => (s.amount.amount.gt(m) ? s.amount.amount.toNumber() : m), 0) || 1;
    return (
        <div className="flex flex-col gap-2.5">
            {slices.map(s => {
                const fraction = Math.min(1, Math.max(0, s.amount.amount.toNumber() / max));
                return (
                    <div key={s.key} className="flex flex-col gap-1">
                        <div className="flex items-center gap-2">
                            <IconCircle icon={s.icon} color={s.color} size={24} />
                            <span className="min-w-0 flex-1 truncate text-sm text-ink">{s.label}</span>
                            <MoneyText money={s.amount} className="text-sm text-ink" />
                        </div>
                        <div className="h-2 overflow-hidden rounded-full bg-raised">
                            <div
                                className="h-full rounded-full transition-[width] duration-500"
                                style={{ width: `${fraction * 100}%`, background: safeColor(s.color) }}
                            />
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

/** One stacked bar of proportions. */
export const StackedShareBar: React.FC<{ slices: BreakdownSlice[] }> = ({ slices }) => {
    const total = slices.reduce((t, s) => t + Math.max(0, s.amount.amount.toNumber()), 0) || 1;
    return (
        <div className="flex h-2.5 overflow-hidden rounded-full">
            {slices.map(s => (
                <div
                    key={s.key}
                    style={{ flexGrow: Math.max(0.001, s.amount.amount.toNumber() / total), background: safeColor(s.color) }}
                />
            ))}
        </div>
    );
};
