<template>
  <div class="agent-trace">
    <!-- 头部信息 -->
    <div class="trace-header">
      <span class="trace-title">Agent 轨迹</span>
      <el-tag v-if="nodeId" size="small" effect="plain">{{ nodeId }}</el-tag>
      <span class="round-count">{{ rounds.length }} 轮迭代</span>
    </div>

    <!-- 迭代轮次列表 -->
    <div class="trace-rounds">
      <template v-if="rounds.length > 0">
        <el-collapse v-model="expandedRounds">
          <el-collapse-item
            v-for="(round, idx) in rounds"
            :key="idx"
            :name="idx"
            class="round-item"
          >
            <template #title>
              <div class="round-title">
                <span class="round-number">#{{ idx + 1 }}</span>
                <el-tag v-if="round.think" size="small" type="info" effect="light">THINK</el-tag>
                <el-tag v-if="round.acts.length" size="small" type="success" effect="light">
                  ACT ×{{ round.acts.length }}
                </el-tag>
                <el-tag v-if="round.observes.length" size="small" effect="light">
                  OBSERVE ×{{ round.observes.length }}
                </el-tag>
              </div>
            </template>

            <!-- THINK 区域 -->
            <div v-if="round.think" class="step-block step-think">
              <div class="step-label">
                <el-icon><ChatDotRound /></el-icon>
                思考内容 / THINK
              </div>
              <pre class="step-content">{{ getThinkContent(round.think) }}</pre>
            </div>

            <!-- ACT/OBSERVE 配对 -->
            <template v-for="(act, actIdx) in round.acts" :key="`act-${actIdx}`">
              <div class="step-block step-act">
                <div class="step-label">
                  <el-icon><Promotion /></el-icon>
                  工具调用 / ACT
                </div>
                <div class="step-content">
                  <div class="tool-info">
                    <span class="tool-name">{{ getToolName(act) }}</span>
                  </div>
                  <pre class="tool-input">{{ getToolInput(act) }}</pre>
                </div>
              </div>

              <!-- 对应的 OBSERVE -->
              <div
                v-if="round.observes[actIdx]"
                class="step-block step-observe"
              >
                <div class="step-label">
                  <el-icon><View /></el-icon>
                  工具结果 / OBSERVE
                </div>
                <pre class="step-content">{{ getObserveContent(round.observes[actIdx]) }}</pre>
              </div>
            </template>

            <!-- 额外的 OBSERVE（如果 observe 比 act 多） -->
            <template v-for="(obs, obsIdx) in round.observes.slice(round.acts.length)" :key="`extra-obs-${obsIdx}`">
              <div class="step-block step-observe">
                <div class="step-label">
                  <el-icon><View /></el-icon>
                  工具结果 / OBSERVE
                </div>
                <pre class="step-content">{{ getObserveContent(obs) }}</pre>
              </div>
            </template>
          </el-collapse-item>
        </el-collapse>
      </template>

      <el-empty v-else description="暂无 Agent 轨迹事件" :image-size="60" />
    </div>

    <!-- 底部统计 -->
    <div v-if="rounds.length > 0" class="trace-footer">
      <div class="stat-item">
        <span class="stat-label">总 Token</span>
        <span class="stat-value">{{ stats.totalTokens }}</span>
      </div>
      <el-divider direction="vertical" />
      <div class="stat-item">
        <span class="stat-label">总 LLM 调用</span>
        <span class="stat-value">{{ stats.totalLlmCalls }}</span>
      </div>
      <el-divider direction="vertical" />
      <div class="stat-item">
        <span class="stat-label">总工具调用</span>
        <span class="stat-value">{{ stats.totalToolCalls }}</span>
      </div>
      <el-divider direction="vertical" />
      <div class="stat-item">
        <span class="stat-label">总耗时</span>
        <span class="stat-value">{{ formatDuration(stats.totalDurationMs) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChatDotRound, Promotion, View } from '@element-plus/icons-vue'
import type { FlowEvent } from '@/types'

// --- Types ---

interface AgentRound {
  think: FlowEvent | null
  acts: FlowEvent[]
  observes: FlowEvent[]
}

interface AgentStats {
  totalTokens: number
  totalLlmCalls: number
  totalToolCalls: number
  totalDurationMs: number
}

// --- Props ---

const props = defineProps<{
  events: FlowEvent[]
  nodeId?: string
}>()

// --- State ---

const expandedRounds = ref<number[]>([0])

// --- Computed ---

/** Filter events to agent-related events for the specified nodeId */
const agentEvents = computed<FlowEvent[]>(() => {
  const agentTypes = ['AGENT_THINK', 'AGENT_ACT', 'AGENT_OBSERVE', 'AGENT_DONE']
  let filtered = props.events.filter((e) => agentTypes.includes(e.type))

  // If nodeId is provided, filter by nodeId
  if (props.nodeId) {
    filtered = filtered.filter((e) => e.nodeId === props.nodeId)
  }

  // Sort by timestamp ascending
  return [...filtered].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  )
})

/** Group agent events into iterations/rounds */
const rounds = computed<AgentRound[]>(() => {
  const result: AgentRound[] = []
  let currentRound: AgentRound | null = null

  for (const event of agentEvents.value) {
    if (event.type === 'AGENT_THINK') {
      // A THINK event starts a new round
      if (currentRound) {
        result.push(currentRound)
      }
      currentRound = { think: event, acts: [], observes: [] }
    } else if (event.type === 'AGENT_ACT') {
      if (!currentRound) {
        // ACT without a preceding THINK — start a round without think
        currentRound = { think: null, acts: [], observes: [] }
      }
      currentRound.acts.push(event)
    } else if (event.type === 'AGENT_OBSERVE') {
      if (!currentRound) {
        currentRound = { think: null, acts: [], observes: [] }
      }
      currentRound.observes.push(event)
    }
    // AGENT_DONE is ignored for grouping but counted in stats
  }

  // Push the last round
  if (currentRound) {
    result.push(currentRound)
  }

  return result
})

/** Aggregate statistics from agent events */
const stats = computed<AgentStats>(() => {
  let totalTokens = 0
  let totalLlmCalls = 0
  let totalToolCalls = 0
  let totalDurationMs = 0

  // Count LLM calls = number of THINK events (each THINK implies an LLM call)
  totalLlmCalls = agentEvents.value.filter((e) => e.type === 'AGENT_THINK').length

  // Count tool calls = number of ACT events
  totalToolCalls = agentEvents.value.filter((e) => e.type === 'AGENT_ACT').length

  // Accumulate tokens from event data
  for (const event of agentEvents.value) {
    if (event.data?.tokens) {
      totalTokens += Number(event.data.tokens) || 0
    }
    if (event.data?.inputTokens) {
      totalTokens += Number(event.data.inputTokens) || 0
    }
    if (event.data?.outputTokens) {
      totalTokens += Number(event.data.outputTokens) || 0
    }
    if (event.data?.tokenUsage) {
      totalTokens += Number(event.data.tokenUsage) || 0
    }
  }

  // Calculate total duration from first to last event
  if (agentEvents.value.length >= 2) {
    const first = new Date(agentEvents.value[0].timestamp).getTime()
    const last = new Date(agentEvents.value[agentEvents.value.length - 1].timestamp).getTime()
    totalDurationMs = last - first
  }

  return { totalTokens, totalLlmCalls, totalToolCalls, totalDurationMs }
})

// --- Methods ---

function getThinkContent(event: FlowEvent): string {
  if (!event.data) return '(无内容)'
  if (event.data.thought) return event.data.thought
  if (event.data.content) return event.data.content
  if (event.data.message) return event.data.message
  return JSON.stringify(event.data, null, 2)
}

function getToolName(event: FlowEvent): string {
  if (!event.data) return '(未知工具)'
  return event.data.tool || event.data.toolName || event.data.name || '(未知工具)'
}

function getToolInput(event: FlowEvent): string {
  if (!event.data) return ''
  const input = event.data.input || event.data.arguments || event.data.params
  if (!input) return ''
  if (typeof input === 'string') return input
  return JSON.stringify(input, null, 2)
}

function getObserveContent(event: FlowEvent): string {
  if (!event.data) return '(无结果)'
  if (event.data.result) {
    return typeof event.data.result === 'string'
      ? event.data.result
      : JSON.stringify(event.data.result, null, 2)
  }
  if (event.data.output) {
    return typeof event.data.output === 'string'
      ? event.data.output
      : JSON.stringify(event.data.output, null, 2)
  }
  return JSON.stringify(event.data, null, 2)
}

function formatDuration(ms: number): string {
  if (ms <= 0) return '0ms'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const mins = Math.floor(ms / 60000)
  const secs = Math.round((ms % 60000) / 1000)
  return `${mins}m ${secs}s`
}
</script>

<style scoped>
.agent-trace {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* Header */
.trace-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
}

.trace-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.round-count {
  margin-left: auto;
  font-size: 11px;
  color: #909399;
}

/* Rounds area */
.trace-rounds {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
}

.round-item {
  margin-bottom: 4px;
}

.round-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.round-number {
  font-size: 13px;
  font-weight: 700;
  color: #409eff;
  min-width: 28px;
}

/* Step blocks */
.step-block {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid transparent;
}

.step-think {
  background-color: #ecf5ff;
  border-color: #d9ecff;
}

.step-act {
  background-color: #f0f9eb;
  border-color: #e1f3d8;
}

.step-observe {
  background-color: #f9f9f9;
  border-color: #ebeef5;
}

.step-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}

.step-think .step-label {
  color: #409eff;
}

.step-act .step-label {
  color: #67c23a;
}

.step-observe .step-label {
  color: #909399;
}

.step-content {
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  max-height: 300px;
  overflow-y: auto;
}

/* Tool info in ACT */
.tool-info {
  margin-bottom: 4px;
}

.tool-name {
  font-weight: 600;
  color: #303133;
  font-size: 12px;
  background: rgba(103, 194, 58, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
}

.tool-input {
  margin: 4px 0 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* Footer statistics */
.trace-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 10px 12px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-label {
  font-size: 11px;
  color: #909399;
}

.stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #303133;
}

/* Collapse overrides */
.trace-rounds :deep(.el-collapse) {
  border: none;
}

.trace-rounds :deep(.el-collapse-item__header) {
  height: 40px;
  line-height: 40px;
  font-size: 13px;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
}

.trace-rounds :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid #f2f3f5;
}

.trace-rounds :deep(.el-collapse-item__content) {
  padding: 8px 4px;
}
</style>
