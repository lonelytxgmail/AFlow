import { get, post } from './client'
import type {
  StartFlowRequest,
  ResumeFlowRequest,
  FlowListParams,
  StartFlowResponse,
  ResumeFlowResponse,
  CancelFlowResponse,
  RetryFlowResponse,
  FlowEventsResponse,
  FlowSnapshotsResponse,
  FlowDiffResponse,
} from './types'
import type { FlowInstance } from '@/types'

/**
 * Flow execution: start / resume / cancel + instance queries
 */
export const flowApi = {
  /** List flow instances, optionally filtered by status */
  list: (params?: FlowListParams) =>
    get<FlowInstance[]>('/flows', params as Record<string, unknown>),

  /** Get a single flow instance */
  get: (id: string) => get<FlowInstance>(`/flows/${id}`),

  /** Start a new flow execution */
  start: (data: StartFlowRequest) =>
    post<StartFlowResponse>('/flows/start', data),

  /** Resume a suspended flow */
  resume: (id: string, data?: ResumeFlowRequest) =>
    post<ResumeFlowResponse>(`/flows/${id}/resume`, data),

  /** Retry a failed node */
  retry: (id: string, nodeId: string) =>
    post<RetryFlowResponse>(`/flows/${id}/retry/${nodeId}`),

  /** Cancel a running flow */
  cancel: (id: string) =>
    post<CancelFlowResponse>(`/flows/${id}/cancel`),

  /** Get execution snapshots for a flow */
  snapshots: (id: string) =>
    get<FlowSnapshotsResponse>(`/flows/${id}/snapshots`),

  /** Get events for a flow */
  events: (id: string) =>
    get<FlowEventsResponse>(`/flows/${id}/events`),

  /** Get context diff for a specific node execution */
  diff: (id: string, nodeId: string) =>
    get<FlowDiffResponse>(`/flows/${id}/diff/${nodeId}`),
}
