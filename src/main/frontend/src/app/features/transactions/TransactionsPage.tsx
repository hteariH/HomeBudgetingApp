import React, { useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { FilterX, ReceiptText, Search } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useAccounts, useHousehold } from '../../api/hooks';
import { invalidateLedger } from '../../api/queryClient';
import type { RestTransaction, TransactionType } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { RowMenu } from '../../components/RowMenu';
import { isoDate } from '../../lib/dates';
import { formatMediumDate } from '../../lib/format';
import { Page, PageLoading } from '../../shell/Page';
import { BREAKPOINTS, useMinWidth } from '../../shell/useMediaQuery';
import { initialsOf } from '../household/initials';
import { QuickEntryPanel } from '../quickadd/QuickEntryPanel';
import { collapseTransfers, counterparts, groupByDay, isDebitLeg, matchesQuery, type Period } from './ledger.logic';
import { TransactionRow } from './TransactionRow';
import { useLedger, useLedgerFilter } from './useLedger';

const Select: React.FC<{
    value: string;
    onChange: (v: string) => void;
    active: boolean;
    label: string;
    children: React.ReactNode;
}> = ({ value, onChange, active, label, children }) => (
    <select
        aria-label={label}
        value={value}
        onChange={e => onChange(e.target.value)}
        className={`form-control w-auto min-w-0 flex-1 rounded-full py-1.5 text-sm ${active ? 'border-brand text-brand-light' : ''}`}
    >
        {children}
    </select>
);

/** The ledger: filters, search, rows grouped by day. Port of TransactionsScreen.kt. */
const TransactionsPage: React.FC = () => {
    const t = useT();
    const [filter, setFilter, clearFilters] = useLedgerFilter();
    const { data: rows = [], isLoading, error } = useLedger(filter);
    const { data: accounts = [] } = useAccounts();
    const members = useHousehold().data?.members ?? [];
    // Who wrote a row only means something once the books are shared.
    const shared = members.length > 1;
    const inlineEntry = useMinWidth(BREAKPOINTS.inlineEntry);
    const [deleteTarget, setDeleteTarget] = useState<RestTransaction | null>(null);

    const { groups, others } = useMemo(() => {
        const others = counterparts(rows);
        const shown = collapseTransfers(rows).filter(r => matchesQuery(r, filter.query, others.get(r.id)));
        return { groups: groupByDay(shown), others };
    }, [rows, filter.query]);

    const remove = useMutation({
        // Deleting either leg of a transfer removes both on the server.
        mutationFn: (tx: RestTransaction) =>
            tx.transferGroupId ? endpoints.deleteTransfer(tx.id) : endpoints.deleteTransaction(tx.id),
        onSuccess: async () => {
            setDeleteTarget(null);
            await invalidateLedger();
        },
    });

    const duplicate = useMutation({
        // Quick repeat: the same entry, dated now. Transfers are not offered this.
        mutationFn: (tx: RestTransaction) =>
            endpoints.createTransaction({
                accountId: tx.account.id,
                categoryId: tx.category?.id ?? null,
                amount: tx.amount.replace('-', ''),
                type: tx.type,
                date: isoDate(new Date()),
                occurredAt: new Date().toISOString(),
                description: tx.description ?? null,
            }),
        onSuccess: invalidateLedger,
    });

    const today = isoDate(new Date());
    const yesterday = isoDate(new Date(Date.now() - 86_400_000));
    const dayTitle = (day: string) =>
        day === today ? t('transactions_today') : day === yesterday ? t('transactions_yesterday') : formatMediumDate(day);
    const filtered =
        filter.period !== 'ALL' || !!filter.accountId || !!filter.type || !!filter.categoryId || !!filter.createdBy || !!filter.query;
    // A transfer is two rows; it belongs to whoever recorded the leg the money left from.
    const authorProps = (tx: RestTransaction) => {
        const other = others.get(tx.id);
        const leg = tx.type === 'TRANSFER' && other && isDebitLeg(other) && !isDebitLeg(tx) ? other : tx;
        const author = shared ? members.find(m => m.userId === leg.createdByUserId) : undefined;
        return author && { author: initialsOf(author.firstName, author.lastName), authorName: `${author.firstName} ${author.lastName}`.trim() };
    };

    return (
        <Page
            title={t('transactions_title')}
            actions={
                filtered && (
                    <button type="button" className="btn btn-ghost btn-sm" onClick={clearFilters}>
                        <FilterX size={16} /> {t('action_clear_filters')}
                    </button>
                )
            }
        >
            <div className={inlineEntry ? 'grid grid-cols-[minmax(0,1fr)_400px] items-start gap-6' : ''}>
                <div className="min-w-0">
                    <div className="relative mb-3">
                        <Search size={16} className="input-icon" />
                        <input
                            className="form-control form-control-icon"
                            value={filter.query}
                            onChange={e => setFilter({ query: e.target.value })}
                            placeholder={t('transactions_search')}
                            aria-label={t('transactions_search')}
                        />
                    </div>
                    <div className="mb-4 flex flex-wrap gap-2">
                        <Select
                            label={t('filter_period')}
                            value={filter.period}
                            active={filter.period !== 'ALL'}
                            onChange={v => setFilter({ period: v as Period })}
                        >
                            <option value="ALL">{t('period_all')}</option>
                            <option value="TODAY">{t('period_today')}</option>
                            <option value="WEEK">{t('period_week')}</option>
                            <option value="MONTH">{t('period_month')}</option>
                            <option value="YEAR">{t('period_year')}</option>
                        </Select>
                        <Select
                            label={t('filter_type')}
                            value={filter.type ?? ''}
                            active={!!filter.type}
                            onChange={v => setFilter({ type: (v || undefined) as TransactionType | undefined })}
                        >
                            <option value="">{t('filter_type')}</option>
                            <option value="EXPENSE">{t('dashboard_add_expense')}</option>
                            <option value="INCOME">{t('dashboard_add_income')}</option>
                            <option value="TRANSFER">{t('dashboard_transfer')}</option>
                        </Select>
                        <Select
                            label={t('filter_account')}
                            value={String(filter.accountId ?? '')}
                            active={!!filter.accountId}
                            onChange={v => setFilter({ accountId: Number(v) || undefined })}
                        >
                            <option value="">{t('filter_account')}</option>
                            {accounts.map(a => (
                                <option key={a.id} value={a.id}>
                                    {a.name}
                                </option>
                            ))}
                        </Select>
                        {shared && (
                            <Select
                                label={t('filter_who')}
                                value={String(filter.createdBy ?? '')}
                                active={!!filter.createdBy}
                                onChange={v => setFilter({ createdBy: Number(v) || undefined })}
                            >
                                <option value="">{t('filter_who')}</option>
                                {members.map(m => (
                                    <option key={m.userId} value={m.userId}>
                                        {`${m.firstName} ${m.lastName}`.trim() || m.email}
                                    </option>
                                ))}
                            </Select>
                        )}
                    </div>
                    <ErrorBanner error={error ?? remove.error ?? duplicate.error} fallbackKey="common_load_failed" className="mb-4" />
                    {isLoading ? (
                        <PageLoading />
                    ) : groups.length === 0 ? (
                        <EmptyState icon={ReceiptText} title={t('transactions_empty')} />
                    ) : (
                        <Card className="overflow-hidden">
                            {groups.map(g => (
                                <div key={g.day}>
                                    <div className="border-t border-line bg-surface/60 px-5 py-2 text-xs font-semibold text-ink-soft first:border-t-0">
                                        {dayTitle(g.day)}
                                    </div>
                                    {g.rows.map(tx => (
                                        <TransactionRow
                                            key={tx.id}
                                            tx={tx}
                                            counterpart={others.get(tx.id)}
                                            {...authorProps(tx)}
                                            trailing={
                                                <RowMenu
                                                    label={t('common_actions')}
                                                    items={[
                                                        ...(tx.transferGroupId
                                                            ? []
                                                            : [{ label: t('action_duplicate'), onClick: () => duplicate.mutate(tx) }]),
                                                        { label: t('action_delete'), danger: true, onClick: () => setDeleteTarget(tx) },
                                                    ]}
                                                />
                                            }
                                        />
                                    ))}
                                </div>
                            ))}
                        </Card>
                    )}
                </div>
                {/* On a wide window the ledger is where you look when you want to record
                    something, so the entry form sits beside it rather than a screen away. */}
                {inlineEntry && (
                    <div className="sticky top-[84px]">
                        <QuickEntryPanel />
                    </div>
                )}
            </div>
            <ConfirmDialog
                open={deleteTarget !== null}
                title={t('action_delete')}
                message={t('transaction_delete_confirm')}
                destructive
                busy={remove.isPending}
                onConfirm={() => deleteTarget && remove.mutate(deleteTarget)}
                onCancel={() => setDeleteTarget(null)}
            />
        </Page>
    );
};

export default TransactionsPage;
