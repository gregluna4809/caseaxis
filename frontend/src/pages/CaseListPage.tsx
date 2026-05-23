import { useState, useEffect } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { CaseSummary, Page } from '../types/api';
import { StatusBadge, PriorityBadge } from '../components/StatusBadge';
import { displayActor, formatDate } from '../lib/utils';
import { CASE_TYPES, PRIORITIES } from '../lib/lookups';

const STATUS_FILTERS = ['ALL', 'NEW', 'ASSIGNED', 'IN_REVIEW', 'PENDING_INFO', 'ESCALATED', 'APPROVED', 'DENIED', 'CLOSED', 'REOPENED'];
const PAGE_SIZE = 50;

export function CaseListPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<CaseSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState(searchParams.get('q') ?? '');
  const [statusFilter, setStatusFilter] = useState(searchParams.get('status') ?? 'ALL');
  const [priorityFilter, setPriorityFilter] = useState(searchParams.get('priority') ?? 'ALL');
  const [typeFilter, setTypeFilter] = useState(searchParams.get('type') ?? 'ALL');

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.cases.list({
          page,
          size: PAGE_SIZE,
          q: query,
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          priority: priorityFilter === 'ALL' ? undefined : priorityFilter,
          type: typeFilter === 'ALL' ? undefined : typeFilter,
        });
        if (!cancelled) setResult(data);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load cases.');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void load();
    return () => { cancelled = true; };
  }, [page, priorityFilter, query, statusFilter, typeFilter]);

  function resetPageAnd(run: () => void) {
    setPage(0);
    run();
  }

  const totalPages = result?.totalPages ?? 0;
  const totalElements = result?.totalElements ?? 0;
  const cases = result?.content ?? [];

  return (
    <div className="page-stack">
      <section className="object-header">
        <div className="object-header-main">
          <span className="object-icon">C</span>
          <div>
            <p className="page-kicker">Cases</p>
            <h1 className="page-title">All Cases</h1>
            <p className="page-subtitle">
              {loading ? 'Loading...' : `${totalElements.toLocaleString()} records matching current filters`}
            </p>
          </div>
        </div>
        <Link to="/cases/new" className="btn btn-primary">New</Link>
      </section>

      <div className="list-view-card">
        <div className="list-view-toolbar">
          <div>
            <span className="list-view-title">Case List View</span>
            <span className="list-view-subtitle">Server-side search, filters, and pagination</span>
          </div>
          <div className="toolbar-controls">
            <div className="search-control">
              <span className="search-icon">Search</span>
              <input
                value={query}
                onChange={(e) => resetPageAnd(() => setQuery(e.target.value))}
                placeholder="Case #, title, or type"
                aria-label="Search cases"
              />
            </div>
            <select
              className="form-select compact-select"
              value={statusFilter}
              onChange={(e) => resetPageAnd(() => setStatusFilter(e.target.value))}
              aria-label="Filter by status"
            >
              {STATUS_FILTERS.map((status) => (
                <option key={status} value={status}>
                  {status === 'ALL' ? 'All statuses' : status.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
            <select
              className="form-select compact-select"
              value={priorityFilter}
              onChange={(e) => resetPageAnd(() => setPriorityFilter(e.target.value))}
              aria-label="Filter by priority"
            >
              <option value="ALL">All priorities</option>
              {PRIORITIES.map((priority) => (
                <option key={priority.code} value={priority.code}>{priority.label}</option>
              ))}
            </select>
            <select
              className="form-select compact-select"
              value={typeFilter}
              onChange={(e) => resetPageAnd(() => setTypeFilter(e.target.value))}
              aria-label="Filter by type"
            >
              <option value="ALL">All types</option>
              {CASE_TYPES.map((type) => (
                <option key={type.code} value={type.code}>{type.label}</option>
              ))}
            </select>
          </div>
        </div>

        {error && <div className="form-error surface-error">{error}</div>}

        {loading && <div className="spinner">Loading cases...</div>}

        {!loading && !error && result && (
          <>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Case</th>
                    <th>Status</th>
                    <th>Priority</th>
                    <th>Type</th>
                    <th>Assignee</th>
                    <th>Due</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {cases.length === 0 && (
                    <tr>
                      <td colSpan={7} className="empty-state">
                        No cases match the current filters.
                      </td>
                    </tr>
                  )}
                  {cases.map((c) => (
                    <tr
                      key={c.id}
                      className="clickable"
                      title="Open case record"
                      onClick={() => navigate(`/cases/${c.id}`)}
                    >
                      <td>
                        <div className="case-cell">
                          <span className="case-cell-number">{c.caseNumber}</span>
                          <span className="case-cell-title">{c.title}</span>
                        </div>
                      </td>
                      <td><StatusBadge code={c.statusCode} label={c.statusDisplayName} /></td>
                      <td><PriorityBadge code={c.priorityCode} label={c.priorityDisplayName} /></td>
                      <td className="td-muted">{c.typeDisplayName}</td>
                      <td className="td-muted">{displayActor(c.assignedToId)}</td>
                      <td className="td-muted">{formatDate(c.dueDate)}</td>
                      <td className="td-muted">{formatDate(c.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="pagination">
              <span>
                Page {page + 1} of {Math.max(totalPages, 1)} - {totalElements.toLocaleString()} records
              </span>
              <div className="pagination-controls">
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => setPage(0)}
                  disabled={result.first}
                >
                  First
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={result.first}
                >
                  Prev
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={result.last}
                >
                  Next
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => setPage(Math.max(0, totalPages - 1))}
                  disabled={result.last}
                >
                  Last
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
