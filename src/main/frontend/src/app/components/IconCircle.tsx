import React from 'react';
import { iconFor } from './icons';

/** The server stores colours as "#RRGGBB"; anything else falls back to the accent. */
export const safeColor = (hex: string | null | undefined, fallback = '#4F46E5') =>
    hex && /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : fallback;

export const IconCircle: React.FC<{ icon?: string | null; color?: string | null; size?: number; className?: string }> = ({
    icon,
    color,
    size = 40,
    className = '',
}) => {
    const Icon = iconFor(icon);
    return (
        <span
            className={`inline-flex shrink-0 items-center justify-center rounded-full text-white ${className}`}
            style={{ width: size, height: size, background: safeColor(color) }}
        >
            <Icon size={Math.round(size * 0.55)} />
        </span>
    );
};
