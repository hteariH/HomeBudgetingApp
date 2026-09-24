import React, { useEffect, useRef, useState } from 'react';
import { MoreVertical } from 'lucide-react';

/**
 * The actions a row carries besides opening it. The Compose client put these behind a long
 * press, which a mouse does not have; here they sit behind a button at the row's end.
 */
export const RowMenu: React.FC<{ label: string; items: { label: string; onClick: () => void; danger?: boolean }[] }> = ({
    label,
    items,
}) => {
    const [open, setOpen] = useState(false);
    const root = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;
        const onDown = (e: MouseEvent) => {
            if (!root.current?.contains(e.target as Node)) setOpen(false);
        };
        const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
        document.addEventListener('mousedown', onDown);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onDown);
            document.removeEventListener('keydown', onKey);
        };
    }, [open]);

    return (
        <div className="relative" ref={root}>
            <button
                type="button"
                aria-label={label}
                aria-haspopup="menu"
                aria-expanded={open}
                className="btn btn-ghost btn-icon border-0 text-ink-dim hover:text-ink"
                onClick={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    setOpen(o => !o);
                }}
            >
                <MoreVertical size={16} />
            </button>
            {open && (
                <div role="menu" className="absolute right-0 top-full z-40 min-w-[160px] rounded-token border border-line bg-card p-1 shadow-token-lg">
                    {items.map(item => (
                        <button
                            key={item.label}
                            type="button"
                            role="menuitem"
                            className={`block w-full rounded-md px-3 py-2 text-left text-sm hover:bg-raised ${item.danger ? 'text-bad' : 'text-ink'}`}
                            onClick={e => {
                                e.preventDefault();
                                e.stopPropagation();
                                setOpen(false);
                                item.onClick();
                            }}
                        >
                            {item.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};
