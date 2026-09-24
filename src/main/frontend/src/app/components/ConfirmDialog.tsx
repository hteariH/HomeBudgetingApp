import React from 'react';
import { useT } from '../../i18n';
import { Dialog } from './Dialog';

export const ConfirmDialog: React.FC<{
    open: boolean;
    title: string;
    message?: React.ReactNode;
    confirmLabel?: string;
    /** Red confirm button, for the things that cannot be undone. */
    destructive?: boolean;
    busy?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}> = ({ open, title, message, confirmLabel, destructive = false, busy = false, onConfirm, onCancel }) => {
    const t = useT();
    return (
        <Dialog
            open={open}
            onClose={onCancel}
            title={title}
            width={420}
            footer={
                <>
                    <button type="button" className="btn btn-ghost" onClick={onCancel}>
                        {t('action_cancel')}
                    </button>
                    <button
                        type="button"
                        disabled={busy}
                        className={`btn ${destructive ? 'bg-bad text-white hover:bg-bad/90' : 'btn-primary'}`}
                        onClick={onConfirm}
                    >
                        {confirmLabel ?? (destructive ? t('action_delete') : t('action_done'))}
                    </button>
                </>
            }
        >
            {message}
        </Dialog>
    );
};
