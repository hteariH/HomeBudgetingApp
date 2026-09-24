import { expect, it } from 'vitest';
import { translate } from '.';
import { en } from './locales/en';
import { ru } from './locales/ru';
import { uk } from './locales/uk';

const appKeys = Object.keys(en).filter(k => /^[a-z0-9_]+$/.test(k));

it('brought every Android string across', () => expect(appKeys.length).toBeGreaterThan(400));

it('every app key exists in all three languages', () => {
    for (const k of appKeys) {
        expect(ru, k).toHaveProperty(k);
        expect(uk, k).toHaveProperty(k);
    }
});

it('placeholders are converted', () => {
    expect(translate('en', 'dashboard_more_categories', { 0: 3 })).toBe('+3 more categories');
    expect(translate('en', 'accounts_delete_warning', { 0: 'Cash', 1: 2 })).toBe(
        'Delete "Cash"? Its 2 transactions will also be permanently deleted.',
    );
});

it('plurals follow Intl rules', () => {
    expect(translate('en', 'dashboard_account_count', { count: 1 })).toBe('1 account');
    expect(translate('en', 'dashboard_account_count', { count: 3 })).toBe('3 accounts');
    expect(translate('ru', 'dashboard_account_count', { count: 5 })).toBe('5 счетов');
    expect(translate('ru', 'dashboard_account_count', { count: 2 })).toBe('2 счёта');
});

it('android escapes and %% are undone', () => {
    expect(en.settings_import_confirm_message).toContain("can't");
    expect(en.analytics_savings_rate).toBe('Savings rate this month: {{0}}%');
    for (const k of appKeys) {
        const v = (en as Record<string, string>)[k];
        expect(v, k).not.toMatch(/%\d\$|\\['u]/);
    }
});
