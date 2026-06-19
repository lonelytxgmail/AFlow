<template>
  <div class="status-bar">
    <span class="status-item">
      <el-icon :size="12"><Connection /></el-icon>
      节点: {{ nodeCount }}
    </span>
    <span class="status-separator">|</span>
    <span class="status-item">
      <el-icon :size="12"><Share /></el-icon>
      边: {{ edgeCount }}
    </span>
    <span class="status-separator">|</span>
    <span class="status-item" :class="saveStatusClass">
      {{ saveStatusText }}
    </span>
    <span class="status-separator">|</span>
    <span
      class="status-item status-validation"
      :class="{ 'status-error': validationErrors > 0, 'status-clickable': validationErrors > 0 }"
      @click="handleValidationClick"
    >
      {{ validationErrors > 0 ? `✗ ${validationErrors} 个验证错误` : '✓ 验证通过' }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Connection, Share } from '@element-plus/icons-vue'

export type SaveStatus = 'saved' | 'unsaved' | 'saving'

const props = withDefaults(defineProps<{
  nodeCount: number
  edgeCount: number
  saveStatus: SaveStatus
  validationErrors: number
}>(), {
  nodeCount: 0,
  edgeCount: 0,
  saveStatus: 'saved',
  validationErrors: 0
})

const emit = defineEmits<{
  (e: 'click-validation'): void
}>()

const saveStatusText = computed(() => {
  switch (props.saveStatus) {
    case 'saved':
      return '✓ 已保存'
    case 'unsaved':
      return '● 未保存'
    case 'saving':
      return '⟳ 保存中...'
    default:
      return ''
  }
})

const saveStatusClass = computed(() => ({
  'status-saved': props.saveStatus === 'saved',
  'status-unsaved': props.saveStatus === 'unsaved',
  'status-saving': props.saveStatus === 'saving'
}))

const handleValidationClick = () => {
  if (props.validationErrors > 0) {
    emit('click-validation')
  }
}
</script>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
  width: 100%;
}

.status-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.status-separator {
  color: #dcdfe6;
}

.status-saved {
  color: #67c23a;
}

.status-unsaved {
  color: #e6a23c;
}

.status-saving {
  color: #409eff;
}

.status-error {
  color: #f56c6c;
}

.status-clickable {
  cursor: pointer;
  text-decoration: underline;
}

.status-clickable:hover {
  opacity: 0.8;
}
</style>
