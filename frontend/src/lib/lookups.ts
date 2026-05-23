export const PRIORITIES = [
  { code: 'LOW', label: 'Low' },
  { code: 'MEDIUM', label: 'Medium' },
  { code: 'HIGH', label: 'High' },
  { code: 'CRITICAL', label: 'Critical' },
] as const;

export const CASE_TYPES = [
  { code: 'COMPLAINT', label: 'Complaint' },
  { code: 'APPLICATION', label: 'Application' },
  { code: 'INQUIRY', label: 'Inquiry' },
  { code: 'INVESTIGATION', label: 'Investigation' },
  { code: 'GENERAL', label: 'General' },
] as const;

export const TASK_STATUSES = [
  { code: 'PENDING', label: 'Pending' },
  { code: 'IN_PROGRESS', label: 'In Progress' },
  { code: 'COMPLETED', label: 'Completed' },
  { code: 'CANCELLED', label: 'Cancelled' },
] as const;

export const CASE_STATUSES = [
  { code: 'NEW', label: 'New' },
  { code: 'ASSIGNED', label: 'Assigned' },
  { code: 'IN_REVIEW', label: 'In Review' },
  { code: 'PENDING_INFO', label: 'Pending Info' },
  { code: 'ESCALATED', label: 'Escalated' },
  { code: 'APPROVED', label: 'Approved' },
  { code: 'DENIED', label: 'Denied' },
  { code: 'CLOSED', label: 'Closed' },
  { code: 'REOPENED', label: 'Reopened' },
] as const;

export const STATUS_LABEL: Record<string, string> = Object.fromEntries(
  CASE_STATUSES.map((s) => [s.code, s.label]),
);

export const ALLOWED_TRANSITIONS: Record<string, string[]> = {
  NEW:          ['ASSIGNED', 'IN_REVIEW', 'PENDING_INFO', 'ESCALATED'],
  ASSIGNED:     ['IN_REVIEW', 'PENDING_INFO', 'ESCALATED', 'APPROVED', 'DENIED', 'CLOSED'],
  IN_REVIEW:    ['PENDING_INFO', 'ASSIGNED', 'ESCALATED', 'APPROVED', 'DENIED', 'CLOSED'],
  PENDING_INFO: ['IN_REVIEW', 'ASSIGNED', 'ESCALATED'],
  ESCALATED:    ['IN_REVIEW', 'ASSIGNED', 'PENDING_INFO', 'APPROVED', 'DENIED'],
  APPROVED:     ['REOPENED'],
  DENIED:       ['REOPENED'],
  CLOSED:       ['REOPENED'],
  REOPENED:     ['ASSIGNED', 'IN_REVIEW', 'PENDING_INFO', 'ESCALATED'],
};
