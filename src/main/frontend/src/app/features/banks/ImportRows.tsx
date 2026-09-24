import React from 'react';
import { useT } from '../../../i18n';
import type { RestAccount, RestCategory, TransactionType } from '../../api/types';
import { MoneyText } from '../../components/MoneyText';
import { formatMediumDate } from '../../lib/format';
import { accountChoice, categoryChoice, minorToMoney, type Choice } from './banks.logic';

export interface ImportRowData {
    externalId: string;
    date: string;
    description: string;
    amountMinor: number;
    type: TransactionType;
    alreadyImported?: boolean;
}

/**
 * Statement lines awaiting import, each with its "what was this?" choice: no category, the
 * categories that suit its direction, or any other account as a transfer - archived ones
 * included, since money did move there and a statement is usually older than the archiving.
 * Lines already in the ledger are shown dimmed and cannot be chosen for.
 */
export const ImportRows: React.FC<{
    rows: ImportRowData[];
    currency: string;
    accountId: number;
    accounts: RestAccount[];
    categories: RestCategory[];
    choices: Record<string, Choice | undefined>;
    onChoose: (externalId: string, choice: Choice | undefined) => void;
}> = ({ rows, currency, accountId, accounts, categories, choices, onChoose }) => {
    const t = useT();
    return (
        <div className="flex flex-col divide-y divide-line">
            {rows.map(row => {
                const wanted = row.type === 'INCOME' ? 'INCOME' : 'EXPENSE';
                const fitting = categories.filter(c => c.type === wanted && !c.archived);
                const transferKey = row.type === 'INCOME' ? 'import_transfer_from' : 'import_transfer_to';
                return (
                    <div key={row.externalId} className={`flex flex-wrap items-center gap-3 py-2.5 ${row.alreadyImported ? 'opacity-45' : ''}`}>
                        <div className="min-w-[200px] flex-1">
                            <div className="line-clamp-2 text-sm text-ink">{row.description}</div>
                            <div className="text-xs text-ink-soft">{formatMediumDate(row.date)}</div>
                        </div>
                        <MoneyText money={minorToMoney(row.amountMinor, currency)} type={row.type} showSign className="text-sm font-semibold" />
                        {row.alreadyImported ? (
                            <span className="text-xs text-ink-dim">{t('csv_already_imported')}</span>
                        ) : (
                            <select
                                aria-label={t('common_category')}
                                className="form-control w-auto min-w-[180px] max-w-[260px] py-1.5 text-sm"
                                value={choices[row.externalId] ?? ''}
                                onChange={e => onChoose(row.externalId, e.target.value || undefined)}
                            >
                                <option value="">{t('common_uncategorised')}</option>
                                {fitting.map(c => (
                                    <option key={c.id} value={categoryChoice(c.id)}>
                                        {c.name}
                                    </option>
                                ))}
                                {accounts
                                    .filter(a => a.id !== accountId)
                                    .map(a => (
                                        <option key={`a${a.id}`} value={accountChoice(a.id)}>
                                            {t(transferKey, { 0: a.name })}
                                        </option>
                                    ))}
                            </select>
                        )}
                    </div>
                );
            })}
        </div>
    );
};
