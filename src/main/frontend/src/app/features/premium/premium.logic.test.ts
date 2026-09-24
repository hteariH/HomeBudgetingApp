import { expect, it } from 'vitest';
import { premiumStatusKey } from './premium.logic';

it('says what kind of premium this is', () => {
    expect(premiumStatusKey({ premium: true })).toBe('premium_active_forever');
    expect(premiumStatusKey({ premium: true, expiresAt: '2027-01-01T00:00:00Z', autoRenewing: true, source: 'PLAY' })).toBe('premium_renews_on');
    expect(premiumStatusKey({ premium: true, expiresAt: '2027-01-01T00:00:00Z', autoRenewing: false, source: 'PLAY' })).toBe('premium_lapsing');
    expect(premiumStatusKey({ premium: true, expiresAt: '2027-01-01T00:00:00Z', autoRenewing: false, source: 'MANUAL' })).toBe('premium_active_until');
});
