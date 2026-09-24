import React from 'react';
import type { TransactionType } from '../api/types';
import { formatMoney } from '../lib/format';
import type { Money } from '../lib/money';

/** Colour by what the money did, not by its sign - as MoneyText.kt does. */
export const moneyColor = (type?: TransactionType | null) =>
    type === 'INCOME' ? 'text-ok' : type === 'EXPENSE' ? 'text-bad' : type === 'TRANSFER' ? 'text-info' : '';

export const MoneyText: React.FC<{
    money: Money;
    type?: TransactionType | null;
    showSign?: boolean;
    className?: string;
}> = ({ money, type, showSign = false, className = '' }) => {
    const sign =
        showSign && !money.amount.isZero() ? (type === 'INCOME' ? '+' : type === 'EXPENSE' ? '−' : '') : '';
    return (
        <span className={`tabular-nums ${moneyColor(type)} ${className}`}>
            {sign}
            {formatMoney(money)}
        </span>
    );
};
