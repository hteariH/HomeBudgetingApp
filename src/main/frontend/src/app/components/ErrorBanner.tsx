import React from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { useT, type TFunction } from '../../i18n';
import { ApiError } from '../api/http';
import { AppRoutes } from '../lib/routes';

/**
 * What to show for a failure: the server's own sentence when it wrote one for a person
 * (a bank's refusal, a plan limit), otherwise a localised fallback - never a raw status.
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
    // A plan limit is the one refusal with a way out, so the way out is offered with it.
    const planLimit = error instanceof ApiError && error.isPlanLimit;
    return (
        <div className={`alert-error ${className}`} role="alert">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
            <div className="flex-1">
                {errorText(error, t, fallbackKey)}
                {planLimit && (
                    <>
                        {' '}
                        <Link to={AppRoutes.premium} className="font-semibold underline">
                            {t('premium_upsell_action')}
                        </Link>
                    </>
                )}
            </div>
        </div>
    );
};
