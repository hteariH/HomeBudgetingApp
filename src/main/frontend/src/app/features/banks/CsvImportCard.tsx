import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { FileUp } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { invalidateLedger } from '../../api/queryClient';
import type { RestAccount, RestCategory, RestCsvMapping, RestCsvPreview, RestImportResult } from '../../api/types';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, Field } from '../../components/forms';
import { importSelections, suggestedChoices, type Choice } from './banks.logic';
import { ImportRows } from './ImportRows';

const ROWS_SHOWN = 200;
const SKIPPED_SHOWN = 5;

/**
 * Importing a bank's CSV statement. Always offered, unlike the connected import: a file needs
 * no connection, which is the point of it - it reaches the banks that have no API at all.
 * Port of CsvImportCard.kt and CsvImportViewModel.kt.
 */
export const CsvImportCard: React.FC<{ accounts: RestAccount[]; categories: RestCategory[] }> = ({ accounts, categories }) => {
    const t = useT();
    const [accountId, setAccountId] = useState<number | null>(null);
    const [file, setFile] = useState<File | null>(null);
    const [preview, setPreview] = useState<RestCsvPreview | null>(null);
    const [choices, setChoices] = useState<Record<string, Choice | undefined>>({});
    const [showMapping, setShowMapping] = useState(false);
    const [result, setResult] = useState<RestImportResult | null>(null);
    const account = accounts.find(a => a.id === accountId);
    const fresh = (preview?.rows ?? []).filter(r => !r.alreadyImported);

    const reset = () => {
        setPreview(null);
        setChoices({});
        setResult(null);
    };

    // A file that will not parse comes back as a 422 carrying the parser's own complaint,
    // which ErrorBanner shows as it is.
    const read = useMutation({
        mutationFn: (mapping?: RestCsvMapping) => endpoints.previewCsv(accountId!, file!, mapping),
        onMutate: () => setResult(null),
        onSuccess: p => {
            setPreview(p);
            setChoices(suggestedChoices(p.rows ?? []));
        },
        onError: () => setPreview(null),
    });

    const confirm = useMutation({
        mutationFn: () => {
            const { categories: cats, transfers } = importSelections(fresh.map(r => r.externalId), choices);
            return endpoints.confirmCsv({ accountId: accountId!, rows: fresh, categories: cats, transfers });
        },
        onSuccess: async r => {
            // The file is spent: importing it again would only find its rows already there.
            setFile(null);
            reset();
            setResult(r);
            await invalidateLedger();
        },
    });

    const setMapping = (key: keyof RestCsvMapping, header: string) => {
        if (!preview) return;
        read.mutate({ ...preview.mapping, [key]: header || null });
    };

    const headerSelect = (label: string, key: keyof RestCsvMapping, required: boolean) => (
        <Field label={label}>
            <select className="form-control" value={(preview?.mapping[key] as string) ?? ''} onChange={e => setMapping(key, e.target.value)}>
                {!required && <option value="">{t('common_none')}</option>}
                {(preview?.headers ?? []).map(h => (
                    <option key={h} value={h}>
                        {h}
                    </option>
                ))}
            </select>
        </Field>
    );

    return (
        <Card title={t('csv_title')}>
            <div className="flex flex-col gap-4 px-5 pb-5 pt-2">
                <div className="grid gap-3 sm:grid-cols-2">
                    <Field label={t('common_account')}>
                        <select
                            className="form-control"
                            value={accountId ?? ''}
                            onChange={e => {
                                setAccountId(Number(e.target.value) || null);
                                reset();
                            }}
                        >
                            <option value="">{t('common_choose')}</option>
                            {accounts
                                .filter(a => !a.archived)
                                .map(a => (
                                    <option key={a.id} value={a.id}>
                                        {a.name} · {a.currency.code}
                                    </option>
                                ))}
                        </select>
                    </Field>
                    <Field label={t('csv_statement_file')}>
                        <label className="form-control flex cursor-pointer items-center gap-2 truncate">
                            <FileUp size={16} className="shrink-0 text-ink-dim" />
                            <span className="truncate">{file?.name ?? t('csv_statement_file')}</span>
                            <input
                                type="file"
                                accept=".csv,text/csv"
                                className="sr-only"
                                onChange={e => {
                                    setFile(e.target.files?.[0] ?? null);
                                    reset();
                                    e.target.value = '';
                                }}
                            />
                        </label>
                    </Field>
                </div>
                <button
                    type="button"
                    className="btn btn-secondary self-start"
                    disabled={!accountId || !file || read.isPending}
                    onClick={() => read.mutate(undefined)}
                >
                    {read.isPending ? t('csv_reading') : t('csv_read')}
                </button>

                <ErrorBanner error={read.error ?? confirm.error} />
                {result && <div className="text-sm text-ok">{t('banks_imported', { 0: result.imported, 1: result.skipped })}</div>}

                {preview && account && (
                    <>
                        <div>
                            <button type="button" className="text-sm text-brand-light" onClick={() => setShowMapping(s => !s)}>
                                {t('csv_columns')}: {[preview.mapping.date, preview.mapping.description, preview.mapping.amount ?? preview.mapping.debit, preview.mapping.currency].filter(Boolean).join(' · ')}
                            </button>
                            {showMapping && (
                                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                    {headerSelect(t('common_date'), 'date', true)}
                                    {headerSelect(t('common_description'), 'description', true)}
                                    {headerSelect(t('common_amount'), 'amount', false)}
                                    {headerSelect(t('accounts_currency'), 'currency', false)}
                                </div>
                            )}
                        </div>
                        {(preview.currencyMismatch?.length ?? 0) > 0 && (
                            <div className="rounded-token bg-warn/10 px-3 py-2 text-sm text-warn">
                                {t('csv_currency_mismatch', { 0: preview.currencyMismatch!.join(', '), 1: account.name, 2: account.currency.code })}
                            </div>
                        )}
                        {(preview.skipped?.length ?? 0) > 0 && (
                            <div className="text-sm text-ink-soft">
                                {t('csv_skipped_count', { count: preview.skipped!.length })}
                                <ul className="mt-1 list-disc pl-5 text-xs text-ink-dim">
                                    {preview.skipped!.slice(0, SKIPPED_SHOWN).map((s, i) => (
                                        <li key={i}>{s}</li>
                                    ))}
                                </ul>
                            </div>
                        )}
                        <div className="text-sm text-ink-soft">
                            {t('csv_summary', { 0: fresh.length, 1: (preview.rows?.length ?? 0) - fresh.length })}
                        </div>
                        <ImportRows
                            rows={(preview.rows ?? []).slice(0, ROWS_SHOWN)}
                            currency={account.currency.code}
                            accountId={account.id}
                            accounts={accounts}
                            categories={categories}
                            choices={choices}
                            onChoose={(id, c) => setChoices(prev => ({ ...prev, [id]: c }))}
                        />
                        {(preview.rows?.length ?? 0) > ROWS_SHOWN && (
                            <div className="text-xs text-ink-dim">{t('csv_truncated', { 0: preview.rows!.length })}</div>
                        )}
                        <button
                            type="button"
                            className="btn btn-primary self-start"
                            disabled={fresh.length === 0 || confirm.isPending}
                            onClick={() => confirm.mutate()}
                        >
                            {t('banks_import_count', { 0: fresh.length })}
                        </button>
                    </>
                )}
            </div>
        </Card>
    );
};
