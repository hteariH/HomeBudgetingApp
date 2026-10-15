import { formatMoney } from '../../lib/format';
import { money } from '../../lib/money';

/** An item's price as money in the receipt's currency; as the model wrote it when the currency is unknown. */
export const receiptPrice = (price: string, currency?: string | null): string =>
    currency ? formatMoney(money(price, currency)) : price;
