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
      <header className="global-header">
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">CA</div>
          <div>
            <span className="sidebar-brand-name">CaseAxis</span>
            <span className="sidebar-brand-subtitle">Operations Workspace</span>
          </div>
        </div>

        <div className="global-search" aria-label="Global search">
          <span className="search-icon">Search</span>
          <span>Search cases, clients, organizations</span>
        </div>

        <div className="global-header-right">
          <span className="workspace-selector">Case Operations</span>
          <span className="username-chip">{username}</span>
          <button className="btn btn-secondary btn-sm" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </header>

      <nav className="sidebar" aria-label="Primary navigation">
        <span className="sidebar-section-label">Apps</span>
        <div className="workspace-card">
          <span className="workspace-dot" />
          <div>
            <strong>Service Console</strong>
            <span>Case management</span>
          </div>
        </div>

        <span className="sidebar-section-label">Objects</span>
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
              <span>Cases</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/clients" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">P</span>
              <span>Clients</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/organizations" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">O</span>
              <span>Organizations</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/tasks" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">T</span>
              <span>Tasks</span>
            </NavLink>
          </li>
          <li>
            <NavLink to="/reports" className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="nav-icon">R</span>
              <span>Reports</span>
            </NavLink>
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
