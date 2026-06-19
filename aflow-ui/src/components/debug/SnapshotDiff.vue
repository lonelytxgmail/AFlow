<template>
  <div class="snapshot-diff">
    <div v-if="nodeId" class="diff-header">
      <el-tag size="small" effect="plain">{{ nodeId }}</el-tag>
    </div>

    <div v-if="diffEntries.length === 0" class="diff-empty">
      <el-empty description="无变量变更" :image-size="40" />
    </div>

    <div v-else class="diff-list">
      <div
        v-for="entry in diffEntries"
        :key="entry.key"
        class="diff-entry"
        :class="`diff-${entry.type}`"
      >
        <span class="diff-indicator">{{ getIndicator(entry.type) }}</span>
        <span class="diff-key">{{ entry.key }}</span>
        <span class="diff-separator">:</span>
        <span v-if="entry.type === 'changed'" class="diff-values">
          <span class="diff-old-value">{{ formatValue(entry.oldValue) }}</span>
          <el-icon class="diff-arrow"><Right /></el-icon>
          <span class="diff-new-value">{{ formatValue(entry.newValue) }}</span>
        </span>
        <span v-else-if="entry.type === 'added'" class="diff-value added-value">
          {{ formatValue(entry.newValue) }}
        </span>
        <span v-else class="diff-value removed-value">
          {{ formatValue(entry.oldValue) }}
        </span>
      </div>
    </div>

    <!-- 统计摘要 -->
    <div v-if="diffEntries.length > 0" class="diff-summary">
      <span v-if="counts.added" class="summary-item summary-added">+{{ counts.added }}</span>
      <span v-if="counts.removed" class="summary-item summary-removed">-{{ counts.removed }}</span>
      <span v-if="counts.changed" class="summary-item summary-changed">~{{ counts.changed }}</span>
      <span class="summary-item summary-unchanged">{{ counts.unchanged }} 不变</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Right } from '@element-plus/icons-vue'

// --- Types ---

type DiffType = 'added' | 'removed' | 'changed' | 'unchanged'

interface DiffEntry {
  key: string
  type: DiffType
  oldValue?: unknown
  newValue?: unknown
}

// --- Props ---

const props = defineProps<{
  before: Record<string, unknown>
  after: Record<string, unknown>
  nodeId?: string
}>()

// --- Computed ---

const diffEntries = computed<DiffEntry[]>(() => {
  const entries: DiffEntry[] = []
  const allKeys = new Set([
    ...Object.keys(props.before),
    ...Object.keys(props.after),
  ])

  for (const key of allKeys) {
    const inBefore = key in props.before
    const inAfter = key in props.after

    if (inAfter && !inBefore) {
      entries.push({ key, type: 'added', newValue: props.after[key] })
    } else if (inBefore && !inAfter) {
      entries.push({ key, type: 'removed', oldValue: props.before[key] })
    } else {
      const oldVal = props.before[key]
      const newVal = props.after[key]
      if (!deepEqual(oldVal, newVal)) {
        entries.push({ key, type: 'changed', oldValue: oldVal, newValue: newVal })
      }
      // unchanged entries are not shown to keep diff concise
    }
  }

  // Sort: added first, then changed, then removed
  const order: Record<DiffType, number> = { added: 0, changed: 1, removed: 2, unchanged: 3 }
  entries.sort((a, b) => order[a.type] - order[b.type] || a.key.localeCompare(b.key))

  return entries
})

const counts = computed(() => {
  const allKeys = new Set([
    ...Object.keys(props.before),
    ...Object.keys(props.after),
  ])
  let added = 0
  let removed = 0
  let changed = 0
  let unchanged = 0

  for (const key of allKeys) {
    const inBefore = key in props.before
    const inAfter = key in props.after

    if (inAfter && !inBefore) added++
    else if (inBefore && !inAfter) removed++
    else if (!deepEqual(props.before[key], props.after[key])) changed++
    else unchanged++
  }

  return { added, removed, changed, unchanged }
})

// --- Methods ---

function getIndicator(type: DiffType): string {
  switch (type) {
    case 'added': return '+'
    case 'removed': return '-'
    case 'changed': return '~'
    default: return ' '
  }
}

function formatValue(value: unknown): string {
  if (value === null) return 'null'
  if (value === undefined) return 'undefined'
  if (typeof value === 'string') return `"${value}"`
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (a === null || b === null) return false
  if (typeof a !== typeof b) return false
  if (typeof a !== 'object') return false

  const objA = a as Record<string, unknown>
  const objB = b as Record<string, unknown>
  const keysA = Object.keys(objA)
  const keysB = Object.keys(objB)

  if (keysA.length !== keysB.length) return false
  for (const key of keysA) {
    if (!keysB.includes(key)) return false
    if (!deepEqual(objA[key], objB[key])) return false
  }
  return true
}
</script>

<style scoped>
.snapshot-diff {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.diff-header {
  margin-bottom: 8px;
}

.diff-empty {
  padding: 12px 0;
}

.diff-list {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.diff-entry {
  display: flex;
  align-items: baseline;
  padding: 4px 10px;
  border-bottom: 1px solid #f2f3f5;
  gap: 6px;
  overflow: hidden;
}

.diff-entry:last-child {
  border-bottom: none;
}

/* Type-based background colors */
.diff-added {
  background-color: #f0f9eb;
}

.diff-removed {
  background-color: #fef0f0;
}

.diff-changed {
  background-color: #fdf6ec;
}

/* Indicator symbol */
.diff-indicator {
  flex-shrink: 0;
  width: 14px;
  font-weight: 700;
  text-align: center;
}

.diff-added .diff-indicator {
  color: #67c23a;
}

.diff-removed .diff-indicator {
  color: #f56c6c;
}

.diff-changed .diff-indicator {
  color: #e6a23c;
}

/* Key name */
.diff-key {
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
}

.diff-separator {
  color: #909399;
  flex-shrink: 0;
}

/* Values */
.diff-values {
  display: flex;
  align-items: baseline;
  gap: 4px;
  overflow: hidden;
  flex-wrap: wrap;
}

.diff-value,
.diff-old-value,
.diff-new-value {
  word-break: break-all;
  overflow-wrap: break-word;
}

.diff-old-value {
  color: #f56c6c;
  text-decoration: line-through;
}

.diff-new-value {
  color: #67c23a;
}

.added-value {
  color: #67c23a;
}

.removed-value {
  color: #f56c6c;
}

.diff-arrow {
  flex-shrink: 0;
  color: #909399;
  font-size: 10px;
}

/* Summary bar */
.diff-summary {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  padding: 4px 0;
  font-size: 11px;
}

.summary-item {
  font-weight: 600;
}

.summary-added {
  color: #67c23a;
}

.summary-removed {
  color: #f56c6c;
}

.summary-changed {
  color: #e6a23c;
}

.summary-unchanged {
  color: #909399;
  font-weight: 400;
}
</style>
