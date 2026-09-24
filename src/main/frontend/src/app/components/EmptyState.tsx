import React from 'react';
import type { LucideIcon } from 'lucide-react';

export const EmptyState: React.FC<{
    title: string;
    description?: string;
    icon?: LucideIcon;
    action?: { label: string; onClick: () => void };
}> = ({ title, description, icon: Icon, action }) => (
    <div className="flex flex-col items-center justify-center gap-3 px-6 py-16 text-center">
        {Icon && <Icon size={48} className="text-ink-dim" />}
        <div className="text-base font-semibold text-ink">{title}</div>
        {description && <div className="max-w-md text-sm text-ink-soft">{description}</div>}
        {action && (
            <button type="button" className="btn btn-primary mt-1" onClick={action.onClick}>
                {action.label}
            </button>
        )}
    </div>
);
