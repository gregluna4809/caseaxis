import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../lib/apiClient';
import { PRIORITIES, CASE_TYPES } from '../lib/lookups';

export function CreateCasePage() {
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priorityCode, setPriorityCode] = useState('MEDIUM');
  const [typeCode, setTypeCode] = useState('GENERAL');
  const [organizationId, setOrganizationId] = useState('');
  const [clientId, setClientId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
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
      setError(err instanceof Error ? err.message : 'Failed to create case.');
      setSubmitting(false);
    }
  }

  return (
    <div>
      <Link to="/cases" className="back-link">← Back to Cases</Link>

      <div className="page-header">
        <h1 className="page-title">New Case</h1>
        <p className="page-subtitle">Create a new case record.</p>
      </div>

      <div className="card" style={{ maxWidth: 700 }}>
        <div className="card-body">
          <form className="create-form" onSubmit={handleSubmit}>
            {error && <div className="form-error">{error}</div>}

            <div className="form-group">
              <label className="form-label" htmlFor="cf-title">Title *</label>
              <input
                id="cf-title"
                className="form-input"
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                maxLength={500}
                placeholder="Brief title for the case"
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="cf-description">Description</label>
              <textarea
                id="cf-description"
                className="form-textarea"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={4}
                maxLength={5000}
                placeholder="Detailed description (optional)"
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label" htmlFor="cf-priority">Priority</label>
                <select
                  id="cf-priority"
                  className="form-select"
                  value={priorityCode}
                  onChange={(e) => setPriorityCode(e.target.value)}
                >
                  {PRIORITIES.map((p) => (
                    <option key={p.code} value={p.code}>{p.label}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="cf-type">Type</label>
                <select
                  id="cf-type"
                  className="form-select"
                  value={typeCode}
                  onChange={(e) => setTypeCode(e.target.value)}
                >
                  {CASE_TYPES.map((t) => (
                    <option key={t.code} value={t.code}>{t.label}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label" htmlFor="cf-org">Organization ID</label>
                <input
                  id="cf-org"
                  className="form-input"
                  type="text"
                  value={organizationId}
                  onChange={(e) => setOrganizationId(e.target.value)}
                  placeholder="UUID (optional)"
                />
                <span className="form-hint">e.g. 018e1234-0000-7000-8000-000000000001</span>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="cf-client">Client ID</label>
                <input
                  id="cf-client"
                  className="form-input"
                  type="text"
                  value={clientId}
                  onChange={(e) => setClientId(e.target.value)}
                  placeholder="UUID (optional)"
                />
                <span className="form-hint">e.g. 018e1234-0000-7000-8000-000000000002</span>
              </div>
            </div>

            <div className="form-group" style={{ maxWidth: 220 }}>
              <label className="form-label" htmlFor="cf-due">Due Date</label>
              <input
                id="cf-due"
                className="form-input"
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
              />
            </div>

            <div className="form-actions">
              <Link to="/cases" className="btn btn-secondary">Cancel</Link>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Creating…' : 'Create Case'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
