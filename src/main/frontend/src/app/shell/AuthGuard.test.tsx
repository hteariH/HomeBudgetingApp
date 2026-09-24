import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, expect, it } from 'vitest';
import { session } from '../api/session';
import { AuthGuard } from './AuthGuard';

const Where = () => {
    const l = useLocation();
    return <div data-testid="where">{l.pathname + l.search}</div>;
};

const renderAt = (path: string) =>
    render(
        <MemoryRouter initialEntries={[path]}>
            <Routes>
                <Route path="/login" element={<Where />} />
                <Route
                    path="/app/*"
                    element={
                        <AuthGuard>
                            <div>inside</div>
                        </AuthGuard>
                    }
                />
            </Routes>
        </MemoryRouter>,
    );

beforeEach(() => localStorage.clear());

it('sends a browser with no session to sign-in, carrying where it was', () => {
    renderAt('/app/accounts?x=1');
    expect(screen.getByTestId('where').textContent).toBe('/login?next=%2Fapp%2Faccounts%3Fx%3D1');
});

it('lets a signed-in browser through', () => {
    session.save({ token: 'a', refreshToken: 'r', email: 'e@x' });
    renderAt('/app');
    expect(screen.getByText('inside')).toBeInTheDocument();
});

it('leaves the moment the session ends', () => {
    session.save({ token: 'a', refreshToken: 'r', email: 'e@x' });
    renderAt('/app/transactions');
    act(() => session.clear());
    expect(screen.getByTestId('where').textContent).toBe('/login?next=%2Fapp%2Ftransactions');
});
