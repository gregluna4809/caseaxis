import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../lib/apiClient';
import type { DashboardMetrics } from '../types/api';

const EMPTY_METRICS: DashboardMetrics = {
  totalCases: 0,
  openCases: 0,
  assignedToMe: 0,
  overdueCases: 0,
  escalatedCases: 0,
  closedToday: 0,
};

export function DashboardPage() {
  const { username } = useAuth();
  const [metrics, setMetrics] = useState<DashboardMetrics>(EMPTY_METRICS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadMetrics() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.dashboard.metrics();
        if (!cancelled) setMetrics(data);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load dashboard metrics.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadMetrics();
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="page-stack">
      <section className="home-hero compact-hero">
        <div>
          <p className="page-kicker">Operations Home</p>
          <h1 className="page-title">Case console</h1>
          <p className="page-subtitle">Signed in as {username}. Live workload metrics from CaseAxis reporting.</p>
        </div>
        <div className="home-actions">
          <Link to="/cases" className="btn btn-secondary">Open Cases</Link>
          <Link to="/cases/new" className="btn btn-primary">New Case</Link>
        </div>
      </section>

      {error && <div className="form-error">{error}</div>}

      <div className="metrics-grid">
        <MetricCard label="Total Cases" value={metrics.totalCases} loading={loading} />
        <MetricCard label="Open Cases" value={metrics.openCases} loading={loading} tone="active" />
        <MetricCard label="Assigned to Me" value={metrics.assignedToMe} loading={loading} />
        <MetricCard label="Overdue" value={metrics.overdueCases} loading={loading} tone="warning" />
        <MetricCard label="Escalated" value={metrics.escalatedCases} loading={loading} tone="danger" />
        <MetricCard label="Closed Today" value={metrics.closedToday} loading={loading} tone="success" />
      </div>

      <div className="crm-home-grid dense-home-grid">
        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">Work Queue</span>
              <p className="card-subtitle">Fast paths for daily operations.</p>
            </div>
          </div>
          <div className="work-list">
            <Link to="/cases?status=ESCALATED" className="work-list-row">
              <span className="object-icon">E</span>
              <div>
                <strong>Escalation review</strong>
                <span>{metrics.escalatedCases.toLocaleString()} escalated cases</span>
              </div>
            </Link>
            <Link to="/cases" className="work-list-row">
              <span className="object-icon">O</span>
              <div>
                <strong>Open case list</strong>
                <span>{metrics.openCases.toLocaleString()} cases not terminal</span>
              </div>
            </Link>
            <Link to="/cases/new" className="work-list-row">
              <span className="object-icon">+</span>
              <div>
                <strong>New intake</strong>
                <span>Create a case record</span>
              </div>
            </Link>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">Service Risk</span>
              <p className="card-subtitle">Counts needing active attention.</p>
            </div>
          </div>
          <div className="health-list">
            <div><span>Overdue cases</span><strong>{metrics.overdueCases.toLocaleString()}</strong></div>
            <div><span>Escalated cases</span><strong>{metrics.escalatedCases.toLocaleString()}</strong></div>
            <div><span>Assigned to me</span><strong>{metrics.assignedToMe.toLocaleString()}</strong></div>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <span className="card-title">Throughput</span>
              <p className="card-subtitle">Daily closure signal.</p>
            </div>
          </div>
          <div className="throughput-panel">
            <strong>{metrics.closedToday.toLocaleString()}</strong>
            <span>cases closed today</span>
          </div>
        </section>
      </div>
    </div>
  );
}

function MetricCard({
  label,
  value,
  loading,
  tone = 'neutral',
}: {
  label: string;
  value: number;
  loading: boolean;
  tone?: 'neutral' | 'active' | 'warning' | 'danger' | 'success';
}) {
  return (
    <div className={`metric-card metric-card-${tone}`}>
      <span>{label}</span>
      <strong>{loading ? '-' : value.toLocaleString()}</strong>
    </div>
  );
}
