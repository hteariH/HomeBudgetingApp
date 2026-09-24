import React from 'react';

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

/** The spinner the landing page already uses, centred in the space a screen would take. */
export const PageLoading: React.FC = () => (
    <div className="loading">
        <div className="spinner" />
    </div>
);
