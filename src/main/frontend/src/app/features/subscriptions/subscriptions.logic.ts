import Decimal from 'decimal.js';
import type { TFunction } from '../../../i18n';
import type { RestSubscription, SubscriptionFrequency } from '../../api/types';
import type { FxSnapshot } from '../../lib/fx';
import { parseAmount, roundTo, type Money } from '../../lib/money';
import { CurrencyRegistry } from '../../lib/currencyRegistry';

// Average days and weeks per month over a 365.25-day year - SubscriptionsViewModel.kt's.
const PER_MONTH: Record<SubscriptionFrequency, Decimal> = {
    DAILY: new Decimal('30.4375'),
    WEEKLY: new Decimal('4.348214'),
    MONTHLY: new Decimal(1),
    YEARLY: new Decimal(1).div(12),
};

/** How many times a month this subscription charges, on average. */
export const chargesPerMonth = (frequency: SubscriptionFrequency, interval: number): Decimal =>
    PER_MONTH[frequency].div(Math.max(1, interval));

/**
 * What the active subscriptions cost a month and a year, in the base currency. One with no
 * rate to the base is left out rather than counted as nothing.
 */
export function subscriptionTotals(subs: RestSubscription[], fx: FxSnapshot, base: string): { perMonth: Money; perYear: Money } {
    let perMonth = new Decimal(0);
    for (const s of subs) {
        if (s.active === false) continue;
        const rate = fx.rate(s.currencyCode, base);
        if (rate === null) continue;
        perMonth = perMonth.plus(parseAmount(s.amount).times(chargesPerMonth(s.frequency, s.intervalCount ?? 1)).times(new Decimal(rate)));
    }
    const decimals = CurrencyRegistry.decimalsOrFallback(base);
    return {
        perMonth: { amount: roundTo(perMonth, decimals), currency: base },
        perYear: { amount: roundTo(perMonth.times(12), decimals), currency: base },
    };
}

/** "monthly", "every 2 weekly" - built from the same strings the Compose client used. */
export function frequencyLabel(t: TFunction, frequency: SubscriptionFrequency, interval: number): string {
    const every = interval > 1 ? t('freq_interval_prefix', { 0: interval }) : '';
    return every + t(`freq_${frequency.toLowerCase()}`);
}
