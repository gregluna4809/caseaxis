import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api, ApiError } from '../lib/apiClient';
import { PRIORITIES, CASE_TYPES } from '../lib/lookups';
import type { OrganizationSummary, ClientSummary } from '../types/api';

export function CreateCasePage() {
  const navigate = useNavigate();

  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([]);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [lookupLoading, setLookupLoading] = useState(true);
  const [lookupError, setLookupError] = useState<string | null>(null);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priorityCode, setPriorityCode] = useState('MEDIUM');
  const [typeCode, setTypeCode] = useState('GENERAL');
  const [organizationId, setOrganizationId] = useState('');
  const [clientId, setClientId] = useState('');
  const [dueDate, setDueDate] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const subjectMissing = !organizationId && !clientId;

  useEffect(() => {
    async function loadLookups() {
      try {
        const [orgsPage, clsPage] = await Promise.all([
          api.organizations.list({ size: 500, active: true }),
          api.clients.list({ size: 500, active: true }),
        ]);
        setOrganizations(orgsPage.content);
        setClients(clsPage.content);
      } catch {
        setLookupError('Could not load organizations or clients. Check your connection and try again.');
      } finally {
        setLookupLoading(false);
      }
    }
    void loadLookups();
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (subjectMissing) return;

    setError(null);
    setFieldErrors({});
    setSubmitting(true);

    try {
      const created = await api.cases.create({
        title,
        description: description || undefined,
        priorityCode,
        typeCode,
        organizationId: organizationId || undefined,
        clientId: clientId || undefined,
        dueDate: dueDate || undefined,
      });
      navigate(`/cases/${created.id}`);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors);
      setError(err instanceof Error ? err.message : 'Failed to create case.');
      setSubmitting(false);
    }
  }

  return (
    <div className="page-stack">
      <Link to="/cases" className="back-link">Back to Cases</Link>

      <div className="page-header">
        <p className="page-kicker">Case intake</p>
        <h1 className="page-title">New Case</h1>
        <p className="page-subtitle">Capture the minimum operational record needed to start workflow.</p>
      </div>

      <div className="card form-card">
        <div className="card-header">
          <div>
            <span className="card-title">Case details</span>
            <p className="card-subtitle">Required fields are marked with an asterisk.</p>
          </div>
        </div>
        <div className="card-body">
          <form className="create-form" onSubmit={handleSubmit}>
            {error && <div className="form-error">{error}</div>}

            <div className="form-group">
              <label className="form-label" htmlFor="cf-title">Title *</label>
              <input
                id="cf-title"
                className={`form-input${fieldErrors.title ? ' form-input-error' : ''}`}
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                maxLength={500}
                placeholder="Brief title for the case"
              />
              {fieldErrors.title && <span className="field-error">{fieldErrors.title}</span>}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="cf-description">Description</label>
              <textarea
                id="cf-description"
                className="form-textarea"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={5}
                maxLength={5000}
                placeholder="Detailed description"
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label" htmlFor="cf-priority">Priority</label>
                <select id="cf-priority" className="form-select" value={priorityCode} onChange={(e) => setPriorityCode(e.target.value)}>
                  {PRIORITIES.map((p) => <option key={p.code} value={p.code}>{p.label}</option>)}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="cf-type">Type</label>
                <select id="cf-type" className="form-select" value={typeCode} onChange={(e) => setTypeCode(e.target.value)}>
                  {CASE_TYPES.map((t) => <option key={t.code} value={t.code}>{t.label}</option>)}
                </select>
              </div>
            </div>

            {lookupError ? (
              <div className="form-error">{lookupError}</div>
            ) : (
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label" htmlFor="cf-org">Organization {!clientId && <span className="required-star">*</span>}</label>
                  <select id="cf-org" className="form-select" value={organizationId} onChange={(e) => setOrganizationId(e.target.value)} disabled={lookupLoading}>
                    <option value="">None</option>
                    {organizations.map((o) => <option key={o.id} value={o.id}>{o.name} ({o.organizationCode})</option>)}
                  </select>
                  {!lookupLoading && organizations.length === 0 && <span className="form-hint">No organizations in system yet.</span>}
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="cf-client">Client {!organizationId && <span className="required-star">*</span>}</label>
                  <select id="cf-client" className="form-select" value={clientId} onChange={(e) => setClientId(e.target.value)} disabled={lookupLoading}>
                    <option value="">None</option>
                    {clients.map((c) => <option key={c.id} value={c.id}>{c.displayName} ({c.clientNumber})</option>)}
                  </select>
                  {!lookupLoading && clients.length === 0 && <span className="form-hint">No clients in system yet.</span>}
                </div>
              </div>
            )}

            {subjectMissing && !lookupLoading && !lookupError && (
              <div className="field-hint-warn">At least one of Organization or Client must be selected.</div>
            )}

            <div className="form-group due-field">
              <label className="form-label" htmlFor="cf-due">Due Date</label>
              <input id="cf-due" className="form-input" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </div>

            <div className="form-actions">
              <Link to="/cases" className="btn btn-secondary">Cancel</Link>
              <button type="submit" className="btn btn-primary" disabled={submitting || lookupLoading || subjectMissing}>
                {submitting ? 'Creating...' : lookupLoading ? 'Loading...' : 'Create Case'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
