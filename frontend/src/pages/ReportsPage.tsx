import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../lib/apiClient';
import { CASE_STATUSES, CASE_TYPES } from '../lib/lookups';
import { formatDate } from '../lib/utils';
import type {
  AssigneeWorkload,
  ClientSummary,
  ClosureTrendPoint,
  DistributionItem,
  OrganizationSummary,
  OrganizationWorkload,
  OverdueAgingBucket,
  ReportFilters,
  ReportSummary,
} from '../types/api';

type RangeKey = 'LAST_7' | 'LAST_30' | 'LAST_90' | 'CUSTOM';

const RANGE_LABELS: Record<RangeKey, string> = {
  LAST_7: 'Last 7 days',
  LAST_30: 'Last 30 days',
  LAST_90: 'Last 90 days',
  CUSTOM: 'Custom range',
};

const EMPTY_SUMMARY: ReportSummary = {
  totalCases: 0,
  openCases: 0,
  closedCases: 0,
  overdueCases: 0,
  escalatedCases: 0,
  averageResolutionHours: null,
  openTasks: 0,
  completedTasks: 0,
};

export function ReportsPage() {
  const [range, setRange] = useState<RangeKey>('LAST_30');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [organizationId, setOrganizationId] = useState('');
  const [clientId, setClientId] = useState('');
  const [caseType, setCaseType] = useState('');
  const [status, setStatus] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const [assigneeSort, setAssigneeSort] = useState('openCases');
  const [organizationSort, setOrganizationSort] = useState('totalCases');

  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([]);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [assigneeOptions, setAssigneeOptions] = useState<AssigneeWorkload[]>([]);

  const [summary, setSummary] = useState<ReportSummary>(EMPTY_SUMMARY);
  const [statusDistribution, setStatusDistribution] = useState<DistributionItem[]>([]);
  const [typeDistribution, setTypeDistribution] = useState<DistributionItem[]>([]);
  const [overdueAging, setOverdueAging] = useState<OverdueAgingBucket[]>([]);
  const [assigneeWorkload, setAssigneeWorkload] = useState<AssigneeWorkload[]>([]);
  const [organizationWorkload, setOrganizationWorkload] = useState<OrganizationWorkload[]>([]);
  const [closureTrend, setClosureTrend] = useState<ClosureTrendPoint[]>([]);

  const [loading, setLoading] = useState(true);
  const [filterLoading, setFilterLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const filters = useMemo<ReportFilters>(() => {
    const dates = dateRange(range, customStart, customEnd);
    return {
      ...dates,
      organizationId: organizationId || undefined,
      clientId: clientId || undefined,
      caseType: caseType || undefined,
      status: status || undefined,
      assigneeId: assigneeId || undefined,
    };
  }, [range, customStart, customEnd, organizationId, clientId, caseType, status, assigneeId]);

  useEffect(() => {
    let cancelled = false;

    async function loadFilters() {
      setFilterLoading(true);
      try {
        const [orgPage, clientPage] = await Promise.all([
          api.organizations.list({ page: 0, size: 500, active: true }),
          api.clients.list({ page: 0, size: 500, active: true }),
        ]);
        if (!cancelled) {
          setOrganizations(orgPage.content);
          setClients(clientPage.content);
        }
      } finally {
        if (!cancelled) setFilterLoading(false);
      }
    }

    void loadFilters();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadClients() {
      try {
        const page = await api.clients.list({
          page: 0,
          size: 500,
          active: true,
          organizationId: organizationId || undefined,
        });
        if (!cancelled) setClients(page.content);
      } catch {
        if (!cancelled) setClients([]);
      }
    }

    setClientId('');
    void loadClients();
    return () => { cancelled = true; };
  }, [organizationId]);

  useEffect(() => {
    let cancelled = false;

    async function loadReports() {
      setLoading(true);
      setError(null);
      try {
        const optionFilters = { ...filters, assigneeId: undefined };
        const [
          nextSummary,
          nextStatusDistribution,
          nextTypeDistribution,
          nextOverdueAging,
          nextAssigneeWorkload,
          nextOrganizationWorkload,
          nextClosureTrend,
          nextAssigneeOptions,
        ] = await Promise.all([
          api.reports.summary(filters),
          api.reports.statusDistribution(filters),
          api.reports.typeDistribution(filters),
          api.reports.overdueAging(filters),
          api.reports.assigneeWorkload({ ...filters, sort: assigneeSort }),
          api.reports.organizationWorkload({ ...filters, sort: organizationSort }),
          api.reports.closureTrend(filters),
          api.reports.assigneeWorkload(optionFilters),
        ]);

        if (!cancelled) {
          setSummary(nextSummary);
          setStatusDistribution(nextStatusDistribution);
          setTypeDistribution(nextTypeDistribution);
          setOverdueAging(nextOverdueAging);
          setAssigneeWorkload(nextAssigneeWorkload);
          setOrganizationWorkload(nextOrganizationWorkload);
          setClosureTrend(nextClosureTrend);
          setAssigneeOptions(nextAssigneeOptions.filter((row) => row.assigneeId));
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load reports.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadReports();
    return () => { cancelled = true; };
  }, [filters, assigneeSort, organizationSort]);

  function resetFilters() {
    setRange('LAST_30');
    setCustomStart('');
    setCustomEnd('');
    setOrganizationId('');
    setClientId('');
    setCaseType('');
    setStatus('');
    setAssigneeId('');
    setAssigneeSort('openCases');
    setOrganizationSort('totalCases');
  }

  async function exportCsv() {
    setExporting(true);
    try {
      const csv = await api.reports.exportCsv(filters);
      downloadText(csv, 'caseaxis-report.csv', 'text/csv;charset=utf-8');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to export CSV.');
    } finally {
      setExporting(false);
    }
  }

  async function exportJson() {
    setExporting(true);
    try {
      const json = await api.reports.exportJson(filters);
      downloadText(JSON.stringify(json, null, 2), 'caseaxis-report.json', 'application/json;charset=utf-8');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to export JSON.');
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="page-stack reports-page">
      <section className="object-header reports-header">
        <div className="object-header-main">
          <span className="object-icon">R</span>
          <div>
            <p className="page-kicker">Analytics</p>
            <h1 className="page-title">Determinations</h1>
            <p className="page-subtitle">Benefit review outcomes, workload, and service timeliness</p>
          </div>
        </div>
        <div className="home-actions">
          <button className="btn btn-secondary" onClick={exportCsv} disabled={exporting}>Export CSV</button>
          <button className="btn btn-secondary" onClick={exportJson} disabled={exporting}>Export JSON</button>
        </div>
      </section>

      <section className="list-view-card report-filter-card">
        <div className="list-view-toolbar reports-filter-toolbar">
          <div>
            <span className="list-view-title">Report Filters</span>
            <span className="list-view-subtitle">Server-driven aggregate filters across cases, tasks, and workload views</span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={resetFilters}>Reset filters</button>
        </div>
        <div className="report-filter-grid">
          <Field label="Date range">
            <select className="form-select" value={range} onChange={(e) => setRange(e.target.value as RangeKey)}>
              {Object.entries(RANGE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </Field>
          {range === 'CUSTOM' && (
            <>
              <Field label="Start date">
                <input className="form-input" type="date" value={customStart} onChange={(e) => setCustomStart(e.target.value)} />
              </Field>
              <Field label="End date">
                <input className="form-input" type="date" value={customEnd} onChange={(e) => setCustomEnd(e.target.value)} />
              </Field>
            </>
          )}
          <Field label="Organization">
            <select className="form-select" value={organizationId} onChange={(e) => setOrganizationId(e.target.value)} disabled={filterLoading}>
              <option value="">All organizations</option>
              {organizations.map((org) => (
                <option key={org.id} value={org.id}>{org.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Client">
            <select className="form-select" value={clientId} onChange={(e) => setClientId(e.target.value)} disabled={filterLoading}>
              <option value="">All clients</option>
              {clients.map((client) => (
                <option key={client.id} value={client.id}>{client.displayName}</option>
              ))}
            </select>
          </Field>
          <Field label="Case type">
            <select className="form-select" value={caseType} onChange={(e) => setCaseType(e.target.value)}>
              <option value="">All types</option>
              {CASE_TYPES.map((type) => (
                <option key={type.code} value={type.code}>{type.label}</option>
              ))}
            </select>
          </Field>
          <Field label="Status">
            <select className="form-select" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {CASE_STATUSES.map((caseStatus) => (
                <option key={caseStatus.code} value={caseStatus.code}>{caseStatus.label}</option>
              ))}
            </select>
          </Field>
          <Field label="Assignee">
            <select className="form-select" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)} disabled={loading && assigneeOptions.length === 0}>
              <option value="">All assignees</option>
              {assigneeOptions.map((assignee) => (
                <option key={assignee.assigneeId ?? assignee.assigneeName} value={assignee.assigneeId ?? ''}>
                  {assignee.assigneeName}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </section>

      {error && <div className="form-error">{error}</div>}

      <div className="metrics-grid report-metrics-grid">
        <MetricCard label="Total Cases" value={summary.totalCases} loading={loading} />
        <MetricCard label="Open Cases" value={summary.openCases} loading={loading} tone="active" />
        <MetricCard label="Closed Cases" value={summary.closedCases} loading={loading} tone="success" />
        <MetricCard label="Overdue Cases" value={summary.overdueCases} loading={loading} tone="warning" />
        <MetricCard label="Escalated Cases" value={summary.escalatedCases} loading={loading} tone="danger" />
        <MetricCard label="Avg Resolution" value={formatDuration(summary.averageResolutionHours)} loading={loading} />
        <MetricCard label="Open Actions" value={summary.openTasks} loading={loading} tone="active" />
        <MetricCard label="Completed Actions" value={summary.completedTasks} loading={loading} tone="success" />
      </div>

      {loading && <div className="spinner">Loading report analytics...</div>}

      {!loading && !error && (
        <>
          <div className="reports-grid">
            <DistributionSection
              title="Case Status Distribution"
              subtitle="Case counts by workflow status"
              rows={statusDistribution}
              codePrefix="badge-status-"
            />
            <DistributionSection
              title="Case Type Distribution"
              subtitle="Case counts by intake category"
              rows={typeDistribution}
            />
            <OverdueSection rows={overdueAging} />
          </div>

          <div className="reports-wide-grid">
            <AssigneeWorkloadSection rows={assigneeWorkload} sort={assigneeSort} onSort={setAssigneeSort} />
            <OrganizationWorkloadSection rows={organizationWorkload} sort={organizationSort} onSort={setOrganizationSort} />
          </div>

          <ClosureTrendSection rows={closureTrend} />
        </>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="form-group report-filter-field">
      <span className="form-label">{label}</span>
      {children}
    </label>
  );
}

function MetricCard({
  label,
  value,
  loading,
  tone = 'neutral',
}: {
  label: string;
  value: number | string;
  loading: boolean;
  tone?: 'neutral' | 'active' | 'warning' | 'danger' | 'success';
}) {
  return (
    <div className={`metric-card metric-card-${tone}`}>
      <span>{label}</span>
      <strong>{loading ? '-' : typeof value === 'number' ? value.toLocaleString() : value}</strong>
    </div>
  );
}

function DistributionSection({
  title,
  subtitle,
  rows,
  codePrefix,
}: {
  title: string;
  subtitle: string;
  rows: DistributionItem[];
  codePrefix?: string;
}) {
  const max = maxCount(rows);
  return (
    <section className="card report-section-card">
      <ReportSectionHeader title={title} subtitle={subtitle} />
      <BarChart rows={rows.map((row) => ({ label: row.label, count: row.count }))} max={max} />
      <ReportTableEmpty rows={rows} colSpan={3} />
      {rows.length > 0 && (
        <div className="table-wrapper report-table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Category</th>
                <th>Count</th>
                <th>Share</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.code}>
                  <td>
                    <span className={`badge ${codePrefix ? `${codePrefix}${row.code}` : 'badge-neutral'}`}>{row.label}</span>
                  </td>
                  <td>{row.count.toLocaleString()}</td>
                  <td className="td-muted">{percentage(row.count, totalCount(rows))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function OverdueSection({ rows }: { rows: OverdueAgingBucket[] }) {
  const max = maxCount(rows);
  return (
    <section className="card report-section-card">
      <ReportSectionHeader title="Overdue Aging Report" subtitle="Open overdue cases grouped by age" />
      <BarChart rows={rows.map((row) => ({ label: row.bucket, count: row.count }))} max={max} tone="warning" />
      <ReportTableEmpty rows={rows.filter((row) => row.count > 0)} colSpan={3} />
      <div className="table-wrapper report-table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>Bucket</th>
              <th>Age</th>
              <th>Count</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.bucket}>
                <td><span className="badge badge-warning">{row.bucket}</span></td>
                <td className="td-muted">{row.maxDays ? `${row.minDays}-${row.maxDays} days` : `${row.minDays}+ days`}</td>
                <td>{row.count.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function AssigneeWorkloadSection({
  rows,
  sort,
  onSort,
}: {
  rows: AssigneeWorkload[];
  sort: string;
  onSort: (sort: string) => void;
}) {
  return (
    <section className="card report-section-card">
      <ReportSectionHeader title="Assignee Workload" subtitle="Open, overdue, escalated, and closed cases by owner" />
      <div className="table-wrapper report-workload-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>Assignee</th>
              <SortableTh label="Open Cases" sortKey="openCases" activeSort={sort} onSort={onSort} />
              <SortableTh label="Overdue Cases" sortKey="overdueCases" activeSort={sort} onSort={onSort} />
              <SortableTh label="Escalated Cases" sortKey="escalatedCases" activeSort={sort} onSort={onSort} />
              <SortableTh label="Closed This Period" sortKey="closedThisPeriod" activeSort={sort} onSort={onSort} />
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && <EmptyRow colSpan={5} message="No assignee workload for the selected filters." />}
            {rows.map((row) => (
              <tr key={row.assigneeId ?? row.assigneeName}>
                <td className="case-cell-title">{row.assigneeName}</td>
                <td>{row.openCases.toLocaleString()}</td>
                <td className={row.overdueCases > 0 ? 'td-overdue' : 'td-muted'}>{row.overdueCases.toLocaleString()}</td>
                <td>{row.escalatedCases.toLocaleString()}</td>
                <td>{row.closedThisPeriod.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function OrganizationWorkloadSection({
  rows,
  sort,
  onSort,
}: {
  rows: OrganizationWorkload[];
  sort: string;
  onSort: (sort: string) => void;
}) {
  return (
    <section className="card report-section-card">
      <ReportSectionHeader title="Organization Workload" subtitle="Case volume and escalation concentration by organization" />
      <div className="table-wrapper report-workload-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>Organization</th>
              <SortableTh label="Total Cases" sortKey="totalCases" activeSort={sort} onSort={onSort} />
              <SortableTh label="Open Cases" sortKey="openCases" activeSort={sort} onSort={onSort} />
              <SortableTh label="Escalated Cases" sortKey="escalatedCases" activeSort={sort} onSort={onSort} />
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && <EmptyRow colSpan={4} message="No organization workload for the selected filters." />}
            {rows.map((row) => (
              <tr key={row.organizationId ?? row.organizationName}>
                <td>
                  <div className="task-cell">
                    <span className="task-cell-title">{row.organizationName}</span>
                    {row.organizationCode && <span className="task-cell-subtitle">{row.organizationCode}</span>}
                  </div>
                </td>
                <td>{row.totalCases.toLocaleString()}</td>
                <td>{row.openCases.toLocaleString()}</td>
                <td>{row.escalatedCases.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ClosureTrendSection({ rows }: { rows: ClosureTrendPoint[] }) {
  const max = maxCount(rows);
  return (
    <section className="card report-section-card">
      <ReportSectionHeader title="Closure Trend" subtitle="Daily closures for the selected reporting period" />
      {rows.length === 0 ? (
        <div className="empty-panel compact-empty">No closures in this period.</div>
      ) : (
        <div className="closure-chart" aria-label="Closure trend">
          {rows.map((row) => (
            <div key={row.date} className="closure-bar-column" title={`${formatDate(row.date)}: ${row.count.toLocaleString()}`}>
              <span className="closure-bar-value">{row.count > 0 ? row.count : ''}</span>
              <span
                className="closure-bar"
                style={{ height: `${barPercent(row.count, max)}%` }}
              />
              <span className="closure-bar-label">{shortDate(row.date)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function ReportSectionHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="card-header">
      <div>
        <span className="card-title">{title}</span>
        <p className="card-subtitle">{subtitle}</p>
      </div>
    </div>
  );
}

function BarChart({ rows, max, tone = 'primary' }: { rows: { label: string; count: number }[]; max: number; tone?: 'primary' | 'warning' }) {
  if (rows.length === 0 || rows.every((row) => row.count === 0)) {
    return <div className="empty-panel compact-empty">No report data for the selected filters.</div>;
  }

  return (
    <div className="report-bar-chart">
      {rows.map((row) => (
        <div key={row.label} className="report-bar-row">
          <span className="report-bar-label">{row.label}</span>
          <div className="report-bar-track">
            <span
              className={`report-bar report-bar-${tone}`}
              style={{ width: `${barPercent(row.count, max)}%` }}
            />
          </div>
          <strong>{row.count.toLocaleString()}</strong>
        </div>
      ))}
    </div>
  );
}

function SortableTh({
  label,
  sortKey,
  activeSort,
  onSort,
}: {
  label: string;
  sortKey: string;
  activeSort: string;
  onSort: (sort: string) => void;
}) {
  return (
    <th>
      <button className={`table-sort-btn ${activeSort === sortKey ? 'active' : ''}`} onClick={() => onSort(sortKey)}>
        {label}
      </button>
    </th>
  );
}

function ReportTableEmpty<T>({ rows, colSpan }: { rows: T[]; colSpan: number }) {
  if (rows.length > 0) return null;
  return (
    <div className="table-wrapper report-table-wrapper">
      <table className="data-table">
        <tbody>
          <EmptyRow colSpan={colSpan} message="No report data for the selected filters." />
        </tbody>
      </table>
    </div>
  );
}

function EmptyRow({ colSpan, message }: { colSpan: number; message: string }) {
  return (
    <tr>
      <td colSpan={colSpan}>
        <div className="empty-state-panel compact-report-empty">
          <div className="empty-state-title">No data</div>
          <div className="empty-state-body">{message}</div>
        </div>
      </td>
    </tr>
  );
}

function dateRange(range: RangeKey, customStart: string, customEnd: string) {
  if (range === 'CUSTOM') {
    return {
      startDate: customStart || undefined,
      endDate: customEnd || undefined,
    };
  }

  const days = range === 'LAST_7' ? 7 : range === 'LAST_90' ? 90 : 30;
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - (days - 1));
  return {
    startDate: isoDate(start),
    endDate: isoDate(end),
  };
}

function isoDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

function maxCount(rows: Array<{ count: number }>) {
  return Math.max(1, ...rows.map((row) => row.count));
}

function totalCount(rows: Array<{ count: number }>) {
  return rows.reduce((sum, row) => sum + row.count, 0);
}

function barPercent(count: number, max: number) {
  if (count <= 0) return 2;
  return Math.max(6, Math.round((count / max) * 100));
}

function percentage(count: number, total: number) {
  if (total === 0) return '0%';
  return `${Math.round((count / total) * 100)}%`;
}

function formatDuration(hours: number | null) {
  if (hours == null) return '-';
  if (hours >= 48) return `${(hours / 24).toFixed(1)} days`;
  return `${hours.toFixed(1)} hrs`;
}

function shortDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function downloadText(content: string, filename: string, type: string) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
