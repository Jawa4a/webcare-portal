export type Role = 'ADMIN' | 'SPECIALIST' | 'VIEWER'

export interface PortalUser {
  id: string
  email: string
  displayName: string
  roles: Role[]
  active: boolean
  createdAt?: string
}

export interface Website {
  id: string
  name: string
  url: string
  normalizedHost: string
  maxPages: number
  maxDepth: number
  active: boolean
  createdByUserId: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface WebsiteInput {
  name: string
  url: string
  maxPages: number
  maxDepth: number
  active: boolean
}

export interface WebsitePage {
  items: Website[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type AuditStatus = 'CREATED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface Audit {
  id: string
  websiteId: string
  websiteName: string
  status: AuditStatus
  createdAt: string
  startedAt?: string
  finishedAt?: string
  pagesDiscovered: number
  pagesChecked: number
  findingsCount: number
  score?: number
  failureCode?: string
  failureMessage?: string
  startedBy: string
}

export interface AuditPage {
  items: Audit[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type FindingSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'
export type FindingChangeType = 'NEW' | 'ONGOING' | 'FIXED'

export interface AuditedPageResult {
  id: string
  url: string
  depth: number
  httpStatus?: number
  responseTimeMs?: number
  contentType?: string
  responseBytes?: number
  checkedAt?: string
  errorCode?: string
  errorMessage?: string
}

export interface Finding {
  id: string
  auditId: string
  pageId: string
  pageUrl: string
  ruleCode: string
  severity: FindingSeverity
  title: string
  description: string
  recommendation: string
  elementContext?: string
  changeType: FindingChangeType
  taskId?: string
  createdAt: string
}

export interface AuditResults {
  audit: Audit
  previousAuditId?: string
  severityCounts: Record<FindingSeverity, number>
  pages: AuditedPageResult[]
  findings: Finding[]
  totalFindings: number
  filteredFindings: number
}

export interface AuditComparison {
  websiteId: string
  websiteName: string
  latestAudit: Audit
  previousAudit?: Audit
  scoreDelta?: number
  newFindings: Finding[]
  ongoingFindings: Finding[]
  fixedFindings: Finding[]
}

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELED'

export interface TaskHistory {
  id: string
  oldStatus?: TaskStatus
  newStatus: TaskStatus
  changedByUserId: string
  changedBy: string
  comment?: string
  changedAt: string
}

export interface MaintenanceTask {
  id: string
  findingId: string
  auditId: string
  websiteId: string
  websiteName: string
  pageUrl: string
  ruleCode: string
  findingTitle: string
  title: string
  description?: string
  priority: TaskPriority
  status: TaskStatus
  assignedToUserId?: string
  assignedTo?: string
  createdByUserId: string
  createdBy: string
  dueAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
  history: TaskHistory[]
}

export interface TaskPage {
  items: MaintenanceTask[]
  totalItems: number
}

export interface TaskCreateInput {
  priority?: TaskPriority
  assignedToUserId?: string
  dueAt?: string
}

export interface TaskUpdateInput {
  title: string
  description?: string
  priority: TaskPriority
  status: TaskStatus
  assignedToUserId?: string
  dueAt?: string
  comment?: string
}

export interface DashboardSite {
  websiteId: string
  websiteName: string
  host: string
  active: boolean
  latestAuditId?: string
  latestAuditStatus?: AuditStatus
  score?: number
  findingsCount: number
  checkedAt?: string
}

export interface Dashboard {
  totalWebsites: number
  activeWebsites: number
  completedAudits: number
  openTasks: number
  latestFindings: number
  averageScore?: number
  sites: DashboardSite[]
  recentAudits: Audit[]
}

export interface ApiProblem {
  code?: string
  message?: string
  fieldErrors?: Array<{ field: string; code: string }>
  correlationId?: string
}
