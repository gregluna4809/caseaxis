import type {
  ApiResponse, Page, LoginResponse,
  CaseSummary, CaseDetail, CaseNote, CaseTask, CaseAttachment,
  OrganizationSummary, ClientSummary, DashboardMetrics, DashboardOverview,
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

  organizations: {
    list() {
      return request<OrganizationSummary[]>('/api/organizations');
    },
  },

  clients: {
    list() {
      return request<ClientSummary[]>('/api/clients');
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
