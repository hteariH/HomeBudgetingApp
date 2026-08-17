import React, { useRef, useState } from 'react';
import { csvImportApi } from '../api';
import { Account, Category, CsvMapping, CsvPreview } from '../types';
import { formatAmount } from '../money';
import { Upload, AlertTriangle, FileText } from 'lucide-react';

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
            setMessage(`Imported ${result.imported}, skipped ${result.skipped}`);
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
            <div className="card-header"><h2>Import from a file</h2></div>

            <div style={{ padding: 16, display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
                <div>
                    <label className="form-label">Account</label>
                    <select className="form-control" value={accountId}
                            onChange={e => { setAccountId(Number(e.target.value)); setPreview(null); }}>
                        <option value="">Choose…</option>
                        {accounts.filter(a => !a.archived).map(a => (
                            <option key={a.id} value={a.id}>{a.name} ({a.currency.code})</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="form-label">Statement file (CSV)</label>
                    <input ref={fileRef} type="file" accept=".csv,text/csv" className="form-control"
                           onChange={e => { setFile(e.target.files?.[0] ?? null); setPreview(null); }} />
                </div>
                <button className="btn btn-primary" disabled={!file || !accountId || busy}
                        onClick={() => runPreview()}>
                    <Upload size={14} /> {busy ? 'Reading…' : 'Read file'}
                </button>
            </div>

            {error && <div style={{ padding: '0 16px 16px' }}><div className="alert alert-error">{error}</div></div>}
            {message && <div style={{ padding: '0 16px 16px' }}>{message}</div>}

            {preview && (
                <div style={{ padding: 16 }}>
                    {/* Detection is a guess; the user gets to correct it. */}
                    <div style={{ marginBottom: 12 }}>
                        <button className="btn btn-ghost btn-sm" onClick={() => setShowMapping(v => !v)}>
                            <FileText size={13} /> Columns: {preview.mapping.date} · {preview.mapping.description} ·{' '}
                            {preview.mapping.amount ?? `${preview.mapping.debit}/${preview.mapping.credit}`}
                            {showMapping ? ' ▲' : ' ▼'}
                        </button>
                        {showMapping && (
                            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 8 }}>
                                {(['date', 'description', 'amount', 'currency'] as const).map(field => (
                                    <div key={field}>
                                        <label className="form-label" style={{ textTransform: 'capitalize' }}>{field}</label>
                                        <select className="form-control"
                                                value={(preview.mapping[field] as string) ?? ''}
                                                onChange={e => updateMapping(field, e.target.value)}>
                                            <option value="">—</option>
                                            {preview.headers.map(h => <option key={h} value={h}>{h}</option>)}
                                        </select>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Converting silently would misstate the balance, so this is a warning. */}
                    {preview.currencyMismatch.length > 0 && account && (
                        <div className="alert alert-error" style={{ marginBottom: 12 }}>
                            <AlertTriangle size={14} /> The file contains {preview.currencyMismatch.join(', ')} but{' '}
                            {account.name} is in {account.currency.code}. Amounts are imported as-is, without conversion.
                        </div>
                    )}

                    {preview.skipped.length > 0 && (
                        <details style={{ marginBottom: 12 }}>
                            <summary style={{ cursor: 'pointer', color: 'var(--text-muted)' }}>
                                {preview.skipped.length} line(s) could not be read
                            </summary>
                            <ul style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                                {preview.skipped.slice(0, 20).map((s, i) => <li key={i}>{s}</li>)}
                            </ul>
                        </details>
                    )}

                    <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                        {fresh.length} new, {duplicates.length} already imported
                    </p>

                    <table className="table">
                        <thead><tr><th>DATE</th><th>DESCRIPTION</th><th>AMOUNT</th><th>CATEGORY</th></tr></thead>
                        <tbody>
                        {preview.rows.slice(0, 200).map(r => (
                            <tr key={r.externalId} style={{ opacity: r.alreadyImported ? 0.45 : 1 }}>
                                <td>{r.date}</td>
                                <td>{r.description}</td>
                                <td style={{ textAlign: 'right', color: r.type === 'INCOME' ? 'var(--green)' : 'var(--red)' }}>
                                    {r.type === 'INCOME' ? '+' : '-'}
                                    {account ? formatAmount(r.amountMinor / 100, account.currency) : (r.amountMinor / 100).toFixed(2)}
                                </td>
                                <td>
                                    {r.alreadyImported ? <span style={{ color: 'var(--text-muted)' }}>imported</span> : (
                                        <select className="form-control" value={chosen[r.externalId] ?? ''}
                                                onChange={e => setChosen(c => ({
                                                    ...c, [r.externalId]: e.target.value ? Number(e.target.value) : null,
                                                }))}>
                                            <option value="">Uncategorised</option>
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
                    {preview.rows.length > 200 && (
                        <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                            Showing the first 200 of {preview.rows.length}; all of them are imported.
                        </p>
                    )}

                    <button className="btn btn-primary" style={{ marginTop: 12 }}
                            disabled={busy || fresh.length === 0} onClick={confirm}>
                        Import {fresh.length}
                    </button>
                </div>
            )}
        </div>
    );
};

export default CsvImport;
