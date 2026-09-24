import React, { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';

/**
 * A modal. Escape and a click on the backdrop close it, focus moves into it on open and
 * back to where it was on close, and Tab stays inside while it is up.
 */
export const Dialog: React.FC<{
    open: boolean;
    onClose: () => void;
    title?: React.ReactNode;
    children: React.ReactNode;
    footer?: React.ReactNode;
    width?: number;
    closeLabel?: string;
}> = ({ open, onClose, title, children, footer, width = 480, closeLabel }) => {
    const panel = useRef<HTMLDivElement>(null);
    // A ref, so the effect below runs on open and close only. With onClose among its deps,
    // every re-render of the caller - a keystroke in the dialog's own form - moved focus
    // back to the first field.
    const closeRef = useRef(onClose);
    closeRef.current = onClose;

    useEffect(() => {
        if (!open) return;
        const previous = document.activeElement as HTMLElement | null;
        const focusable = () =>
            Array.from(
                panel.current?.querySelectorAll<HTMLElement>(
                    'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])',
                ) ?? [],
            );
        (focusable().find(el => el.dataset.autofocus !== undefined) ?? focusable()[0] ?? panel.current)?.focus();

        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                e.stopPropagation();
                closeRef.current();
            } else if (e.key === 'Tab') {
                const items = focusable();
                if (items.length === 0) return;
                const first = items[0];
                const last = items[items.length - 1];
                if (e.shiftKey && document.activeElement === first) {
                    e.preventDefault();
                    last.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                    e.preventDefault();
                    first.focus();
                }
            }
        };
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('keydown', onKey);
            previous?.focus?.();
        };
    }, [open]);

    if (!open) return null;

    return createPortal(
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-black/55" onClick={onClose} data-testid="dialog-backdrop" />
            <div
                ref={panel}
                role="dialog"
                aria-modal="true"
                tabIndex={-1}
                className="relative flex max-h-[90vh] w-full flex-col rounded-token-lg border border-line bg-card shadow-token-lg outline-none"
                style={{ maxWidth: width }}
            >
                {title !== undefined && (
                    <div className="flex items-center gap-3 px-5 pb-2 pt-5">
                        <h2 className="flex-1 text-lg font-semibold text-ink">{title}</h2>
                        {closeLabel && (
                            <button type="button" className="btn btn-ghost btn-icon border-0" onClick={onClose} aria-label={closeLabel}>
                                <X size={18} />
                            </button>
                        )}
                    </div>
                )}
                <div className="overflow-y-auto px-5 py-3 text-sm text-ink-soft">{children}</div>
                {footer && <div className="flex justify-end gap-2 px-5 pb-5 pt-2">{footer}</div>}
            </div>
        </div>,
        document.body,
    );
};
