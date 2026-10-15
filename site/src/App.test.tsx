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
