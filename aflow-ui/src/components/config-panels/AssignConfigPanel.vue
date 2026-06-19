<script setup lang="ts">
/**
 * 变量赋值配置面板
 *
 * 提供 key-value 赋值表格，用于将表达式结果赋值给变量。
 * Config 结构: { assignments: Record<string, string> }
 */
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

interface AssignmentEntry {
  variableName: string
  expression: string
}

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- 计算属性 ---

const assignments = computed({
  get: (): AssignmentEntry[] => {
    const raw = props.modelValue.assignments
    if (Array.isArray(raw)) return raw
    if (raw && typeof raw === 'object') {
      return Object.entries(raw).map(([variableName, expression]) => ({
        variableName,
        expression: String(expression),
      }))
    }
    return []
  },
  set: (val: AssignmentEntry[]) => {
    // Store as Record<string, string> for config structure
    const record: Record<string, string> = {}
    for (const entry of val) {
      if (entry.variableName.trim()) {
        record[entry.variableName.trim()] = entry.expression
      }
    }
    updateField('assignments', record)
  },
})

// --- 行操作 ---

function addRow() {
  const current = [...assignments.value]
  current.push({ variableName: '', expression: '' })
  assignments.value = current
}

function removeRow(index: number) {
  const current = [...assignments.value]
  current.splice(index, 1)
  assignments.value = current
}

function updateVariableName(index: number, variableName: string) {
  const current = [...assignments.value]
  current[index] = { ...current[index], variableName }
  assignments.value = current
}

function updateExpression(index: number, expression: string) {
  const current = [...assignments.value]
  current[index] = { ...current[index], expression }
  assignments.value = current
}

// --- 高级 JSON fallback ---

const showAdvancedJson = ref(false)

const advancedJsonText = computed({
  get: () => JSON.stringify(props.modelValue, null, 2),
  set: (val: string) => {
    try {
      const parsed = JSON.parse(val)
      advancedJsonError.value = null
      emit('update:modelValue', parsed)
    } catch (e) {
      advancedJsonError.value = (e as Error).message
    }
  },
})

const advancedJsonError = ref<string | null>(null)
</script>

<template>
  <div class="assign-config-panel">
    <el-form label-position="top" size="default">
      <!-- 赋值表格 -->
      <el-form-item label="变量赋值">
        <div class="assignments-section">
          <div
            v-for="(entry, index) in assignments"
            :key="index"
            class="assignment-row"
          >
            <el-input
              :model-value="entry.variableName"
              @update:model-value="updateVariableName(index, $event)"
              placeholder="变量名"
              class="variable-name"
            />
            <span class="assignment-arrow">→</span>
            <el-input
              :model-value="entry.expression"
              @update:model-value="updateExpression(index, $event)"
              placeholder="表达式"
              class="expression-input"
            />
            <el-button
              :icon="Delete"
              type="danger"
              text
              circle
              size="small"
              @click="removeRow(index)"
            />
          </div>
          <el-button
            :icon="Plus"
            type="primary"
            text
            size="small"
            @click="addRow"
          >
            添加赋值
          </el-button>
        </div>
      </el-form-item>
    </el-form>

    <!-- 高级 JSON 折叠入口 -->
    <div class="advanced-json-section">
      <el-button
        text
        type="primary"
        size="small"
        @click="showAdvancedJson = !showAdvancedJson"
      >
        {{ showAdvancedJson ? '收起' : '展开' }} JSON 编辑
      </el-button>

      <div v-if="showAdvancedJson" class="json-editor-wrapper">
        <el-input
          :model-value="advancedJsonText"
          @update:model-value="advancedJsonText = $event"
          type="textarea"
          :autosize="{ minRows: 6, maxRows: 20 }"
          spellcheck="false"
          class="json-input"
        />
        <div v-if="advancedJsonError" class="field-error">
          JSON 格式错误: {{ advancedJsonError }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.assign-config-panel {
  padding: 12px;
}

.assign-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.assign-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.assignments-section {
  width: 100%;
}

.assignment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.variable-name {
  flex: 2;
}

.assignment-arrow {
  color: #909399;
  font-size: 14px;
  flex-shrink: 0;
}

.expression-input {
  flex: 3;
}

.json-input :deep(textarea) {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
}

.field-error {
  margin-top: 4px;
  font-size: 12px;
  color: #f56c6c;
}

.advanced-json-section {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

.json-editor-wrapper {
  margin-top: 8px;
}
</style>
