import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function DashboardPage() {
  const { username } = useAuth();

  return (
    <div className="page-stack">
      <div className="page-header page-header-row">
        <div>
          <p className="page-kicker">Operations overview</p>
          <h1 className="page-title">Dashboard</h1>
          <p className="page-subtitle">Welcome back, {username}. Monitor the active case workspace.</p>
        </div>
        <Link to="/cases/new" className="btn btn-primary">New Case</Link>
      </div>

      <div className="dashboard-grid">
        <div className="stat-card">
          <div className="stat-card-topline">
            <span className="stat-card-label">Total cases</span>
            <span className="trend-chip">Live</span>
          </div>
          <div className="stat-card-value">-</div>
          <div className="stat-card-sub">Open the case queue for paged operational totals.</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-topline">
            <span className="stat-card-label">Open workload</span>
            <span className="trend-chip neutral">Queue</span>
          </div>
          <div className="stat-card-value">-</div>
          <div className="stat-card-sub">Aggregate reporting is intentionally backend-driven.</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-topline">
            <span className="stat-card-label">Escalations</span>
            <span className="trend-chip warning">Watch</span>
          </div>
          <div className="stat-card-value">-</div>
          <div className="stat-card-sub">Escalation metrics will surface from reporting APIs.</div>
        </div>
      </div>

      <div className="split-grid">
        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">Primary workflow</span>
              <p className="card-subtitle">High-frequency case operations.</p>
            </div>
          </div>
          <div className="action-list">
            <Link to="/cases" className="action-list-item">
              <span>Review case queue</span>
              <strong>View all cases</strong>
            </Link>
            <Link to="/cases/new" className="action-list-item">
              <span>Intake new work</span>
              <strong>Create case</strong>
            </Link>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">Operating posture</span>
              <p className="card-subtitle">Controls preserved in the Spring backend.</p>
            </div>
          </div>
          <div className="health-list">
            <div><span>Authentication</span><strong>JWT protected</strong></div>
            <div><span>Workflow</span><strong>Controlled transitions</strong></div>
            <div><span>Auditability</span><strong>Server enforced</strong></div>
          </div>
        </section>
      </div>
    </div>
  );
}
