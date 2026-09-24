import React from 'react';
import { useT } from '../../../i18n';
import { Page } from '../../shell/Page';

// Placeholder until this screen is ported from the Compose client.
const AnalyticsPage: React.FC = () => {
    const t = useT();
    return <Page title={t('nav_analytics')} />;
};

export default AnalyticsPage;
