import { get } from './client'
import type { MetricsSummaryResponse } from './types'

/**
 * Metrics API: monitoring dashboard data
 */
export const metricsApi = {
  /** Fetch aggregated metrics summary snapshot */
  summary: () => get<MetricsSummaryResponse>('/metrics/summary'),
}
