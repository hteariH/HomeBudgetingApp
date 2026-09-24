import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Search } from 'lucide-react';
import { useT } from '../../i18n';

export interface CurrencyOption {
    code: string;
    name: string;
}

/**
 * Matches for a typed query: codes that start with it first, then any code or name that
 * contains it. Typing three letters lands on the currency (a75bdef).
 */
export function filterCurrencies<T extends CurrencyOption>(all: T[], query: string): T[] {
    const q = query.trim().toLowerCase();
    if (!q) return all;
    const prefix = all.filter(c => c.code.toLowerCase().startsWith(q));
    const rest = all.filter(
        c => !prefix.includes(c) && (c.code.toLowerCase().includes(q) || c.name.toLowerCase().includes(q)),
    );
    return [...prefix, ...rest];
}

/**
 * A currency picker that filters as you type. The field doubles as the search box:
 * opening it clears the text so the first keystroke starts a fresh query.
 */
export const CurrencySelect: React.FC<{
    selected: string;
    currencies: CurrencyOption[];
    onSelect: (code: string) => void;
    label?: string;
    disabled?: boolean;
}> = ({ selected, currencies, onSelect, label, disabled = false }) => {
    const t = useT();
    const [query, setQuery] = useState<string | null>(null);
    const [active, setActive] = useState(0);
    const root = useRef<HTMLDivElement>(null);
    const open = query !== null;

    const matches = useMemo(() => filterCurrencies(currencies, query ?? ''), [currencies, query]);
    const selectedLabel = currencies.find(c => c.code === selected);

    useEffect(() => {
        if (!open) return;
        const onDown = (e: MouseEvent) => {
            if (!root.current?.contains(e.target as Node)) setQuery(null);
        };
        document.addEventListener('mousedown', onDown);
        return () => document.removeEventListener('mousedown', onDown);
    }, [open]);

    useEffect(() => setActive(0), [query]);

    const pick = (code: string) => {
        onSelect(code);
        setQuery(null);
    };

    return (
        <div className="form-group" ref={root}>
            {label && <label className="form-label">{label}</label>}
            <div className="relative">
                <Search size={16} className="input-icon" />
                <input
                    className="form-control form-control-icon"
                    disabled={disabled}
                    value={open ? query : selectedLabel ? `${selectedLabel.code} · ${selectedLabel.name}` : selected}
                    placeholder={t('currencies_search')}
                    onFocus={() => setQuery('')}
                    onChange={e => setQuery(e.target.value)}
                    onKeyDown={e => {
                        if (!open) return;
                        if (e.key === 'ArrowDown') {
                            e.preventDefault();
                            setActive(a => Math.min(a + 1, matches.length - 1));
                        } else if (e.key === 'ArrowUp') {
                            e.preventDefault();
                            setActive(a => Math.max(a - 1, 0));
                        } else if (e.key === 'Enter' && matches[active]) {
                            e.preventDefault();
                            pick(matches[active].code);
                        } else if (e.key === 'Escape') {
                            setQuery(null);
                        }
                    }}
                    role="combobox"
                    aria-expanded={open}
                />
                {open && (
                    <div role="listbox" className="absolute left-0 right-0 top-[calc(100%+4px)] z-50 max-h-80 overflow-y-auto rounded-token border border-line bg-card p-1 shadow-token-lg">
                        {matches.length === 0 && <div className="px-3 py-2 text-sm text-ink-dim">{t('currencies_no_matches')}</div>}
                        {matches.map((c, i) => (
                            <button
                                type="button"
                                role="option"
                                aria-selected={c.code === selected}
                                key={c.code}
                                onMouseDown={e => e.preventDefault()}
                                onClick={() => pick(c.code)}
                                className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm ${
                                    i === active ? 'bg-raised text-ink' : 'text-ink-soft'
                                }`}
                            >
                                <span className="flex-1">
                                    {c.code} · {c.name}
                                </span>
                                {c.code === selected && <Check size={16} className="text-brand-light" />}
                            </button>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};
