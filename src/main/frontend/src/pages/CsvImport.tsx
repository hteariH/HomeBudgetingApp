import React, { useRef, useState } from 'react';
import { csvImportApi } from '../api';
import { Account, Category, CsvMapping, CsvPreview } from '../types';
import { formatAmount } from '../money';
import { Upload, AlertTriangle, FileText, ChevronDown } from 'lucide-react';
import { useT } from '../i18n';

interface Props {
    accounts: Account[];
    categories: Category[];
    onImported: () => void;
}

/**
 * Import a statement file.
 *
 * Every bank exports CSV, including the ones with no API a small service can reach, so
 * this covers far more ground than any single integration. The layout follows the same
 * two steps as the bank import - preview, assign categories, confirm - because it is
 * the same decision the user is making.
 *
 * The column mapping is shown and editable rather than hidden: detection is a guess
 * from the header row, and a file with two date columns (Revolut has "Started" and
 * "Completed") has no single right answer.
 */
const CsvImport: React.FC<Props> = ({ accounts, categories, onImported }) => {
    const t = useT();
    const fileRef = useRef<HTMLInputElement>(null);
    const [file, setFile] = useState<File | null>(null);
    const [accountId, setAccountId] = useState<number | ''>('');
    const [preview, setPreview] = useState<CsvPreview | null>(null);
    const [chosen, setChosen] = useState<Record<string, number | null>>({});
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [showMapping, setShowMapping] = useState(false);

    const account = accounts.find(a => a.id === accountId);

    const runPreview = async (mapping?: Partial<CsvMapping>) => {
        if (!file || !accountId) return;
        setBusy(true);
        setError(null);
        setMessage(null);
        try {
            const result = await csvImportApi.preview(file, Number(accountId), mapping);
            setPreview(result);
            setChosen(Object.fromEntries(result.rows.map(r => [r.externalId, r.suggestedCategoryId ?? null])));
        } catch (e: any) {
            setError(e.message);
            setPreview(null);
        } finally {
            setBusy(false);
        }
    };

    const confirm = async () => {
        if (!preview || !accountId) return;
        setBusy(true);
        try {
            const fresh = preview.rows.filter(r => !r.alreadyImported);
            const result = await csvImportApi.confirm(
                Number(accountId),
                fresh,
                Object.fromEntries(fresh.map(r => [r.externalId, chosen[r.externalId] ?? null])),
            );
            setMessage(t('banks.imported', { imported: result.imported, skipped: result.skipped }));
            setPreview(null);
            setFile(null);
            if (fileRef.current) fileRef.current.value = '';
            onImported();
        } catch (e: any) {
            setError(e.message);
        } finally {
            setBusy(false);
        }
    };

    const updateMapping = (field: keyof CsvMapping, value: string) => {
        if (!preview) return;
        const next = { ...preview.mapping, [field]: value || null };
        setPreview({ ...preview, mapping: next });
        runPreview(next);
    };

    const fresh = preview?.rows.filter(r => !r.alreadyImported) ?? [];
    const duplicates = preview?.rows.filter(r => r.alreadyImported) ?? [];

    return (
        <div className="card">
            <p className="section-title mb-4">{t('csv.title')}</p>

            <div className="flex flex-wrap items-end gap-3">
                <div className="form-group min-w-[160px] flex-1">
                    <label className="form-label">{t('common.account')}</label>
                    <select
                        className="form-control"
                        value={accountId}
                        onChange={e => { setAccountId(Number(e.target.value)); setPreview(null); }}
                    >
                        <option value="">{t('common.choose')}</option>
                        {accounts.filter(a => !a.archived).map(a => (
                            <option key={a.id} value={a.id}>{a.name} ({a.currency.code})</option>
                        ))}
                    </select>
                </div>
                <div className="form-group min-w-[200px] flex-1">
                    <label className="form-label">{t('csv.statementFile')}</label>
                    <input
                        ref={fileRef}
                        type="file"
                        accept=".csv,text/csv"
                        className="form-control"
                        onChange={e => { setFile(e.target.files?.[0] ?? null); setPreview(null); }}
                    />
                </div>
                <button className="btn btn-primary" disabled={!file || !accountId || busy} onClick={() => runPreview()}>
                    <Upload size={14} /> {busy ? t('csv.reading') : t('csv.read')}
                </button>
            </div>

            {error && (
                <div className="alert-error mt-4" role="alert">
                    <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                    <span>{error}</span>
                </div>
            )}
            {message && <p className="text-muted mt-4 text-sm">{message}</p>}

            {preview && (
                <div className="mt-4 border-t border-line pt-4">
                    {/* Detection is a guess; the user gets to correct it. */}
                    <div className="mb-3">
                        <button className="btn btn-ghost btn-sm" onClick={() => setShowMapping(v => !v)}>
                            <FileText size={13} /> {t('csv.columns')}: {preview.mapping.date} · {preview.mapping.description} ·{' '}
                            {preview.mapping.amount ?? `${preview.mapping.debit}/${preview.mapping.credit}`}
                            <ChevronDown size={13} className={showMapping ? 'rotate-180 transition-transform' : 'transition-transform'} />
                        </button>
                        {showMapping && (
                            <div className="mt-3 flex flex-wrap gap-3">
                                {(['date', 'description', 'amount', 'currency'] as const).map(field => (
                                    <div className="form-group min-w-[140px]" key={field}>
                                        <label className="form-label capitalize">{field}</label>
                                        <select
                                            className="form-control"
                                            value={(preview.mapping[field] as string) ?? ''}
                                            onChange={e => updateMapping(field, e.target.value)}
                                        >
                                            <option value="">{t('common.none')}</option>
                                            {preview.headers.map(h => <option key={h} value={h}>{h}</option>)}
                                        </select>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Converting silently would misstate the balance, so this is a warning. */}
                    {preview.currencyMismatch.length > 0 && account && (
                        <div className="alert-error mb-3" role="alert">
                            <AlertTriangle size={15} className="mt-0.5 flex-shrink-0" />
                            <span>
                                {t('csv.currencyMismatch', {
                                    currencies: preview.currencyMismatch.join(', '),
                                    account: account.name,
                                    accountCurrency: account.currency.code,
                                })}
                            </span>
                        </div>
                    )}

                    {preview.skipped.length > 0 && (
                        <details className="mb-3">
                            <summary className="text-muted cursor-pointer text-sm">
                                {t('csv.skippedCount', { count: preview.skipped.length })}
                            </summary>
                            <ul className="text-muted mt-2 list-disc pl-5 text-xs">
                                {preview.skipped.slice(0, 20).map((s, i) => <li key={i}>{s}</li>)}
                            </ul>
                        </details>
                    )}

                    <p className="text-muted mb-3 text-sm">
                        {t('csv.summary', { fresh: fresh.length, duplicates: duplicates.length })}
                    </p>

                    <div className="table-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>{t('common.date')}</th>
                                    <th>{t('common.description')}</th>
                                    <th className="text-right">{t('common.amount')}</th>
                                    <th>{t('common.category')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {preview.rows.slice(0, 200).map(r => (
                                    <tr key={r.externalId} style={{ opacity: r.alreadyImported ? 0.45 : 1 }}>
                                        <td className="whitespace-nowrap">{r.date}</td>
                                        <td>{r.description}</td>
                                        <td
                                            className="whitespace-nowrap text-right font-semibold"
                                            style={{ color: r.type === 'INCOME' ? 'var(--green)' : 'var(--red)' }}
                                        >
                                            {r.type === 'INCOME' ? '+' : '-'}
                                            {account
                                                ? formatAmount(r.amountMinor / 100, account.currency)
                                                : (r.amountMinor / 100).toFixed(2)}
                                        </td>
                                        <td>
                                            {r.alreadyImported ? (
                                                <span className="text-muted">{t('csv.alreadyImported')}</span>
                                            ) : (
                                                <select
                                                    className="form-control"
                                                    value={chosen[r.externalId] ?? ''}
                                                    onChange={e => setChosen(c => ({
                                                        ...c, [r.externalId]: e.target.value ? Number(e.target.value) : null,
                                                    }))}
                                                >
                                                    <option value="">{t('common.uncategorised')}</option>
                                                    {categories
                                                        .filter(c => !c.archived && c.type === (r.type === 'INCOME' ? 'INCOME' : 'EXPENSE'))
                                                        .map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                                </select>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {preview.rows.length > 200 && (
                        <p className="text-muted mt-2 text-xs">{t('csv.truncated', { total: preview.rows.length })}</p>
                    )}

                    <button className="btn btn-primary mt-4" disabled={busy || fresh.length === 0} onClick={confirm}>
                        {t('banks.importCount', { count: fresh.length })}
                    </button>
                </div>
            )}
        </div>
    );
};

export default CsvImport;
