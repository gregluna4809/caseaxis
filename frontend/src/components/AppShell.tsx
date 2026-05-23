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
      <nav className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">CA</div>
          <span className="sidebar-brand-name">CaseAxis</span>
        </div>

        <span className="sidebar-section-label">Navigation</span>
        <ul className="sidebar-nav">
          <li>
            <NavLink
              to="/dashboard"
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              Dashboard
            </NavLink>
          </li>
          <li>
            <NavLink
              to="/cases"
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              Cases
            </NavLink>
          </li>
        </ul>
      </nav>

      <div className="main-content">
        <header className="app-header">
          <span className="app-header-title">CaseAxis</span>
          <div className="app-header-right">
            <span className="username-chip">{username}</span>
            <button className="btn btn-secondary" onClick={handleLogout}>
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
