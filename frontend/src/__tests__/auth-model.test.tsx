import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppShell } from '../components/AppShell';
import { ProtectedRoute } from '../components/ProtectedRoute';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { LoginPage } from '../pages/LoginPage';
import type { RoleCode } from '../types/api';

function seedAuth(roles: RoleCode[] = []) {
  localStorage.setItem('caseaxis_username', 'caseaxis-user');
  localStorage.setItem('caseaxis_roles', JSON.stringify(roles));
}

function renderProtectedRoute(initialPath = '/protected') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div>Login Route</div>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/protected" element={<div>Protected Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

function renderShellForRoles(roles: RoleCode[]) {
  seedAuth(roles);

  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/dashboard" element={<div>Dashboard Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

function LogoutHarness() {
  const { logout } = useAuth();
  return <button onClick={() => void logout()}>Logout</button>;
}

describe('frontend auth model', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('redirects unauthenticated users away from protected routes', () => {
    renderProtectedRoute();

    expect(screen.getByText('Login Route')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('renders protected routes for authenticated users', () => {
    seedAuth(['CASE_WORKER']);

    renderProtectedRoute();

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
    expect(screen.queryByText('Login Route')).not.toBeInTheDocument();
  });

  it.each<RoleCode>(['CASE_WORKER', 'SUPERVISOR', 'ADMIN'])(
    'shows mutating case navigation for %s users',
    (role) => {
      renderShellForRoles([role]);

      const navigation = screen.getByRole('navigation', { name: 'Primary navigation' });
      expect(within(navigation).getByRole('link', { name: /new review/i })).toBeInTheDocument();
    },
  );

  it.each<RoleCode>(['SUPERVISOR', 'ADMIN', 'AUDITOR'])(
    'shows report navigation for %s users',
    (role) => {
      renderShellForRoles([role]);

      const navigation = screen.getByRole('navigation', { name: 'Primary navigation' });
      expect(within(navigation).getByRole('link', { name: /service outcomes/i })).toBeInTheDocument();
    },
  );

  it('keeps auditors read-only in role-conditional navigation', () => {
    renderShellForRoles(['AUDITOR']);

    const navigation = screen.getByRole('navigation', { name: 'Primary navigation' });
    expect(screen.getByText('Read-only reviewer')).toBeInTheDocument();
    expect(within(navigation).getByRole('link', { name: /service outcomes/i })).toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: /new review/i })).not.toBeInTheDocument();
  });

  it('logs in through the cookie auth flow without storing a JavaScript-readable token', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      status: 200,
      json: async () => ({
        success: true,
        data: { username: 'caseworker', roles: ['CASE_WORKER'] },
        timestamp: '2026-06-19T00:00:00Z',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/dashboard" element={<div>Dashboard Content</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'caseworker' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await screen.findByText('Dashboard Content');

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
    }));
    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers).not.toHaveProperty('Authorization');
    expect(localStorage.getItem('caseaxis_username')).toBe('caseworker');
    expect(localStorage.getItem('caseaxis_roles')).toBe(JSON.stringify(['CASE_WORKER']));
    expect(localStorage.getItem('caseaxis_token')).toBeNull();
  });

  it('logs out by clearing local auth metadata and asking the server to clear the cookie', async () => {
    seedAuth(['SUPERVISOR']);
    const fetchMock = vi.fn().mockResolvedValue({ status: 200 });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AuthProvider>
        <LogoutHarness />
      </AuthProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));

    await waitFor(() => {
      expect(localStorage.getItem('caseaxis_username')).toBeNull();
      expect(localStorage.getItem('caseaxis_roles')).toBeNull();
    });
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    });
  });
});
