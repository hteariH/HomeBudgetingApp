export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

/** Months are added the way kotlinx-datetime adds them: Jan 31 + 1 month is Feb 28/29. */
function addMonths(y: number, month0: number, day: number, months: number): Date {
    const total = month0 + months;
    const year = y + Math.floor(total / 12);
    const month = ((total % 12) + 12) % 12;
    const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
    return new Date(Date.UTC(year, month, Math.min(day, lastDay)));
}

/** Port of SubscriptionScheduler.kt. Dates are yyyy-MM-dd and computed in UTC. */
export function nextChargeDate(currentIso: string, frequency: Frequency, interval: number): string {
    const n = Math.max(1, interval);
    const [y, m, d] = currentIso.slice(0, 10).split('-').map(Number);
    let next: Date;
    switch (frequency) {
        case 'DAILY':
            next = new Date(Date.UTC(y, m - 1, d + n));
            break;
        case 'WEEKLY':
            next = new Date(Date.UTC(y, m - 1, d + 7 * n));
            break;
        case 'MONTHLY':
            next = addMonths(y, m - 1, d, n);
            break;
        case 'YEARLY':
            next = addMonths(y, m - 1, d, 12 * n);
            break;
    }
    return next.toISOString().slice(0, 10);
}
