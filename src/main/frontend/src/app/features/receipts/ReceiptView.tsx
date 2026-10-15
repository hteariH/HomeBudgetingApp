import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { qk } from '../../api/queryClient';
import { Dialog } from '../../components/Dialog';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { receiptPrice } from './receiptPrice';

/** An object URL for a protected photo, revoked when the photo changes or the view goes away. */
function useReceiptPhoto(uuid: string): string | null {
    const [url, setUrl] = useState<string | null>(null);
    useEffect(() => {
        let made: string | null = null;
        let cancelled = false;
        endpoints
            .receiptImage(uuid)
            .then(blob => {
                if (cancelled) return;
                made = URL.createObjectURL(blob);
                setUrl(made);
            })
            .catch(() => {
                // No photo to show; the items still read.
            });
        return () => {
            cancelled = true;
            if (made) URL.revokeObjectURL(made);
            setUrl(null);
        };
    }, [uuid]);
    return url;
}

/** The receipt a transaction was saved from: its photo, which opens full size, and what was read. */
export const ReceiptView: React.FC<{ uuid: string }> = ({ uuid }) => {
    const t = useT();
    const [open, setOpen] = useState(false);
    const photo = useReceiptPhoto(uuid);
    const { data: receipt, error } = useQuery({ queryKey: qk.receipt(uuid), queryFn: () => endpoints.receipt(uuid) });

    return (
        <Card className="mt-4 flex flex-col gap-3 p-5">
            <div className="text-sm font-semibold text-ink">{t('receipt_title')}</div>
            <ErrorBanner error={error} fallbackKey="common_load_failed" />
            <div className="flex gap-4">
                {photo && (
                    <button type="button" className="shrink-0" onClick={() => setOpen(true)} aria-label={t('receipt_title')}>
                        <img src={photo} alt="" className="h-28 w-20 rounded-token border border-line object-cover" />
                    </button>
                )}
                {receipt && (
                    <div className="min-w-0 flex-1 text-sm">
                        {receipt.merchant && <div className="font-medium text-ink">{receipt.merchant}</div>}
                        {receipt.items.length > 0 && (
                            <>
                                <div className="form-label mt-1">{t('receipt_items')}</div>
                                <ul className="mt-1 flex flex-col gap-1">
                                    {receipt.items.map((item, i) => (
                                        <li key={i} className="flex justify-between gap-3 text-ink-soft">
                                            <span className="min-w-0 truncate">
                                                {item.quantity ? `${item.quantity} × ` : ''}
                                                {item.name}
                                            </span>
                                            <span className="shrink-0 tabular-nums">{receiptPrice(item.price, receipt.currency)}</span>
                                        </li>
                                    ))}
                                </ul>
                            </>
                        )}
                    </div>
                )}
            </div>
            <Dialog open={open} onClose={() => setOpen(false)} title={t('receipt_title')} width={720}>
                {photo && <img src={photo} alt={t('receipt_title')} className="mx-auto max-h-[75vh] max-w-full" />}
            </Dialog>
        </Card>
    );
};
