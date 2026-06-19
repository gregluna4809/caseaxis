import type { ReactNode } from 'react';
import { useAuth } from '../context/AuthContext';
import type { RoleCode } from '../types/api';

interface RoleGateProps {
  allow: RoleCode[];
  children: ReactNode;
  fallback?: ReactNode;
}

export function RoleGate({ allow, children, fallback = null }: RoleGateProps) {
  const { hasAnyRole } = useAuth();
  return hasAnyRole(...allow) ? <>{children}</> : <>{fallback}</>;
}
