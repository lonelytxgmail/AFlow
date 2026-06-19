import { get, post, put, del } from './client'
import type {
  GetBreakpointsResponse,
  StepResponse,
  UpdateContextResponse,
} from './types'

/**
 * Debug operations: breakpoints, step, updateContext
 */
export const debugApi = {
  /** Add a breakpoint to a node */
  addBreakpoint: (flowId: string, nodeId: string) =>
    post<void>(`/debug/${flowId}/breakpoint/${nodeId}`),

  /** Remove a breakpoint from a node */
  removeBreakpoint: (flowId: string, nodeId: string) =>
    del<void>(`/debug/${flowId}/breakpoint/${nodeId}`),

  /** Get all breakpoints for a flow */
  getBreakpoints: (flowId: string) =>
    get<GetBreakpointsResponse>(`/debug/${flowId}/breakpoints`),

  /** Step to the next node (when paused at breakpoint) */
  step: (flowId: string) =>
    post<StepResponse>(`/debug/${flowId}/step`),

  /** Update flow context variables at runtime */
  updateContext: (flowId: string, variables: Record<string, unknown>) =>
    put<UpdateContextResponse>(`/debug/${flowId}/context`, variables),
}
