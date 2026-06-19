<template>
  <div class="custom-node" :class="{ 'is-active': props.selected }">
    <Handle type="target" position="left" />
    <div class="node-header">
      <span>{{ data.type }}</span>
      <span v-if="data.breakpoint" class="breakpoint-dot" title="断点已开启"></span>
    </div>
    <div class="node-body">{{ data.label }}</div>
    <div v-if="data.output" class="node-output">{{ data.output }}</div>
    <Handle type="source" position="right" />
  </div>
</template>

<script setup lang="ts">
import { Handle } from '@vue-flow/core'

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
</script>

<style scoped>
.custom-node {
  padding: 10px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: white;
  min-width: 150px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  transition: all 0.3s;
}
.custom-node.is-active {
  border-color: #409EFF;
  box-shadow: 0 0 10px rgba(64, 158, 255, 0.5);
  background: #ecf5ff;
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
  box-shadow: 0 0 0 2px rgba(230, 126, 34, 0.16);
}
</style>
