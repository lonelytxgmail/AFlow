<template>
  <div class="instance-detail">
    <div class="instance-header">
      <el-page-header @back="$router.back()" content="实例详情" />
      <div class="header-actions">
        <el-tag :type="getStatusType(instance?.status)" size="large">
          {{ instance?.status || 'LOADING' }}
        </el-tag>
        <el-tag v-if="sseConnected" type="success" size="small" effect="light">
          <el-icon><Connection /></el-icon> SSE 已连接
        </el-tag>
      </div>
    </div>

    <EditorLayout>
      <!-- 左栏：DAG Viewer（只读，显示流程执行状态） -->
      <template #left>
        <div class="panel-section">
          <div class="panel-header">流程图 / DAG</div>
          <div class="dag-container">
            <DagViewer
              v-if="dsl"
              :dsl="dsl"
              :active-node="instance?.currentNodeId"
              :execution-path="instance?.executionPath"
              @node-click="loadSnapshotsForNode"
            />
          </div>
        </div>
      </template>

      <!-- 中栏：事件时间线 -->
      <template #center>
        <div class="center-panel">
          <!-- 调试控制栏 -->
          <div class="debug-toolbar">
            <el-button type="success" size="small" :disabled="instance?.status !== 'SUSPENDED'" @click="resumeFlow">
              <el-icon><VideoPlay /></el-icon> 恢复
            </el-button>
            <el-button type="primary" size="small" :disabled="!isDebugging" @click="stepFlow">
              <el-icon><Right /></el-icon> 单步
            </el-button>
            <el-button type="danger" size="small" :disabled="['COMPLETED', 'FAILED', 'CANCELLED'].includes(instance?.status)" @click="cancelFlow">
              <el-icon><CircleClose /></el-icon> 取消
            </el-button>
            <el-button type="warning" size="small" plain :disabled="instance?.status !== 'FAILED' || !instance?.currentNodeId" @click="retryFlow">
              <el-icon><RefreshRight /></el-icon> 重试节点
            </el-button>
          </div>

          <!-- 事件时间线 -->
          <EventTimeline
            :events="typedEvents"
            @select-event="handleSelectEvent"
          />
        </div>
      </template>

      <!-- 右栏：节点详情/快照 diff -->
      <template #right>
        <div class="right-panel">
          <!-- 调试状态信息 -->
          <div class="panel-section">
            <div class="panel-header">调试状态 / Debug</div>
            <div class="debug-state">
              <div class="debug-line"><span class="label">当前节点：</span>{{ instance?.currentNodeId || '-' }}</div>
              <div class="debug-line"><span class="label">断点数：</span>{{ breakpoints.length }}</div>
              <div class="debug-line"><span class="label">执行路径：</span>{{ instance?.executionPath?.length || 0 }} 步</div>
            </div>
          </div>

          <!-- 断点管理 -->
          <div class="panel-section">
            <div class="panel-header">断点管理</div>
            <div class="breakpoint-editor">
              <el-input v-model="newBreakpointNodeId" placeholder="节点 ID" size="small" />
              <el-button type="primary" size="small" @click="addBreakpoint">添加</el-button>
            </div>
            <div class="breakpoint-list">
              <el-tag v-for="bp in breakpoints" :key="bp" class="bp-tag" closable size="small" @close="removeBreakpoint(bp)">
                {{ bp }}
              </el-tag>
              <span v-if="!breakpoints.length" class="empty-hint">暂无断点</span>
            </div>
          </div>

          <!-- 节点快照 Diff -->
          <div class="panel-section" v-if="selectedNodeId">
            <div class="panel-header">快照 Diff / {{ selectedNodeId }}</div>
            <SnapshotDiff
              :before="snapshotBefore"
              :after="snapshotAfter"
              :node-id="selectedNodeId"
            />
          </div>

          <!-- 上下文变量 -->
          <div class="panel-section">
            <div class="panel-header">上下文变量 / Context</div>
            <vue-json-pretty :data="instance?.variables || {}" :deep="2" />
            <el-divider />
            <el-input v-model="contextDraft" type="textarea" :rows="4" placeholder='{"debugFlag": true}' />
            <div class="context-actions">
              <el-button type="primary" size="small" :disabled="instance?.status !== 'SUSPENDED'" @click="updateContext">
                更新上下文
              </el-button>
            </div>
          </div>
        </div>
      </template>

      <!-- 底部状态栏 -->
      <template #statusbar>
        <span>实例: {{ instanceId }}</span>
        <el-divider direction="vertical" />
        <span>状态: {{ instance?.status || '-' }}</span>
        <el-divider direction="vertical" />
        <span>事件: {{ events.length }}</span>
        <el-divider direction="vertical" />
        <span>SSE: {{ sseConnected ? '已连接' : '未连接' }}</span>
      </template>
    </EditorLayout>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoPlay, Right, CircleClose, RefreshRight, Connection } from '@element-plus/icons-vue'
import VueJsonPretty from 'vue-json-pretty'
import 'vue-json-pretty/lib/styles.css'
import { flowApi, definitionApi, debugApi } from '../api'
import { useSseStream, type UseSseStreamReturn } from '../composables/useSseStream'
import type { FlowEvent } from '../types'
import DagViewer from '../components/DagViewer.vue'
import EditorLayout from '../layouts/EditorLayout.vue'
import EventTimeline from '../components/debug/EventTimeline.vue'
import SnapshotDiff from '../components/debug/SnapshotDiff.vue'

const route = useRoute()
const instanceId = route.params.id as string

const instance = ref<any>(null)
const dsl = ref<any>(null)
const events = ref<any[]>([])
const breakpoints = ref<string[]>([])
const selectedNodeId = ref<string | null>(null)
const selectedSnapshots = ref<any[]>([])
const contextDraft = ref('{\n}')
const newBreakpointNodeId = ref('')

let sseStream: UseSseStreamReturn | null = null
let refreshTimer: ReturnType<typeof setInterval> | null = null

const isDebugging = computed(() => instance.value?.debugMode)
const sseConnected = computed(() => sseStream?.isConnected.value ?? false)

/**
 * Extract the "BEFORE" snapshot context data for the selected node.
 * Used by the SnapshotDiff component.
 */
const snapshotBefore = computed<Record<string, unknown>>(() => {
  const beforeSnapshot = selectedSnapshots.value.find(
    (s: any) => s.phase === 'BEFORE'
  )
  if (!beforeSnapshot) return {}
  if (typeof beforeSnapshot.contextData === 'string') {
    try { return JSON.parse(beforeSnapshot.contextData) } catch { return {} }
  }
  return beforeSnapshot.contextData || beforeSnapshot.variables || {}
})

/**
 * Extract the "AFTER" snapshot context data for the selected node.
 * Used by the SnapshotDiff component.
 */
const snapshotAfter = computed<Record<string, unknown>>(() => {
  const afterSnapshot = selectedSnapshots.value.find(
    (s: any) => s.phase === 'AFTER'
  )
  if (!afterSnapshot) return {}
  if (typeof afterSnapshot.contextData === 'string') {
    try { return JSON.parse(afterSnapshot.contextData) } catch { return {} }
  }
  return afterSnapshot.contextData || afterSnapshot.variables || {}
})

/**
 * Adapt raw event objects to the FlowEvent interface.
 * API may return `eventType` field; the FlowEvent type uses `type`.
 */
const typedEvents = computed<FlowEvent[]>(() => {
  return events.value.map((e: any) => ({
    type: e.type || e.eventType,
    flowId: e.flowId || instanceId,
    nodeId: e.nodeId,
    timestamp: e.timestamp || e.createdAt || '',
    traceId: e.traceId,
    data: e.data,
  }))
})

/** Handle event selection from the timeline */
const handleSelectEvent = (event: FlowEvent) => {
  if (event.nodeId) {
    loadSnapshotsForNode(event.nodeId)
  }
}

/** 加载实例详情 + DSL + 事件列表 */
const loadData = async () => {
  try {
    const ctx = await flowApi.get(instanceId)
    instance.value = ctx

    const defRes = await definitionApi.get(ctx.flowDefinitionId)
    dsl.value = JSON.parse(defRes.dslContent)

    events.value = await flowApi.events(instanceId)
    breakpoints.value = await debugApi.getBreakpoints(instanceId).catch(() => [])
    contextDraft.value = JSON.stringify(instance.value?.variables || {}, null, 2)
    if (selectedNodeId.value) {
      selectedSnapshots.value = await flowApi.diff(instanceId, selectedNodeId.value)
    }
  } catch (e) {
    console.error('加载实例数据失败', e)
  }
}

/**
 * 建立 SSE 连接，订阅流程执行的实时状态更新。
 * 使用 useSseStream composable，自动处理重连和组件卸载清理。
 */
const connectSSE = () => {
  if (sseStream) {
    sseStream.disconnect()
    sseStream = null
  }

  sseStream = useSseStream({
    url: `/api/v1/flows/${instanceId}/stream`,
    handlers: {
      NODE_ENTER: (data: any) => {
        if (!instance.value) return
        instance.value.currentNodeId = data.currentNodeId
        instance.value.status = data.status
        instance.value.executionPath = data.executionPath
        if (data.variables) instance.value.variables = data.variables
      },
      NODE_EXIT: (data: any) => {
        if (!instance.value) return
        instance.value.currentNodeId = data.currentNodeId
        instance.value.status = data.status
        instance.value.executionPath = data.executionPath
        if (data.variables) instance.value.variables = data.variables
      },
      NODE_ERROR: (data: any) => {
        if (!instance.value) return
        instance.value.currentNodeId = data.currentNodeId
        instance.value.status = data.status
        instance.value.executionPath = data.executionPath
      },
      FLOW_COMPLETED: () => {
        loadData()
        sseStream?.disconnect()
        sseStream = null
        ElMessage.success('流程执行完成')
      },
      FLOW_FAILED: () => {
        loadData()
        sseStream?.disconnect()
        sseStream = null
        ElMessage.error('流程执行失败')
      },
      FLOW_CANCELLED: () => {
        loadData()
        sseStream?.disconnect()
        sseStream = null
        ElMessage.warning('流程已取消')
      },
    },
    autoConnect: true,
    maxRetries: 8,
  })
}

const getStatusType = (status: string | undefined) => {
  switch (status) {
    case 'RUNNING': return 'primary'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'SUSPENDED': return 'warning'
    default: return 'info'
  }
}

const resumeFlow = async () => {
  await flowApi.resume(instanceId, {})
  ElMessage.success('已恢复')
  await loadData()
  connectSSE()
}

const stepFlow = async () => {
  await debugApi.step(instanceId)
  ElMessage.success('单步执行成功')
  await loadData()
}

const cancelFlow = async () => {
  await flowApi.cancel(instanceId)
  ElMessage.success('已取消')
  await loadData()
}

const retryFlow = async () => {
  if (!instance.value?.currentNodeId) return
  await flowApi.retry(instanceId, instance.value.currentNodeId)
  ElMessage.success('已发起重试')
  await loadData()
  connectSSE()
}

const updateContext = async () => {
  try {
    const variables = JSON.parse(contextDraft.value || '{}')
    await debugApi.updateContext(instanceId, variables)
    ElMessage.success('上下文已更新')
    await loadData()
  } catch (e) {
    ElMessage.error('上下文 JSON 非法或更新失败')
  }
}

const removeBreakpoint = async (nodeId: string) => {
  await debugApi.removeBreakpoint(instanceId, nodeId)
  breakpoints.value = breakpoints.value.filter(item => item !== nodeId)
  ElMessage.success('断点已移除')
}

const addBreakpoint = async () => {
  const nodeId = newBreakpointNodeId.value.trim()
  if (!nodeId) {
    ElMessage.warning('请输入节点 ID')
    return
  }
  await debugApi.addBreakpoint(instanceId, nodeId)
  if (!breakpoints.value.includes(nodeId)) {
    breakpoints.value.push(nodeId)
  }
  newBreakpointNodeId.value = ''
  ElMessage.success('断点已添加')
}

const loadSnapshotsForNode = async (nodeId: string) => {
  selectedNodeId.value = nodeId
  if (!nodeId) {
    selectedSnapshots.value = []
    return
  }
  selectedSnapshots.value = await flowApi.diff(instanceId, nodeId)
}

onMounted(async () => {
  await loadData()
  if (instance.value && ['RUNNING', 'SUSPENDED'].includes(instance.value.status)) {
    connectSSE()
  }
  refreshTimer = setInterval(() => {
    if (!sseConnected.value && instance.value && ['RUNNING', 'SUSPENDED'].includes(instance.value.status)) {
      loadData()
    }
  }, 3000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (sseStream) {
    sseStream.disconnect()
    sseStream = null
  }
})
</script>

<style scoped>
.instance-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.instance-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  flex-shrink: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 左栏：DAG */
.panel-section {
  padding: 12px;
}

.panel-header {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.dag-container {
  height: calc(100vh - 160px);
  min-height: 400px;
}

/* 中栏：事件时间线 */
.center-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.debug-toolbar {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
  flex-wrap: wrap;
}

/* 右栏：节点详情 */
.right-panel {
  height: 100%;
  overflow-y: auto;
}

.debug-state {
  display: grid;
  gap: 6px;
  margin-bottom: 8px;
}

.debug-line {
  font-size: 12px;
  color: #606266;
}

.debug-line .label {
  color: #909399;
}

.breakpoint-editor {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.breakpoint-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.bp-tag {
  margin-bottom: 4px;
}

.empty-hint {
  font-size: 12px;
  color: #909399;
}

.context-actions {
  margin-top: 8px;
}
</style>
