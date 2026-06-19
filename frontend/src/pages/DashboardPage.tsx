import { useEffect, useState, type ReactNode } from 'react';
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
  const activeWork = metrics.openCases + metrics.escalatedCases + metrics.overdueCases;
  const appealsWaiting = metrics.escalatedCases;
  const deadlineRisk = metrics.overdueCases;
  const immediateAttention = appealsWaiting + deadlineRisk;
  const averageResolutionDays = activeWork === 0
    ? '0 days'
    : `${Math.max(7, Math.round((metrics.overdueCases * 14 + metrics.openCases * 6 + metrics.escalatedCases * 10) / activeWork))} days`;
  const achievedToday = metrics.closedToday;

  return (
    <div className="page-stack dashboard-page command-center-page">
      <section className="mission-brief" aria-label="MBRA operations brief">
        <div className="mission-brief-main">
          <p className="page-kicker">MBRA Operations Desk</p>
          <h1 className="mission-title">
            {loading
              ? 'Loading today\'s review posture.'
              : immediateAttention > 0
                ? `${immediateAttention.toLocaleString()} cases require operational attention.`
                : 'Today\'s review posture is stable.'}
          </h1>
          <p className="mission-statement">
            Signed in as {username}. Every Case Matters means disciplined review queues, timely follow-up, and accurate determinations.
          </p>
          <div className="mission-actions">
            <Link to="/tasks?overdueOnly=true" className="btn btn-primary">Open deadline queue</Link>
            <Link to="/cases" className="btn btn-secondary">Review case queue</Link>
            <Link to="/cases/new" className="btn btn-secondary">Start a new review</Link>
          </div>
        </div>

        <div className="mission-alerts" aria-label="Attention required">
          <Signal label="Appeals requiring decision" value={appealsWaiting} loading={loading} tone="danger" detail="Requires review and documented next action" />
          <Signal label="Reviews nearing deadline" value={deadlineRisk} loading={loading} tone="warning" detail="Due dates require operational follow-up" />
          <Signal label="Determinations completed today" value={achievedToday} loading={loading} tone="success" detail="Cases moved through structured review" />
        </div>
      </section>

      {error && <div className="form-error">{error}</div>}

      <div className="operations-strip" aria-label="Operational awareness">
        <Signal label="Open benefit reviews" value={metrics.openCases} loading={loading} tone="active" detail={`${metrics.assignedToMe.toLocaleString()} assigned to your workstream`} />
        <Signal label="Assigned to your queue" value={metrics.assignedToMe} loading={loading} detail="Reviews waiting for staff action" />
        <Signal label="Resolution pace" value={averageResolutionDays} loading={loading} tone="warning" detail="Estimated pace for open reviews" />
      </div>

      <div className="command-layout">
        <section className="operations-panel priority-panel">
          <div className="panel-heading">
            <span className="panel-eyebrow">Priority Work</span>
            <h2>Case review queues</h2>
            <p>Start with appeal decisions, due dates, and reviews requiring documented action.</p>
          </div>
          <div className="queue-stack">
            <CaseWidget
              title="Appeals Requiring Decision"
              subtitle="Escalated reviews awaiting determination"
              emptyText="No appeals are waiting for decision."
              cases={overview.escalationWatch}
              onOpen={(id) => navigate(`/cases/${id}`)}
              compact
            />
            <CaseWidget
              title="Reviews Near Deadline"
              subtitle="Oldest due dates and timeliness risk"
              emptyText="No reviews are near deadline."
              cases={overview.overdueQueue}
              onOpen={(id) => navigate(`/cases/${id}`)}
              compact
              action={<Link to="/tasks?overdueOnly=true" className="btn btn-secondary btn-sm">Open actions</Link>}
            />
          </div>
        </section>

        <section className="operations-panel workload-panel">
          <div className="panel-heading">
            <span className="panel-eyebrow">Assigned Work</span>
            <h2>Your review queue</h2>
            <p>{loading ? 'Loading queue...' : `${metrics.assignedToMe.toLocaleString()} reviews are waiting for your next action.`}</p>
          </div>
          <CaseWidget
            title="Assigned Benefit Reviews"
            subtitle="Newest benefit reviews assigned to you"
            emptyText="No benefit reviews are currently assigned to your queue."
            cases={overview.recentAssignedCases}
            onOpen={(id) => navigate(`/cases/${id}`)}
            compact
          />
        </section>

        <ActivityWidget
          activity={overview.recentActivity}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
      </div>

      <div className="dashboard-workspace-grid dashboard-fallback-grid">
        <CaseWidget
          title="Case Officer Queue"
          subtitle="Newest benefit reviews assigned to you"
          emptyText="No assigned benefit reviews."
          cases={overview.recentAssignedCases}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
        <CaseWidget
          title="Appeals Requiring Decision"
          subtitle="Escalated benefit determinations awaiting review"
          emptyText="No appeals or escalated determinations."
          cases={overview.escalationWatch}
          onOpen={(id) => navigate(`/cases/${id}`)}
        />
        <CaseWidget
          title="Reviews Near Deadline"
          subtitle="Oldest statutory due dates first"
          emptyText="No overdue reviews."
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

function Signal({
  label,
  value,
  loading,
  tone = 'neutral',
  detail,
}: {
  label: string;
  value: number | string;
  loading: boolean;
  tone?: 'neutral' | 'active' | 'warning' | 'danger' | 'success';
  detail: string;
}) {
  return (
    <div className={`mission-signal mission-signal-${tone}`}>
      <span>{label}</span>
      <strong>{loading ? '-' : typeof value === 'number' ? value.toLocaleString() : value}</strong>
      <em>{detail}</em>
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
  compact = false,
}: {
  title: string;
  subtitle: string;
  emptyText: string;
  cases: DashboardCaseItem[];
  onOpen: (id: string) => void;
  action?: ReactNode;
  compact?: boolean;
}) {
  return (
    <section className={`dashboard-widget queue-widget ${compact ? 'queue-widget-compact' : 'card'}`}>
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
    <section className="operations-panel activity-widget command-activity">
      <div className="panel-heading">
        <div>
          <span className="panel-eyebrow">Recent Activity</span>
          <h2>Review activity stream</h2>
          <p>Recent decisions, notes, status changes, and actions recorded by case staff.</p>
        </div>
      </div>
      <div className="dashboard-activity-list">
        {activity.length === 0 && <div className="empty-panel compact-empty">No recent review activity.</div>}
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
