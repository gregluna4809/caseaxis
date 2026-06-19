import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { ClientSummary, Page } from '../types/api';
import { formatDate, formatPhoneNumber } from '../lib/utils';

const PAGE_SIZE = 20;

export function ClientListPage() {
  const navigate = useNavigate();

  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [draftQ, setDraftQ] = useState('');
  const [activeFilter, setActiveFilter] = useState<boolean | undefined>(undefined);
  const [result, setResult] = useState<Page<ClientSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.clients.list({ page, size: PAGE_SIZE, q: q || undefined, active: activeFilter })
      .then((data) => { if (!cancelled) setResult(data); })
      .catch((e: Error) => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [page, q, activeFilter]);

  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    setPage(0);
    setQ(draftQ);
  }

  function setActiveAndReset(val: boolean | undefined) {
    setActiveFilter(val);
    setPage(0);
  }

  const rows = result?.content ?? [];
  const totalElements = result?.totalElements ?? 0;
  const totalPages = result?.totalPages ?? 0;

  return (
    <div className="page-stack">
      <section className="object-header">
        <div className="object-header-main">
          <span className="object-icon">P</span>
          <div>
            <p className="page-kicker">Benefit Recipients</p>
            <h1 className="page-title">Recipient Directory</h1>
            <p className="page-subtitle">
              {loading ? 'Loading...' : `${totalElements.toLocaleString()} recipients matching current filters`}
            </p>
          </div>
        </div>
      </section>

      <div className="list-view-card">
        <div className="list-view-toolbar">
          <div>
            <span className="list-view-title">Recipient List View</span>
            <span className="list-view-subtitle">Search by name, recipient number, or email</span>
          </div>
          <div className="toolbar-controls">
            <form onSubmit={handleSearch} style={{ display: 'contents' }}>
              <div className="search-control">
                <span className="search-icon">Search</span>
                <input
                  value={draftQ}
                  onChange={(e) => setDraftQ(e.target.value)}
                  placeholder="Name or recipient number"
                  aria-label="Search recipients"
                />
              </div>
              <button type="submit" className="btn btn-secondary btn-sm">Search</button>
            </form>
            <select
              className="form-select compact-select"
              value={activeFilter === undefined ? 'all' : String(activeFilter)}
              onChange={(e) => {
                const v = e.target.value;
                setActiveAndReset(v === 'all' ? undefined : v === 'true');
              }}
              aria-label="Filter by status"
            >
              <option value="all">All statuses</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </div>
        </div>

        {error && <div className="form-error surface-error">{error}</div>}
        {loading && <div className="spinner">Loading recipients...</div>}

        {!loading && !error && result && (
          <>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Recipient #</th>
                    <th>Name</th>
                    <th>Agency</th>
                    <th>Email</th>
                    <th>Phone</th>
                    <th>Status</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 && (
                    <tr>
                      <td colSpan={7}>
                        <div className="empty-state-panel">
                          <div className="empty-state-title">No recipients found</div>
                          <div className="empty-state-body">
                            No benefit recipients match the current search or filter criteria. Try adjusting your search terms or clearing a filter.
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                  {rows.map((c) => (
                    <tr
                      key={c.id}
                      className="clickable"
                      title="Open recipient record"
                      onClick={() => navigate(`/clients/${c.id}`)}
                    >
                      <td className="td-mono">{c.clientNumber}</td>
                      <td><strong>{c.displayName}</strong></td>
                      <td className="td-muted">{c.organizationName ?? <span className="td-null">—</span>}</td>
                      <td className="td-muted">{c.email ?? <span className="td-null">—</span>}</td>
                      <td className="td-muted">{c.phone ? formatPhoneNumber(c.phone) : <span className="td-null">—</span>}</td>
                      <td>
                        <span className={`badge ${c.active ? 'badge-status-ASSIGNED' : 'badge-neutral'}`}>
                          {c.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="td-muted">{formatDate(c.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="pagination">
              <span className="pagination-summary">
                {totalElements.toLocaleString()} record{totalElements !== 1 ? 's' : ''}
              </span>
              <div className="pagination-controls">
                <button className="btn btn-secondary btn-sm" onClick={() => setPage(0)} disabled={result.first} title="First page">«</button>
                <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={result.first} title="Previous page">‹</button>
                <span className="pagination-page-info">Page {page + 1} of {Math.max(totalPages, 1)}</span>
                <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => p + 1)} disabled={result.last} title="Next page">›</button>
                <button className="btn btn-secondary btn-sm" onClick={() => setPage(Math.max(0, totalPages - 1))} disabled={result.last} title="Last page">»</button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
