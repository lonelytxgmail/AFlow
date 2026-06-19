<script setup lang="ts">
/**
 * 版本历史侧边抽屉
 * 显示定义的版本列表，支持查看和回滚操作。
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { definitionApi } from '@/api'
import type { DefinitionVersion } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  definitionId: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'rollback': [snapshotJson: string]
}>()

const visible = ref(props.modelValue)
const loading = ref(false)
const versions = ref<DefinitionVersion[]>([])

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    loadVersions()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadVersions() {
  loading.value = true
  try {
    const data = await definitionApi.listVersions(props.definitionId)
    versions.value = Array.isArray(data) ? data : []
  } catch (e) {
    ElMessage.error('加载版本历史失败')
    versions.value = []
  } finally {
    loading.value = false
  }
}

async function handleRollback(version: DefinitionVersion) {
  try {
    await ElMessageBox.confirm(
      `确定要回滚到版本 ${version.versionNumber} 吗？当前未保存的修改将丢失。`,
      '回滚确认',
      { confirmButtonText: '确认回滚', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // User cancelled
  }

  try {
    await definitionApi.rollback(props.definitionId, version.versionNumber)
    // Fetch the version content to apply locally
    const versionData = await definitionApi.getVersion(props.definitionId, version.versionNumber)
    emit('rollback', versionData.snapshotJson)
    visible.value = false
    ElMessage.success(`已回滚到版本 ${version.versionNumber}`)
  } catch (e) {
    ElMessage.error('回滚失败')
  }
}

function formatDate(dateStr: string): string {
  try {
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN')
  } catch {
    return dateStr
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="版本历史"
    direction="rtl"
    size="360px"
  >
    <div v-loading="loading" class="version-list">
      <div v-if="versions.length === 0 && !loading" class="empty-state">
        暂无版本记录。每次发布将自动创建版本快照。
      </div>
      <div
        v-for="version in versions"
        :key="version.versionNumber"
        class="version-item"
      >
        <div class="version-info">
          <span class="version-number">v{{ version.versionNumber }}</span>
          <span class="version-time">{{ formatDate(version.createdAt) }}</span>
        </div>
        <div class="version-actions">
          <el-button size="small" text type="primary" @click="handleRollback(version)">
            回滚
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.version-list {
  padding: 0 4px;
}

.empty-state {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 40px 20px;
}

.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 8px;
  border-bottom: 1px solid #ebeef5;
}

.version-item:last-child {
  border-bottom: none;
}

.version-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.version-number {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.version-time {
  font-size: 12px;
  color: #909399;
}

.version-actions {
  flex-shrink: 0;
}
</style>
