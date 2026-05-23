import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { CaseSummary, Page } from '../types/api';
import { StatusBadge, PriorityBadge } from '../components/StatusBadge';
import { formatDate } from '../lib/utils';

export function CaseListPage() {
  const navigate = useNavigate();

  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<CaseSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.cases.list(page, 20);
        if (!cancelled) setResult(data);
      } catch (err) {
        if (!cancelled)
          setError(err instanceof Error ? err.message : 'Failed to load cases.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void load();
    return () => { cancelled = true; };
  }, [page]);

  const totalPages = result?.totalPages ?? 0;
  const totalElements = result?.totalElements ?? 0;

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Cases</h1>
        <p className="page-subtitle">
          {loading ? 'Loading…' : `${totalElements.toLocaleString()} total cases`}
        </p>
      </div>

      <div className="card">
        {error && (
          <div className="form-error" style={{ margin: '16px', borderRadius: '6px' }}>
            {error}
          </div>
        )}

        {loading && <div className="spinner">Loading cases…</div>}

        {!loading && !error && result && (
          <>
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Case #</th>
                    <th>Title</th>
                    <th>Status</th>
                    <th>Priority</th>
                    <th>Type</th>
                    <th>Due Date</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {result.content.length === 0 && (
                    <tr>
                      <td colSpan={7} className="empty-state">
                        No cases found.
                      </td>
                    </tr>
                  )}
                  {result.content.map((c) => (
                    <tr
                      key={c.id}
                      className="clickable"
                      onClick={() => navigate(`/cases/${c.id}`)}
                    >
                      <td className="td-mono">{c.caseNumber}</td>
                      <td>
                        <span className="table-link">{c.title}</span>
                      </td>
                      <td>
                        <StatusBadge
                          code={c.statusCode}
                          label={c.statusDisplayName}
                        />
                      </td>
                      <td>
                        <PriorityBadge
                          code={c.priorityCode}
                          label={c.priorityDisplayName}
                        />
                      </td>
                      <td className="td-muted">{c.typeDisplayName}</td>
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
                  Page {page + 1} of {totalPages} &mdash; {totalElements.toLocaleString()} cases
                </span>
                <div className="pagination-controls">
                  <button
                    className="btn btn-secondary"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={result.first}
                  >
                    ← Prev
                  </button>
                  <button
                    className="btn btn-secondary"
                    onClick={() => setPage((p) => p + 1)}
                    disabled={result.last}
                  >
                    Next →
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
