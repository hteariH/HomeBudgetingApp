import React from 'react';
import { Check } from 'lucide-react';
import { useT } from '../../i18n';
import type { RestAccount } from '../api/types';
import { Dialog } from './Dialog';
import { IconCircle } from './IconCircle';

/**
 * Choosing an account. A separate screen in Compose only so it could hand a result back to
 * the previous one; here it is a dialog over the form that asked.
 */
export const AccountPicker: React.FC<{
    open: boolean;
    accounts: RestAccount[];
    selectedId: number | null;
    onPick: (id: number) => void;
    onClose: () => void;
    title?: string;
}> = ({ open, accounts, selectedId, onPick, onClose, title }) => {
    const t = useT();
    return (
        <Dialog open={open} onClose={onClose} title={title ?? t('account_picker_title')} closeLabel={t('action_close')}>
            <div className="-mx-5">
                {accounts.map(a => (
                    <button
                        type="button"
                        key={a.id}
                        onClick={() => {
                            onPick(a.id);
                            onClose();
                        }}
                        className="flex w-full items-center gap-4 border-b border-line px-5 py-3 text-left last:border-b-0 hover:bg-raised"
                    >
                        <IconCircle icon={a.icon} color={a.color} size={40} />
                        <span className="flex-1">
                            <span className="block font-medium text-ink">{a.name}</span>
                            <span className="block text-sm text-ink-soft">{a.currency.code}</span>
                        </span>
                        {a.id === selectedId && <Check size={18} className="text-brand-light" />}
                    </button>
                ))}
            </div>
        </Dialog>
    );
};
