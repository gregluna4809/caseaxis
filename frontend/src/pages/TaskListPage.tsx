import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { Page, TaskSummary } from '../types/api';
import { TaskStatusBadge } from '../components/StatusBadge';
import { TASK_STATUSES } from '../lib/lookups';
import { formatDate, formatDateTime } from '../lib/utils';

const PAGE_SIZE = 50;
const STATUS_FILTERS = ['ALL', ...TASK_STATUSES.map((s) => s.code)];

export function TaskListPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<TaskSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState(searchParams.get('q') ?? '');
  const [statusFilter, setStatusFilter] = useState(searchParams.get('status') ?? 'ALL');
  const [overdueOnly, setOverdueOnly] = useState(searchParams.get('overdueOnly') === 'true');

  useEffect(() => {
    let cancelled = false;

    async function loadTasks() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.tasks.workspace({
          page,
          size: PAGE_SIZE,
          q: query,
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          overdueOnly,
        });
        if (!cancelled) setResult(data);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load tasks.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadTasks();
    return () => { cancelled = true; };
  }, [page, query, statusFilter, overdueOnly]);

  function resetPageAnd(run: () => void) {
    setPage(0);
    run();
  }

  const tasks = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const totalElements = result?.totalElements ?? 0;

  return (
    <div className="page-stack">
      <section className="object-header">
        <div className="object-header-main">
          <span className="object-icon">T</span>
          <div>
            <p className="page-kicker">Review Actions</p>
            <h1 className="page-title">Officer Action Queue</h1>
            <p className="page-subtitle">
              {loading ? 'Loading...' : `${totalElements.toLocaleString()} review actions matching current filters`}
            </p>
          </div>
        </div>
      </section>

      <div className="list-view-card">
        <div className="list-view-toolbar">
          <div>
            <span className="list-view-title">Review Action List</span>
            <span className="list-view-subtitle">Search action items, status filters, overdue queue, and pagination</span>
          </div>
          <div className="toolbar-controls">
            <div className="search-control task-search-control">
              <span className="search-icon">Search</span>
              <input
                value={query}
                onChange={(e) => resetPageAnd(() => setQuery(e.target.value))}
                placeholder="Action title"
                aria-label="Search tasks"
              />
            </div>
            <select
              className="form-select compact-select"
              value={statusFilter}
              onChange={(e) => resetPageAnd(() => setStatusFilter(e.target.value))}
              aria-label="Filter by task status"
            >
              {STATUS_FILTERS.map((status) => (
                <option key={status} value={status}>
                  {status === 'ALL' ? 'All statuses' : status.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
            <label className={`toggle-control ${overdueOnly ? 'active' : ''}`}>
              <input
                type="checkbox"
                checked={overdueOnly}
                onChange={(e) => resetPageAnd(() => setOverdueOnly(e.target.checked))}
              />
              <span>Overdue only</span>
            </label>
          </div>
        </div>

        {error && <div className="form-error surface-error">{error}</div>}
        {loading && <div className="spinner">Loading tasks...</div>}

        {!loading && !error && result && (
          <>
            <div className="table-wrapper task-table-wrapper">
              <table className="data-table task-data-table">
                <thead>
                  <tr>
                    <th>Review Action</th>
                    <th>Status</th>
                    <th>Due Date</th>
                    <th>Case #</th>
                    <th>Case Title</th>
                    <th>Assignee</th>
                    <th>Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {tasks.length === 0 && (
                    <tr>
                      <td colSpan={7}>
                        <div className="empty-state-panel">
                          <div className="empty-state-title">No review actions found</div>
                          <div className="empty-state-body">
                            No review actions match the current filters. Adjust the search, status, or overdue filter.
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                  {tasks.map((task) => {
                    const overdue = isTaskOverdue(task);
                    return (
                      <tr
                        key={task.id}
                        className={`clickable ${overdue ? 'task-row-overdue' : ''}`}
                        title="Open review action record"
                        onClick={() => navigate(`/tasks/${task.id}`)}
                      >
                        <td>
                          <div className="task-cell">
                            <span className="task-cell-title">{task.title}</span>
                            {task.description && <span className="task-cell-subtitle">{task.description}</span>}
                          </div>
                        </td>
                        <td><TaskStatusBadge code={task.statusCode} label={task.statusDisplayName} /></td>
                        <td className={overdue ? 'td-overdue' : 'td-muted'}>{formatDate(task.dueDate)}</td>
                        <td>
                          {task.caseNumber ? <span className="case-cell-number">{task.caseNumber}</span> : <span className="td-muted">-</span>}
                        </td>
                        <td className="td-muted task-case-title">{task.caseTitle ?? '-'}</td>
                        <td className="td-muted">{task.assigneeDisplayName ?? 'Unassigned'}</td>
                        <td className="td-muted">{formatDateTime(task.updatedAt)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="pagination">
              <span className="pagination-summary">
                {totalElements.toLocaleString()} action{totalElements !== 1 ? 's' : ''}
              </span>
              <div className="pagination-controls">
                <button className="btn btn-secondary btn-sm" onClick={() => setPage(0)} disabled={result.first} title="First page">First</button>
                <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={result.first} title="Previous page">Prev</button>
                <span className="pagination-page-info">Page {page + 1} of {Math.max(totalPages, 1)}</span>
                <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => p + 1)} disabled={result.last} title="Next page">Next</button>
                <button className="btn btn-secondary btn-sm" onClick={() => setPage(Math.max(0, totalPages - 1))} disabled={result.last} title="Last page">Last</button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function isTaskOverdue(task: Pick<TaskSummary, 'dueDate' | 'terminal'>) {
  if (!task.dueDate || task.terminal) return false;
  return task.dueDate < new Date().toISOString().slice(0, 10);
}
