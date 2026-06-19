<template>
  <div class="monitoring-page">
    <div class="header">
      <h2>监控面板 / Monitoring</h2>
      <div class="header-actions">
        <span v-if="lastUpdated" class="last-updated">
          Last updated: {{ lastUpdatedText }}
        </span>
        <el-button :icon="Refresh" :loading="refreshing" circle size="small" @click="manualRefresh" />
      </div>
    </div>

    <!-- 概览指标 -->
    <section class="metrics-section">
      <h3>概览 / Overview</h3>
      <el-row :gutter="16">
        <el-col :span="6">
          <MetricCard
            title="活跃流程数"
            :value="loading ? '--' : String(metrics.activeFlows)"
            color="#409eff"
          />
        </el-col>
        <el-col :span="6">
          <MetricCard
            title="今日执行数"
            :value="loading ? '--' : String(metrics.todayExecutions)"
            color="#67c23a"
          />
        </el-col>
        <el-col :span="6">
          <MetricCard
            title="成功率"
            :value="loading ? '--' : formatPercent(metrics.successRate)"
            color="#e6a23c"
          />
        </el-col>
        <el-col :span="6">
          <MetricCard
            title="平均耗时"
            :value="loading ? '--' : formatDuration(metrics.avgDurationMs)"
            color="#909399"
          />
        </el-col>
      </el-row>
    </section>

    <!-- Agent 指标 -->
    <section class="metrics-section">
      <h3>Agent 指标</h3>
      <el-row :gutter="16">
        <el-col :span="6">
          <MetricCard
            title="总 Token 消耗"
            :value="loading ? '--' : formatTokens(metrics.agent.totalTokens)"
            color="#9b59b6"
          />
        </el-col>
        <el-col :span="6">
          <MetricCard
            title="平均迭代轮次"
            :value="loading ? '--' : metrics.agent.avgIterations.toFixed(1)"
            color="#9b59b6"
          />
        </el-col>
        <el-col :span="12">
          <div class="tool-top5-card">
            <div class="tool-top5-header">Tool 调用 Top5</div>
            <div v-if="loading" class="tool-top5-placeholder">--</div>
            <ul v-else class="tool-top5-list">
              <li v-for="item in metrics.agent.topTools" :key="item.tool" class="tool-top5-item">
                <span class="tool-name">{{ item.tool }}</span>
                <span class="tool-count">{{ item.count }}</span>
              </li>
              <li v-if="metrics.agent.topTools.length === 0" class="tool-top5-empty">
                暂无数据
              </li>
            </ul>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- LLM 指标 -->
    <section class="metrics-section">
      <h3>LLM 指标</h3>
      <el-row :gutter="16">
        <el-col :span="8">
          <MetricCard
            title="调用次数"
            :value="loading ? '--' : String(metrics.llm.totalCalls)"
            color="#3498db"
          />
        </el-col>
        <el-col :span="8">
          <MetricCard
            title="P50 延迟"
            :value="loading ? '--' : formatDuration(metrics.llm.p50LatencyMs)"
            color="#3498db"
          />
        </el-col>
        <el-col :span="8">
          <MetricCard
            title="重试率"
            :value="loading ? '--' : formatPercent(metrics.llm.retryRate)"
            color="#3498db"
          />
        </el-col>
      </el-row>
    </section>

    <!-- 节点耗时分布 -->
    <section class="metrics-section">
      <h3>节点耗时分布</h3>
      <el-card shadow="hover">
        <div v-if="loading" class="chart-placeholder">
          <el-skeleton :rows="6" animated />
        </div>
        <div v-else-if="metrics.nodeDurations.length === 0" class="chart-placeholder">
          <el-empty description="暂无节点耗时数据" />
        </div>
        <DurationChart v-else :data="metrics.nodeDurations" />
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import MetricCard from '@/components/monitoring/MetricCard.vue'
import DurationChart from '@/components/monitoring/DurationChart.vue'
import { metricsApi } from '@/api'
import type { MetricsSummaryResponse } from '@/api/types'

// --- Constants ---

const POLL_INTERVAL_MS = 30_000

// --- State ---

const loading = ref(true)
const refreshing = ref(false)
const lastUpdated = ref<Date | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const metrics = reactive<MetricsSummaryResponse>({
  activeFlows: 0,
  todayExecutions: 0,
  successRate: 0,
  avgDurationMs: 0,
  agent: {
    totalTokens: 0,
    avgIterations: 0,
    topTools: [],
  },
  llm: {
    totalCalls: 0,
    p50LatencyMs: 0,
    retryRate: 0,
  },
  nodeDurations: [],
})

// --- Computed ---

const lastUpdatedText = computed(() => {
  if (!lastUpdated.value) return ''
  return lastUpdated.value.toLocaleTimeString()
})

// --- Formatting helpers ---

/** Format a ratio (0-1) as a percentage string */
function formatPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`
}

/** Format milliseconds to a human-readable duration */
function formatDuration(ms: number): string {
  if (ms < 1000) return `${Math.round(ms)}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60_000).toFixed(1)}min`
}

/** Format token count with K/M suffix for readability */
function formatTokens(count: number): string {
  if (count < 1_000) return String(count)
  if (count < 1_000_000) return `${(count / 1_000).toFixed(1)}K`
  return `${(count / 1_000_000).toFixed(2)}M`
}

// --- Data fetching ---

/** Initial fetch — shows loading skeleton */
async function fetchMetrics() {
  loading.value = true
  try {
    const data = await metricsApi.summary()
    Object.assign(metrics, data)
    lastUpdated.value = new Date()
  } catch {
    // Error is already shown via the global axios interceptor (ElMessage)
  } finally {
    loading.value = false
  }
}

/** Silent refresh — updates data without showing loading state (avoids flickering) */
async function silentRefresh() {
  try {
    const data = await metricsApi.summary()
    Object.assign(metrics, data)
    lastUpdated.value = new Date()
  } catch {
    // Silently ignore polling errors to avoid spamming error messages
  }
}

/** Manual refresh triggered by the user — shows a brief loading indicator on the button */
async function manualRefresh() {
  refreshing.value = true
  try {
    const data = await metricsApi.summary()
    Object.assign(metrics, data)
    lastUpdated.value = new Date()
  } catch {
    // Error shown by global interceptor
  } finally {
    refreshing.value = false
  }
}

// --- Polling lifecycle ---

function startPolling() {
  stopPolling()
  pollTimer = setInterval(silentRefresh, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  fetchMetrics()
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.monitoring-page {
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header h2 {
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-updated {
  font-size: 13px;
  color: #909399;
}

.metrics-section {
  margin-bottom: 24px;
}

.metrics-section h3 {
  margin-bottom: 12px;
  color: #303133;
}

.chart-placeholder {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Tool Top5 card styling */
.tool-top5-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #ebeef5;
  height: 100%;
  transition: box-shadow 0.3s ease, transform 0.2s ease;
}

.tool-top5-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.tool-top5-header {
  font-size: 14px;
  color: #909399;
  margin-bottom: 12px;
}

.tool-top5-placeholder {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.tool-top5-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.tool-top5-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
}

.tool-top5-item:last-child {
  border-bottom: none;
}

.tool-name {
  font-size: 13px;
  color: #606266;
}

.tool-count {
  font-size: 13px;
  font-weight: 600;
  color: #9b59b6;
}

.tool-top5-empty {
  font-size: 13px;
  color: #c0c4cc;
  padding: 6px 0;
}
</style>
