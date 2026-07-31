import type {
  ApiProblem, Audit, AuditComparison, AuditPage, AuditResults, Dashboard, FindingChangeType,
  FindingSeverity, MaintenanceTask, PortalUser, TaskCreateInput, TaskPage, TaskPriority, TaskStatus,
  TaskUpdateInput, Website, WebsiteInput, WebsitePage,
} from './types'

let csrfToken = ''
let csrfHeader = 'X-XSRF-TOKEN'

export class ApiRequestError extends Error {
  status: number

  constructor(status: number, problem: ApiProblem) {
    super(problem.message || 'Не удалось выполнить запрос.')
    this.name = 'ApiRequestError'
    this.status = status
  }
}

async function ensureCsrf() {
  if (csrfToken) return
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw await asError(response)
  const data = await response.json() as { headerName: string; token: string }
  csrfHeader = data.headerName
  csrfToken = data.token
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    await ensureCsrf()
    headers.set(csrfHeader, csrfToken)
  }
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })
  if (!response.ok) throw await asError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function asError(response: Response) {
  let problem: ApiProblem = { message: `Ошибка запроса (${response.status}).` }
  try {
    problem = await response.json() as ApiProblem
  } catch {
    // The status code remains available even when a proxy returns a non-JSON error.
  }
  return new ApiRequestError(response.status, problem)
}

export const api = {
  me: () => request<PortalUser>('/api/v1/me'),
  login: (email: string, password: string) => request<PortalUser>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  }),
  logout: () => request<void>('/api/v1/auth/logout', { method: 'POST' }),
  websites: () => request<WebsitePage>('/api/v1/websites?size=100&sort=name'),
  website: (id: string) => request<Website>(`/api/v1/websites/${id}`),
  createWebsite: (input: WebsiteInput) => request<Website>('/api/v1/websites', {
    method: 'POST',
    body: JSON.stringify(input),
  }),
  updateWebsite: (id: string, input: WebsiteInput) => request<Website>(`/api/v1/websites/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  }),
  audits: () => request<AuditPage>('/api/v1/audits?size=100'),
  auditResults: (id: string, filters: { severity?: FindingSeverity; ruleCode?: string; changeType?: FindingChangeType; query?: string } = {}) => {
    const params = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
    const suffix = params.size ? `?${params.toString()}` : ''
    return request<AuditResults>(`/api/v1/audits/${id}/results${suffix}`)
  },
  auditComparison: (websiteId: string) => request<AuditComparison>(`/api/v1/websites/${websiteId}/audit-comparison`),
  startAudit: (websiteId: string) => request<Audit>(`/api/v1/websites/${websiteId}/audits`, {
    method: 'POST',
  }),
  users: () => request<PortalUser[]>('/api/v1/users'),
  dashboard: () => request<Dashboard>('/api/v1/dashboard'),
  tasks: (filters: { status?: TaskStatus; priority?: TaskPriority; assignedToUserId?: string; query?: string } = {}) => {
    const params = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
    const suffix = params.size ? `?${params.toString()}` : ''
    return request<TaskPage>(`/api/v1/tasks${suffix}`)
  },
  createTask: (findingId: string, input: TaskCreateInput = {}) => request<MaintenanceTask>(`/api/v1/findings/${findingId}/tasks`, {
    method: 'POST',
    body: JSON.stringify(input),
  }),
  updateTask: (id: string, input: TaskUpdateInput) => request<MaintenanceTask>(`/api/v1/tasks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  }),
}
