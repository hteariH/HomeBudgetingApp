import React from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Plus, Tags } from 'lucide-react';
import { useT } from '../../../i18n';
import { useCategories } from '../../api/hooks';
import type { CategoryType } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card, Segmented } from '../../components/forms';
import { IconCircle } from '../../components/IconCircle';
import { AppRoutes } from '../../lib/routes';
import { Page, PageLoading } from '../../shell/Page';
import { categoriesOfType, parseCategoryType } from './categories.logic';

/** Expense and income categories on two tabs. The tab lives in the address, so it survives a reload. */
const CategoriesPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const tab = parseCategoryType(params.get('type'));
    const { data = [], isLoading, error } = useCategories();
    const list = categoriesOfType(data, tab);

    return (
        <Page
            title={t('categories_title')}
            width="form"
            actions={
                <button type="button" className="btn btn-primary" onClick={() => navigate(AppRoutes.categoryEdit(undefined, tab))}>
                    <Plus size={16} /> {t('categories_new')}
                </button>
            }
        >
            <Segmented<CategoryType>
                className="mb-4"
                value={tab}
                onChange={v => setParams({ type: v }, { replace: true })}
                options={[
                    { value: 'EXPENSE', label: t('categories_expense') },
                    { value: 'INCOME', label: t('categories_income') },
                ]}
            />
            <ErrorBanner error={error} fallbackKey="common_load_failed" className="mb-4" />
            {isLoading ? (
                <PageLoading />
            ) : list.length === 0 ? (
                <EmptyState
                    icon={Tags}
                    title={t(tab === 'EXPENSE' ? 'categories_empty_expense' : 'categories_empty_income')}
                    description={t('categories_empty_body')}
                    action={{ label: t('action_add'), onClick: () => navigate(AppRoutes.categoryEdit(undefined, tab)) }}
                />
            ) : (
                <Card>
                    {list.map(c => (
                        <Link
                            key={c.id}
                            to={AppRoutes.categoryEdit(c.id, c.type)}
                            className="flex items-center gap-4 border-t border-line px-5 py-3 transition-colors first:border-t-0 hover:bg-raised"
                        >
                            <IconCircle icon={c.icon} color={c.color} size={40} />
                            <span className="min-w-0 flex-1 truncate font-medium text-ink">{c.name}</span>
                        </Link>
                    ))}
                </Card>
            )}
        </Page>
    );
};

export default CategoriesPage;
