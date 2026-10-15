import { fireEvent, render, screen } from '@testing-library/react';
import React, { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n';
import { ApiError } from '../api/http';
import { AmountField } from './AmountField';
import { ConfirmDialog } from './ConfirmDialog';
import { Dialog } from './Dialog';
import { filterCurrencies } from './CurrencySelect';
import { ErrorBanner } from './ErrorBanner';
import { iconFor, KNOWN_ICON_KEYS, PICKER_ICONS } from './icons';

const wrap = (ui: React.ReactNode) =>
    render(
        <MemoryRouter>
            <I18nProvider>{ui}</I18nProvider>
        </MemoryRouter>,
    );

describe('AmountField', () => {
    const Harness = ({ onChange }: { onChange: (v: string) => void }) => {
        const [v, setV] = useState('');
        return (
            <AmountField
                value={v}
                decimals={2}
                symbol="$"
                label="amount"
                onChange={next => {
                    setV(next);
                    onChange(next);
                }}
            />
        );
    };

    it('sanitises what is typed and previews the arithmetic', () => {
        const onChange = vi.fn();
        wrap(<Harness onChange={onChange} />);
        fireEvent.change(screen.getByLabelText('amount'), { target: { value: '1a2+3' } });
        expect(onChange).toHaveBeenLastCalledWith('12+3');
        expect(screen.getByText('= 15.00')).toBeInTheDocument();
    });

    it('ignores a second separator', () => {
        const onChange = vi.fn();
        wrap(<Harness onChange={onChange} />);
        fireEvent.change(screen.getByLabelText('amount'), { target: { value: '12.34.' } });
        expect(onChange).toHaveBeenLastCalledWith('12.34');
    });
});

describe('ConfirmDialog', () => {
    it('Escape cancels and the button confirms', () => {
        const onConfirm = vi.fn();
        const onCancel = vi.fn();
        wrap(<ConfirmDialog open title="Delete?" destructive onConfirm={onConfirm} onCancel={onCancel} />);
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(onCancel).toHaveBeenCalledTimes(1);
        fireEvent.click(screen.getByText('Delete'));
        expect(onConfirm).toHaveBeenCalledTimes(1);
    });
});

describe('Dialog', () => {
    it('keeps focus where the user is typing when the caller re-renders', () => {
        const Harness = () => {
            const [a, setA] = useState('');
            const [b, setB] = useState('');
            return (
                <Dialog open onClose={() => undefined} title="x">
                    <input aria-label="first" value={a} onChange={e => setA(e.target.value)} />
                    <input aria-label="second" value={b} onChange={e => setB(e.target.value)} />
                </Dialog>
            );
        };
        wrap(<Harness />);
        const second = screen.getByLabelText('second');
        second.focus();
        fireEvent.change(second, { target: { value: 'typed' } });
        expect(document.activeElement).toBe(second);
    });
});

describe('ErrorBanner', () => {
    it('falls back to a localised sentence when the server wrote none', () => {
        wrap(<ErrorBanner error={new ApiError(500, null)} />);
        expect(screen.getByRole('alert').textContent).toContain('Something went wrong');
        expect(screen.queryByRole('link')).toBeNull();
    });
});

describe('icons', () => {
    it('every icon the Compose client offers has a glyph', () => {
        for (const key of PICKER_ICONS) expect(KNOWN_ICON_KEYS, key).toContain(key);
    });
    it("the server's default categories get the icons they name", () => {
        for (const key of ['briefcase', 'laptop', 'home', 'shopping-cart', 'truck', 'film', 'zap', 'tag']) {
            expect(iconFor(key), key).not.toBe(iconFor('more_horiz'));
        }
    });
    it('an unknown key falls back rather than breaking the row', () => {
        expect(iconFor('no_such_icon')).toBe(iconFor('more_horiz'));
        expect(iconFor(null)).toBe(iconFor('more_horiz'));
    });
});

describe('filterCurrencies', () => {
    const all = [
        { code: 'EUR', name: 'Euro' },
        { code: 'UAH', name: 'Ukrainian hryvnia' },
        { code: 'USD', name: 'US Dollar' },
        { code: 'AUD', name: 'Australian Dollar' },
    ];
    it('puts codes starting with the query first', () =>
        expect(filterCurrencies(all, 'u').map(c => c.code)).toEqual(['UAH', 'USD', 'EUR', 'AUD']));
    it('matches names too', () => expect(filterCurrencies(all, 'dollar').map(c => c.code)).toEqual(['USD', 'AUD']));
    it('three letters land on the currency', () => expect(filterCurrencies(all, 'usd')[0].code).toBe('USD'));
});
