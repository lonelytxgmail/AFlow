<template>
  <div class="dag-editor" @drop="onDrop" @dragover.prevent @click="onContainerClick">
    <VueFlow
      v-model="elements"
      :default-zoom="1"
      :min-zoom="0.2"
      :max-zoom="4"
      :fit-view-on-init="true"
      :snap-to-grid="true"
      :snap-grid="[15, 15]"
      delete-key-code=""
      @connect="onConnect"
      @edges-change="onEdgesChange"
      @nodes-change="onNodesChange"
      @node-click="onNodeClick"
      @edge-click="onEdgeClick"
      @pane-click="onPaneClick"
    >
      <Background pattern-color="#aaa" gap="20" />
      <Controls />
      <MiniMap position="bottom-right" />
      <template #node-custom="props">
        <CustomNode :data="props.data" :selected="props.selected" />
      </template>
      <template #edge-normal="edgeProps">
        <NormalEdge v-bind="edgeProps" />
      </template>
      <template #edge-error="edgeProps">
        <ErrorEdge v-bind="edgeProps" />
      </template>
      <template #edge-conditional="edgeProps">
        <ConditionalEdge v-bind="edgeProps" />
      </template>
    </VueFlow>

    <!-- 边类型选择弹出 -->
    <EdgeTypeSelector
      :visible="edgeTypeSelectorVisible"
      :position="edgeTypeSelectorPosition"
      @select="onEdgeTypeSelected"
      @cancel="onEdgeTypeCancelled"
    />

    <!-- 边条件编辑器浮层 -->
    <div class="edge-editor-overlay" v-if="selectedEdge" :style="edgeEditorStyle">
      <EdgeConditionEditor
        :visible="true"
        :edge="selectedEdge"
        @update:condition="onConditionUpdate"
        @close="selectedEdge = null"
      />
    </div>

    <!-- 自动布局按钮 -->
    <div class="auto-layout-btn">
      <el-tooltip content="自动布局 (dagre)" placement="left">
        <el-button circle size="small" @click="onAutoLayout">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="7" height="7"/>
            <rect x="14" y="3" width="7" height="7"/>
            <rect x="14" y="14" width="7" height="7"/>
            <rect x="3" y="14" width="7" height="7"/>
          </svg>
        </el-button>
      </el-tooltip>
    </div>

    <!-- Undo/Redo 按钮 -->
    <div class="undo-redo-btns">
      <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
        <el-button circle size="small" :disabled="!canUndo" @click="onUndo">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="1 4 1 10 7 10"/>
            <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
          </svg>
        </el-button>
      </el-tooltip>
      <el-tooltip content="重做 (Ctrl+Shift+Z)" placement="bottom">
        <el-button circle size="small" :disabled="!canRedo" @click="onRedo">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="23 4 23 10 17 10"/>
            <path d="M20.49 15a9 9 0 1 1-2.13-9.36L23 10"/>
          </svg>
        </el-button>
      </el-tooltip>
    </div>

    <!-- 画布提示 -->
    <div class="canvas-hint" v-if="showHint">
      <span>滚轮缩放 | 拖拽平移 | 拖拽节点连线 | 点击边编辑条件</span>
      <el-button link size="small" @click="showHint = false">关闭</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import CustomNode from './dag/CustomNode.vue'
import NormalEdge from './dag/NormalEdge.vue'
import ErrorEdge from './dag/ErrorEdge.vue'
import ConditionalEdge from './dag/ConditionalEdge.vue'
import EdgeTypeSelector from './dag/EdgeTypeSelector.vue'
import EdgeConditionEditor from './EdgeConditionEditor.vue'
import { v4 as uuidv4 } from 'uuid'
import type { EdgeType } from '@/types'
import { useAutoLayout } from '@/composables/useAutoLayout'
import { useHistory } from '@/composables/useHistory'

interface DslModel {
  nodes: any[]
  edges: any[]
}

const props = defineProps<{
  modelValue: DslModel
}>()

const emit = defineEmits<{
  'update:modelValue': [value: DslModel]
  'node-select': [nodeId: string | null]
}>()

const { addEdges, fitView, screenToFlowCoordinate } = useVueFlow()
const elements = ref<any[]>([])
const selectedEdge = ref<{ from: string; to: string; condition: string } | null>(null)
const showHint = ref(true)
const edgeEditorStyle = ref({ top: '16px', right: '16px' })

// Edge type selector state
const edgeTypeSelectorVisible = ref(false)
const edgeTypeSelectorPosition = ref({ x: 0, y: 0 })
const pendingConnection = ref<{ source: string; target: string; sourceHandle?: string | null } | null>(null)

// Undo/Redo history
const { pushState, undo, redo, canUndo, canRedo } = useHistory<DslModel>({ maxSize: 50 })
let isUndoRedoAction = false
let isSyncing = false

const parseDslToElements = (dsl: DslModel) => {
  if (isSyncing) return
  isSyncing = true

  const nodes: any[] = []
  const edges: any[] = []

  if (dsl && dsl.nodes) {
    dsl.nodes.forEach((n: any, index: number) => {
      const x = n.ui?.x ?? (index * 250 % 800)
      const y = n.ui?.y ?? (Math.floor(index * 250 / 800) * 150 + 50)

      nodes.push({
        id: n.id,
        type: 'custom',
        position: { x, y },
        data: {
          type: n.type,
          label: n.name || n.id,
          config: n.config,
          output: n.output || '',
          breakpoint: !!n.breakpoint
        }
      })
    })
  }

  if (dsl && dsl.edges) {
    dsl.edges.forEach((e: any) => {
      const edgeType: EdgeType = e.type || 'normal'
      edges.push({
        id: `e-${e.from}-${e.to}`,
        source: e.from,
        target: e.to,
        type: edgeType,
        label: e.condition ? truncateCondition(e.condition) : '',
        updatable: true,
        data: { condition: e.condition || '', edgeType }
      })
    })
  }

  elements.value = [...nodes, ...edges]

  nextTick(() => {
    isSyncing = false
  })
}

function truncateCondition(cond: string): string {
  return cond.length > 20 ? cond.substring(0, 18) + '...' : cond
}

const syncElementsToDsl = () => {
  if (isSyncing) return
  isSyncing = true

  const newNodes: any[] = []
  const newEdges: any[] = []

  elements.value.forEach((el: any) => {
    if (el.source && el.target) {
      newEdges.push({
        from: el.source,
        to: el.target,
        type: el.data?.edgeType || el.type || 'normal',
        condition: el.data?.condition || undefined
      })
    } else {
      newNodes.push({
        id: el.id,
        type: el.data.type,
        name: el.data.label,
        config: el.data.config || {},
        output: el.data.output || undefined,
        breakpoint: !!el.data.breakpoint,
        ui: { x: el.position.x, y: el.position.y }
      })
    }
  })

  const newDsl: DslModel = { nodes: newNodes, edges: newEdges }
  emit('update:modelValue', newDsl)

  // Push to history unless this sync was triggered by an undo/redo action
  if (!isUndoRedoAction) {
    pushState(newDsl)
  }

  nextTick(() => { isSyncing = false })
}

watch(() => props.modelValue, (newVal) => {
  parseDslToElements(newVal)
}, { deep: true, immediate: true })

const onConnect = (connection: any) => {
  // If connection originates from the "error" handle, auto-set type to "error"
  if (connection.sourceHandle === 'error') {
    addEdges([{
      ...connection,
      type: 'error',
      data: { condition: '', edgeType: 'error' }
    }])
    syncElementsToDsl()
    return
  }

  // Otherwise, show the edge type selector popup
  pendingConnection.value = {
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle
  }

  // Position the selector near the midpoint of source and target nodes
  const sourceNode = elements.value.find((el: any) => el.id === connection.source)
  const targetNode = elements.value.find((el: any) => el.id === connection.target)
  if (sourceNode && targetNode) {
    const midX = (sourceNode.position.x + targetNode.position.x) / 2 + 75
    const midY = (sourceNode.position.y + targetNode.position.y) / 2
    edgeTypeSelectorPosition.value = { x: midX, y: midY }
  } else {
    // Fallback: center of the canvas
    edgeTypeSelectorPosition.value = { x: 300, y: 200 }
  }

  edgeTypeSelectorVisible.value = true
}

const onEdgeTypeSelected = (type: EdgeType) => {
  if (!pendingConnection.value) return

  addEdges([{
    source: pendingConnection.value.source,
    target: pendingConnection.value.target,
    sourceHandle: pendingConnection.value.sourceHandle,
    type,
    data: { condition: '', edgeType: type }
  }])

  edgeTypeSelectorVisible.value = false
  pendingConnection.value = null
  syncElementsToDsl()
}

const onEdgeTypeCancelled = () => {
  edgeTypeSelectorVisible.value = false
  pendingConnection.value = null
}

const onContainerClick = () => {
  if (edgeTypeSelectorVisible.value) {
    onEdgeTypeCancelled()
  }
}

const onEdgesChange = (changes: any[]) => {
  if (changes.some((c: any) => c.type === 'remove' || c.type === 'add')) {
    syncElementsToDsl()
  }
}

const onNodesChange = (changes: any[]) => {
  if (changes.some((c: any) => c.type === 'remove' || (c.type === 'position' && c.dragging === false))) {
    syncElementsToDsl()
  }
}

const onNodeClick = ({ node }: { node: any }) => {
  selectedEdge.value = null
  emit('node-select', node.id)
}

const onEdgeClick = ({ edge }: { edge: any }) => {
  selectedEdge.value = {
    from: edge.source,
    to: edge.target,
    condition: edge.data?.condition || ''
  }
}

const onConditionUpdate = (newCondition: string) => {
  if (!selectedEdge.value) return
  const edgeId = `e-${selectedEdge.value.from}-${selectedEdge.value.to}`
  const el = elements.value.find((e: any) => e.id === edgeId)
  if (el) {
    el.data = { ...el.data, condition: newCondition }
    el.label = newCondition ? truncateCondition(newCondition) : ''
  }
  syncElementsToDsl()
}

const onPaneClick = () => {
  selectedEdge.value = null
  emit('node-select', null)
}

const onDrop = (event: DragEvent) => {
  const rawPayload = event.dataTransfer?.getData('application/vueflow')
  if (!rawPayload) return

  let payload: any
  try {
    payload = JSON.parse(rawPayload)
  } catch {
    payload = { type: rawPayload, name: `${rawPayload} Node`, config: {} }
  }
  const nodeType = payload.type
  if (!nodeType) return

  const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  const newNodeId = `node_${uuidv4().substring(0, 8)}`

  elements.value.push({
    id: newNodeId,
    type: 'custom',
    position,
    data: {
      type: nodeType,
      label: payload.name || `${nodeType} Node`,
      config: payload.config || {},
      output: payload.output || '',
      breakpoint: !!payload.breakpoint
    }
  })

  syncElementsToDsl()
  emit('node-select', newNodeId)
}

const { applyAutoLayout } = useAutoLayout()

const onAutoLayout = () => {
  const nodes = elements.value.filter((el: any) => !el.source && !el.target)
  const edges = elements.value.filter((el: any) => el.source && el.target)

  const layoutedNodes = applyAutoLayout(nodes, edges, { direction: 'TB' })

  // Update elements with new positions
  elements.value = [...layoutedNodes, ...edges]

  // Sync to DSL so positions persist
  syncElementsToDsl()

  // Fit the view to show all nodes after layout
  nextTick(() => fitView())
}

// --- Undo/Redo ---

const onUndo = () => {
  const state = undo()
  if (state) {
    isUndoRedoAction = true
    emit('update:modelValue', state)
    nextTick(() => { isUndoRedoAction = false })
  }
}

const onRedo = () => {
  const state = redo()
  if (state) {
    isUndoRedoAction = true
    emit('update:modelValue', state)
    nextTick(() => { isUndoRedoAction = false })
  }
}

// Keyboard shortcuts: Ctrl+Z (undo), Ctrl+Shift+Z (redo)
const onKeydown = (e: KeyboardEvent) => {
  // Skip if the user is typing in an input, textarea, or contenteditable element
  const target = e.target as HTMLElement
  const isInputFocused = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable
  if (isInputFocused) return

  const isMeta = e.ctrlKey || e.metaKey
  if (isMeta && e.key === 'z' && !e.shiftKey) {
    e.preventDefault()
    onUndo()
  } else if (isMeta && e.key === 'z' && e.shiftKey) {
    e.preventDefault()
    onRedo()
  } else if (isMeta && e.key === 'y') {
    e.preventDefault()
    onRedo()
  }
}

// Expose undo/redo for parent components
defineExpose({ onUndo, onRedo, canUndo, canRedo })

onMounted(() => {
  setTimeout(() => fitView(), 50)
  // Push initial state to history
  if (props.modelValue) {
    pushState(props.modelValue)
  }
  // Register keyboard shortcuts
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.dag-editor {
  width: 100%;
  height: 100%;
  background: #fcfcfc;
  position: relative;
}
.edge-editor-overlay {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 100;
}
.canvas-hint {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 50;
}
.canvas-hint .el-button { color: #fff; }

.auto-layout-btn {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 50;
}
.auto-layout-btn .el-button {
  background: #fff;
  border: 1px solid #dcdfe6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.auto-layout-btn .el-button:hover {
  border-color: #409EFF;
  color: #409EFF;
}

.undo-redo-btns {
  position: absolute;
  top: 12px;
  left: 56px;
  z-index: 50;
  display: flex;
  gap: 4px;
}
.undo-redo-btns .el-button {
  background: #fff;
  border: 1px solid #dcdfe6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.undo-redo-btns .el-button:hover:not(:disabled) {
  border-color: #409EFF;
  color: #409EFF;
}
.undo-redo-btns .el-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

:deep(.vue-flow__edge-path) {
  stroke: #409EFF;
  stroke-width: 2;
  stroke-dasharray: 8 4;
  animation: dash-flow 1s linear infinite;
}
:deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: #67C23A;
  stroke-width: 3;
}
:deep(.vue-flow__edge:hover .vue-flow__edge-path) {
  stroke: #409EFF;
  stroke-width: 3;
}
:deep(.vue-flow__edge-text) {
  font-size: 11px;
  fill: #909399;
}
:deep(.vue-flow__node.selected) {
  box-shadow: 0 0 0 2px #409EFF;
}
@keyframes dash-flow {
  to { stroke-dashoffset: -12; }
}
</style>
