<template>
  <div
    class="custom-node"
    :class="{ 'is-active': props.selected }"
    :style="nodeStyle"
  >
    <!-- 入口 Handle -->
    <Handle type="target" :position="Position.Left" />

    <!-- 断点标识：左上角红色圆点 -->
    <span v-if="data.breakpoint" class="breakpoint-indicator" title="断点已开启"></span>

    <!-- 节点头部：图标 + 类型标签 -->
    <div class="node-header">
      <span class="node-icon" :style="{ color: categoryColor }">{{ nodeIcon }}</span>
      <span class="node-type-label">{{ nodeLabel }}</span>
    </div>

    <!-- 节点名称 -->
    <div class="node-body">{{ data.label }}</div>

    <!-- 输出变量 -->
    <div v-if="data.output" class="node-output">→ {{ data.output }}</div>

    <!-- 正常出口 Handle -->
    <Handle type="source" :position="Position.Right" id="default" />

    <!-- Error 出口端口：红色，位于节点右下 -->
    <Handle
      type="source"
      :position="Position.Right"
      id="error"
      class="error-handle"
      :style="{ bottom: '8px', top: 'auto' }"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { getNodeMeta, NODE_CATEGORIES } from '@/config/nodeTypes'
import type { NodeType } from '@/types'

interface NodeData {
  type: string
  label: string
  config?: Record<string, any>
  output?: string
  breakpoint?: boolean
}

const props = defineProps<{
  data: NodeData
  selected?: boolean
}>()

const meta = computed(() => getNodeMeta(props.data.type as NodeType))

const nodeIcon = computed(() => meta.value?.icon ?? '●')

const nodeLabel = computed(() => meta.value?.label ?? props.data.type)

const categoryColor = computed(() => {
  const category = meta.value?.category
  if (!category) return '#909399'
  return NODE_CATEGORIES[category]?.color ?? '#909399'
})

const nodeStyle = computed(() => ({
  borderColor: categoryColor.value,
  borderLeftColor: categoryColor.value,
  '--category-color': categoryColor.value
}))
</script>

<style scoped>
.custom-node {
  position: relative;
  padding: 10px 14px;
  border-radius: 6px;
  border: 1px solid #dcdfe6;
  border-left: 4px solid var(--category-color, #dcdfe6);
  background: white;
  min-width: 150px;
  text-align: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
  transition: all 0.2s ease;
}

.custom-node.is-active {
  box-shadow: 0 0 0 2px var(--category-color, #409EFF), 0 4px 12px rgba(0, 0, 0, 0.1);
  background: #fafbfc;
}

/* 断点标识 - 左上角红色圆点 */
.breakpoint-indicator {
  position: absolute;
  top: -4px;
  left: -4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #E74C3C;
  border: 2px solid white;
  box-shadow: 0 0 4px rgba(231, 76, 60, 0.6);
  z-index: 10;
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 4px;
}

.node-icon {
  font-size: 16px;
  line-height: 1;
}

.node-type-label {
  font-size: 11px;
  color: #909399;
  font-weight: 500;
}

.node-body {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  word-break: break-word;
}

.node-output {
  margin-top: 4px;
  font-size: 10px;
  color: #909399;
  border-top: 1px dashed #e4e7ed;
  padding-top: 4px;
}

/* Error 出口端口样式 - 红色 */
:deep(.error-handle) {
  width: 8px;
  height: 8px;
  background: #E74C3C !important;
  border: 2px solid #fff;
  box-shadow: 0 0 3px rgba(231, 76, 60, 0.5);
}
</style>
