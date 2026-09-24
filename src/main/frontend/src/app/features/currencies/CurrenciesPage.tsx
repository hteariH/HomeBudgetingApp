import React, { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Search, Star } from 'lucide-react';
import { useT } from '../../../i18n';
import { useBaseCurrency, useCurrencies } from '../../api/hooks';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { AppRoutes } from '../../lib/routes';
import { Page, PageLoading } from '../../shell/Page';

/**
 * The currencies this account can hold, searchable.
 *
 * The catalogue belongs to the server's administrators: every currency it returns is one
 * the user can hold, and none can be added, switched off or deleted from a browser. The
 * Compose web client still drew the switches and the "add" button, and each one failed.
 * What a user can do here is set a rate by hand, on the currency's own page.
 */
const CurrenciesPage: React.FC = () => {
    const t = useT();
    const base = useBaseCurrency();
    const { data = [], isLoading, error } = useCurrencies();
    const [query, setQuery] = useState('');

    const matches = useMemo(() => {
        const needle = query.trim().toLowerCase();
        const sorted = [...data].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.code.localeCompare(b.code));
        return needle ? sorted.filter(c => c.code.toLowerCase().includes(needle) || c.name.toLowerCase().includes(needle)) : sorted;
    }, [data, query]);

    return (
        <Page title={t('currencies_title')} width="form">
            <div className="relative mb-4">
                <Search size={16} className="input-icon" />
                <input
                    className="form-control form-control-icon"
                    value={query}
                    onChange={e => setQuery(e.target.value)}
                    placeholder={t('currencies_search')}
                    aria-label={t('currencies_search')}
                />
            </div>
            <ErrorBanner error={error} fallbackKey="common_load_failed" className="mb-4" />
            {isLoading ? (
                <PageLoading />
            ) : matches.length === 0 ? (
                <EmptyState title={query ? t('currencies_no_matches') : t('currencies_empty')} />
            ) : (
                <Card>
                    <div className="px-5 pb-2 pt-4 text-sm font-semibold text-brand-light">
                        {t('currencies_active')} · {matches.length}
                    </div>
                    {matches.map(c => (
                        <Link
                            key={c.code}
                            to={AppRoutes.currencyEdit(c.code)}
                            className="flex items-center gap-3 border-t border-line px-5 py-2.5 transition-colors first:border-t-0 hover:bg-raised"
                        >
                            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] bg-brand/15 text-base font-semibold text-brand-light">
                                {c.symbol.slice(0, 3)}
                            </span>
                            <span className="min-w-0 flex-1">
                                <span className="flex items-center gap-1.5 font-medium text-ink">
                                    {c.code}
                                    {c.code === base && <Star size={14} className="fill-current text-brand-light" aria-label={t('currencies_base')} />}
                                </span>
                                <span className="block truncate text-xs text-ink-soft">{c.name}</span>
                            </span>
                        </Link>
                    ))}
                </Card>
            )}
        </Page>
    );
};

export default CurrenciesPage;
