import { useState, useEffect, type ReactNode } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { ClientDetail, CaseSummary, Page } from '../types/api';
import { StatusBadge, PriorityBadge } from '../components/StatusBadge';
import { Modal } from '../components/Modal';
import { formatDate, formatDateTime, formatPhoneNumber } from '../lib/utils';

type TabId = 'overview' | 'cases';

export function ClientDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [client, setClient] = useState<ClientDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabId>('overview');
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [submittingAction, setSubmittingAction] = useState(false);

  const [casesPage, setCasesPage] = useState(0);
  const [casesResult, setCasesResult] = useState<Page<CaseSummary> | null>(null);
  const [casesLoading, setCasesLoading] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.clients.get(id)
      .then((data) => { if (!cancelled) setClient(data); })
      .catch((e: Error) => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id]);

  useEffect(() => {
    if (!id || activeTab !== 'cases') return;
    let cancelled = false;
    setCasesLoading(true);
    api.clients.cases(id, { page: casesPage })
      .then((data) => { if (!cancelled) setCasesResult(data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setCasesLoading(false); });
    return () => { cancelled = true; };
  }, [id, activeTab, casesPage]);

  if (loading) return <div className="spinner">Loading recipient...</div>;

  if (error || !client) {
    return (
      <div className="page-stack">
        <Link to="/clients" className="back-link">Back to Recipients</Link>
        <div className="form-error">{error ?? 'Recipient not found.'}</div>
      </div>
    );
  }

  return (
    <div className="page-stack">
      <Link to="/clients" className="back-link">Back to Recipients</Link>

      <section className="case-hero record-highlights">
        <div className="object-icon record-icon">P</div>
        <div className="case-hero-main">
          <div className="case-number">Recipient {client.clientNumber}</div>
          <h1 className="case-title-large">{client.displayName}</h1>
          <div className="case-badges">
            <span className={`badge ${client.active ? 'badge-status-ASSIGNED' : 'badge-neutral'}`}>
              {client.active ? 'Active' : 'Inactive'}
            </span>
            {client.organizationName && (
              <Link
                to={`/organizations/${client.organizationId}`}
                className="badge badge-neutral"
                onClick={(e) => e.stopPropagation()}
              >
                {client.organizationCode} · {client.organizationName}
              </Link>
            )}
          </div>
        </div>
        <div className="case-hero-meta highlights-fields">
          <Metric label="Total Reviews" value={String(client.totalCases)} />
          <Metric label="Active" value={String(client.openCases)} />
          <Metric label="Appeals" value={String(client.escalatedCases)} />
          <Metric label="Overdue" value={String(client.overdueCases)} />
        </div>
      </section>

      <div className="action-bar quick-actions">
        <button
          className="btn btn-secondary"
          onClick={() => setConfirmDeactivate(true)}
          disabled={!client.active}
        >
          Deactivate Recipient
        </button>
      </div>

      {actionError && <div className="form-error">{actionError}</div>}

      <section className="card detail-card">
        <div className="tabs" role="tablist" aria-label="Client sections">
          <button
            className={`tab-btn ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
            role="tab"
            aria-selected={activeTab === 'overview'}
          >
            Overview
          </button>
          <button
            className={`tab-btn ${activeTab === 'cases' ? 'active' : ''}`}
            onClick={() => setActiveTab('cases')}
            role="tab"
            aria-selected={activeTab === 'cases'}
          >
            Benefit Cases
            {client.totalCases > 0 && <span className="tab-count">{client.totalCases}</span>}
          </button>
        </div>

        <div className="tab-content">
          {activeTab === 'overview' && <OverviewTab client={client} />}
          {activeTab === 'cases' && (
            <CasesTab
              result={casesResult}
              loading={casesLoading}
              page={casesPage}
              setPage={setCasesPage}
              onCaseClick={(caseId) => navigate(`/cases/${caseId}`)}
            />
          )}
        </div>
      </section>

      {confirmDeactivate && (
        <ConfirmActionModal
          title="Deactivate Recipient"
          message="This recipient will be removed from active recipient lists and lookup workflows. Existing cases and history will remain linked to the recipient record."
          confirmLabel="Deactivate Recipient"
          submitting={submittingAction}
          onClose={() => setConfirmDeactivate(false)}
          onConfirm={async () => {
            if (!id) return;
            setSubmittingAction(true);
            setActionError(null);
            try {
              const updated = await api.clients.deactivate(id);
              setClient(updated);
              setConfirmDeactivate(false);
            } catch (err) {
              setActionError(err instanceof Error ? err.message : 'Failed to deactivate client.');
            } finally {
              setSubmittingAction(false);
            }
          }}
        />
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function OverviewTab({ client }: { client: ClientDetail }) {
  return (
    <div className="detail-grid">
      <DetailField label="Recipient number" value={client.clientNumber} />
      <DetailField label="First name" value={client.firstName} />
      <DetailField label="Last name" value={client.lastName} />
      <DetailField label="Email" value={client.email ?? '-'} />
      <DetailField label="Phone" value={formatPhoneNumber(client.phone)} />
      <DetailField
        label="Agency"
        value={
          client.organizationName && client.organizationId ? (
            <Link to={`/organizations/${client.organizationId}`}>
              {client.organizationName} ({client.organizationCode})
            </Link>
          ) : '-'
        }
      />
      <DetailField label="Status" value={
        <span className={`badge ${client.active ? 'badge-status-ASSIGNED' : 'badge-neutral'}`}>
          {client.active ? 'Active' : 'Inactive'}
        </span>
      } />
      <DetailField label="Created" value={formatDateTime(client.createdAt)} />
      <DetailField label="Last updated" value={formatDateTime(client.updatedAt)} />
    </div>
  );
}

function DetailField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="detail-field">
      <span className="detail-label">{label}</span>
      <span className="detail-value">{value}</span>
    </div>
  );
}

function ConfirmActionModal({
  title,
  message,
  confirmLabel,
  submitting,
  onClose,
  onConfirm,
}: {
  title: string;
  message: string;
  confirmLabel: string;
  submitting: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>Cancel</button>
          <button type="button" className="btn btn-primary" onClick={onConfirm} disabled={submitting}>
            {submitting ? 'Saving...' : confirmLabel}
          </button>
        </>
      )}
    >
      <div className="field-hint-warn">{message}</div>
    </Modal>
  );
}

function CasesTab({
  result, loading, page, setPage, onCaseClick,
}: {
  result: Page<CaseSummary> | null;
  loading: boolean;
  page: number;
  setPage: (fn: (p: number) => number) => void;
  onCaseClick: (id: string) => void;
}) {
  if (loading) return <div className="spinner">Loading benefit cases...</div>;

  const rows = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const totalElements = result?.totalElements ?? 0;

  return (
    <>
      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>Case</th>
              <th>Status</th>
              <th>Priority</th>
              <th>Type</th>
              <th>Due</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={6}>
                  <div className="empty-state-panel">
                    <div className="empty-state-title">No benefit cases</div>
                    <div className="empty-state-body">This recipient has no benefit cases on record.</div>
                  </div>
                </td>
              </tr>
            )}
            {rows.map((c) => (
              <tr key={c.id} className="clickable" onClick={() => onCaseClick(c.id)}>
                <td>
                  <div className="case-cell">
                    <span className="case-cell-number">{c.caseNumber}</span>
                    <span className="case-cell-title">{c.title}</span>
                  </div>
                </td>
                <td><StatusBadge code={c.statusCode} label={c.statusDisplayName} /></td>
                <td><PriorityBadge code={c.priorityCode} label={c.priorityDisplayName} /></td>
                <td className="td-muted">{c.typeDisplayName}</td>
                <td className="td-muted">{formatDate(c.dueDate)}</td>
                <td className="td-muted">{formatDate(c.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalPages > 1 && (
        <div className="pagination">
          <span className="pagination-summary">{totalElements.toLocaleString()} case{totalElements !== 1 ? 's' : ''}</span>
          <div className="pagination-controls">
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(() => 0)} disabled={result?.first} title="First page">«</button>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={result?.first} title="Previous page">‹</button>
            <span className="pagination-page-info">Page {page + 1} of {totalPages}</span>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => p + 1)} disabled={result?.last} title="Next page">›</button>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(() => Math.max(0, totalPages - 1))} disabled={result?.last} title="Last page">»</button>
          </div>
        </div>
      )}
    </>
  );
}
