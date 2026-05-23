import type {
  ApiResponse, Page, LoginResponse,
  CaseSummary, CaseDetail, CaseNote, CaseTask, CaseAttachment,
  TaskSummary, TaskDetail,
  OrganizationSummary, OrganizationDetail, ClientSummary, ClientDetail,
  DashboardMetrics, DashboardOverview,
  ReportFilters, ReportSummary, DistributionItem, OverdueAgingBucket,
  AssigneeWorkload, OrganizationWorkload, ClosureTrendPoint, ReportExport,
} from '../types/api';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function getToken(): string | null {
  return localStorage.getItem('caseaxis_token');
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const response = await fetch(path, { ...options, headers });

  if (response.status === 401) {
    localStorage.removeItem('caseaxis_token');
    localStorage.removeItem('caseaxis_username');
    window.location.href = '/login';
    throw new ApiError('Session expired. Please log in again.');
  }

  const body: ApiResponse<T> = await response.json();

  if (!body.success) {
    // Validation failures include field errors in body.data
    const rawData = body.data as unknown;
    let fieldErrors: Record<string, string> | undefined;
    if (rawData && typeof rawData === 'object' && !Array.isArray(rawData)) {
      fieldErrors = rawData as Record<string, string>;
    }
    throw new ApiError(
      body.message ?? `Request failed (HTTP ${response.status})`,
      fieldErrors,
    );
  }

  return body.data as T;
}

async function requestText(path: string, options: RequestInit = {}): Promise<string> {
  const token = getToken();

  const headers: Record<string, string> = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const response = await fetch(path, { ...options, headers });

  if (response.status === 401) {
    localStorage.removeItem('caseaxis_token');
    localStorage.removeItem('caseaxis_username');
    window.location.href = '/login';
    throw new ApiError('Session expired. Please log in again.');
  }

  if (!response.ok) {
    throw new ApiError(`Request failed (HTTP ${response.status})`);
  }

  return response.text();
}

function reportQuery(params: ReportFilters & { sort?: string } = {}) {
  const search = new URLSearchParams();
  if (params.startDate) search.set('startDate', params.startDate);
  if (params.endDate) search.set('endDate', params.endDate);
  if (params.organizationId) search.set('organizationId', params.organizationId);
  if (params.clientId) search.set('clientId', params.clientId);
  if (params.caseType) search.set('caseType', params.caseType);
  if (params.status) search.set('status', params.status);
  if (params.assigneeId) search.set('assigneeId', params.assigneeId);
  if (params.sort) search.set('sort', params.sort);
  const query = search.toString();
  return query ? `?${query}` : '';
}

export const api = {
  auth: {
    login(username: string, password: string) {
      return request<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      });
    },
  },

  dashboard: {
    metrics() {
      return request<DashboardMetrics>('/api/dashboard/metrics');
    },
    overview() {
      return request<DashboardOverview>('/api/dashboard/overview');
    },
  },

  reports: {
    summary(params: ReportFilters = {}) {
      return request<ReportSummary>(`/api/reports/summary${reportQuery(params)}`);
    },
    statusDistribution(params: ReportFilters = {}) {
      return request<DistributionItem[]>(`/api/reports/status-distribution${reportQuery(params)}`);
    },
    typeDistribution(params: ReportFilters = {}) {
      return request<DistributionItem[]>(`/api/reports/type-distribution${reportQuery(params)}`);
    },
    overdueAging(params: ReportFilters = {}) {
      return request<OverdueAgingBucket[]>(`/api/reports/overdue-aging${reportQuery(params)}`);
    },
    assigneeWorkload(params: ReportFilters & { sort?: string } = {}) {
      return request<AssigneeWorkload[]>(`/api/reports/assignee-workload${reportQuery(params)}`);
    },
    organizationWorkload(params: ReportFilters & { sort?: string } = {}) {
      return request<OrganizationWorkload[]>(`/api/reports/organization-workload${reportQuery(params)}`);
    },
    closureTrend(params: ReportFilters = {}) {
      return request<ClosureTrendPoint[]>(`/api/reports/closure-trend${reportQuery(params)}`);
    },
    exportJson(params: ReportFilters = {}) {
      return request<ReportExport>(`/api/reports/export/json${reportQuery(params)}`);
    },
    exportCsv(params: ReportFilters = {}) {
      return requestText(`/api/reports/export/csv${reportQuery(params)}`);
    },
  },

  organizations: {
    list(params: { page?: number; size?: number; q?: string; active?: boolean } = {}) {
      const search = new URLSearchParams();
      search.set('page', String(params.page ?? 0));
      search.set('size', String(params.size ?? 20));
      if (params.q?.trim()) search.set('q', params.q.trim());
      if (params.active !== undefined) search.set('active', String(params.active));
      return request<Page<OrganizationSummary>>(`/api/organizations?${search.toString()}`);
    },
    get(id: string) {
      return request<OrganizationDetail>(`/api/organizations/${id}`);
    },
    clients(id: string, params: { page?: number; size?: number } = {}) {
      const search = new URLSearchParams();
      search.set('page', String(params.page ?? 0));
      search.set('size', String(params.size ?? 20));
      return request<Page<ClientSummary>>(`/api/organizations/${id}/clients?${search.toString()}`);
    },
    cases(id: string, params: { page?: number; size?: number } = {}) {
      const search = new URLSearchParams();
      search.set('page', String(params.page ?? 0));
      search.set('size', String(params.size ?? 20));
      return request<Page<CaseSummary>>(`/api/organizations/${id}/cases?${search.toString()}`);
    },
  },

  clients: {
    list(params: { page?: number; size?: number; q?: string; organizationId?: string; active?: boolean } = {}) {
      const search = new URLSearchParams();
      search.set('page', String(params.page ?? 0));
      search.set('size', String(params.size ?? 20));
      if (params.q?.trim()) search.set('q', params.q.trim());
      if (params.organizationId) search.set('organizationId', params.organizationId);
      if (params.active !== undefined) search.set('active', String(params.active));
      return request<Page<ClientSummary>>(`/api/clients?${search.toString()}`);
    },
    get(id: string) {
      return request<ClientDetail>(`/api/clients/${id}`);
    },
    cases(id: string, params: { page?: number; size?: number } = {}) {
      const search = new URLSearchParams();
      search.set('page', String(params.page ?? 0));
      search.set('size', String(params.size ?? 20));
      return request<Page<CaseSummary>>(`/api/clients/${id}/cases?${search.toString()}`);
    },
  },

  cases: {
    list(params: {
      page?: number;
      size?: number;
      q?: string;
      status?: string;
      priority?: string;
      type?: string;
    } = {}) {
      const search = new URLSearchParams();
      search.set('page', String(params.page ?? 0));
      search.set('size', String(params.size ?? 20));
      if (params.q?.trim()) search.set('q', params.q.trim());
      if (params.status) search.set('status', params.status);
      if (params.priority) search.set('priority', params.priority);
      if (params.type) search.set('type', params.type);
      return request<Page<CaseSummary>>(`/api/cases?${search.toString()}`);
    },
    get(id: string) {
      return request<CaseDetail>(`/api/cases/${id}`);
    },
    create(data: {
      title: string;
      description?: string;
      priorityCode: string;
      typeCode: string;
      organizationId?: string;
      clientId?: string;
      dueDate?: string;
    }) {
      return request<CaseDetail>('/api/cases', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },
    transitionStatus(id: string, statusCode: string, reason?: string) {
      return request<CaseDetail>(`/api/cases/${id}/status`, {
        method: 'POST',
        body: JSON.stringify({ statusCode, reason }),
      });
    },
  },

  notes: {
    list(caseId: string) {
      return request<CaseNote[]>(`/api/cases/${caseId}/notes`);
    },
    create(caseId: string, body: string, internal: boolean) {
      return request<CaseNote>(`/api/cases/${caseId}/notes`, {
        method: 'POST',
        body: JSON.stringify({ body, internal }),
      });
    },
  },

  tasks: {
    workspace(params: {
      page?: number;
      size?: number;
      q?: string;
      status?: string;
      overdueOnly?: boolean;
    } = {}) {
      const search = new URLSearchParams();
      search.set('page', String(params.page ?? 0));
      search.set('size', String(params.size ?? 20));
      if (params.q?.trim()) search.set('q', params.q.trim());
      if (params.status) search.set('status', params.status);
      if (params.overdueOnly) search.set('overdueOnly', 'true');
      return request<Page<TaskSummary>>(`/api/tasks?${search.toString()}`);
    },
    get(id: string) {
      return request<TaskDetail>(`/api/tasks/${id}`);
    },
    update(id: string, data: {
      title: string;
      description?: string | null;
      statusCode: string;
      assignedToId?: string | null;
      dueDate?: string | null;
    }) {
      return request<CaseTask>(`/api/tasks/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    },
    list(caseId: string) {
      return request<CaseTask[]>(`/api/cases/${caseId}/tasks`);
    },
    create(caseId: string, data: {
      title: string;
      description?: string;
      statusCode?: string;
      assignedToId?: string;
      dueDate?: string;
    }) {
      return request<CaseTask>(`/api/cases/${caseId}/tasks`, {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },
  },

  attachments: {
    list(caseId: string) {
      return request<CaseAttachment[]>(`/api/cases/${caseId}/attachments`);
    },
  },
};
