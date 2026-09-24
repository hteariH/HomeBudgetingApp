const pad = (n: number) => String(n).padStart(2, '0');

/** The local calendar date - what the user means by "today" - as yyyy-MM-dd. */
export const isoDate = (d: Date): string => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

export const todayIso = (): string => isoDate(new Date());

/** A yyyy-MM-dd string as local midnight, which is how the server's day bounds read it. */
export const parseIsoDate = (iso: string): Date => {
    const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
    return new Date(y, m - 1, d);
};

/** [first day of the month, first day of the next), in local time. `month1` is 1-based. */
export const monthRange = (year: number, month1: number): { from: Date; to: Date } => ({
    from: new Date(year, month1 - 1, 1),
    to: new Date(year, month1, 1),
});

/**
 * The server reads dateTo as an inclusive day, so an exclusive midnight bound goes out as
 * the day before it. Sending the bound itself was how a month's list picked up the 1st of
 * the next month (fixed in the wasm client by 6d9a525).
 */
export const apiDateTo = (toExclusive: Date): string => isoDate(new Date(toExclusive.getTime() - 1));

/** The other half of the same fix: filter on the instant too, over [from, to). */
export function inRange(occurredAt: string, from?: Date, to?: Date): boolean {
    const t = new Date(occurredAt).getTime();
    return (!from || t >= from.getTime()) && (!to || t < to.getTime());
}
