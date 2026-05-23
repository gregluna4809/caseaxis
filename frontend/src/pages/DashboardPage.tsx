import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function DashboardPage() {
  const { username } = useAuth();

  return (
    <div className="page-stack">
      <section className="home-hero">
        <div>
          <p className="page-kicker">Operations Home</p>
          <h1 className="page-title">Good to see you, {username}</h1>
          <p className="page-subtitle">Review case workload, service posture, and high-frequency actions.</p>
        </div>
        <div className="home-actions">
          <Link to="/cases" className="btn btn-secondary">Open Cases</Link>
          <Link to="/cases/new" className="btn btn-primary">New Case</Link>
        </div>
      </section>

      <div className="dashboard-grid">
        <div className="stat-card">
          <div className="stat-card-topline">
            <span className="stat-card-label">Case Volume</span>
            <span className="trend-chip neutral">List API</span>
          </div>
          <div className="stat-card-value">-</div>
          <div className="stat-card-sub">Totals are available from the case list endpoint.</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-topline">
            <span className="stat-card-label">Open Workload</span>
            <span className="trend-chip">Active</span>
          </div>
          <div className="stat-card-value">-</div>
          <div className="stat-card-sub">Operational rollups require backend reporting endpoints.</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-topline">
            <span className="stat-card-label">Escalation Watch</span>
            <span className="trend-chip warning">Monitor</span>
          </div>
          <div className="stat-card-value">-</div>
          <div className="stat-card-sub">Escalated case metrics remain backend-owned.</div>
        </div>
      </div>

      <div className="crm-home-grid">
        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">My Work</span>
              <p className="card-subtitle">Service console shortcuts for case operators.</p>
            </div>
          </div>
          <div className="work-list">
            <Link to="/cases" className="work-list-row">
              <span className="object-icon">C</span>
              <div>
                <strong>All Cases</strong>
                <span>Browse the current object list view</span>
              </div>
            </Link>
            <Link to="/cases/new" className="work-list-row">
              <span className="object-icon">+</span>
              <div>
                <strong>Case Intake</strong>
                <span>Create a new operational record</span>
              </div>
            </Link>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">Service Controls</span>
              <p className="card-subtitle">Backend-enforced governance remains unchanged.</p>
            </div>
          </div>
          <div className="health-list">
            <div><span>Authentication</span><strong>JWT protected</strong></div>
            <div><span>Workflow</span><strong>Controlled status transitions</strong></div>
            <div><span>Auditability</span><strong>Critical actions tracked server-side</strong></div>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">Recent Activity</span>
              <p className="card-subtitle">Activity cards will populate when reporting APIs exist.</p>
            </div>
          </div>
          <div className="empty-panel">No activity summary endpoint is currently exposed.</div>
        </section>
      </div>
    </div>
  );
}
