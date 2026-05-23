// Shapes derived from docs/API_CONTRACT.md. Do not add fields not present in the contract.

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  timestamp: string;
}

export interface Page<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
  };
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
  empty: boolean;
}

export interface LoginResponse {
  token: string;
}

// GET /api/dashboard/metrics
export interface DashboardMetrics {
  totalCases: number;
  openCases: number;
  assignedToMe: number;
  overdueCases: number;
  escalatedCases: number;
  closedToday: number;
}

export interface DashboardCaseItem {
  id: string;
  caseNumber: string;
  title: string;
  statusCode: string;
  statusDisplayName: string;
  priorityCode: string;
  priorityDisplayName: string;
  dueDate: string | null;
  assignedToId: string | null;
  updatedAt: string;
}

export interface DashboardActivity {
  type: 'NOTE' | 'TASK' | 'STATUS';
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  summary: string;
  actorId: string | null;
  occurredAt: string;
}

export interface DashboardOverview {
  metrics: DashboardMetrics;
  recentAssignedCases: DashboardCaseItem[];
  escalationWatch: DashboardCaseItem[];
  overdueQueue: DashboardCaseItem[];
  recentActivity: DashboardActivity[];
}

// GET /api/cases - content items
export interface CaseSummary {
  id: string;
  caseNumber: string;
  title: string;
  statusCode: string;
  statusDisplayName: string;
  priorityCode: string;
  priorityDisplayName: string;
  typeCode: string;
  typeDisplayName: string;
  assignedToId: string | null;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
}

// GET /api/cases/:id
export interface CaseDetail {
  id: string;
  caseNumber: string;
  title: string;
  description: string | null;
  statusCode: string;
  statusDisplayName: string;
  priorityCode: string;
  priorityDisplayName: string;
  typeCode: string;
  typeDisplayName: string;
  organizationId: string | null;
  organizationCode: string | null;
  organizationName: string | null;
  clientId: string | null;
  clientNumber: string | null;
  clientDisplayName: string | null;
  assignedToId: string | null;
  assignedAt: string | null;
  dueDate: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  reopenedCount: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// GET /api/cases/:id/notes
export interface CaseNote {
  id: string;
  caseId: string;
  body: string;
  internal: boolean;
  supersedesNoteId: string | null;
  createdBy: string;
  createdAt: string;
  deleted: boolean;
}

// GET /api/cases/:id/tasks
export interface CaseTask {
  id: string;
  caseId: string;
  title: string;
  description: string | null;
  statusCode: string;
  statusDisplayName: string;
  assignedToId: string | null;
  dueDate: string | null;
  completedAt: string | null;
  completedBy: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// GET /api/tasks - content items
export interface TaskSummary {
  id: string;
  title: string;
  description: string | null;
  statusCode: string;
  statusDisplayName: string;
  terminal: boolean;
  dueDate: string | null;
  completedAt: string | null;
  caseId: string;
  caseNumber: string | null;
  caseTitle: string | null;
  assigneeDisplayName: string | null;
  createdAt: string;
  updatedAt: string;
}

// GET /api/tasks/:id
export interface TaskDetail {
  id: string;
  caseId: string;
  caseNumber: string | null;
  caseTitle: string | null;
  title: string;
  description: string | null;
  statusCode: string;
  statusDisplayName: string;
  terminal: boolean;
  dueDate: string | null;
  completedAt: string | null;
  assigneeDisplayName: string | null;
  createdAt: string;
  updatedAt: string;
}

// GET /api/organizations
export interface OrganizationSummary {
  id: string;
  organizationCode: string;
  name: string;
  active: boolean;
  createdAt: string;
  clientCount: number;
  caseCount: number;
  openCaseCount: number;
}

// GET /api/organizations/:id
export interface OrganizationDetail {
  id: string;
  organizationCode: string;
  name: string;
  phone: string | null;
  email: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  clientCount: number;
  caseCount: number;
  openCaseCount: number;
  escalatedCases: number;
  overdueCases: number;
}

// GET /api/clients
export interface ClientSummary {
  id: string;
  clientNumber: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  organizationId: string | null;
  organizationCode: string | null;
  organizationName: string | null;
  active: boolean;
  createdAt: string;
}

// GET /api/clients/:id
export interface ClientDetail {
  id: string;
  clientNumber: string;
  displayName: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  organizationId: string | null;
  organizationCode: string | null;
  organizationName: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  totalCases: number;
  openCases: number;
  escalatedCases: number;
  overdueCases: number;
}

// GET /api/cases/:id/attachments
export interface CaseAttachment {
  id: string;
  caseId: string;
  originalFilename: string;
  storagePath: string;
  fileSizeBytes: number | null;
  mimeType: string | null;
  description: string | null;
  createdBy: string;
  createdAt: string;
}
