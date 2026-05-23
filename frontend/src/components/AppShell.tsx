import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function AppShell() {
  const { username, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="app-layout">
      <nav className="sidebar" aria-label="Primary navigation">
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">CA</div>
          <div>
            <span className="sidebar-brand-name">CaseAxis</span>
            <span className="sidebar-brand-subtitle">Case operations</span>
          </div>
        </div>

        <div className="sidebar-workspace">
          <span className="sidebar-section-label">Workspace</span>
          <div className="workspace-card">
            <span className="workspace-dot" />
            <div>
              <strong>Operations</strong>
              <span>Production queue</span>
            </div>
          </div>
        </div>

        <span className="sidebar-section-label">Navigation</span>
        <ul className="sidebar-nav">
          <li>
            <NavLink to="/dashboard" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">D</span>
              <span>Dashboard</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/cases" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">C</span>
              <span>Cases</span>
            </NavLink>
          </li>
        </ul>

        <div className="sidebar-user">
          <div className="user-avatar">{username?.slice(0, 1).toUpperCase() ?? 'U'}</div>
          <div className="sidebar-user-meta">
            <strong>{username}</strong>
            <span>Authenticated</span>
          </div>
        </div>
      </nav>

      <div className="main-content">
        <header className="app-header">
          <div>
            <span className="app-header-title">Enterprise case management</span>
            <span className="app-header-subtitle">Workflow, tasks, notes, and decisions</span>
          </div>
          <div className="app-header-right">
            <span className="username-chip">{username}</span>
            <button className="btn btn-secondary btn-sm" onClick={handleLogout}>
              Sign out
            </button>
          </div>
        </header>

        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
