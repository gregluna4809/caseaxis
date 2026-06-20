import { useState, useEffect, type FormEvent, type ReactNode } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { AuditEvent, CaseDetail, CaseNote, CaseTask, CaseAttachment } from '../types/api';
import { StatusBadge, PriorityBadge, TaskStatusBadge } from '../components/StatusBadge';
import { Modal } from '../components/Modal';
import { ALLOWED_TRANSITIONS, STATUS_LABEL, TASK_STATUSES } from '../lib/lookups';
import { displayActor, formatBytes, formatDate, formatDateTime } from '../lib/utils';

type TabId = 'overview' | 'audit' | 'notes' | 'tasks' | 'attachments';
type ModalId = 'note' | 'task' | 'status' | 'archive' | null;

const TABS: { id: TabId; label: string }[] = [
  { id: 'overview', label: 'Details' },
  { id: 'audit', label: 'Audit' },
  { id: 'notes', label: 'Notes' },
  { id: 'tasks', label: 'Review Actions' },
  { id: 'attachments', label: 'Files' },
];

export function CaseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [activeTab, setActiveTab] = useState<TabId>('overview');
  const [activeModal, setActiveModal] = useState<ModalId>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const [caseDetail, setCaseDetail] = useState<CaseDetail | null>(null);
  const [notes, setNotes] = useState<CaseNote[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [tasks, setTasks] = useState<CaseTask[]>([]);
  const [attachments, setAttachments] = useState<CaseAttachment[]>([]);

  const [loadingCase, setLoadingCase] = useState(true);
  const [loadingNotes, setLoadingNotes] = useState(true);
  const [loadingAudit, setLoadingAudit] = useState(true);
  const [loadingTasks, setLoadingTasks] = useState(true);
  const [loadingAttachments, setLoadingAttachments] = useState(true);
  const [caseError, setCaseError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [submittingAction, setSubmittingAction] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    async function loadAll() {
      setLoadingCase(true);
      setLoadingNotes(true);
      setLoadingAudit(true);
      setLoadingTasks(true);
      setLoadingAttachments(true);
      setCaseError(null);
      setActionError(null);

      try {
        const detail = await api.cases.get(id!);
        if (!cancelled) setCaseDetail(detail);
      } catch (err) {
        if (!cancelled) setCaseError(err instanceof Error ? err.message : 'Failed to load case.');
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
        const audit = await api.audit.caseEvents(id!);
        if (!cancelled) setAuditEvents(audit);
      } finally {
        if (!cancelled) setLoadingAudit(false);
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

  if (loadingCase) return <div className="spinner">Loading case...</div>;

  if (caseError || !caseDetail) {
    return (
      <div className="page-stack">
        <Link to="/cases" className="back-link">Back to Cases</Link>
        <div className="form-error">{caseError ?? 'Case not found.'}</div>
      </div>
    );
  }

  return (
    <div className="page-stack">
      <Link to="/cases" className="back-link">Back to Cases</Link>

      <section className="case-hero record-highlights">
        <div className="object-icon record-icon">C</div>
        <div className="case-hero-main">
          <div className="case-number">Case {caseDetail.caseNumber}</div>
          <h1 className="case-title-large">{caseDetail.title}</h1>
          <div className="case-badges">
            <StatusBadge code={caseDetail.statusCode} label={caseDetail.statusDisplayName} />
            <PriorityBadge code={caseDetail.priorityCode} label={caseDetail.priorityDisplayName} />
            <span className="badge badge-neutral">{caseDetail.typeDisplayName}</span>
            {caseDetail.reopenedCount > 0 && (
              <span className="badge badge-warning">Reopened x{caseDetail.reopenedCount}</span>
            )}
          </div>
        </div>
        <div className="case-hero-meta highlights-fields">
          <Metric label="Due" value={formatDate(caseDetail.dueDate)} />
          <Metric label="Assignee" value={displayActor(caseDetail.assignedToId)} />
          <Metric label="Updated" value={formatDate(caseDetail.updatedAt)} />
        </div>
      </section>

      <div className="action-bar quick-actions">
        <button className="btn btn-secondary" onClick={() => setActiveModal('note')}>Add Note</button>
        <button className="btn btn-secondary" onClick={() => setActiveModal('task')}>Add Task</button>
        <button className="btn btn-primary" onClick={() => setActiveModal('status')}>Transition Status</button>
        <button
          className="btn btn-secondary"
          onClick={() => setActiveModal('archive')}
          disabled={caseDetail.statusCode === 'CLOSED'}
        >
          Archive Case
        </button>
      </div>

      {actionError && <div className="form-error">{actionError}</div>}

      <section className="card detail-card">
        <div className="tabs" role="tablist" aria-label="Case sections">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
              type="button"
              role="tab"
              aria-selected={activeTab === tab.id}
            >
              {tab.label}
              {tab.id === 'audit' && !loadingAudit && <span className="tab-count">{auditEvents.length}</span>}
              {tab.id === 'notes' && !loadingNotes && <span className="tab-count">{notes.length}</span>}
              {tab.id === 'tasks' && !loadingTasks && <span className="tab-count">{tasks.length}</span>}
              {tab.id === 'attachments' && !loadingAttachments && <span className="tab-count">{attachments.length}</span>}
            </button>
          ))}
        </div>

        <div className="tab-content">
          {activeTab === 'overview' && <OverviewTab c={caseDetail} />}
          {activeTab === 'audit' && <AuditTab events={auditEvents} loading={loadingAudit} />}
          {activeTab === 'notes' && <NotesTab notes={notes} loading={loadingNotes} />}
          {activeTab === 'tasks' && <TasksTab tasks={tasks} loading={loadingTasks} />}
          {activeTab === 'attachments' && <AttachmentsTab attachments={attachments} loading={loadingAttachments} />}
        </div>
      </section>

      {activeModal === 'note' && (
        <AddNoteModal caseId={caseDetail.id} onClose={() => setActiveModal(null)} onDone={() => handleActionDone('notes')} />
      )}
      {activeModal === 'task' && (
        <AddTaskModal caseId={caseDetail.id} onClose={() => setActiveModal(null)} onDone={() => handleActionDone('tasks')} />
      )}
      {activeModal === 'status' && (
        <TransitionStatusModal
          caseId={caseDetail.id}
          currentStatus={caseDetail.statusCode}
          onClose={() => setActiveModal(null)}
          onDone={() => handleActionDone()}
        />
      )}
      {activeModal === 'archive' && (
        <Modal
          title="Archive Case"
          onClose={() => setActiveModal(null)}
          footer={(
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setActiveModal(null)} disabled={submittingAction}>Cancel</button>
              <button
                type="button"
                className="btn btn-primary"
                disabled={submittingAction}
                onClick={async () => {
                  setSubmittingAction(true);
                  setActionError(null);
                  try {
                    await api.cases.archive(caseDetail.id);
                    handleActionDone();
                  } catch (err) {
                    setActionError(err instanceof Error ? err.message : 'Failed to archive case.');
                  } finally {
                    setSubmittingAction(false);
                  }
                }}
              >
                {submittingAction ? 'Archiving...' : 'Archive Case'}
              </button>
            </>
          )}
        >
          <div className="field-hint-warn">
            This will close the case through the archive workflow. The case record, notes, tasks, attachments, and status history remain preserved.
          </div>
        </Modal>
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

function OverviewTab({ c }: { c: CaseDetail }) {
  return (
    <div className="overview-layout">
      <div className="overview-main">
        <div className="section-heading">
          <span>Description</span>
        </div>
        <p className="description-panel">{c.description || 'No description has been added to this case.'}</p>
      </div>

      <div className="detail-grid">
        <DetailField label="Case number" value={c.caseNumber} />
        <DetailField label="Status" value={<StatusBadge code={c.statusCode} label={c.statusDisplayName} />} />
        <DetailField label="Priority" value={<PriorityBadge code={c.priorityCode} label={c.priorityDisplayName} />} />
        <DetailField label="Type" value={c.typeDisplayName} />
        <DetailField label="Organization" value={c.organizationName && c.organizationCode ? `${c.organizationName} (${c.organizationCode})` : '-'} />
        <DetailField label="Client" value={c.clientDisplayName && c.clientNumber ? `${c.clientDisplayName} (${c.clientNumber})` : '-'} />
        <DetailField label="Assigned to" value={displayActor(c.assignedToId)} />
        <DetailField label="Assigned at" value={formatDateTime(c.assignedAt)} />
        <DetailField label="Due date" value={formatDate(c.dueDate)} />
        <DetailField label="Reopened count" value={String(c.reopenedCount)} />
        <DetailField label="Resolved at" value={formatDateTime(c.resolvedAt)} />
        <DetailField label="Closed at" value={formatDateTime(c.closedAt)} />
        <DetailField label="Created by" value={displayActor(c.createdBy)} />
        <DetailField label="Created at" value={formatDateTime(c.createdAt)} />
        <DetailField label="Last updated" value={formatDateTime(c.updatedAt)} />
      </div>
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

function NotesTab({ notes, loading }: { notes: CaseNote[]; loading: boolean }) {
  if (loading) return <div className="spinner">Loading notes...</div>;
  if (notes.length === 0) return (
    <div className="empty-state-panel">
      <div className="empty-state-title">No activity recorded</div>
      <div className="empty-state-body">
        This case has no notes yet. Add the first note to begin documenting case activity and decisions.
      </div>
    </div>
  );

  return (
    <div className="activity-feed">
      {notes.map((note) => (
        <article key={note.id} className="activity-item">
          <div className="activity-marker" />
          <div className="note-card">
            <div className="note-meta">
              <strong>{displayActor(note.createdBy)}</strong>
              <span>{formatDateTime(note.createdAt)}</span>
              {note.internal && <span className="badge badge-internal">Internal</span>}
              {note.supersedesNoteId && <span className="badge badge-warning">Correction</span>}
            </div>
            <div className="note-body">{note.body}</div>
          </div>
        </article>
      ))}
    </div>
  );
}

function AuditTab({ events, loading }: { events: AuditEvent[]; loading: boolean }) {
  if (loading) return <div className="spinner">Loading audit events...</div>;
  if (events.length === 0) return (
    <div className="empty-state-panel">
      <div className="empty-state-title">No audit events recorded</div>
      <div className="empty-state-body">
        Business actions on this case will appear here as an immutable timeline.
      </div>
    </div>
  );

  return (
    <div className="activity-feed audit-feed">
      {events.map((event) => (
        <article key={event.id} className="activity-item">
          <div className="activity-marker audit-marker" />
          <div className="note-card audit-card">
            <div className="note-meta">
              <strong>{event.actorDisplayName}</strong>
              <span>{formatDateTime(event.occurredAt)}</span>
              <span className="badge badge-neutral">{event.eventType}</span>
            </div>
            <div className="note-body audit-summary">{event.summary}</div>
          </div>
        </article>
      ))}
    </div>
  );
}

function TasksTab({ tasks, loading }: { tasks: CaseTask[]; loading: boolean }) {
  if (loading) return <div className="spinner">Loading tasks...</div>;
  if (tasks.length === 0) return (
    <div className="empty-state-panel">
      <div className="empty-state-title">No tasks assigned</div>
      <div className="empty-state-body">
        This case has no tasks yet. Use Add Task to create work items and track progress toward resolution.
      </div>
    </div>
  );

  return (
    <div className="task-list">
      {tasks.map((task) => (
        <article key={task.id} className="task-card">
          <div className="task-card-main">
            <div>
              <h3>{task.title}</h3>
              {task.description && <p>{task.description}</p>}
            </div>
            <TaskStatusBadge code={task.statusCode} label={task.statusDisplayName} />
          </div>
          <div className="task-meta-grid">
            <Metric label="Assignee" value={displayActor(task.assignedToId)} />
            <Metric label="Due" value={formatDate(task.dueDate)} />
            <Metric label="Completed" value={formatDateTime(task.completedAt)} />
            <Metric label="Created" value={formatDate(task.createdAt)} />
          </div>
        </article>
      ))}
    </div>
  );
}

function AttachmentsTab({ attachments, loading }: { attachments: CaseAttachment[]; loading: boolean }) {
  if (loading) return <div className="spinner">Loading files...</div>;
  if (attachments.length === 0) return (
    <div className="empty-state-panel">
      <div className="empty-state-title">No files attached</div>
      <div className="empty-state-body">
        No file metadata has been registered for this case. Register attachment records via the API to track case documents.
      </div>
    </div>
  );

  return (
    <div className="table-wrapper">
      <table className="data-table">
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
              <td className="filename-cell">{att.originalFilename}</td>
              <td className="td-muted">{att.mimeType ?? '-'}</td>
              <td className="td-muted">{formatBytes(att.fileSizeBytes)}</td>
              <td className="td-muted">{att.description ?? '-'}</td>
              <td className="td-muted">{formatDateTime(att.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AddNoteModal({ caseId, onClose, onDone }: { caseId: string; onClose: () => void; onDone: () => void }) {
  const [body, setBody] = useState('');
  const [internal, setInternal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
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
      footer={(
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" form="add-note-form" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving...' : 'Add Note'}
          </button>
        </>
      )}
    >
      <form id="add-note-form" onSubmit={handleSubmit} className="modal-form">
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
            placeholder="Enter note text..."
          />
        </div>
        <label className="checkbox-row">
          <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
          <span className="checkbox-label">Internal note</span>
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

  async function handleSubmit(e: FormEvent) {
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
      footer={(
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" form="add-task-form" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving...' : 'Add Task'}
          </button>
        </>
      )}
    >
      <form id="add-task-form" onSubmit={handleSubmit} className="modal-form">
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
        <div className="form-row">
          <div className="form-group">
            <label className="form-label" htmlFor="modal-task-status">Status</label>
            <select id="modal-task-status" className="form-select" value={statusCode} onChange={(e) => setStatusCode(e.target.value)}>
              {TASK_STATUSES.map((s) => <option key={s.code} value={s.code}>{s.label}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="modal-task-due">Due Date</label>
            <input id="modal-task-due" className="form-input" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="modal-task-assigned">Assignee</label>
          <input
            id="modal-task-assigned"
            className="form-input"
            type="text"
            value={assignedToId}
            onChange={(e) => setAssignedToId(e.target.value)}
            placeholder="Leave blank for unassigned"
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
      <Modal title="Transition Status" onClose={onClose} footer={<button className="btn btn-secondary" onClick={onClose}>Close</button>}>
        <p className="muted-copy">
          No transitions are available from <strong>{STATUS_LABEL[currentStatus] ?? currentStatus}</strong>.
        </p>
      </Modal>
    );
  }

  async function handleSubmit(e: FormEvent) {
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
      footer={(
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" form="transition-form" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving...' : 'Apply Transition'}
          </button>
        </>
      )}
    >
      <form id="transition-form" onSubmit={handleSubmit} className="modal-form">
        {error && <div className="form-error">{error}</div>}
        <div className="form-group">
          <label className="form-label" htmlFor="modal-ts-current">Current Status</label>
          <input id="modal-ts-current" className="form-input" type="text" value={STATUS_LABEL[currentStatus] ?? currentStatus} disabled />
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="modal-ts-new">New Status *</label>
          <select id="modal-ts-new" className="form-select" value={statusCode} onChange={(e) => setStatusCode(e.target.value)} required>
            {allowed.map((code) => <option key={code} value={code}>{STATUS_LABEL[code] ?? code}</option>)}
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
