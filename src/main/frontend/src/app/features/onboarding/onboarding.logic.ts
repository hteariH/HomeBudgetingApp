import type { CreateAccountRequest, RestCurrency } from '../../api/types';

export const ONBOARDING_STEPS = 3;

/** Whether the step on screen has what it needs. Welcome needs nothing; the account step a name. */
export function canProceed(step: number, currency: string, accountName: string): boolean {
    if (step === 1) return currency.trim() !== '';
    if (step === 2) return accountName.trim() !== '';
    return true;
}

/**
 * The first account, if the user named one. Skipping setup creates nothing: the name is the
 * only thing that says an account was wanted.
 */
export function firstAccountRequest(name: string, currency: string, currencies: RestCurrency[]): CreateAccountRequest | null {
    const trimmed = name.trim();
    const currencyId = currencies.find(c => c.code === currency)?.id;
    if (!trimmed || !currencyId) return null;
    return { name: trimmed, currencyId, initialBalance: '0', icon: 'wallet', color: '#6750A4', includeInTotal: true };
}
