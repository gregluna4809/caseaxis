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

// GET /api/cases — content items
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
  clientId: string | null;
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

// GET /api/organizations
export interface OrganizationSummary {
  id: string;
  name: string;
}

// GET /api/clients
export interface ClientSummary {
  id: string;
  displayName: string;
  organizationId: string | null;
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
