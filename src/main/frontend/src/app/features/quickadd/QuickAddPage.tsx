import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useT } from '../../../i18n';
import { AppRoutes } from '../../lib/routes';
import { BackButton, Page } from '../../shell/Page';
import { EntryForm } from './EntryForm';
import { parseEntryMode } from './quickadd.logic';

/**
 * The entry form as a page of its own - what a narrow window gets from the header's "+".
 * `?type=` picks the mode and `?categoryId=` preselects a category, as the Compose route did.
 */
const QuickAddPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const mode = parseEntryMode(params.get('type'));
    const categoryId = Number(params.get('categoryId')) || undefined;

    return (
        <Page
            width="form"
            leading={<BackButton fallback={AppRoutes.dashboard} label={t('action_close')} />}
            title={t(mode === 'TRANSFER' ? 'transfer_title' : mode === 'INCOME' ? 'quickadd_title_income' : 'quickadd_title_expense')}
        >
            <EntryForm
                // A different ?type= is a different form, not an edit of this one.
                key={`${mode}-${categoryId ?? ''}`}
                variant="page"
                initialMode={mode}
                initialCategoryId={categoryId}
                onSaved={() => (window.history.state?.idx > 0 ? navigate(-1) : navigate(AppRoutes.dashboard, { replace: true }))}
            />
        </Page>
    );
};

export default QuickAddPage;
