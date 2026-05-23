import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function DashboardPage() {
  const { username } = useAuth();

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Dashboard</h1>
        <p className="page-subtitle">Welcome back, {username}.</p>
      </div>

      <div className="dashboard-grid">
        <div className="stat-card">
          <div className="stat-card-label">Cases</div>
          <div className="stat-card-value">—</div>
          <div className="stat-card-sub">View all cases to see totals</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">Open</div>
          <div className="stat-card-value">—</div>
          <div className="stat-card-sub">Aggregate reporting coming soon</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">Escalated</div>
          <div className="stat-card-value">—</div>
          <div className="stat-card-sub">Aggregate reporting coming soon</div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <span className="card-title">Quick Links</span>
        </div>
        <div className="card-body" style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <Link to="/cases" className="btn btn-primary">
            View All Cases
          </Link>
        </div>
      </div>
    </div>
  );
}
