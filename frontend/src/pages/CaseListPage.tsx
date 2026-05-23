import { useMemo, useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { CaseSummary, Page } from '../types/api';
import { StatusBadge, PriorityBadge } from '../components/StatusBadge';
import { displayActor, formatDate } from '../lib/utils';

const STATUS_FILTERS = ['ALL', 'NEW', 'ASSIGNED', 'IN_REVIEW', 'PENDING_INFO', 'ESCALATED', 'APPROVED', 'DENIED', 'CLOSED', 'REOPENED'];

export function CaseListPage() {
  const navigate = useNavigate();

  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<CaseSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.cases.list(page, 20);
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
  }, [page]);

  const totalPages = result?.totalPages ?? 0;
  const totalElements = result?.totalElements ?? 0;

  const filteredCases = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (result?.content ?? []).filter((c) => {
      const matchesStatus = statusFilter === 'ALL' || c.statusCode === statusFilter;
      const matchesQuery = !normalizedQuery
        || c.caseNumber.toLowerCase().includes(normalizedQuery)
        || c.title.toLowerCase().includes(normalizedQuery)
        || c.typeDisplayName.toLowerCase().includes(normalizedQuery);
      return matchesStatus && matchesQuery;
    });
  }, [query, result?.content, statusFilter]);

  return (
    <div className="page-stack">
      <section className="object-header">
        <div className="object-header-main">
          <span className="object-icon">C</span>
          <div>
            <p className="page-kicker">Cases</p>
            <h1 className="page-title">All Cases</h1>
            <p className="page-subtitle">
              {loading ? 'Loading...' : `${totalElements.toLocaleString()} records in this object list`}
            </p>
          </div>
        </div>
        <Link to="/cases/new" className="btn btn-primary">New</Link>
      </section>

      <div className="list-view-card">
        <div className="list-view-toolbar">
          <div>
            <span className="list-view-title">All Open and Closed Cases</span>
            <span className="list-view-subtitle">Sorted by server pagination</span>
          </div>
          <div className="toolbar-controls">
            <div className="search-control">
              <span className="search-icon">Search</span>
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search this list"
                aria-label="Search cases"
              />
            </div>
            <select
              className="form-select compact-select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              aria-label="Filter by status"
            >
              {STATUS_FILTERS.map((status) => (
                <option key={status} value={status}>
                  {status === 'ALL' ? 'All statuses' : status.replace(/_/g, ' ')}
                </option>
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
                  {filteredCases.length === 0 && (
                    <tr>
                      <td colSpan={7} className="empty-state">
                        No cases match the current filters.
                      </td>
                    </tr>
                  )}
                  {filteredCases.map((c) => (
                    <tr
                      key={c.id}
                      className="clickable"
                      onClick={() => navigate(`/cases/${c.id}`)}
                    >
                      <td>
                        <div className="case-cell">
                          <span className="case-cell-number">{c.caseNumber}</span>
                          <span className="case-cell-title">{c.title}</span>
                        </div>
                      </td>
                      <td>
                        <StatusBadge code={c.statusCode} label={c.statusDisplayName} />
                      </td>
                      <td>
                        <PriorityBadge code={c.priorityCode} label={c.priorityDisplayName} />
                      </td>
                      <td className="td-muted">{c.typeDisplayName}</td>
                      <td className="td-muted">{displayActor(c.assignedToId)}</td>
                      <td className="td-muted">{formatDate(c.dueDate)}</td>
                      <td className="td-muted">{formatDate(c.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="pagination">
                <span>
                  Page {page + 1} of {totalPages} - {totalElements.toLocaleString()} cases
                </span>
                <div className="pagination-controls">
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
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
