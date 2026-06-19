import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { GlobalSearch } from './GlobalSearch';
import { RoleGate } from './RoleGate';

type IconName = 'home' | 'cases' | 'plus' | 'clients' | 'orgs' | 'tasks' | 'reports' | 'logout';

function ShellIcon({ name }: { name: IconName }) {
  const paths: Record<IconName, string[]> = {
    home: ['M3 10.5 12 3l9 7.5', 'M5 10v10h14V10', 'M9 20v-6h6v6'],
    cases: ['M6 4h12l3 4v12H3V8l3-4Z', 'M3 8h18', 'M8 13h8', 'M8 17h5'],
    plus: ['M12 5v14', 'M5 12h14'],
    clients: ['M16 19c0-2.2-1.8-4-4-4s-4 1.8-4 4', 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M20 18c0-1.7-1-3.1-2.5-3.7'],
    orgs: ['M4 20V7l8-4 8 4v13', 'M9 20v-6h6v6', 'M8 9h1', 'M15 9h1', 'M8 12h1', 'M15 12h1'],
    tasks: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3.5 6l1 1 2-2', 'M3.5 12l1 1 2-2', 'M3.5 18l1 1 2-2'],
    reports: ['M4 19V5', 'M4 19h16', 'M8 16v-5', 'M13 16V8', 'M18 16v-8'],
    logout: ['M15 17l5-5-5-5', 'M20 12H9', 'M11 20H5V4h6'],
  };

  return (
    <svg className="shell-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      {paths[name].map((d) => <path key={d} d={d} />)}
    </svg>
  );
}

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
          <div className="sidebar-brand-mark service-mark">MB</div>
          <div>
            <span className="sidebar-brand-name">MBRA Review Operations</span>
            <span className="sidebar-brand-subtitle">Benefits Operations Center</span>
          </div>
        </div>

        <GlobalSearch />

        <div className="global-header-right">
          <span className="workspace-selector">Human Services Operations</span>
          {isReadOnlyAuditor && <span className="username-chip">Read-only reviewer</span>}
          <span className="username-chip">{username}</span>
          <button className="btn btn-secondary btn-sm shell-logout" onClick={handleLogout}>
            <ShellIcon name="logout" />
            Sign out
          </button>
        </div>
      </header>

      <nav className="sidebar" aria-label="Primary navigation">
        <div className="workspace-card">
          <span className="workspace-dot" />
          <div>
            <strong>Metropolitan Benefits Review Authority</strong>
            <span>Structured benefit review work</span>
          </div>
        </div>

        <div className="motto-card">Every Case Matters. Fair. Timely. Accurate.</div>

        <span className="sidebar-section-label">Operations</span>
        <ul className="sidebar-nav">
          <li>
            <NavLink to="/dashboard" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon"><ShellIcon name="home" /></span>
              <span>Operations Desk</span>
            </NavLink>
          </li>
        </ul>

        <span className="sidebar-section-label">Case Work</span>
        <ul className="sidebar-nav">
          <li>
            <NavLink to="/cases" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon"><ShellIcon name="cases" /></span>
              <span>Review Queue</span>
            </NavLink>
          </li>
          <RoleGate allow={['ADMIN', 'SUPERVISOR', 'CASE_WORKER']}>
            <li>
              <NavLink to="/cases/new" className={({ isActive }) => (isActive ? 'active' : '')}>
                <span className="nav-icon"><ShellIcon name="plus" /></span>
                <span>New Review</span>
              </NavLink>
            </li>
          </RoleGate>
          <li>
            <NavLink to="/tasks" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon"><ShellIcon name="tasks" /></span>
              <span>Review Actions</span>
            </NavLink>
          </li>
        </ul>

        <span className="sidebar-section-label">Records</span>
        <ul className="sidebar-nav">
          <li>
            <NavLink to="/clients" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon"><ShellIcon name="clients" /></span>
              <span>Recipients</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/organizations" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon"><ShellIcon name="orgs" /></span>
              <span>Agencies</span>
            </NavLink>
          </li>
        </ul>

        <span className="sidebar-section-label">Oversight</span>
        <ul className="sidebar-nav">
          <li>
            <RoleGate allow={['ADMIN', 'SUPERVISOR', 'AUDITOR']}>
              <NavLink to="/reports" className={({ isActive }) => (isActive ? 'active' : '')}>
                <span className="nav-icon"><ShellIcon name="reports" /></span>
                <span>Service Outcomes</span>
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
