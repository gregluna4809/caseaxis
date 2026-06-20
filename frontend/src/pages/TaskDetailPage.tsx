import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../lib/apiClient';
import type { TaskDetail } from '../types/api';
import { TaskStatusBadge } from '../components/StatusBadge';
import { Modal } from '../components/Modal';
import { TASK_STATUSES } from '../lib/lookups';
import { formatDate, formatDateTime } from '../lib/utils';

export function TaskDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [task, setTask] = useState<TaskDetail | null>(null);
  const [assignedToId, setAssignedToId] = useState<string | null>(null);
  const [statusCode, setStatusCode] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const loadTask = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    setActionError(null);
    try {
      const detail = await api.tasks.get(id);
      setTask(detail);
      setStatusCode(detail.statusCode);

      const caseTasks = await api.tasks.list(detail.caseId);
      const matchingTask = caseTasks.find((item) => item.id === id);
      setAssignedToId(matchingTask?.assignedToId ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load task.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadTask();
  }, [loadTask]);

  async function updateStatus(nextStatus: string) {
    if (!id || !task) return;
    setSaving(true);
    setActionError(null);
    try {
      await api.tasks.update(id, {
        title: task.title,
        description: task.description,
        statusCode: nextStatus,
        assignedToId,
        dueDate: task.dueDate,
      });
      await loadTask();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to update task.');
    } finally {
      setSaving(false);
    }
  }

  async function deleteTask() {
    if (!id) return;
    setSaving(true);
    setActionError(null);
    try {
      await api.tasks.delete(id);
      navigate('/tasks');
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to delete task.');
      setSaving(false);
    }
  }

  if (loading) return <div className="spinner">Loading task...</div>;

  if (error || !task) {
    return (
      <div className="page-stack">
        <Link to="/tasks" className="back-link">Back to Review Actions</Link>
        <div className="form-error">{error ?? 'Task not found.'}</div>
      </div>
    );
  }

  const overdue = isTaskOverdue(task);

  return (
    <div className="page-stack">
      <Link to="/tasks" className="back-link">Back to Review Actions</Link>

      <section className={`case-hero record-highlights task-record-hero ${overdue ? 'task-record-overdue' : ''}`}>
        <div className="object-icon record-icon">T</div>
        <div className="case-hero-main">
          <div className="case-number">Review action record</div>
          <h1 className="case-title-large">{task.title}</h1>
          <div className="case-badges">
            <TaskStatusBadge code={task.statusCode} label={task.statusDisplayName} />
            {overdue && <span className="badge badge-warning">Overdue</span>}
            {task.caseNumber && (
              <Link to={`/cases/${task.caseId}`} className="badge badge-neutral">
                {task.caseNumber}
              </Link>
            )}
          </div>
        </div>
        <div className="case-hero-meta highlights-fields">
          <Metric label="Due" value={formatDate(task.dueDate)} tone={overdue ? 'warning' : undefined} />
          <Metric label="Assignee" value={task.assigneeDisplayName ?? 'Unassigned'} />
          <Metric label="Updated" value={formatDateTime(task.updatedAt)} />
        </div>
      </section>

      <section className="card detail-card">
        <div className="card-header task-action-header">
          <div>
            <span className="card-title">Task Details</span>
            <p className="card-subtitle">Update action status and review benefit context</p>
          </div>
          <div className="action-bar">
            <select
              className="form-select compact-select"
              value={statusCode}
              onChange={(e) => setStatusCode(e.target.value)}
              disabled={saving}
              aria-label="Task status"
            >
              {TASK_STATUSES.map((status) => (
                <option key={status.code} value={status.code}>{status.label}</option>
              ))}
            </select>
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => updateStatus(statusCode)}
              disabled={saving || statusCode === task.statusCode}
            >
              Save Status
            </button>
            <button
              className="btn btn-primary btn-sm"
              onClick={() => updateStatus('COMPLETED')}
              disabled={saving || task.statusCode === 'COMPLETED'}
            >
              Mark Complete
            </button>
          </div>
        </div>

        {actionError && <div className="form-error surface-error">{actionError}</div>}

        <div className="tab-content">
          <div className="overview-layout task-detail-layout">
            <section>
              <h2 className="section-heading">Description</h2>
              <div className="description-panel">
                {task.description || 'No description provided.'}
              </div>
            </section>
            <section>
              <h2 className="section-heading">Record Fields</h2>
              <div className="detail-grid">
                <DetailField label="Status" value={<TaskStatusBadge code={task.statusCode} label={task.statusDisplayName} />} />
                <DetailField label="Due date" value={formatDate(task.dueDate)} />
                <DetailField label="Assignee" value={task.assigneeDisplayName ?? 'Unassigned'} />
                <DetailField
                  label="Linked case"
                  value={
                    task.caseNumber ? (
                      <Link to={`/cases/${task.caseId}`}>{task.caseNumber} - {task.caseTitle ?? 'Untitled case'}</Link>
                    ) : '-'
                  }
                />
                <DetailField label="Created" value={formatDateTime(task.createdAt)} />
                <DetailField label="Updated" value={formatDateTime(task.updatedAt)} />
              </div>
            </section>
          </div>
        </div>
      </section>

      <div className="action-bar">
        <button className="btn btn-secondary btn-sm" onClick={() => navigate(`/cases/${task.caseId}`)}>
          Open Linked Case
        </button>
        <button className="btn btn-secondary btn-sm" onClick={() => void loadTask()} disabled={saving}>
          Refresh
        </button>
        <button className="btn btn-secondary btn-sm" onClick={() => setConfirmDelete(true)} disabled={saving}>
          Delete Task
        </button>
      </div>

      {confirmDelete && (
        <Modal
          title="Delete Task"
          onClose={() => setConfirmDelete(false)}
          footer={(
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setConfirmDelete(false)} disabled={saving}>Cancel</button>
              <button type="button" className="btn btn-primary" onClick={() => void deleteTask()} disabled={saving}>
                {saving ? 'Deleting...' : 'Delete Task'}
              </button>
            </>
          )}
        >
          <div className="field-hint-warn">
            This task will be removed from active task views. The linked case and historical task metadata remain preserved.
          </div>
        </Modal>
      )}
    </div>
  );
}

function Metric({ label, value, tone }: { label: string; value: string; tone?: 'warning' }) {
  return (
    <div className={`metric ${tone ? `metric-${tone}` : ''}`}>
      <span>{label}</span>
      <strong>{value}</strong>
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

function isTaskOverdue(task: Pick<TaskDetail, 'dueDate' | 'terminal'>) {
  if (!task.dueDate || task.terminal) return false;
  return task.dueDate < new Date().toISOString().slice(0, 10);
}
