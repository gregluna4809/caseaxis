interface StatusBadgeProps {
  code: string;
  label: string;
}

export function StatusBadge({ code, label }: StatusBadgeProps) {
  return (
    <span className={`badge badge-status-${code}`}>
      {label}
    </span>
  );
}

export function PriorityBadge({ code, label }: StatusBadgeProps) {
  return (
    <span className={`badge badge-priority-${code}`}>
      {label}
    </span>
  );
}

export function TaskStatusBadge({ code, label }: StatusBadgeProps) {
  return (
    <span className={`badge badge-task-${code}`}>
      {label}
    </span>
  );
}
