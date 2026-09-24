import { expect, it } from 'vitest';
import { translate } from '../../../i18n';
import type { RestSubscription } from '../../api/types';
import { FxSnapshot } from '../../lib/fx';
import { chargesPerMonth, frequencyLabel, subscriptionTotals } from './subscriptions.logic';

const fx = FxSnapshot.from([{ baseCode: 'USD', quoteCode: 'UAH', rate: 40 }]);
const sub = (amount: string, frequency: RestSubscription['frequency'], extra: Partial<RestSubscription> = {}) =>
    ({ id: 1, name: 's', amount, currencyCode: 'USD', frequency, intervalCount: 1, active: true, ...extra }) as RestSubscription;

it('normalises every frequency to charges per month', () => {
    expect(chargesPerMonth('MONTHLY', 1).toNumber()).toBe(1);
    expect(chargesPerMonth('MONTHLY', 3).toFixed(4)).toBe('0.3333');
    expect(chargesPerMonth('YEARLY', 1).toFixed(4)).toBe('0.0833');
    expect(chargesPerMonth('WEEKLY', 2).toFixed(6)).toBe('2.174107');
    expect(chargesPerMonth('DAILY', 0).toFixed(4)).toBe('30.4375');
});

it('totals the active subscriptions in the base currency', () => {
    const t = subscriptionTotals(
        [
            sub('10.00', 'MONTHLY'),
            sub('120.00', 'YEARLY'),
            sub('400.00', 'MONTHLY', { currencyCode: 'UAH' }), // 10 USD
            sub('99.00', 'MONTHLY', { active: false }),
            sub('1.00', 'MONTHLY', { currencyCode: 'BTC' }), // no rate: left out
        ],
        fx,
        'USD',
    );
    expect(t.perMonth.amount.toFixed(2)).toBe('30.00');
    expect(t.perYear.amount.toFixed(2)).toBe('360.00');
});

it('labels the frequency the way the Compose client did', () => {
    const t = (key: string, vars?: Record<string, string | number>) => translate('en', key, vars);
    expect(frequencyLabel(t, 'MONTHLY', 1)).toBe(translate('en', 'freq_monthly'));
    expect(frequencyLabel(t, 'WEEKLY', 2)).toBe(`every 2 ${translate('en', 'freq_weekly')}`);
});
