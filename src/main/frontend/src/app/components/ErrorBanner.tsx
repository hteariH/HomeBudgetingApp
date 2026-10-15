import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { useT, type TFunction } from '../../i18n';
import { ApiError } from '../api/http';

/**
 * What to show for a failure: the server's own sentence when it wrote one for a person
 * (a bank's refusal, a validation failure), otherwise a localised fallback - never a raw status.
 */
export function errorText(error: unknown, t: TFunction, fallbackKey = 'common_request_failed'): string {
    if (error instanceof ApiError && error.serverMessage) return error.serverMessage;
    return t(fallbackKey);
}

export const ErrorBanner: React.FC<{ error: unknown; fallbackKey?: string; className?: string }> = ({
    error,
    fallbackKey,
    className = '',
}) => {
    const t = useT();
    if (!error) return null;
    return (
        <div className={`alert-error ${className}`} role="alert">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
            <div className="flex-1">
                {errorText(error, t, fallbackKey)}
            </div>
        </div>
    );
};
