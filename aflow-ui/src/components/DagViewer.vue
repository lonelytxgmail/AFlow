<template>
  <div class="vue-flow-container">
    <VueFlow v-model="elements" :default-zoom="1" :fit-view-on-init="true">
      <Background pattern-color="#aaa" gap="20" />
      <Controls />
      <template #node-custom="props">
        <div
          class="custom-node"
          :class="getNodeStateClass(props.id)"
          @click="emit('node-click', props.id)"
        >
          <div class="node-header">
            <span>{{ props.data.type }}</span>
            <span v-if="props.data.breakpoint" class="breakpoint-dot" title="断点已开启"></span>
          </div>
          <div class="node-body">{{ props.label }}</div>
          <div v-if="props.data.output" class="node-output">{{ props.data.output }}</div>
        </div>
      </template>
    </VueFlow>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import dagre from 'dagre'

export type NodeExecutionStatus = 'SUCCESS' | 'FAILED' | 'RUNNING' | 'SUSPENDED'

export interface NodeExecutionEntry {
  nodeId: string
  status: NodeExecutionStatus
}

interface DslModel {
  nodes: any[]
  edges?: any[]
}

const props = defineProps<{
  dsl: DslModel
  activeNode?: string | null
  executionPath?: string[] | NodeExecutionEntry[]
}>()

const emit = defineEmits<{
  'node-click': [nodeId: string]
}>()

const { fitView } = useVueFlow()
const elements = ref<any[]>([])

/**
 * Normalize executionPath into a Map<nodeId, status>.
 * - If executionPath is a simple string[], the activeNode is RUNNING and others are SUCCESS.
 * - If executionPath is NodeExecutionEntry[], use the provided status directly.
 */
const nodeStatusMap = computed<Map<string, NodeExecutionStatus>>(() => {
  const map = new Map<string, NodeExecutionStatus>()
  const path = props.executionPath || []

  if (path.length === 0) return map

  // Determine if it's a simple string array or object array
  if (typeof path[0] === 'string') {
    // Simple string array: completed nodes are SUCCESS, activeNode is RUNNING
    for (const nodeId of path as string[]) {
      if (nodeId === props.activeNode) {
        map.set(nodeId, 'RUNNING')
      } else {
        map.set(nodeId, 'SUCCESS')
      }
    }
    // If activeNode is set but not in executionPath, still mark it as RUNNING
    if (props.activeNode && !map.has(props.activeNode)) {
      map.set(props.activeNode, 'RUNNING')
    }
  } else {
    // Object array with { nodeId, status }
    for (const entry of path as NodeExecutionEntry[]) {
      map.set(entry.nodeId, entry.status)
    }
    // Override activeNode as RUNNING if provided
    if (props.activeNode) {
      map.set(props.activeNode, 'RUNNING')
    }
  }

  return map
})

/**
 * Returns the CSS class for a node based on its execution state.
 */
const getNodeStateClass = (nodeId: string): string => {
  const status = nodeStatusMap.value.get(nodeId)

  if (!status) return 'node-pending'

  switch (status) {
    case 'SUCCESS':
      return 'node-success'
    case 'FAILED':
      return 'node-failed'
    case 'RUNNING':
      return 'node-running'
    case 'SUSPENDED':
      return 'node-suspended'
    default:
      return 'node-pending'
  }
}

/**
 * Compute topological waves for the DAG.
 * Nodes in the same wave have no mutual dependencies and can be executed in parallel.
 * This mirrors the backend ParallelWaveExecutor.computeWaves() algorithm.
 */
const computeWaves = (dsl: DslModel): Map<string, number> => {
  const nodeWaveMap = new Map<string, number>()
  const nodes = dsl.nodes || []
  const edges = (dsl.edges || []).filter((e: any) => e.type !== 'error')

  // Build inDegree map
  const inDegree = new Map<string, number>()
  const predecessors = new Map<string, Set<string>>()

  for (const node of nodes) {
    inDegree.set(node.id, 0)
    predecessors.set(node.id, new Set())
  }

  for (const edge of edges) {
    if (inDegree.has(edge.to) && inDegree.has(edge.from)) {
      predecessors.get(edge.to)!.add(edge.from)
      inDegree.set(edge.to, (inDegree.get(edge.to) || 0) + 1)
    }
  }

  const remaining = new Set(inDegree.keys())
  let waveIndex = 0

  while (remaining.size > 0) {
    const currentWave: string[] = []
    for (const nodeId of remaining) {
      if (inDegree.get(nodeId) === 0) {
        currentWave.push(nodeId)
      }
    }

    if (currentWave.length === 0) {
      // Cycle detected — assign remaining to last wave
      for (const nodeId of remaining) {
        nodeWaveMap.set(nodeId, waveIndex)
      }
      break
    }

    for (const nodeId of currentWave) {
      nodeWaveMap.set(nodeId, waveIndex)
      remaining.delete(nodeId)

      // Reduce inDegree for successors
      for (const edge of edges) {
        if (edge.from === nodeId && remaining.has(edge.to)) {
          inDegree.set(edge.to, (inDegree.get(edge.to) || 0) - 1)
        }
      }
    }

    waveIndex++
  }

  return nodeWaveMap
}

const generateElements = () => {
  const nodes: any[] = []
  const edges: any[] = []
  const dsl = props.dsl

  if (!dsl || !dsl.nodes) return

  // Compute waves for parallel node grouping
  const nodeWaveMap = computeWaves(dsl)

  // Use dagre for layout with rank=same for nodes in the same wave
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({
    rankdir: 'LR',
    ranksep: 120,
    nodesep: 60
  })

  const NODE_WIDTH = 180
  const NODE_HEIGHT = 60

  // Add nodes to dagre
  for (const n of dsl.nodes) {
    g.setNode(n.id, { width: NODE_WIDTH, height: NODE_HEIGHT })
  }

  // Add edges to dagre (normal edges only for layout)
  const normalEdges = (dsl.edges || []).filter((e: any) => e.type !== 'error')
  for (const e of normalEdges) {
    g.setEdge(e.from, e.to)
  }

  // Apply rank=same constraint: nodes in the same wave get the same rank
  const waveGroups = new Map<number, string[]>()
  for (const [nodeId, wave] of nodeWaveMap.entries()) {
    if (!waveGroups.has(wave)) {
      waveGroups.set(wave, [])
    }
    waveGroups.get(wave)!.push(nodeId)
  }

  // Set rank for nodes in parallel waves (waves with multiple nodes)
  for (const [_wave, nodeIds] of waveGroups.entries()) {
    if (nodeIds.length > 1) {
      // dagre rank group: set the same rank for all nodes in this wave
      for (const nodeId of nodeIds) {
        const nodeConfig = g.node(nodeId)
        if (nodeConfig) {
          nodeConfig.rank = _wave
        }
      }
    }
  }

  // Run dagre layout
  dagre.layout(g)

  // Build VueFlow nodes from dagre result
  for (const n of dsl.nodes) {
    const dagreNode = g.node(n.id)
    const position = dagreNode
      ? { x: dagreNode.x - NODE_WIDTH / 2, y: dagreNode.y - NODE_HEIGHT / 2 }
      : { x: 0, y: 0 }

    nodes.push({
      id: n.id,
      type: 'custom',
      label: n.name || n.id,
      position,
      data: {
        type: n.type,
        output: n.output || '',
        breakpoint: !!n.breakpoint,
        wave: nodeWaveMap.get(n.id) ?? -1
      }
    })
  }

  dsl.edges?.forEach((e: any) => {
    const sourceStatus = nodeStatusMap.value.get(e.from)
    const targetStatus = nodeStatusMap.value.get(e.to)

    // Color edges based on execution state of the target node
    let strokeColor = '#b1b1b7' // default gray
    if (targetStatus === 'SUCCESS' || sourceStatus === 'SUCCESS') {
      strokeColor = '#67C23A' // green for successful path
    } else if (targetStatus === 'FAILED') {
      strokeColor = '#F56C6C' // red for failed path
    } else if (targetStatus === 'RUNNING' || sourceStatus === 'RUNNING') {
      strokeColor = '#409EFF' // blue for active path
    }

    edges.push({
      id: `${e.from}-${e.to}`,
      source: e.from,
      target: e.to,
      animated: sourceStatus === 'RUNNING' || targetStatus === 'RUNNING',
      style: { stroke: strokeColor }
    })
  })

  elements.value = [...nodes, ...edges]
  setTimeout(() => fitView(), 50)
}

watch(() => props.dsl, generateElements, { deep: true })
watch(() => props.activeNode, generateElements)
watch(() => props.executionPath, generateElements, { deep: true })

onMounted(() => {
  generateElements()
})
</script>

<style scoped>
.vue-flow-container {
  width: 100%;
  height: 100%;
  background: white;
}

/* Base node style */
.custom-node {
  padding: 10px;
  border-radius: 4px;
  border: 2px solid #dcdfe6;
  background: white;
  min-width: 150px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
  position: relative;
}

/* 未执行=灰色 (nodes not in executionPath) */
.custom-node.node-pending {
  border-color: #dcdfe6;
  background: #f5f7fa;
  color: #909399;
}

/* 成功=绿色 */
.custom-node.node-success {
  border-color: #67C23A;
  background: #f0f9eb;
}

/* 失败=红色 */
.custom-node.node-failed {
  border-color: #F56C6C;
  background: #fef0f0;
}

/* 运行中=蓝色脉冲 + 蓝色外圈动画 */
.custom-node.node-running {
  border-color: #409EFF;
  background: #ecf5ff;
  animation: node-pulse 2s ease-in-out infinite;
  box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.7);
}

/* 挂起=橙色 */
.custom-node.node-suspended {
  border-color: #E6A23C;
  background: #fdf6ec;
}

/* 当前执行节点蓝色外圈动画 */
@keyframes node-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.7);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(64, 158, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0);
  }
}

.node-header {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.node-success .node-header {
  color: #529b2e;
}

.node-failed .node-header {
  color: #c45656;
}

.node-running .node-header {
  color: #337ecc;
}

.node-suspended .node-header {
  color: #b88230;
}

.node-body {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.node-output {
  margin-top: 6px;
  font-size: 11px;
  color: #606266;
}

.breakpoint-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e67e22;
  box-shadow: 0 0 0 2px rgba(230, 126, 34, 0.18);
}
</style>
