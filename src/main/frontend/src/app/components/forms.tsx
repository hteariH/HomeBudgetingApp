import React from 'react';
import { IconCircle, safeColor } from './IconCircle';
import { PICKER_ICONS } from './icons';

/** A labelled control, in the landing page's form styling. */
export const Field: React.FC<{ label: string; hint?: React.ReactNode; error?: string | null; children: React.ReactNode }> = ({
    label,
    hint,
    error,
    children,
}) => (
    <label className="form-group">
        <span className="form-label">{label}</span>
        {children}
        {error ? <span className="text-xs text-bad">{error}</span> : hint ? <span className="text-xs text-ink-dim">{hint}</span> : null}
    </label>
);

/** Two or three mutually exclusive options - expense/income, a period, a mode. */
export function Segmented<T extends string>({
    value,
    options,
    onChange,
    className = '',
}: {
    value: T;
    options: { value: T; label: string }[];
    onChange: (v: T) => void;
    className?: string;
}) {
    return (
        <div role="tablist" className={`inline-flex rounded-full border border-line bg-surface p-1 ${className}`}>
            {options.map(o => (
                <button
                    key={o.value}
                    type="button"
                    role="tab"
                    aria-selected={o.value === value}
                    onClick={() => onChange(o.value)}
                    className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
                        o.value === value ? 'bg-brand text-white' : 'text-ink-soft hover:text-ink'
                    }`}
                >
                    {o.label}
                </button>
            ))}
        </div>
    );
}

export const Switch: React.FC<{ checked: boolean; onChange: (v: boolean) => void; label: string; description?: string }> = ({
    checked,
    onChange,
    label,
    description,
}) => (
    <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className="flex w-full items-center gap-4 py-2 text-left"
    >
        <span className="flex-1">
            <span className="block text-sm font-medium text-ink">{label}</span>
            {description && <span className="block text-xs text-ink-soft">{description}</span>}
        </span>
        <span className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${checked ? 'bg-brand' : 'bg-raised border border-line'}`}>
            <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-[22px]' : 'translate-x-0.5'}`}
            />
        </span>
    </button>
);

/** The colours offered for accounts, categories and subscriptions - PickerColors.kt. */
export const PICKER_COLORS = [
    '#3D4499', '#6366F1', '#3E63DD', '#06B6D4', '#008080',
    '#30A46C', '#FFCC00', '#FF9F1C', '#E5484D', '#EC4899',
    '#AB47BC', '#8B4513', '#6B7280', '#94A3B8',
];

/** What an uncategorised row, or a colourless one, is drawn in. */
export const NEUTRAL_COLOR = '#94A3B8';

export const ColorPicker: React.FC<{ value: string; onChange: (c: string) => void; colors?: string[] }> = ({
    value,
    onChange,
    colors = PICKER_COLORS,
}) => (
    <div className="flex flex-wrap gap-2">
        {colors.map(c => (
            <button
                key={c}
                type="button"
                aria-label={c}
                aria-pressed={c.toLowerCase() === value.toLowerCase()}
                onClick={() => onChange(c)}
                className={`h-8 w-8 rounded-full border-2 transition-transform hover:scale-110 ${
                    c.toLowerCase() === value.toLowerCase() ? 'border-ink' : 'border-transparent'
                }`}
                style={{ background: safeColor(c) }}
            />
        ))}
    </div>
);

export const IconPicker: React.FC<{ value: string; color: string; onChange: (key: string) => void; icons?: string[] }> = ({
    value,
    color,
    onChange,
    icons = PICKER_ICONS,
}) => (
    <div className="flex flex-wrap gap-2">
        {icons.map(key => (
            <button
                key={key}
                type="button"
                aria-label={key}
                aria-pressed={key === value}
                onClick={() => onChange(key)}
                className={`rounded-full p-0.5 ring-2 transition ${key === value ? 'ring-brand' : 'ring-transparent hover:ring-line-strong'}`}
            >
                <IconCircle icon={key} color={key === value ? color : '#64748B'} size={36} />
            </button>
        ))}
    </div>
);

/** A bordered surface for a group of rows. */
export const Card: React.FC<{ children: React.ReactNode; className?: string; title?: React.ReactNode; action?: React.ReactNode }> = ({
    children,
    className = '',
    title,
    action,
}) => (
    <section className={`rounded-token-lg border border-line bg-card ${className}`}>
        {(title || action) && (
            <div className="flex items-center gap-3 px-5 pb-1 pt-4">
                <h2 className="flex-1 text-sm font-semibold text-ink">{title}</h2>
                {action}
            </div>
        )}
        {children}
    </section>
);
