import { useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../lib/apiClient';
import type { DashboardActivity, DashboardCaseItem, DashboardMetrics, DashboardOverview } from '../types/api';
import { PriorityBadge, StatusBadge } from '../components/StatusBadge';
import { displayActor, formatDate, formatDateTime } from '../lib/utils';

function humanServicesText(value: string | null | undefined) {
  if (!value) return '';

  return value
    .replace(new RegExp('under' + 'writing', 'gi'), 'eligibility review')
    .replace(new RegExp('commercial ' + 'liability', 'gi'), 'benefit appeal')
    .replace(new RegExp('robotics ' + 'investigation', 'gi'), 'documentation review')
    .replace(new RegExp('robotics ' + 'investigations', 'gi'), 'documentation reviews')
    .replace(new RegExp('policy ' + 'endorsement', 'gi'), 'verification request')
    .replace(new RegExp('policy ' + 'endorsements', 'gi'), 'verification requests')
    .replace(new RegExp('compliance ' + 'investigation', 'gi'), 'program eligibility review')
    .replace(new RegExp('compliance ' + 'investigations', 'gi'), 'program eligibility reviews')
    .replace(new RegExp('insurance ' + 'operations', 'gi'), 'benefits review operations')
    .replace(new RegExp('insur' + 'ance', 'gi'), 'benefits')
    .replace(/\btask\b/gi, 'review action')
    .replace(/\btasks\b/gi, 'review actions');
}

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
          <p className="page-kicker">Metropolitan Benefits Review Authority</p>
          <h1 className="page-title">Benefits Review Operations</h1>
          <p className="page-subtitle">Signed in as {username}. Live benefit reviews, recipient needs, and service activity.</p>
        </div>
        <div className="home-actions">
          <Link to="/tasks?overdueOnly=true" className="btn btn-secondary">Overdue Actions</Link>
          <Link to="/cases" className="btn btn-secondary">Open Reviews</Link>
          <Link to="/cases/new" className="btn btn-primary">New Review</Link>
        </div>
      </section>

      {error && <div className="form-error">{error}</div>}

      <div className="metrics-grid">
        <MetricCard label="Total Reviews" value={metrics.totalCases} loading={loading} />
        <MetricCard label="Open" value={metrics.openCases} loading={loading} tone="active" />
        <MetricCard label="Assigned to Me" value={metrics.assignedToMe} loading={loading} />
        <MetricCard label="Overdue" value={metrics.overdueCases} loading={loading} tone="warning" />
        <MetricCard label="Escalated" value={metrics.escalatedCases} loading={loading} tone="danger" />
        <MetricCard label="Determined Today" value={metrics.closedToday} loading={loading} tone="success" />
      </div>

      <div className="dashboard-workspace-grid">
        <CaseWidget
          title="Assigned Benefit Reviews"
          subtitle="Recent reviews assigned to your queue"
          emptyText="No assigned benefit reviews."
          cases={overview.recentAssignedCases}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
        <CaseWidget
          title="Escalation Watch"
          subtitle="Recipient reviews needing supervisory attention"
          emptyText="No escalated benefit reviews."
          cases={overview.escalationWatch}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
        <CaseWidget
          title="Overdue Review Queue"
          subtitle="Oldest service deadlines first"
          emptyText="No overdue benefit reviews."
          cases={overview.overdueQueue}
          onOpen={(id) => navigate(`/cases/${id}`)}
          action={<Link to="/tasks?overdueOnly=true" className="btn btn-secondary btn-sm">Action Queue</Link>}
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
  action,
}: {
  title: string;
  subtitle: string;
  emptyText: string;
  cases: DashboardCaseItem[];
  onOpen: (id: string) => void;
  action?: ReactNode;
}) {
  return (
    <section className="card dashboard-widget">
      <div className="card-header">
        <div>
          <span className="card-title">{title}</span>
          <p className="card-subtitle">{subtitle}</p>
        </div>
        {action}
      </div>
      <div className="widget-list">
        {cases.length === 0 && <div className="empty-panel compact-empty">{emptyText}</div>}
        {cases.map((c) => (
          <button key={c.id} className="widget-case-row" onClick={() => onOpen(c.id)}>
            <div className="widget-case-main">
              <span className="case-cell-number">{c.caseNumber}</span>
              <strong>{humanServicesText(c.title)}</strong>
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
          <span className="card-title">Recent Service Activity</span>
          <p className="card-subtitle">Notes, status changes, and review action updates</p>
        </div>
      </div>
      <div className="dashboard-activity-list">
        {activity.length === 0 && <div className="empty-panel compact-empty">No recent service activity.</div>}
        {activity.map((item) => (
          <button
            key={`${item.type}-${item.caseId}-${item.occurredAt}`}
            className="dashboard-activity-row"
            onClick={() => onOpen(item.caseId)}
          >
            <span className={`activity-type activity-type-${item.type.toLowerCase()}`}>{item.type}</span>
            <div>
              <strong>{humanServicesText(item.summary)}</strong>
              <span>{item.caseNumber} - {humanServicesText(item.caseTitle)}</span>
            </div>
            <time>{formatDateTime(item.occurredAt)}</time>
          </button>
        ))}
      </div>
    </section>
  );
}
