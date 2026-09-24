import React from 'react';
import { useT } from '../../../i18n';
import { Page } from '../../shell/Page';

// Placeholder until this screen is ported from the Compose client.
const AccountsPage: React.FC = () => {
    const t = useT();
    return <Page title={t('nav_accounts')} />;
};

export default AccountsPage;
