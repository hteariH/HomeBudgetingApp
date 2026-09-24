import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowUpDown, ChevronDown } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useAccounts, useCategories, useFxSnapshot } from '../../api/hooks';
import { invalidateLedger } from '../../api/queryClient';
import type { RestAccount } from '../../api/types';
import { AccountPicker } from '../../components/AccountPicker';
import { AmountField } from '../../components/AmountField';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Field, Segmented } from '../../components/forms';
import { IconCircle } from '../../components/IconCircle';
import { CurrencyRegistry } from '../../lib/currencyRegistry';
import { todayIso } from '../../lib/dates';
import { sanitizeExpression } from '../../lib/expressionInput';
import { AppRoutes } from '../../lib/routes';
import { prefs } from '../../prefs';
import { splitAccounts } from '../accounts/accounts.logic';
import {
    mostUsed,
    occurredAtFor,
    positiveAmount,
    rateHint,
    suggestedDestination,
    transferAmounts,
    type EntryMode,
} from './quickadd.logic';

const symbolOf = (a?: RestAccount | null) => (a ? CurrencyRegistry.lookup(a.currency.code)?.symbol ?? a.currency.symbol : '');

/** Which account a new entry starts on: the one last used, else the first live one. */
function defaultAccount(active: RestAccount[]): RestAccount | undefined {
    const stored = prefs.defaultAccountId;
    return active.find(a => a.id === stored) ?? active[0];
}

const AccountButton: React.FC<{ label: string; account?: RestAccount; onClick: () => void }> = ({ label, account, onClick }) => (
    <button
        type="button"
        onClick={onClick}
        className="flex w-full items-center gap-3 rounded-token border border-line bg-base px-3 py-2 text-left transition-colors hover:border-line-strong"
    >
        <IconCircle icon={account?.icon} color={account?.color} size={32} />
        <span className="min-w-0 flex-1">
            <span className="block text-xs text-ink-dim">{label}</span>
            <span className="block truncate text-sm font-medium text-ink">{account ? `${account.name} · ${account.currency.code}` : '—'}</span>
        </span>
        <ChevronDown size={16} className="text-ink-dim" />
    </button>
);

/**
 * Recording an expense, an income or a transfer. One form, two shapes: the full entry page
 * (the big amount, category chips) and the panel that sits beside the overview and the
 * ledger on a wide window (compact fields). The panel clears itself after a save and stays,
 * because entering several in a row is what someone at a desk is most likely doing.
 * Port of QuickAddScreen.kt, QuickEntryPanel.kt and their two view models.
 */
export const EntryForm: React.FC<{
    variant: 'page' | 'panel';
    initialMode?: EntryMode;
    initialCategoryId?: number;
    onSaved?: () => void;
}> = ({ variant, initialMode = 'EXPENSE', initialCategoryId, onSaved }) => {
    const t = useT();
    const page = variant === 'page';
    const [mode, setMode] = useState<EntryMode>(initialMode);
    const { data: allAccounts = [] } = useAccounts();
    const active = useMemo(() => splitAccounts(allAccounts).active, [allAccounts]);
    const categoryType = mode === 'INCOME' ? 'INCOME' : 'EXPENSE';
    const { data: allCategories = [] } = useCategories();
    const categories = useMemo(
        () => allCategories.filter(c => c.type === categoryType && !c.archived),
        [allCategories, categoryType],
    );
    const { data: recent = [] } = useQuery({
        queryKey: ['transactions', 'recent-sample'],
        queryFn: async () => (await endpoints.transactions({ size: 100 })).content,
        staleTime: 60_000,
    });
    const fx = useFxSnapshot();

    // ── ledger entry ────────────────────────────────────────────────────────
    const [accountId, setAccountId] = useState<number | null>(null);
    const [categoryId, setCategoryId] = useState<number | null>(initialCategoryId ?? null);
    const [amount, setAmount] = useState('');
    const [day, setDay] = useState(todayIso());
    const [note, setNote] = useState('');
    const [picking, setPicking] = useState<null | 'account' | 'from' | 'to'>(null);

    // ── transfer ────────────────────────────────────────────────────────────
    const [fromId, setFromId] = useState<number | null>(null);
    const [toId, setToId] = useState<number | null>(null);
    const [amountTo, setAmountTo] = useState('');
    const [amountToEdited, setAmountToEdited] = useState(false);
    const [fee, setFee] = useState('');
    const [showFee, setShowFee] = useState(false);
    const [transferError, setTransferError] = useState<string | null>(null);

    const account = active.find(a => a.id === accountId) ?? defaultAccount(active);
    const from = active.find(a => a.id === fromId) ?? account;
    const to = active.find(a => a.id === toId) ?? active.find(a => a.id !== from?.id);
    const sameCurrency = from?.currency.code === to?.currency.code;
    const shownCategories = page ? mostUsed(categories, recent, 8) : categories;
    const moreCategories = page ? categories.filter(c => !shownCategories.includes(c)) : [];

    // The destination follows the source at the stored rate until the user types their own.
    useEffect(() => {
        if (mode === 'TRANSFER' && from && to && !sameCurrency && !amountToEdited) {
            setAmountTo(suggestedDestination(amount, from.currency.code, to.currency.code, fx));
        }
    }, [mode, amount, from, to, sameCurrency, amountToEdited, fx]);

    const reset = () => {
        setAmount('');
        setNote('');
        setAmountTo('');
        setAmountToEdited(false);
        setFee('');
        setShowFee(false);
        setTransferError(null);
        if (!initialCategoryId) setCategoryId(null);
    };

    const afterSave = async () => {
        await invalidateLedger();
        reset();
        onSaved?.();
    };

    const saveEntry = useMutation({
        mutationFn: async () => {
            const value = positiveAmount(amount, account!.currency.code)!;
            await endpoints.createTransaction({
                accountId: account!.id,
                categoryId,
                amount: value.toFixed(CurrencyRegistry.decimalsOrFallback(account!.currency.code)),
                type: mode === 'INCOME' ? 'INCOME' : 'EXPENSE',
                date: day,
                occurredAt: occurredAtFor(day),
                description: note.trim() || null,
            });
            // The next entry opens on the account this one used.
            prefs.setDefaultAccountId(account!.id);
        },
        onSuccess: afterSave,
    });

    const saveTransfer = useMutation({
        mutationFn: async () => {
            const amounts = transferAmounts({
                fromExpr: amount,
                toExpr: sameCurrency ? '' : amountTo,
                feeExpr: fee,
                from: from!.currency.code,
                to: to!.currency.code,
                fx,
            });
            if (!amounts.ok) {
                setTransferError(t(amounts.error));
                throw new Error(amounts.error);
            }
            setTransferError(null);
            await endpoints.createTransfer({
                fromAccountId: from!.id,
                toAccountId: to!.id,
                fromAmount: amounts.fromAmount,
                toAmount: amounts.toAmount,
                fee: amounts.fee,
                date: day,
                occurredAt: occurredAtFor(day),
                description: note.trim() || null,
            });
        },
        onSuccess: afterSave,
    });

    const saving = saveEntry.isPending || saveTransfer.isPending;
    const entryValid = !!account && categoryId !== null && positiveAmount(amount, account.currency.code) !== null;
    const transferValid = !!from && !!to && from.id !== to.id && amount.trim() !== '';
    const canSave = !saving && (mode === 'TRANSFER' ? transferValid : entryValid);
    const save = () => canSave && (mode === 'TRANSFER' ? saveTransfer.mutate() : saveEntry.mutate());

    if (active.length === 0) {
        return (
            <div className="rounded-token-lg border border-line bg-card p-5 text-sm text-ink-soft">
                {t('accounts_empty')}{' '}
                <Link to={AppRoutes.accountEdit()} className="font-semibold text-brand-light">
                    {t('accounts_new')}
                </Link>
            </div>
        );
    }

    const amountInput = (label: string, value: string, onChange: (v: string) => void, code?: string) => (
        <Field label={label}>
            <div className="relative">
                <input
                    className="form-control pr-14"
                    inputMode="decimal"
                    value={value}
                    onChange={e => onChange(sanitizeExpression(e.target.value))}
                    onKeyDown={e => e.key === 'Enter' && save()}
                />
                {code && <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-ink-dim">{code}</span>}
            </div>
        </Field>
    );

    return (
        <div className={`flex flex-col gap-4 ${page ? '' : 'rounded-token-lg border border-line bg-card p-4'}`}>
            <Segmented<EntryMode>
                className={page ? 'self-center' : ''}
                value={mode}
                onChange={m => {
                    setMode(m);
                    setCategoryId(null);
                }}
                options={[
                    { value: 'EXPENSE', label: t('dashboard_add_expense') },
                    { value: 'INCOME', label: t('dashboard_add_income') },
                    { value: 'TRANSFER', label: t('transfer_mode') },
                ]}
            />

            {page ? (
                <AmountField
                    value={amount}
                    onChange={setAmount}
                    decimals={CurrencyRegistry.decimalsOrFallback((mode === 'TRANSFER' ? from : account)?.currency.code ?? 'USD')}
                    symbol={symbolOf(mode === 'TRANSFER' ? from : account)}
                    autoFocus
                    onSubmit={save}
                    label={t('common_amount')}
                />
            ) : (
                amountInput(
                    mode === 'TRANSFER' && !sameCurrency ? t('transfer_amount_from') : t('common_amount'),
                    amount,
                    setAmount,
                    (mode === 'TRANSFER' ? from : account)?.currency.code,
                )
            )}

            {mode === 'TRANSFER' ? (
                <>
                    <div className="flex flex-col gap-2">
                        <AccountButton label={t('transfer_from')} account={from} onClick={() => setPicking('from')} />
                        <button
                            type="button"
                            className="btn btn-ghost btn-icon self-center"
                            aria-label={t('a11y_swap_accounts')}
                            title={t('a11y_swap_accounts')}
                            onClick={() => {
                                setFromId(to?.id ?? null);
                                setToId(from?.id ?? null);
                                setAmountToEdited(false);
                            }}
                        >
                            <ArrowUpDown size={16} />
                        </button>
                        <AccountButton label={t('transfer_to')} account={to} onClick={() => setPicking('to')} />
                    </div>
                    {from && to && from.id === to.id && <div className="text-xs text-bad">{t('transfer_same_account')}</div>}
                    {!sameCurrency && from && to && (
                        <div>
                            {amountInput(t('transfer_amount_received'), amountTo, v => {
                                setAmountTo(v);
                                setAmountToEdited(true);
                            }, to.currency.code)}
                            {rateHint(from.currency.code, to.currency.code, fx) && (
                                <div className="mt-1 text-xs text-ink-dim">{rateHint(from.currency.code, to.currency.code, fx)}</div>
                            )}
                        </div>
                    )}
                    {showFee ? (
                        amountInput(t('transfer_fee'), fee, setFee, from?.currency.code)
                    ) : (
                        <button type="button" className="btn btn-ghost btn-sm self-start" onClick={() => setShowFee(true)}>
                            {t('transfer_add_fee')}
                        </button>
                    )}
                </>
            ) : (
                <>
                    <AccountButton label={t('common_account')} account={account} onClick={() => setPicking('account')} />
                    {categories.length === 0 ? (
                        <div className="rounded-token border border-dashed border-line p-3 text-sm text-ink-soft">
                            {t('quickadd_pick_category')}{' '}
                            <Link to={AppRoutes.categoryEdit(undefined, categoryType)} className="font-semibold text-brand-light">
                                {t('categories_new')}
                            </Link>
                        </div>
                    ) : page ? (
                        <div>
                            <div className="form-label mb-2">{t('common_category')}</div>
                            <div className="flex flex-wrap gap-2">
                                {[...shownCategories, ...moreCategories].map(c => (
                                    <button
                                        key={c.id}
                                        type="button"
                                        aria-pressed={c.id === categoryId}
                                        onClick={() => setCategoryId(c.id)}
                                        className={`flex items-center gap-2 rounded-full border py-1 pl-1 pr-3 text-sm transition-colors ${
                                            c.id === categoryId
                                                ? 'border-brand bg-brand/15 text-ink'
                                                : 'border-line text-ink-soft hover:border-line-strong hover:text-ink'
                                        }`}
                                    >
                                        <IconCircle icon={c.icon} color={c.color} size={24} />
                                        {c.name}
                                    </button>
                                ))}
                            </div>
                        </div>
                    ) : (
                        <Field label={t('common_category')}>
                            <select
                                className="form-control"
                                value={categoryId ?? ''}
                                onChange={e => setCategoryId(Number(e.target.value) || null)}
                            >
                                <option value="">{t('quickadd_pick_category')}</option>
                                {categories.map(c => (
                                    <option key={c.id} value={c.id}>
                                        {c.name}
                                    </option>
                                ))}
                            </select>
                        </Field>
                    )}
                </>
            )}

            <div className="grid grid-cols-[minmax(0,160px)_minmax(0,1fr)] gap-3">
                <Field label={t('quickadd_date')}>
                    <input type="date" className="form-control" value={day} max="9999-12-31" onChange={e => e.target.value && setDay(e.target.value)} />
                </Field>
                <Field label={t('quickadd_note')}>
                    <input
                        className="form-control"
                        value={note}
                        placeholder={t('quickadd_note_hint')}
                        onChange={e => setNote(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && save()}
                    />
                </Field>
            </div>

            {transferError && <div className="text-sm text-bad">{transferError}</div>}
            <ErrorBanner error={saveEntry.error ?? (transferError ? null : saveTransfer.error)} />
            <button type="button" className={`btn btn-primary ${page ? 'btn-lg' : ''}`} disabled={!canSave} onClick={save}>
                {saving ? t('common_saving') : t('action_save')}
            </button>

            <AccountPicker
                open={picking !== null}
                accounts={active}
                selectedId={(picking === 'from' ? from : picking === 'to' ? to : account)?.id ?? null}
                onClose={() => setPicking(null)}
                onPick={id => {
                    if (picking === 'from') setFromId(id);
                    else if (picking === 'to') setToId(id);
                    else setAccountId(id);
                    setAmountToEdited(false);
                }}
            />
        </div>
    );
};
