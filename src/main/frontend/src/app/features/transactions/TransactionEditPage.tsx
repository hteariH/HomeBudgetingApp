import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Trash2 } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useCategories } from '../../api/hooks';
import { invalidateLedger, qk } from '../../api/queryClient';
import { AmountField } from '../../components/AmountField';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, Field } from '../../components/forms';
import { IconCircle } from '../../components/IconCircle';
import { MoneyText } from '../../components/MoneyText';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import { isoDate } from '../../lib/dates';
import { formatMediumDate } from '../../lib/format';
import { money, parseAmount, toTrimmedString } from '../../lib/money';
import { AppRoutes } from '../../lib/routes';
import { BackButton, Page, PageLoading } from '../../shell/Page';
import { ReceiptView } from '../receipts/ReceiptView';
import { occurredAtFor, positiveAmount } from '../quickadd/quickadd.logic';

/**
 * One transaction. The server lets an edit change the amount, the category (of the same
 * kind), the day and the note; the account and the type are fixed once a row exists, so
 * they are shown rather than offered. A transfer leg cannot be edited at all - the server
 * refuses, to keep both legs consistent - so a transfer is shown, and can be deleted.
 */
const TransactionEditPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const id = Number(useParams().id);
    const { data: tx, isLoading, error: loadError } = useQuery({
        queryKey: qk.transaction(id),
        queryFn: () => endpoints.transaction(id),
        enabled: Number.isFinite(id) && id > 0,
    });
    const isTransfer = !!tx?.transferGroupId;
    const { data: allCategories = [] } = useCategories();
    const categories = allCategories.filter(c => c.type === (tx?.type === 'INCOME' ? 'INCOME' : 'EXPENSE') && !c.archived);

    const [amount, setAmount] = useState('');
    const [categoryId, setCategoryId] = useState<number | null>(null);
    const [day, setDay] = useState('');
    const [note, setNote] = useState('');
    const [loaded, setLoaded] = useState(false);
    const [confirmDelete, setConfirmDelete] = useState(false);

    useEffect(() => {
        if (tx && !loaded) {
            setAmount(toTrimmedString(parseAmount(tx.accountAmount).abs()));
            setCategoryId(tx.category?.id ?? null);
            setDay(isoDate(new Date(tx.occurredAt)));
            setNote(tx.description ?? '');
            setLoaded(true);
        }
    }, [tx, loaded]);

    const done = async () => {
        await invalidateLedger();
        window.history.state?.idx > 0 ? navigate(-1) : navigate(AppRoutes.transactions, { replace: true });
    };

    const save = useMutation({
        mutationFn: () => {
            const code = tx!.account.currency.code;
            return endpoints.updateTransaction(id, {
                categoryId,
                amount: positiveAmount(amount, code)!.toFixed(CurrencyRegistry.decimalsOrFallback(code)),
                date: day,
                occurredAt: occurredAtFor(day, new Date(tx!.occurredAt)),
                description: note.trim() || null,
            });
        },
        onSuccess: done,
    });

    const remove = useMutation({
        mutationFn: () => (isTransfer ? endpoints.deleteTransfer(id) : endpoints.deleteTransaction(id)),
        onSuccess: done,
    });

    if (isLoading) return <PageLoading />;
    if (!tx) {
        return (
            <Page width="form" leading={<BackButton fallback={AppRoutes.transactions} label={t('action_back')} />}>
                <ErrorBanner error={loadError} fallbackKey="common_load_failed" className="mb-4" />
                <EmptyState title={t('transactions_empty')} />
            </Page>
        );
    }

    const code = tx.account.currency.code;
    const canSave = !isTransfer && !save.isPending && categoryId !== null && positiveAmount(amount, code) !== null;

    return (
        <Page
            width="form"
            leading={<BackButton fallback={AppRoutes.transactions} label={t('action_back')} />}
            title={t('transaction_edit_title')}
            actions={
                <>
                    <button
                        type="button"
                        className="btn btn-ghost btn-icon"
                        aria-label={t('action_delete')}
                        title={t('action_delete')}
                        onClick={() => setConfirmDelete(true)}
                    >
                        <Trash2 size={18} />
                    </button>
                    {!isTransfer && (
                        <button type="button" className="btn btn-primary" disabled={!canSave} onClick={() => save.mutate()}>
                            {save.isPending ? t('common_saving') : t('action_save')}
                        </button>
                    )}
                </>
            }
        >
            <ErrorBanner error={save.error ?? remove.error} className="mb-4" />
            {isTransfer ? (
                <Card className="flex flex-col gap-3 p-5 text-sm">
                    <div className="flex items-center gap-3">
                        <IconCircle icon="currency_exchange" color="#94A3B8" size={40} />
                        <div className="flex-1">
                            <div className="font-medium text-ink">{t('dashboard_transfer')}</div>
                            <div className="text-ink-soft">{tx.account.name}</div>
                        </div>
                        <MoneyText money={money(tx.accountAmount, code)} type="TRANSFER" className="text-lg font-semibold" />
                    </div>
                    <div className="text-ink-soft">{formatMediumDate(isoDate(new Date(tx.occurredAt)))}</div>
                    {tx.description && <div className="text-ink">{tx.description}</div>}
                    <div className="rounded-token bg-raised px-3 py-2 text-xs text-ink-soft">{t('transaction_transfer_readonly')}</div>
                </Card>
            ) : (
                <Card className="flex flex-col gap-5 p-5">
                    <div className="flex items-center justify-between text-sm">
                        <span className={tx.type === 'INCOME' ? 'font-medium text-ok' : 'font-medium text-bad'}>
                            {t(tx.type === 'INCOME' ? 'dashboard_add_income' : 'dashboard_add_expense')}
                        </span>
                        <span className="text-ink-soft">
                            {tx.account.name} · {code}
                            {tx.source === 'MCP' && (
                                <>
                                    {' · '}
                                    <span>{t('transaction_via_assistant')}</span>
                                </>
                            )}
                        </span>
                    </div>
                    <AmountField
                        value={amount}
                        onChange={setAmount}
                        decimals={CurrencyRegistry.decimalsOrFallback(code)}
                        symbol={CurrencyRegistry.lookup(code)?.symbol ?? tx.account.currency.symbol}
                        onSubmit={() => canSave && save.mutate()}
                        label={t('common_amount')}
                    />
                    <div>
                        <div className="form-label mb-2">{t('common_category')}</div>
                        <div className="flex flex-wrap gap-2">
                            {categories.map(c => (
                                <button
                                    key={c.id}
                                    type="button"
                                    aria-pressed={c.id === categoryId}
                                    onClick={() => setCategoryId(c.id)}
                                    className={`flex items-center gap-2 rounded-full border py-1 pl-1 pr-3 text-sm transition-colors ${
                                        c.id === categoryId ? 'border-brand bg-brand/15 text-ink' : 'border-line text-ink-soft hover:text-ink'
                                    }`}
                                >
                                    <IconCircle icon={c.icon} color={c.color} size={24} />
                                    {c.name}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div className="grid grid-cols-[minmax(0,160px)_minmax(0,1fr)] gap-3">
                        <Field label={t('quickadd_date')}>
                            <input type="date" className="form-control" value={day} onChange={e => e.target.value && setDay(e.target.value)} />
                        </Field>
                        <Field label={t('quickadd_note')}>
                            <input className="form-control" value={note} placeholder={t('quickadd_note_hint')} onChange={e => setNote(e.target.value)} />
                        </Field>
                    </div>
                </Card>
            )}
            {tx.receiptUuid && <ReceiptView uuid={tx.receiptUuid} />}
            <ConfirmDialog
                open={confirmDelete}
                title={t('action_delete')}
                message={t('transaction_delete_confirm')}
                destructive
                busy={remove.isPending}
                onConfirm={() => remove.mutate()}
                onCancel={() => setConfirmDelete(false)}
            />
        </Page>
    );
};

export default TransactionEditPage;
