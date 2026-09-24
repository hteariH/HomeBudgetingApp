import React, { useEffect, useRef } from 'react';
import type Decimal from 'decimal.js';
import { Calculator } from '../lib/calculator';
import { sanitizeExpression } from '../lib/expressionInput';

/** The amount an expression stands for, or null while it cannot be read. */
export const evaluateAmount = (expression: string, decimals: number): Decimal | null =>
    Calculator.evaluate(expression, decimals);

/**
 * The big centred amount. Port of AmountField.kt: the currency symbol, one oversized
 * number, and an `= result` line once the field holds arithmetic. Input is sanitised on the
 * way in, so a paste or a stray letter cannot leave the field unreadable.
 */
export const AmountField: React.FC<{
    value: string;
    onChange: (expression: string) => void;
    decimals: number;
    symbol: string;
    autoFocus?: boolean;
    onSubmit?: () => void;
    label?: string;
}> = ({ value, onChange, decimals, symbol, autoFocus = false, onSubmit, label }) => {
    const input = useRef<HTMLInputElement>(null);

    useEffect(() => {
        // Entry is the app's hot path: opening the form should put the caret in the amount.
        if (autoFocus) input.current?.focus();
    }, [autoFocus]);

    const preview = Calculator.hasOperator(value) ? Calculator.evaluate(value, decimals) : null;
    const shown = value || '0';
    const big = shown.length > 10;

    return (
        <div className="flex flex-col items-center">
            <div className="flex max-w-full items-baseline justify-center gap-1.5">
                <span className="text-2xl font-medium text-ink-soft">{symbol}</span>
                <input
                    ref={input}
                    value={value}
                    onChange={e => onChange(sanitizeExpression(e.target.value))}
                    onKeyDown={e => {
                        if (e.key === 'Enter') onSubmit?.();
                    }}
                    inputMode="decimal"
                    placeholder="0"
                    aria-label={label}
                    // Sized to its content so the number sits beside its symbol instead of
                    // being dragged away by a full-width box.
                    size={Math.max(1, shown.length)}
                    className={`min-w-0 max-w-full bg-transparent text-center font-bold tabular-nums text-ink caret-brand outline-none placeholder:text-ink-dim ${
                        big ? 'text-[40px]' : 'text-[56px]'
                    } leading-tight`}
                />
            </div>
            {preview && <div className="text-lg font-medium text-brand-light">= {preview.toFixed(Math.max(0, decimals))}</div>}
        </div>
    );
};
