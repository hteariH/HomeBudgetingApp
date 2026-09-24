import type { BillingStatusDto } from '../../api/types';

/**
 * The line under "Premium is on". Four different things to say: a grant that never ends, a
 * subscription that renews, one that was cancelled and is running out, and a grant with a
 * date on it. Port of PremiumScreen.statusLine.
 */
export function premiumStatusKey(s: BillingStatusDto): 'premium_active_forever' | 'premium_lapsing' | 'premium_active_until' | 'premium_renews_on' {
    if (!s.expiresAt) return 'premium_active_forever';
    if (!s.autoRenewing) return s.source === 'MANUAL' ? 'premium_active_until' : 'premium_lapsing';
    return s.source === 'MANUAL' ? 'premium_active_until' : 'premium_renews_on';
}
