// ============================================================
// AFlow API 请求/响应 TypeScript 接口定义
// ============================================================

import type {
  FlowDefinition,
  FlowInstance,
  FlowEvent,
  FlowStatus,
  AtomicComponent,
  Snapshot,
} from '@/types'

// --- 通用响应包装 ---

export interface ApiResponse<T> {
  success: boolean
  message?: string
  errorCode?: string
  data: T
  timestamp?: string
}

export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

// --- Definition API ---

export interface CreateDefinitionRequest {
  id?: string
  name: string
  dslContent: string
}

export interface UpdateDefinitionRequest {
  name: string
  dslContent: string
}

export interface DefinitionDetailResponse {
  definition: FlowDefinition
  dslContent: string
}

export interface PublishDefinitionResponse {
  message: string
}

// --- Flow Execution API ---

export interface StartFlowRequest {
  flowDefinitionId: string
  inputs?: Record<string, unknown>
}

export interface ResumeFlowRequest {
  additionalInputs?: Record<string, unknown>
}

export interface FlowListParams {
  status?: FlowStatus
}

export type StartFlowResponse = FlowInstance

export type ResumeFlowResponse = FlowInstance

export type CancelFlowResponse = FlowInstance

export type RetryFlowResponse = FlowInstance

export type FlowListResponse = FlowInstance[]

export type FlowDetailResponse = FlowInstance

export type FlowEventsResponse = FlowEvent[]

export type FlowSnapshotsResponse = Snapshot[]

export interface FlowDiffResponse {
  nodeId: string
  before: Record<string, unknown>
  after: Record<string, unknown>
}

// --- Debug API ---

export interface AddBreakpointRequest {
  flowId: string
  nodeId: string
}

export interface RemoveBreakpointRequest {
  flowId: string
  nodeId: string
}

export type GetBreakpointsResponse = string[]

export type StepResponse = FlowInstance

export interface UpdateContextRequest {
  variables: Record<string, unknown>
}

export type UpdateContextResponse = FlowInstance

// --- Atomic Component API ---

export interface CreateAtomicComponentRequest {
  name: string
  description?: string
  nodeType: string
  category?: string
  config: Record<string, unknown>
  inputSchema?: Record<string, unknown>
  outputSchema?: Record<string, unknown>
}

export interface UpdateAtomicComponentRequest {
  name?: string
  description?: string
  nodeType?: string
  category?: string
  config?: Record<string, unknown>
  inputSchema?: Record<string, unknown>
  outputSchema?: Record<string, unknown>
  status?: string
}

export interface AtomicInvokeRequest {
  config?: Record<string, unknown>
  inputs?: Record<string, unknown>
}

export interface AtomicInvokeResponse {
  status: 'SUCCESS' | 'FAILED' | 'SUSPENDED'
  output?: Record<string, unknown>
  error?: string
}

export interface NodeRegistryItem {
  type: string
  name: string
  description: string
  configSchema: string
}

export interface ComponentRegistryItem {
  id: string
  name: string
  category: string
  nodeType: string
  status: string
  inputSchema?: Record<string, unknown>
  outputSchema?: Record<string, unknown>
  description?: string
}

export interface AtomicComponentListParams {
  category?: string
  status?: string
  keyword?: string
}

// --- Approval API ---

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'TIMEOUT'

export interface ApprovalRequest {
  id: string
  flowId: string
  nodeId: string
  title: string
  description?: string
  options?: ApprovalOption[]
  status: ApprovalStatus
  deadline?: string
  createdAt?: string
  updatedAt?: string
}

export interface ApprovalOption {
  label: string
  value: string
}

export interface ApproveRequest {
  data?: Record<string, unknown>
}

export interface RejectRequest {
  reason?: string
}

export interface ApprovalListParams {
  status?: ApprovalStatus
  page?: number
  pageSize?: number
}

export type ApprovalListResponse = ApprovalRequest[]

export type ApprovalDetailResponse = ApprovalRequest

// --- Metrics API ---

export interface MetricsSummaryResponse {
  activeFlows: number
  todayExecutions: number
  successRate: number
  avgDurationMs: number
  agent: AgentMetricsSummary
  llm: LlmMetricsSummary
  nodeDurations: NodeDurationEntry[]
}

export interface AgentMetricsSummary {
  totalTokens: number
  avgIterations: number
  topTools: ToolUsageEntry[]
}

export interface LlmMetricsSummary {
  totalCalls: number
  p50LatencyMs: number
  retryRate: number
}

export interface ToolUsageEntry {
  tool: string
  count: number
}

export interface NodeDurationEntry {
  nodeType: string
  avgDurationMs: number
  p95DurationMs: number
  count: number
}

// --- SSE Event Types ---

export interface SseEventHandlers {
  [eventName: string]: (data: unknown) => void
}

export interface SseConnectionState {
  connected: boolean
  reconnecting: boolean
  closed: boolean
}

// --- Definition Version API ---

export interface DefinitionVersion {
  versionNumber: number
  snapshotJson: string
  createdAt: string
}

export type DefinitionVersionListResponse = DefinitionVersion[]

export interface RollbackVersionRequest {
  versionNumber: number
}

// --- DSL Validation ---

export interface ValidationError {
  field: string
  code: string
  message: string
  nodeId?: string | null
}

export interface ValidationResult {
  valid: boolean
  errors: ValidationError[]
}

// --- Import DSL ---

export interface ImportDslRequest {
  dslContent: string
}

export interface ImportDslResponse {
  definition: FlowDefinition
}
