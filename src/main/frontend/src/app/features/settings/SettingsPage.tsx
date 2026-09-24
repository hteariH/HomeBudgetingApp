import React from 'react';
import { useT } from '../../../i18n';
import { Page } from '../../shell/Page';

// Placeholder until this screen is ported from the Compose client.
const SettingsPage: React.FC = () => {
    const t = useT();
    return <Page title={t('dashboard_settings')} />;
};

export default SettingsPage;
