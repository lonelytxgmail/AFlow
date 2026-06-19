<template>
  <div class="node-palette">
    <div class="palette-header">
      <span class="palette-title">节点面板</span>
    </div>
    <div class="palette-search">
      <el-input v-model="searchQuery" placeholder="搜索节点..." size="small" clearable prefix-icon="Search" />
    </div>
    <div class="palette-categories">
      <div v-for="(cat, key) in categories" :key="key" class="category-group">
        <div class="category-header" @click="toggleCategory(key)">
          <span class="category-dot" :style="{ background: cat.color }"></span>
          <span class="category-label">{{ cat.label }}</span>
          <span class="category-toggle">{{ collapsed[key] ? '▸' : '▾' }}</span>
        </div>
        <div v-show="!collapsed[key]" class="category-nodes">
          <div
            v-for="node in getFilteredNodes(key)"
            :key="node.type"
            class="palette-node"
            draggable="true"
            @dragstart="onDragStart($event, node)"
          >
            <span class="node-icon" :style="{ background: node.color + '20', color: node.color }">
              {{ node.icon }}
            </span>
            <div class="node-info">
              <span class="node-label">{{ node.label }}</span>
              <span class="node-desc">{{ node.description }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { NODE_CATEGORIES, NODE_TYPE_REGISTRY, type NodeCategory, type NodeTypeMeta } from '@/config/nodeTypes'

const searchQuery = ref('')
const categories = NODE_CATEGORIES
const collapsed = reactive<Record<string, boolean>>({
  control: false,
  ai: false,
  data: false,
  integration: false
})

function toggleCategory(key: string) {
  collapsed[key] = !collapsed[key]
}

function getFilteredNodes(category: string): NodeTypeMeta[] {
  let nodes = NODE_TYPE_REGISTRY.filter(n => n.category === category)
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    nodes = nodes.filter(n =>
      n.label.toLowerCase().includes(q) ||
      n.description.toLowerCase().includes(q) ||
      n.type.toLowerCase().includes(q)
    )
  }
  return nodes
}

function onDragStart(event: DragEvent, node: NodeTypeMeta) {
  if (!event.dataTransfer) return
  const payload = {
    type: node.type,
    name: node.label,
    config: { ...node.defaultConfig }
  }
  event.dataTransfer.setData('application/vueflow', JSON.stringify(payload))
  event.dataTransfer.effectAllowed = 'move'
}
</script>

<style scoped>
.node-palette {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.palette-header {
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.palette-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.palette-search {
  padding: 8px 12px;
}

.palette-categories {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.category-group {
  margin-bottom: 4px;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: pointer;
  user-select: none;
}

.category-header:hover {
  background: #f5f7fa;
}

.category-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.category-label {
  font-size: 12px;
  font-weight: 500;
  color: #606266;
  flex: 1;
}

.category-toggle {
  font-size: 10px;
  color: #909399;
}

.category-nodes {
  padding: 0 8px;
}

.palette-node {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: grab;
  transition: background 0.2s;
  margin-bottom: 2px;
}

.palette-node:hover {
  background: #ecf5ff;
}

.palette-node:active {
  cursor: grabbing;
  background: #d9ecff;
}

.node-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.node-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.node-label {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-desc {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
