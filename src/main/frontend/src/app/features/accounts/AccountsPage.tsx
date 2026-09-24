import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Plus, Wallet } from 'lucide-react';
import { useT } from '../../../i18n';
import { useAccounts } from '../../api/hooks';
import type { RestAccount } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { IconCircle } from '../../components/IconCircle';
import { MoneyText } from '../../components/MoneyText';
import { AppRoutes } from '../../lib/routes';
import { Page, PageLoading } from '../../shell/Page';
import { accountBalance, splitAccounts } from './accounts.logic';

const AccountRow: React.FC<{ account: RestAccount; archived?: boolean }> = ({ account, archived = false }) => (
    <Link
        to={AppRoutes.accountEdit(account.id)}
        className="flex items-center gap-3 border-t border-line px-5 py-3 transition-colors first:border-t-0 hover:bg-raised"
    >
        <IconCircle icon={account.icon} color={account.color} size={40} />
        <span className="min-w-0 flex-1">
            <span className={`block truncate font-medium ${archived ? 'text-ink-soft' : 'text-ink'}`}>{account.name}</span>
            <span className="block text-xs text-ink-soft">{account.currency.code}</span>
        </span>
        <MoneyText money={accountBalance(account)} className="font-semibold text-ink" />
    </Link>
);

/** Every account with its own balance; archived ones in a section of their own. */
const AccountsPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const { data = [], isLoading, error } = useAccounts();
    const { active, archived } = splitAccounts(data);

    return (
        <Page
            title={t('accounts_title')}
            width="form"
            actions={
                <button type="button" className="btn btn-primary" onClick={() => navigate(AppRoutes.accountEdit())}>
                    <Plus size={16} /> {t('accounts_new')}
                </button>
            }
        >
            <ErrorBanner error={error} fallbackKey="common_load_failed" className="mb-4" />
            {isLoading ? (
                <PageLoading />
            ) : data.length === 0 ? (
                <EmptyState
                    icon={Wallet}
                    title={t('accounts_empty')}
                    action={{ label: t('accounts_new'), onClick: () => navigate(AppRoutes.accountEdit()) }}
                />
            ) : (
                <div className="flex flex-col gap-4">
                    {active.length > 0 && (
                        <Card>
                            {active.map(a => (
                                <AccountRow key={a.id} account={a} />
                            ))}
                        </Card>
                    )}
                    {archived.length > 0 && (
                        <Card title={<span className="text-brand-light">{t('accounts_archived')}</span>}>
                            <div className="pt-2">
                                {archived.map(a => (
                                    <AccountRow key={a.id} account={a} archived />
                                ))}
                            </div>
                        </Card>
                    )}
                </div>
            )}
        </Page>
    );
};

export default AccountsPage;
