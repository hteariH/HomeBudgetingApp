import React, { useRef } from 'react';
import { Camera, Loader2 } from 'lucide-react';
import { useT } from '../../../i18n';

/** Picks a receipt photo, then shows that it is being read, with a way to stop waiting. */
export const ScanButton: React.FC<{
    reading: boolean;
    onFile: (file: File) => void;
    onCancel: () => void;
}> = ({ reading, onFile, onCancel }) => {
    const t = useT();
    const input = useRef<HTMLInputElement>(null);

    if (reading) {
        return (
            <div className="flex items-center gap-3 rounded-token border border-line bg-base px-3 py-2 text-sm text-ink-soft" role="status">
                <Loader2 size={16} className="animate-spin" />
                <span className="flex-1">{t('receipt_reading')}</span>
                <button type="button" className="btn btn-ghost btn-sm" onClick={onCancel}>
                    {t('receipt_cancel')}
                </button>
            </div>
        );
    }
    return (
        <>
            <input
                ref={input}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
                data-testid="receipt-file"
                aria-hidden="true"
                tabIndex={-1}
                onChange={e => {
                    const file = e.target.files?.[0];
                    // Cleared so picking the same photo again still fires a change.
                    e.target.value = '';
                    if (file) onFile(file);
                }}
            />
            <button type="button" className="btn btn-ghost self-start" onClick={() => input.current?.click()}>
                <Camera size={16} />
                {t('quickadd_scan_receipt')}
            </button>
        </>
    );
};
