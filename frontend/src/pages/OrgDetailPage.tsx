import { useState, useEffect, type ReactNode } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { OrganizationDetail, ClientSummary, CaseSummary, Page } from '../types/api';
import { StatusBadge, PriorityBadge } from '../components/StatusBadge';
import { Modal } from '../components/Modal';
import { formatDate, formatDateTime, formatPhoneNumber } from '../lib/utils';

type TabId = 'overview' | 'clients' | 'cases';

export function OrgDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [org, setOrg] = useState<OrganizationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabId>('overview');
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [submittingAction, setSubmittingAction] = useState(false);

  const [clientsPage, setClientsPage] = useState(0);
  const [clientsResult, setClientsResult] = useState<Page<ClientSummary> | null>(null);
  const [clientsLoading, setClientsLoading] = useState(false);

  const [casesPage, setCasesPage] = useState(0);
  const [casesResult, setCasesResult] = useState<Page<CaseSummary> | null>(null);
  const [casesLoading, setCasesLoading] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.organizations.get(id)
      .then((data) => { if (!cancelled) setOrg(data); })
      .catch((e: Error) => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id]);

  useEffect(() => {
    if (!id || activeTab !== 'clients') return;
    let cancelled = false;
    setClientsLoading(true);
    api.organizations.clients(id, { page: clientsPage })
      .then((data) => { if (!cancelled) setClientsResult(data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setClientsLoading(false); });
    return () => { cancelled = true; };
  }, [id, activeTab, clientsPage]);

  useEffect(() => {
    if (!id || activeTab !== 'cases') return;
    let cancelled = false;
    setCasesLoading(true);
    api.organizations.cases(id, { page: casesPage })
      .then((data) => { if (!cancelled) setCasesResult(data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setCasesLoading(false); });
    return () => { cancelled = true; };
  }, [id, activeTab, casesPage]);

  if (loading) return <div className="spinner">Loading organization...</div>;

  if (error || !org) {
    return (
      <div className="page-stack">
        <Link to="/organizations" className="back-link">Back to Agencies</Link>
        <div className="form-error">{error ?? 'Organization not found.'}</div>
      </div>
    );
  }

  return (
    <div className="page-stack">
      <Link to="/organizations" className="back-link">Back to Agencies</Link>

      <section className="case-hero record-highlights">
        <div className="object-icon record-icon">O</div>
        <div className="case-hero-main">
          <div className="case-number">Agency {org.organizationCode}</div>
          <h1 className="case-title-large">{org.name}</h1>
          <div className="case-badges">
            <span className={`badge ${org.active ? 'badge-status-ASSIGNED' : 'badge-neutral'}`}>
              {org.active ? 'Active' : 'Inactive'}
            </span>
          </div>
        </div>
        <div className="case-hero-meta highlights-fields">
          <Metric label="Recipients" value={org.clientCount.toLocaleString()} />
          <Metric label="Total Cases" value={org.caseCount.toLocaleString()} />
          <Metric label="Open" value={org.openCaseCount.toLocaleString()} />
          <Metric label="Escalated" value={org.escalatedCases.toLocaleString()} />
          <Metric label="Overdue" value={org.overdueCases.toLocaleString()} />
        </div>
      </section>

      <div className="action-bar quick-actions">
        <button
          className="btn btn-secondary"
          onClick={() => setConfirmDeactivate(true)}
          disabled={!org.active}
        >
          Deactivate Organization
        </button>
      </div>

      {actionError && <div className="form-error">{actionError}</div>}

      <section className="card detail-card">
        <div className="tabs" role="tablist" aria-label="Organization sections">
          <button
            className={`tab-btn ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
            role="tab"
            aria-selected={activeTab === 'overview'}
          >
            Overview
          </button>
          <button
            className={`tab-btn ${activeTab === 'clients' ? 'active' : ''}`}
            onClick={() => setActiveTab('clients')}
            role="tab"
            aria-selected={activeTab === 'clients'}
          >
            Recipients
            {org.clientCount > 0 && <span className="tab-count">{org.clientCount.toLocaleString()}</span>}
          </button>
          <button
            className={`tab-btn ${activeTab === 'cases' ? 'active' : ''}`}
            onClick={() => setActiveTab('cases')}
            role="tab"
            aria-selected={activeTab === 'cases'}
          >
            Cases
            {org.caseCount > 0 && <span className="tab-count">{org.caseCount.toLocaleString()}</span>}
          </button>
        </div>

        <div className="tab-content">
          {activeTab === 'overview' && <OverviewTab org={org} />}
          {activeTab === 'clients' && (
            <ClientsTab
              result={clientsResult}
              loading={clientsLoading}
              page={clientsPage}
              setPage={setClientsPage}
              onClientClick={(clientId) => navigate(`/clients/${clientId}`)}
            />
          )}
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
          title="Deactivate Organization"
          message="This organization will be removed from active organization lists and lookup workflows. Deactivation is blocked while active clients or open cases remain linked."
          confirmLabel="Deactivate Organization"
          submitting={submittingAction}
          onClose={() => setConfirmDeactivate(false)}
          onConfirm={async () => {
            if (!id) return;
            setSubmittingAction(true);
            setActionError(null);
            try {
              const updated = await api.organizations.deactivate(id);
              setOrg(updated);
              setConfirmDeactivate(false);
            } catch (err) {
              setActionError(err instanceof Error ? err.message : 'Failed to deactivate organization.');
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

function OverviewTab({ org }: { org: OrganizationDetail }) {
  return (
    <div className="detail-grid">
      <DetailField label="Org code" value={org.organizationCode} />
      <DetailField label="Name" value={org.name} />
      <DetailField label="Phone" value={formatPhoneNumber(org.phone)} />
      <DetailField label="Email" value={org.email ?? '-'} />
      <DetailField label="Notes" value={org.notes ?? '-'} />
      <DetailField label="Status" value={
        <span className={`badge ${org.active ? 'badge-status-ASSIGNED' : 'badge-neutral'}`}>
          {org.active ? 'Active' : 'Inactive'}
        </span>
      } />
      <DetailField label="Created" value={formatDateTime(org.createdAt)} />
      <DetailField label="Last updated" value={formatDateTime(org.updatedAt)} />
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

function ClientsTab({
  result, loading, page, setPage, onClientClick,
}: {
  result: Page<ClientSummary> | null;
  loading: boolean;
  page: number;
  setPage: (p: number) => void;
  onClientClick: (id: string) => void;
}) {
  if (loading) return <div className="spinner">Loading clients...</div>;

  const rows = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const totalElements = result?.totalElements ?? 0;

  return (
    <>
      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>Client #</th>
              <th>Name</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Status</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={6}>
                  <div className="empty-state-panel">
                    <div className="empty-state-title">No clients</div>
                    <div className="empty-state-body">This organization has no clients on record.</div>
                  </div>
                </td>
              </tr>
            )}
            {rows.map((c) => (
              <tr key={c.id} className="clickable" onClick={() => onClientClick(c.id)}>
                <td className="td-mono">{c.clientNumber}</td>
                <td><strong>{c.displayName}</strong></td>
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
      {totalPages > 1 && (
        <div className="pagination">
          <span className="pagination-summary">{totalElements.toLocaleString()} client{totalElements !== 1 ? 's' : ''}</span>
          <div className="pagination-controls">
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(0)} disabled={result?.first} title="First page">«</button>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(Math.max(0, page - 1))} disabled={result?.first} title="Previous page">‹</button>
            <span className="pagination-page-info">Page {page + 1} of {totalPages}</span>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(page + 1)} disabled={result?.last} title="Next page">›</button>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(Math.max(0, totalPages - 1))} disabled={result?.last} title="Last page">»</button>
          </div>
        </div>
      )}
    </>
  );
}

function CasesTab({
  result, loading, page, setPage, onCaseClick,
}: {
  result: Page<CaseSummary> | null;
  loading: boolean;
  page: number;
  setPage: (p: number) => void;
  onCaseClick: (id: string) => void;
}) {
  if (loading) return <div className="spinner">Loading cases...</div>;

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
                    <div className="empty-state-title">No cases</div>
                    <div className="empty-state-body">This organization has no cases on record.</div>
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
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(0)} disabled={result?.first} title="First page">«</button>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(Math.max(0, page - 1))} disabled={result?.first} title="Previous page">‹</button>
            <span className="pagination-page-info">Page {page + 1} of {totalPages}</span>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(page + 1)} disabled={result?.last} title="Next page">›</button>
            <button className="btn btn-secondary btn-sm" onClick={() => setPage(Math.max(0, totalPages - 1))} disabled={result?.last} title="Last page">»</button>
          </div>
        </div>
      )}
    </>
  );
}
