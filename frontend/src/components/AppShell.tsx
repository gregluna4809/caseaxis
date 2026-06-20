import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { GlobalSearch } from './GlobalSearch';
import { RoleGate } from './RoleGate';

export function AppShell() {
  const { username, isReadOnlyAuditor, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <div className="app-layout">
      <header className="global-header">
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">MBRA</div>
          <div>
            <span className="sidebar-brand-name">MBRA</span>
            <span className="sidebar-brand-subtitle">Benefits Review Operations</span>
          </div>
        </div>

        <GlobalSearch />

        <div className="global-header-right">
          <span className="workspace-selector">Benefits Review</span>
          {isReadOnlyAuditor && <span className="username-chip">Read-only auditor</span>}
          <span className="username-chip">{username}</span>
          <button className="btn btn-secondary btn-sm" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </header>

      <nav className="sidebar" aria-label="Primary navigation">
        <span className="sidebar-section-label">Workspace</span>
        <div className="workspace-card">
          <span className="workspace-dot" />
          <div>
            <strong>Benefits Review Operations</strong>
            <span>Human services portal</span>
          </div>
        </div>

        <span className="sidebar-section-label">Review Work</span>
        <ul className="sidebar-nav">
          <li>
            <NavLink to="/dashboard" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">H</span>
              <span>Home</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/cases" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">C</span>
              <span>Benefit Reviews</span>
            </NavLink>
          </li>
          <RoleGate allow={['ADMIN', 'SUPERVISOR', 'CASE_WORKER']}>
            <li>
              <NavLink to="/cases/new" className={({ isActive }) => (isActive ? 'active' : '')}>
                <span className="nav-icon">+</span>
                <span>New Review</span>
              </NavLink>
            </li>
          </RoleGate>
          <li>
            <NavLink to="/clients" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">P</span>
              <span>Recipients</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/organizations" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">O</span>
              <span>Agencies</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/tasks" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">T</span>
              <span>Review Actions</span>
            </NavLink>
          </li>
          <li>
            <RoleGate allow={['ADMIN', 'SUPERVISOR', 'AUDITOR']}>
              <NavLink to="/reports" className={({ isActive }) => (isActive ? 'active' : '')}>
                <span className="nav-icon">R</span>
                <span>Determinations</span>
              </NavLink>
            </RoleGate>
          </li>
        </ul>
      </nav>

      <div className="main-content">
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
