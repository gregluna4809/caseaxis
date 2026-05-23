import type { ApiResponse, Page, LoginResponse, CaseSummary, CaseDetail, CaseNote, CaseTask, CaseAttachment } from '../types/api';

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
    throw new Error('Session expired. Please log in again.');
  }

  const body: ApiResponse<T> = await response.json();

  if (!body.success) {
    throw new Error(body.message ?? `Request failed (HTTP ${response.status})`);
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

  cases: {
    list(page = 0, size = 20) {
      return request<Page<CaseSummary>>(`/api/cases?page=${page}&size=${size}`);
    },
    get(id: string) {
      return request<CaseDetail>(`/api/cases/${id}`);
    },
  },

  notes: {
    list(caseId: string) {
      return request<CaseNote[]>(`/api/cases/${caseId}/notes`);
    },
  },

  tasks: {
    list(caseId: string) {
      return request<CaseTask[]>(`/api/cases/${caseId}/tasks`);
    },
  },

  attachments: {
    list(caseId: string) {
      return request<CaseAttachment[]>(`/api/cases/${caseId}/attachments`);
    },
  },
};
