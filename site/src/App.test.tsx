import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import App from './App';
import { en } from './locales/en';
import { ru } from './locales/ru';
import { uk } from './locales/uk';
import { COMPOSE_STEPS } from './install';

it('shows how to install with docker compose', () => {
    render(<App />);
    expect(screen.getByText(en['install.title'])).toBeInTheDocument();
    expect(screen.getByText(COMPOSE_STEPS[4])).toBeInTheDocument();
});

it('has every key in every language', () => {
    const keys = Object.keys(en).sort();
    expect(Object.keys(ru).sort()).toEqual(keys);
    expect(Object.keys(uk).sort()).toEqual(keys);
});

it('never offers a hosted account', () => {
    render(<App />);
    expect(document.body.innerHTML).not.toMatch(/\/register|\/login|Premium/);
});

it('announces nothing as coming soon, since every feature on the page has shipped', () => {
    render(<App />);
    expect(document.body.textContent).not.toContain(en['features.soon']);
    expect(document.body.textContent).not.toMatch(/coming soon/i);
});

it('lets the last feature card fill its row, so the grid has no empty cell', () => {
    render(<App />);
    const cards = Array.from(document.querySelectorAll('#features article'));
    expect(cards).toHaveLength(7);
    // Seven cards: 2 columns leave one over, 3 columns leave one over.
    expect(cards[6].className).toContain('sm:col-span-2');
    expect(cards[6].className).toContain('lg:col-span-3');
    cards.slice(0, 6).forEach((c) => expect(c.className).not.toContain('col-span'));
});
