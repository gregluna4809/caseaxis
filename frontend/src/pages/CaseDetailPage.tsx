import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { CaseDetail, CaseNote, CaseTask, CaseAttachment } from '../types/api';
import { StatusBadge, PriorityBadge, TaskStatusBadge } from '../components/StatusBadge';
import { formatDate, formatDateTime, formatBytes, truncate } from '../lib/utils';

type TabId = 'overview' | 'notes' | 'tasks' | 'attachments';

const TABS: { id: TabId; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'notes', label: 'Notes' },
  { id: 'tasks', label: 'Tasks' },
  { id: 'attachments', label: 'Attachments' },
];

export function CaseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [activeTab, setActiveTab] = useState<TabId>('overview');

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
  }, [id]);

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
    </div>
  );
}

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
          <span className="detail-label">Organization ID</span>
          <span className="detail-value mono">{truncate(c.organizationId, 16)}</span>
        </div>
        <div className="detail-field">
          <span className="detail-label">Client ID</span>
          <span className="detail-value mono">{truncate(c.clientId, 16)}</span>
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
