import { createContext, useContext, useState, type ReactNode } from 'react';
import type { RoleCode } from '../types/api';

interface AuthContextValue {
  username: string | null;
  roles: RoleCode[];
  isAuthenticated: boolean;
  hasAnyRole: (...allowedRoles: RoleCode[]) => boolean;
  isReadOnlyAuditor: boolean;
  login: (username: string, roles?: RoleCode[]) => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const ROLES_STORAGE_KEY = 'caseaxis_roles';
const USERNAME_STORAGE_KEY = 'caseaxis_username';

function readStoredRoles(): RoleCode[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(ROLES_STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(() =>
    localStorage.getItem(USERNAME_STORAGE_KEY),
  );
  const [roles, setRoles] = useState<RoleCode[]>(readStoredRoles);

  function login(newUsername: string, newRoles: RoleCode[] = []) {
    localStorage.setItem(USERNAME_STORAGE_KEY, newUsername);
    localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(newRoles));
    setUsername(newUsername);
    setRoles(newRoles);
  }

  async function logout() {
    await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    });
    localStorage.removeItem(USERNAME_STORAGE_KEY);
    localStorage.removeItem(ROLES_STORAGE_KEY);
    setUsername(null);
    setRoles([]);
  }

  function hasAnyRole(...allowedRoles: RoleCode[]) {
    return roles.some((role) => allowedRoles.includes(role));
  }

  return (
    <AuthContext.Provider
      value={{
        username,
        roles,
        isAuthenticated: !!username,
        hasAnyRole,
        isReadOnlyAuditor: roles.includes('AUDITOR') && roles.length === 1,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
