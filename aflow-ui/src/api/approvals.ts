import { get, post } from './client'
import type { ApprovalRequest, ApprovalListResponse } from './types'

/**
 * 审批 API 模块
 */
export const approvalApi = {
  /**
   * 获取审批列表
   */
  async list(status?: string): Promise<ApprovalListResponse> {
    const params: Record<string, unknown> = {}
    if (status) params.status = status
    return get<ApprovalListResponse>('/approvals', params)
  },

  /**
   * 获取审批详情
   */
  async getById(id: string): Promise<ApprovalRequest> {
    return get<ApprovalRequest>(`/approvals/${id}`)
  },

  /**
   * 批准审批
   */
  async approve(id: string, data?: Record<string, unknown>): Promise<ApprovalRequest> {
    return post<ApprovalRequest>(`/approvals/${id}/approve`, { data })
  },

  /**
   * 拒绝审批
   */
  async reject(id: string, reason?: string): Promise<ApprovalRequest> {
    return post<ApprovalRequest>(`/approvals/${id}/reject`, { reason })
  },
}
