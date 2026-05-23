import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { CaseDetail, CaseNote, CaseTask, CaseAttachment } from '../types/api';
import { StatusBadge, PriorityBadge, TaskStatusBadge } from '../components/StatusBadge';
import { Modal } from '../components/Modal';
import { ALLOWED_TRANSITIONS, STATUS_LABEL, TASK_STATUSES } from '../lib/lookups';
import { formatDate, formatDateTime, formatBytes, truncate } from '../lib/utils';

type TabId = 'overview' | 'notes' | 'tasks' | 'attachments';
type ModalId = 'note' | 'task' | 'status' | null;

const TABS: { id: TabId; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'notes', label: 'Notes' },
  { id: 'tasks', label: 'Tasks' },
  { id: 'attachments', label: 'Attachments' },
];

export function CaseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [activeTab, setActiveTab] = useState<TabId>('overview');
  const [activeModal, setActiveModal] = useState<ModalId>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const [caseDetail, setCaseDetail] = useState<CaseDetail | null>(null);
  const [notes, setNotes] = useState<CaseNote[]>([]);
  const [tasks, setTasks] = useState<CaseTask[]>([]);
  const [attachments, setAttachments] = useState<CaseAttachment[]>([]);

  const [loadingCase, setLoadingCase] = useState(true);
  const [loadingNotes, setLoadingNotes] = useState(true);
  const [loadingTasks, setLoadingTasks] = useState(true);
  const [loadingAttachments, setLoadingAttachments] = useState(true);

  const [caseError, setCaseError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    async function loadAll() {
      try {
        const detail = await api.cases.get(id!);
        if (!cancelled) setCaseDetail(detail);
      } catch (err) {
        if (!cancelled)
          setCaseError(err instanceof Error ? err.message : 'Failed to load case.');
      } finally {
        if (!cancelled) setLoadingCase(false);
      }

      try {
        const n = await api.notes.list(id!);
        if (!cancelled) setNotes(n);
      } finally {
        if (!cancelled) setLoadingNotes(false);
      }

      try {
        const t = await api.tasks.list(id!);
        if (!cancelled) setTasks(t);
      } finally {
        if (!cancelled) setLoadingTasks(false);
      }

      try {
        const a = await api.attachments.list(id!);
        if (!cancelled) setAttachments(a);
      } finally {
        if (!cancelled) setLoadingAttachments(false);
      }
    }

    void loadAll();
    return () => { cancelled = true; };
  }, [id, refreshKey]);

  function handleActionDone(tab?: TabId) {
    setActiveModal(null);
    setRefreshKey((k) => k + 1);
    if (tab) setActiveTab(tab);
  }

  if (loadingCase) {
    return <div className="spinner">Loading case…</div>;
  }

  if (caseError || !caseDetail) {
    return (
      <div>
        <Link to="/cases" className="back-link">← Back to Cases</Link>
        <div className="form-error">{caseError ?? 'Case not found.'}</div>
      </div>
    );
  }

  return (
    <div>
      <Link to="/cases" className="back-link">← Back to Cases</Link>

      {/* Case header */}
      <div className="case-detail-header">
        <div className="case-number">{caseDetail.caseNumber}</div>
        <div className="case-title-large">{caseDetail.title}</div>
        <div className="case-badges">
          <StatusBadge code={caseDetail.statusCode} label={caseDetail.statusDisplayName} />
          <PriorityBadge code={caseDetail.priorityCode} label={caseDetail.priorityDisplayName} />
          <span className="badge" style={{ background: '#f1f5f9', color: '#475569' }}>
            {caseDetail.typeDisplayName}
          </span>
          {caseDetail.reopenedCount > 0 && (
            <span className="badge" style={{ background: '#fef3c7', color: '#b45309' }}>
              Reopened ×{caseDetail.reopenedCount}
            </span>
          )}
        </div>
      </div>

      {/* Action bar */}
      <div className="action-bar">
        <button className="btn btn-secondary" onClick={() => setActiveModal('note')}>
          + Add Note
        </button>
        <button className="btn btn-secondary" onClick={() => setActiveModal('task')}>
          + Add Task
        </button>
        <button className="btn btn-primary" onClick={() => setActiveModal('status')}>
          Transition Status
        </button>
      </div>

      {/* Tab bar */}
      <div className="card">
        <div className="tabs">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
              {tab.id === 'notes' && !loadingNotes && (
                <span className="text-muted" style={{ marginLeft: 4 }}>({notes.length})</span>
              )}
              {tab.id === 'tasks' && !loadingTasks && (
                <span className="text-muted" style={{ marginLeft: 4 }}>({tasks.length})</span>
              )}
              {tab.id === 'attachments' && !loadingAttachments && (
                <span className="text-muted" style={{ marginLeft: 4 }}>({attachments.length})</span>
              )}
            </button>
          ))}
        </div>

        <div className="tab-content">
          {activeTab === 'overview' && (
            <OverviewTab c={caseDetail} />
          )}
          {activeTab === 'notes' && (
            <NotesTab notes={notes} loading={loadingNotes} />
          )}
          {activeTab === 'tasks' && (
            <TasksTab tasks={tasks} loading={loadingTasks} />
          )}
          {activeTab === 'attachments' && (
            <AttachmentsTab attachments={attachments} loading={loadingAttachments} />
          )}
        </div>
      </div>

      {/* Modals */}
      {activeModal === 'note' && (
        <AddNoteModal
          caseId={caseDetail.id}
          onClose={() => setActiveModal(null)}
          onDone={() => handleActionDone('notes')}
        />
      )}
      {activeModal === 'task' && (
        <AddTaskModal
          caseId={caseDetail.id}
          onClose={() => setActiveModal(null)}
          onDone={() => handleActionDone('tasks')}
        />
      )}
      {activeModal === 'status' && (
        <TransitionStatusModal
          caseId={caseDetail.id}
          currentStatus={caseDetail.statusCode}
          onClose={() => setActiveModal(null)}
          onDone={() => handleActionDone()}
        />
      )}
    </div>
  );
}

/* ── Sub-tab components ──────────────────────────── */

function OverviewTab({ c }: { c: CaseDetail }) {
  return (
    <div>
      {c.description && (
        <div style={{ marginBottom: 24 }}>
          <div className="detail-label" style={{ marginBottom: 6 }}>Description</div>
          <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--color-text)', whiteSpace: 'pre-wrap' }}>
            {c.description}
          </p>
        </div>
      )}

      <div className="detail-grid">
        <div className="detail-field">
          <span className="detail-label">Case Number</span>
          <span className="detail-value mono">{c.caseNumber}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Status</span>
          <span className="detail-value">
            <StatusBadge code={c.statusCode} label={c.statusDisplayName} />
          </span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Priority</span>
          <span className="detail-value">
            <PriorityBadge code={c.priorityCode} label={c.priorityDisplayName} />
          </span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Type</span>
          <span className="detail-value">{c.typeDisplayName}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Organization</span>
          <span className="detail-value">
            {c.organizationName && c.organizationCode
              ? `${c.organizationName} (${c.organizationCode})`
              : 'â€”'}
          </span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Client</span>
          <span className="detail-value">
            {c.clientDisplayName && c.clientNumber
              ? `${c.clientDisplayName} (${c.clientNumber})`
              : 'â€”'}
          </span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Assigned To</span>
          <span className="detail-value mono">{truncate(c.assignedToId, 16)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Assigned At</span>
          <span className="detail-value">{formatDateTime(c.assignedAt)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Due Date</span>
          <span className="detail-value">{formatDate(c.dueDate)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Reopened Count</span>
          <span className="detail-value">{c.reopenedCount}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Resolved At</span>
          <span className="detail-value">{formatDateTime(c.resolvedAt)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Closed At</span>
          <span className="detail-value">{formatDateTime(c.closedAt)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Created By</span>
          <span className="detail-value mono">{truncate(c.createdBy, 16)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Created At</span>
          <span className="detail-value">{formatDateTime(c.createdAt)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Last Updated</span>
          <span className="detail-value">{formatDateTime(c.updatedAt)}</span>
        </div>
      </div>
    </div>
  );
}

function NotesTab({ notes, loading }: { notes: CaseNote[]; loading: boolean }) {
  if (loading) return <div className="spinner">Loading notes…</div>;
  if (notes.length === 0) return <div className="empty-state">No notes on this case yet.</div>;

  return (
    <div className="note-list">
      {notes.map((note) => (
        <div key={note.id} className="note-card">
          <div className="note-meta">
            <span>{formatDateTime(note.createdAt)}</span>
            <span className="td-mono">·</span>
            <span className="td-mono">{truncate(note.createdBy, 16)}</span>
            {note.internal && (
              <span className="badge badge-internal">Internal</span>
            )}
            {note.supersedesNoteId && (
              <span className="badge" style={{ background: '#fef3c7', color: '#b45309' }}>
                Correction
              </span>
            )}
          </div>
          <div className="note-body">{note.body}</div>
        </div>
      ))}
    </div>
  );
}

function TasksTab({ tasks, loading }: { tasks: CaseTask[]; loading: boolean }) {
  if (loading) return <div className="spinner">Loading tasks…</div>;
  if (tasks.length === 0) return <div className="empty-state">No tasks on this case yet.</div>;

  return (
    <div className="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>Title</th>
            <th>Status</th>
            <th>Assigned To</th>
            <th>Due Date</th>
            <th>Completed</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id}>
              <td>
                <div>{task.title}</div>
                {task.description && (
                  <div className="td-muted" style={{ marginTop: 2 }}>{task.description}</div>
                )}
              </td>
              <td>
                <TaskStatusBadge code={task.statusCode} label={task.statusDisplayName} />
              </td>
              <td className="td-mono">{truncate(task.assignedToId, 16)}</td>
              <td className="td-muted">{formatDate(task.dueDate)}</td>
              <td className="td-muted">{formatDateTime(task.completedAt)}</td>
              <td className="td-muted">{formatDate(task.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AttachmentsTab({ attachments, loading }: { attachments: CaseAttachment[]; loading: boolean }) {
  if (loading) return <div className="spinner">Loading attachments…</div>;
  if (attachments.length === 0) return <div className="empty-state">No attachments on this case yet.</div>;

  return (
    <div className="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>Filename</th>
            <th>Type</th>
            <th>Size</th>
            <th>Description</th>
            <th>Uploaded</th>
          </tr>
        </thead>
        <tbody>
          {attachments.map((att) => (
            <tr key={att.id}>
              <td className="td-mono">{att.originalFilename}</td>
              <td className="td-muted">{att.mimeType ?? '—'}</td>
              <td className="td-muted">{formatBytes(att.fileSizeBytes)}</td>
              <td className="td-muted">{att.description ?? '—'}</td>
              <td className="td-muted">{formatDateTime(att.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ── Action modals ───────────────────────────────── */

function AddNoteModal({ caseId, onClose, onDone }: { caseId: string; onClose: () => void; onDone: () => void }) {
  const [body, setBody] = useState('');
  const [internal, setInternal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.notes.create(caseId, body, internal);
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add note.');
      setSubmitting(false);
    }
  }

  return (
    <Modal
      title="Add Note"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" form="add-note-form" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Add Note'}
          </button>
        </>
      }
    >
      <form id="add-note-form" onSubmit={handleSubmit} style={{ display: 'contents' }}>
        {error && <div className="form-error">{error}</div>}
        <div className="form-group">
          <label className="form-label" htmlFor="modal-note-body">Note *</label>
          <textarea
            id="modal-note-body"
            className="form-textarea"
            rows={6}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            required
            maxLength={10000}
            placeholder="Enter note text…"
          />
        </div>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={internal}
            onChange={(e) => setInternal(e.target.checked)}
          />
          <span className="checkbox-label">Internal note (not visible to client)</span>
        </label>
      </form>
    </Modal>
  );
}

function AddTaskModal({ caseId, onClose, onDone }: { caseId: string; onClose: () => void; onDone: () => void }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [statusCode, setStatusCode] = useState('PENDING');
  const [assignedToId, setAssignedToId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.tasks.create(caseId, {
        title,
        description: description || undefined,
        statusCode,
        assignedToId: assignedToId || undefined,
        dueDate: dueDate || undefined,
      });
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add task.');
      setSubmitting(false);
    }
  }

  return (
    <Modal
      title="Add Task"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" form="add-task-form" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Add Task'}
          </button>
        </>
      }
    >
      <form id="add-task-form" onSubmit={handleSubmit} style={{ display: 'contents' }}>
        {error && <div className="form-error">{error}</div>}
        <div className="form-group">
          <label className="form-label" htmlFor="modal-task-title">Title *</label>
          <input
            id="modal-task-title"
            className="form-input"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            maxLength={500}
            placeholder="Task title"
          />
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="modal-task-desc">Description</label>
          <textarea
            id="modal-task-desc"
            className="form-textarea"
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={2000}
            placeholder="Optional description"
          />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div className="form-group">
            <label className="form-label" htmlFor="modal-task-status">Status</label>
            <select
              id="modal-task-status"
              className="form-select"
              value={statusCode}
              onChange={(e) => setStatusCode(e.target.value)}
            >
              {TASK_STATUSES.map((s) => (
                <option key={s.code} value={s.code}>{s.label}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="modal-task-due">Due Date</label>
            <input
              id="modal-task-due"
              className="form-input"
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
            />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="modal-task-assigned">Assigned To (UUID)</label>
          <input
            id="modal-task-assigned"
            className="form-input"
            type="text"
            value={assignedToId}
            onChange={(e) => setAssignedToId(e.target.value)}
            placeholder="Optional user UUID"
          />
        </div>
      </form>
    </Modal>
  );
}

function TransitionStatusModal({
  caseId,
  currentStatus,
  onClose,
  onDone,
}: {
  caseId: string;
  currentStatus: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const allowed = ALLOWED_TRANSITIONS[currentStatus] ?? [];
  const [statusCode, setStatusCode] = useState(allowed[0] ?? '');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (allowed.length === 0) {
    return (
      <Modal title="Transition Status" onClose={onClose} footer={
        <button className="btn btn-secondary" onClick={onClose}>Close</button>
      }>
        <p style={{ fontSize: 14, color: 'var(--color-text-muted)' }}>
          No transitions are available from <strong>{STATUS_LABEL[currentStatus] ?? currentStatus}</strong>.
        </p>
      </Modal>
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.cases.transitionStatus(caseId, statusCode, reason || undefined);
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to transition status.');
      setSubmitting(false);
    }
  }

  return (
    <Modal
      title="Transition Status"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" form="transition-form" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Apply Transition'}
          </button>
        </>
      }
    >
      <form id="transition-form" onSubmit={handleSubmit} style={{ display: 'contents' }}>
        {error && <div className="form-error">{error}</div>}
        <div className="form-group">
          <label className="form-label" htmlFor="modal-ts-current">Current Status</label>
          <input
            id="modal-ts-current"
            className="form-input"
            type="text"
            value={STATUS_LABEL[currentStatus] ?? currentStatus}
            disabled
            style={{ background: 'var(--color-bg)', color: 'var(--color-text-muted)' }}
          />
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="modal-ts-new">New Status *</label>
          <select
            id="modal-ts-new"
            className="form-select"
            value={statusCode}
            onChange={(e) => setStatusCode(e.target.value)}
            required
          >
            {allowed.map((code) => (
              <option key={code} value={code}>{STATUS_LABEL[code] ?? code}</option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="modal-ts-reason">Reason</label>
          <textarea
            id="modal-ts-reason"
            className="form-textarea"
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={1000}
            placeholder="Optional reason for this transition"
          />
        </div>
      </form>
    </Modal>
  );
}
