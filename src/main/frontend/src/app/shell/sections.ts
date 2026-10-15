import {
    ArrowLeftRight,
    BarChart3,
    Globe,
    Home,
    Landmark,
    List,
    Repeat,
    Settings,
    Tags,
    Users,
    Wallet,
    type LucideIcon,
} from 'lucide-react';
import { AppRoutes } from '../lib/routes';

export interface Section {
    path: string;
    label: string;
    icon: LucideIcon;
}

/**
 * The sections, in the order the Compose header listed them. Settings is not one of the
 * header's: it lives in the account menu, where a site keeps it, and in the drawer, where
 * the column has room for it.
 */
export const HEADER_SECTIONS: Section[] = [
    { path: AppRoutes.dashboard, label: 'nav_overview', icon: Home },
    { path: AppRoutes.transactions, label: 'nav_transactions', icon: List },
    { path: AppRoutes.analytics, label: 'nav_analytics', icon: BarChart3 },
    { path: AppRoutes.accounts, label: 'nav_accounts', icon: Wallet },
    { path: AppRoutes.categories, label: 'nav_categories', icon: Tags },
    { path: AppRoutes.subscriptions, label: 'dashboard_subscriptions', icon: Repeat },
    { path: AppRoutes.currencies, label: 'nav_currencies', icon: Globe },
    { path: AppRoutes.homeMoney, label: 'nav_homemoney', icon: ArrowLeftRight },
    { path: AppRoutes.banks, label: 'nav_banks', icon: Landmark },
    { path: AppRoutes.household, label: 'nav_household', icon: Users },
];

export const DRAWER_SECTIONS: Section[] = [
    ...HEADER_SECTIONS,
    { path: AppRoutes.settings, label: 'dashboard_settings', icon: Settings },
];

/** Which section a path belongs to: an editor highlights the list it edits. */
export function sectionOf(pathname: string): string {
    const clean = pathname.replace(/\/+$/, '') || AppRoutes.dashboard;
    if (clean === AppRoutes.dashboard) return AppRoutes.dashboard;
    const match = DRAWER_SECTIONS.filter(s => s.path !== AppRoutes.dashboard).find(
        s => clean === s.path || clean.startsWith(`${s.path}/`),
    );
    return match?.path ?? '';
}
