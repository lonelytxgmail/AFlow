<template>
  <div class="event-timeline">
    <!-- 类型筛选 tabs -->
    <div class="timeline-toolbar">
      <el-tabs v-model="activeTab" class="filter-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="流程" name="flow" />
        <el-tab-pane label="节点" name="node" />
        <el-tab-pane label="Agent" name="agent" />
        <el-tab-pane label="工具" name="tool" />
      </el-tabs>
      <el-input
        v-model="searchQuery"
        placeholder="搜索事件内容..."
        :prefix-icon="Search"
        clearable
        size="small"
        class="search-input"
      />
    </div>

    <!-- 事件列表 -->
    <div class="timeline-content">
      <template v-if="filteredEvents.length">
        <div
          v-for="event in filteredEvents"
          :key="eventKey(event)"
          class="event-item"
          @click="emit('select-event', event)"
        >
          <div class="event-row">
            <span class="event-timestamp">{{ formatTimestamp(event.timestamp) }}</span>
            <el-tag
              size="small"
              :type="getEventTagType(event.type)"
              class="event-badge"
              effect="light"
            >
              {{ event.type }}
            </el-tag>
            <span v-if="event.nodeId" class="event-node-id">{{ event.nodeId }}</span>
            <!-- Agent 事件展开按钮 -->
            <el-icon
              v-if="isAgentEvent(event.type)"
              class="expand-icon"
              @click.stop="toggleExpand(eventKey(event))"
            >
              <ArrowDown v-if="!expandedKeys.has(eventKey(event))" />
              <ArrowUp v-else />
            </el-icon>
          </div>

          <!-- Agent 事件展开详情 -->
          <div
            v-if="isAgentEvent(event.type) && expandedKeys.has(eventKey(event))"
            class="event-detail"
          >
            <div v-if="event.type === 'AGENT_THINK' && event.data?.thought" class="detail-section">
              <span class="detail-label">THINK:</span>
              <pre class="detail-content">{{ event.data.thought }}</pre>
            </div>
            <div v-if="event.type === 'AGENT_ACT' && event.data" class="detail-section">
              <span class="detail-label">ACT:</span>
              <pre class="detail-content">{{ formatActData(event.data) }}</pre>
            </div>
            <div v-if="event.type === 'AGENT_OBSERVE' && event.data?.result" class="detail-section">
              <span class="detail-label">OBSERVE:</span>
              <pre class="detail-content">{{ event.data.result }}</pre>
            </div>
            <!-- Fallback: show raw data if specific fields are not present -->
            <div v-if="event.data && !hasSpecificAgentData(event)" class="detail-section">
              <span class="detail-label">DATA:</span>
              <pre class="detail-content">{{ JSON.stringify(event.data, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else description="暂无匹配事件" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import type { FlowEvent, FlowEventType } from '@/types'

// --- Props & Emits ---

const props = defineProps<{
  events: FlowEvent[]
}>()

const emit = defineEmits<{
  'select-event': [event: FlowEvent]
}>()

// --- State ---

const activeTab = ref<string>('all')
const searchQuery = ref('')
const expandedKeys = ref<Set<string>>(new Set())

// --- Category Mapping ---

// Event types grouped by filter category tab
const CATEGORY_MAP: Record<string, string[]> = {
  flow: ['FLOW_STARTED', 'FLOW_COMPLETED', 'FLOW_FAILED', 'FLOW_CANCELLED'],
  node: ['NODE_ENTER', 'NODE_EXIT', 'NODE_ERROR', 'NODE_TIMEOUT', 'NODE_RETRY'],
  agent: ['AGENT_THINK', 'AGENT_ACT', 'AGENT_OBSERVE', 'AGENT_DONE', 'AGENT_TOOL_TIMEOUT', 'AGENT_TOOL_RATE_LIMITED', 'AGENT_TOKEN_PRUNE', 'AGENT_OUTPUT_VALIDATION_FAILED'],
  tool: ['TOOL_CALL', 'TOOL_RESULT'],
}

// --- Computed ---

/** Events in reverse chronological order, filtered by category and search */
const filteredEvents = computed(() => {
  let result = [...props.events].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  )

  // Filter by category tab
  if (activeTab.value !== 'all') {
    const allowedTypes = CATEGORY_MAP[activeTab.value] || []
    result = result.filter((e) => allowedTypes.includes(e.type))
  }

  // Filter by search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter((e) => {
      const typeMatch = e.type.toLowerCase().includes(query)
      const nodeMatch = e.nodeId?.toLowerCase().includes(query)
      const dataMatch = e.data ? JSON.stringify(e.data).toLowerCase().includes(query) : false
      return typeMatch || nodeMatch || dataMatch
    })
  }

  return result
})

// --- Methods ---

function handleTabChange() {
  // Reset expanded state on tab change for cleaner UX
  expandedKeys.value.clear()
}

function eventKey(event: FlowEvent): string {
  return `${event.type}-${event.nodeId || ''}-${event.timestamp}-${event.traceId || ''}`
}

function isAgentEvent(type: FlowEventType): boolean {
  return type === 'AGENT_THINK' || type === 'AGENT_ACT' || type === 'AGENT_OBSERVE'
}

function toggleExpand(key: string) {
  if (expandedKeys.value.has(key)) {
    expandedKeys.value.delete(key)
  } else {
    expandedKeys.value.add(key)
  }
}

function formatTimestamp(ts: string): string {
  try {
    const date = new Date(ts)
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    return ts
  }
}

function formatActData(data: Record<string, any>): string {
  if (data.tool && data.input) {
    return `Tool: ${data.tool}\nInput: ${JSON.stringify(data.input, null, 2)}`
  }
  return JSON.stringify(data, null, 2)
}

function hasSpecificAgentData(event: FlowEvent): boolean {
  if (!event.data) return false
  if (event.type === 'AGENT_THINK' && event.data.thought) return true
  if (event.type === 'AGENT_ACT' && (event.data.tool || event.data.input)) return true
  if (event.type === 'AGENT_OBSERVE' && event.data.result) return true
  return false
}

function getEventTagType(type: FlowEventType): '' | 'success' | 'warning' | 'info' | 'danger' {
  if (type.includes('ERROR') || type.includes('FAILED') || type.includes('TIMEOUT')) return 'danger'
  if (type.includes('COMPLETED') || type.includes('EXIT') || type.includes('DONE')) return 'success'
  if (type.includes('AGENT')) return 'warning'
  if (type.includes('TOOL')) return 'info'
  if (type.includes('ENTER') || type.includes('STARTED')) return ''
  return 'info'
}
</script>

<style scoped>
.event-timeline {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.timeline-toolbar {
  flex-shrink: 0;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.filter-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.filter-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

.filter-tabs :deep(.el-tabs__item) {
  font-size: 12px;
  height: 28px;
  line-height: 28px;
  padding: 0 10px;
}

.search-input {
  width: 100%;
}

.timeline-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.event-item {
  padding: 8px 12px;
  border-bottom: 1px solid #f2f3f5;
  cursor: pointer;
  transition: background-color 0.15s;
}

.event-item:hover {
  background: #f5f7fa;
}

.event-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}

.event-timestamp {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  flex-shrink: 0;
}

.event-badge {
  flex-shrink: 0;
}

.event-node-id {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.expand-icon {
  cursor: pointer;
  color: #909399;
  flex-shrink: 0;
  transition: color 0.2s;
}

.expand-icon:hover {
  color: #409eff;
}

.event-detail {
  margin-top: 8px;
  padding: 8px;
  background: #f9f9f9;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.detail-section {
  margin-bottom: 6px;
}

.detail-section:last-child {
  margin-bottom: 0;
}

.detail-label {
  font-size: 11px;
  font-weight: 600;
  color: #909399;
  display: block;
  margin-bottom: 4px;
}

.detail-content {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}
</style>
