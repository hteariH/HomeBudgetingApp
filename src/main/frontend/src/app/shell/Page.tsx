import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

/**
 * A screen's frame: a title row and a column that stops growing.
 *
 * Two caps, as in PageWidth.kt. A list or a dashboard reads well over a wide column; a form
 * does not - a text field a thousand pixels wide invites nothing - so forms stay near 480.
 */
export const Page: React.FC<{
    title?: React.ReactNode;
    /** Controls on the right of the title row: add, filters, delete. */
    actions?: React.ReactNode;
    /** Left of the title - a back link on editors. */
    leading?: React.ReactNode;
    width?: 'content' | 'form';
    children?: React.ReactNode;
}> = ({ title, actions, leading, width = 'content', children }) => (
    <div className={`mx-auto w-full px-4 pb-16 pt-6 sm:px-6 ${width === 'form' ? 'max-w-[528px]' : 'max-w-[1448px]'}`}>
        {(title || actions || leading) && (
            <div className="mb-5 flex min-h-[40px] items-center gap-3">
                {leading}
                {title && <h1 className="min-w-0 flex-1 truncate text-xl font-semibold text-ink">{title}</h1>}
                {!title && <div className="flex-1" />}
                {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
            </div>
        )}
        {children}
    </div>
);

/**
 * The arrow on an editor. Goes back when there is somewhere in this app to go back to,
 * otherwise to the list the editor belongs to - a bookmarked editor has no history.
 */
export const BackButton: React.FC<{ fallback: string; label: string }> = ({ fallback, label }) => {
    const navigate = useNavigate();
    return (
        <button
            type="button"
            className="btn btn-ghost btn-icon -ml-2 border-0"
            aria-label={label}
            title={label}
            onClick={() => (window.history.state?.idx > 0 ? navigate(-1) : navigate(fallback, { replace: true }))}
        >
            <ArrowLeft size={20} />
        </button>
    );
};

/** The spinner the sign-in pages already use, centred in the space a screen would take. */
export const PageLoading: React.FC = () => (
    <div className="loading">
        <div className="spinner" />
    </div>
);
