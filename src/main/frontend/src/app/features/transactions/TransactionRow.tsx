import React from 'react';
import { Link } from 'react-router-dom';
import { useT } from '../../../i18n';
import type { RestTransaction } from '../../api/types';
import { NEUTRAL_COLOR } from '../../components/forms';
import { IconCircle } from '../../components/IconCircle';
import { MoneyText } from '../../components/MoneyText';
import { AppRoutes } from '../../lib/routes';
import { money } from '../../lib/money';

/** What a transfer leg moved, without the sign that says which way. */
const legAmount = (leg: RestTransaction) => {
    const m = money(leg.accountAmount, leg.account.currency.code);
    return { ...m, amount: m.amount.abs() };
};

/**
 * One row of the ledger. A transfer reads "source → destination" whichever leg survived
 * the filter, and a cross-currency one shows both amounts, source over destination.
 */
export const TransactionRow: React.FC<{
    tx: RestTransaction;
    counterpart?: RestTransaction;
    trailing?: React.ReactNode;
    /** Initials of whoever recorded it; given only when the household has more than one member. */
    author?: string;
    /** The author's full name, for the badge's tooltip. */
    authorName?: string;
}> = ({ tx, counterpart, trailing, author, authorName }) => {
    const t = useT();
    const isTransfer = tx.type === 'TRANSFER';
    const amount = money(tx.accountAmount, tx.account.currency.code);
    const outgoing = amount.amount.isNegative();
    const source = isTransfer && counterpart ? (outgoing ? tx : counterpart) : tx;
    const dest = isTransfer && counterpart ? (outgoing ? counterpart : tx) : undefined;

    const title = isTransfer
        ? outgoing
            ? `${tx.account.name} → ${counterpart?.account.name ?? ''}`
            : `${counterpart?.account.name ?? ''} → ${tx.account.name}`
        : tx.category?.name ?? tx.account.name;
    const subtitle = tx.description?.trim() ? tx.description : isTransfer ? t('dashboard_transfer') : tx.account.name;
    const crossCurrency = isTransfer && dest && dest.account.currency.code !== source.account.currency.code;

    return (
        <Link
            to={AppRoutes.transactionEdit(tx.id)}
            className="group flex items-center gap-3 px-5 py-2.5 transition-colors hover:bg-raised"
        >
            <IconCircle
                icon={isTransfer ? 'currency_exchange' : tx.category?.icon ?? 'more_horiz'}
                color={isTransfer ? NEUTRAL_COLOR : tx.category?.color ?? NEUTRAL_COLOR}
                size={36}
            />
            {author && (
                <span
                    title={authorName}
                    className="flex h-6 min-w-6 shrink-0 items-center justify-center rounded-full bg-raised px-1 text-[10px] font-semibold text-ink-soft"
                >
                    {author}
                </span>
            )}
            <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-ink">{title}</span>
                <span className="block truncate text-xs text-ink-soft">{subtitle}</span>
            </span>
            {crossCurrency ? (
                <span className="flex flex-col items-end">
                    <MoneyText money={legAmount(source)} type="TRANSFER" className="text-sm font-semibold" />
                    <MoneyText money={legAmount(dest!)} type="TRANSFER" className="text-xs" />
                </span>
            ) : (
                <MoneyText
                    money={isTransfer ? legAmount(tx) : amount}
                    type={tx.type}
                    showSign
                    className="text-sm font-semibold"
                />
            )}
            {trailing}
        </Link>
    );
};
