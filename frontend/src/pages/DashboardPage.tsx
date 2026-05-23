import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../lib/apiClient';
import type { DashboardActivity, DashboardCaseItem, DashboardMetrics, DashboardOverview } from '../types/api';
import { PriorityBadge, StatusBadge } from '../components/StatusBadge';
import { displayActor, formatDate, formatDateTime } from '../lib/utils';

const EMPTY_METRICS: DashboardMetrics = {
  totalCases: 0,
  openCases: 0,
  assignedToMe: 0,
  overdueCases: 0,
  escalatedCases: 0,
  closedToday: 0,
};

const EMPTY_OVERVIEW: DashboardOverview = {
  metrics: EMPTY_METRICS,
  recentAssignedCases: [],
  escalationWatch: [],
  overdueQueue: [],
  recentActivity: [],
};

export function DashboardPage() {
  const { username } = useAuth();
  const navigate = useNavigate();
  const [overview, setOverview] = useState<DashboardOverview>(EMPTY_OVERVIEW);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadOverview() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.dashboard.overview();
        if (!cancelled) setOverview(data);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load dashboard.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadOverview();
    return () => { cancelled = true; };
  }, []);

  const metrics = overview.metrics;

  return (
    <div className="page-stack dashboard-page">
      <section className="home-hero compact-hero">
        <div>
          <p className="page-kicker">Operations Home</p>
          <h1 className="page-title">Service console</h1>
          <p className="page-subtitle">Signed in as {username}. Live operational workload and activity.</p>
        </div>
        <div className="home-actions">
          <Link to="/cases" className="btn btn-secondary">Open Cases</Link>
          <Link to="/cases/new" className="btn btn-primary">New Case</Link>
        </div>
      </section>

      {error && <div className="form-error">{error}</div>}

      <div className="metrics-grid">
        <MetricCard label="Total Cases" value={metrics.totalCases} loading={loading} />
        <MetricCard label="Open" value={metrics.openCases} loading={loading} tone="active" />
        <MetricCard label="Mine" value={metrics.assignedToMe} loading={loading} />
        <MetricCard label="Overdue" value={metrics.overdueCases} loading={loading} tone="warning" />
        <MetricCard label="Escalated" value={metrics.escalatedCases} loading={loading} tone="danger" />
        <MetricCard label="Closed Today" value={metrics.closedToday} loading={loading} tone="success" />
      </div>

      <div className="dashboard-workspace-grid">
        <CaseWidget
          title="Recent Assigned Cases"
          subtitle="Latest cases assigned to you"
          emptyText="No assigned cases."
          cases={overview.recentAssignedCases}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
        <CaseWidget
          title="Escalation Watch"
          subtitle="Newest escalated records"
          emptyText="No escalated cases."
          cases={overview.escalationWatch}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
        <CaseWidget
          title="Overdue Queue"
          subtitle="Oldest due dates first"
          emptyText="No overdue cases."
          cases={overview.overdueQueue}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
        <ActivityWidget
          activity={overview.recentActivity}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
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

function CaseWidget({
  title,
  subtitle,
  emptyText,
  cases,
  onOpen,
}: {
  title: string;
  subtitle: string;
  emptyText: string;
  cases: DashboardCaseItem[];
  onOpen: (id: string) => void;
}) {
  return (
    <section className="card dashboard-widget">
      <div className="card-header">
        <div>
          <span className="card-title">{title}</span>
          <p className="card-subtitle">{subtitle}</p>
        </div>
      </div>
      <div className="widget-list">
        {cases.length === 0 && <div className="empty-panel compact-empty">{emptyText}</div>}
        {cases.map((c) => (
          <button key={c.id} className="widget-case-row" onClick={() => onOpen(c.id)}>
            <div className="widget-case-main">
              <span className="case-cell-number">{c.caseNumber}</span>
              <strong>{c.title}</strong>
            </div>
            <div className="widget-case-meta">
              <StatusBadge code={c.statusCode} label={c.statusDisplayName} />
              <PriorityBadge code={c.priorityCode} label={c.priorityDisplayName} />
              <span>{formatDate(c.dueDate)}</span>
              <span>{displayActor(c.assignedToId)}</span>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

function ActivityWidget({
  activity,
  onOpen,
}: {
  activity: DashboardActivity[];
  onOpen: (id: string) => void;
}) {
  return (
    <section className="card dashboard-widget activity-widget">
      <div className="card-header">
        <div>
          <span className="card-title">Recent Activity</span>
          <p className="card-subtitle">Notes, status changes, and task updates</p>
        </div>
      </div>
      <div className="dashboard-activity-list">
        {activity.length === 0 && <div className="empty-panel compact-empty">No recent activity.</div>}
        {activity.map((item) => (
          <button
            key={`${item.type}-${item.caseId}-${item.occurredAt}`}
            className="dashboard-activity-row"
            onClick={() => onOpen(item.caseId)}
          >
            <span className={`activity-type activity-type-${item.type.toLowerCase()}`}>{item.type}</span>
            <div>
              <strong>{item.summary}</strong>
              <span>{item.caseNumber} - {item.caseTitle}</span>
            </div>
            <time>{formatDateTime(item.occurredAt)}</time>
          </button>
        ))}
      </div>
    </section>
  );
}
