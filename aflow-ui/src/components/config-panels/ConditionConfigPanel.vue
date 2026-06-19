<script setup lang="ts">
/**
 * 条件分支配置面板
 *
 * 提供 Condition 节点的完整配置界面，包含：
 * - 条件列表：每条为 { expression: string, targetNodeId?: string }
 * - 每条条件有 SpEL 表达式输入
 * - 添加/删除条件按钮
 * - 高级 JSON 编辑折叠入口
 */
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import ExpressionInput from '@/components/common/ExpressionInput.vue'
import type { FlowVariable } from '@/composables/useFlowVariables'

interface ConditionItem {
  expression: string
  targetNodeId?: string
}

const props = withDefaults(defineProps<{
  modelValue: Record<string, any>
  /** Available flow variables for expression autocomplete */
  variables?: FlowVariable[]
}>(), {
  variables: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- 计算属性：条件列表 ---

const conditions = computed({
  get: (): ConditionItem[] => {
    const c = props.modelValue.conditions
    if (Array.isArray(c)) return c
    return []
  },
  set: (val: ConditionItem[]) => updateField('conditions', val),
})

// --- 条件操作 ---

function addCondition() {
  const current = [...conditions.value]
  current.push({ expression: '', targetNodeId: undefined })
  conditions.value = current
}

function removeCondition(index: number) {
  const current = [...conditions.value]
  current.splice(index, 1)
  conditions.value = current
}

function updateExpression(index: number, expression: string) {
  const current = [...conditions.value]
  current[index] = { ...current[index], expression }
  conditions.value = current
}

function updateTargetNodeId(index: number, targetNodeId: string) {
  const current = [...conditions.value]
  current[index] = { ...current[index], targetNodeId: targetNodeId || undefined }
  conditions.value = current
}

// --- 默认分支 (当所有条件不匹配时) ---

const defaultTargetNodeId = computed({
  get: () => props.modelValue.defaultTargetNodeId ?? '',
  set: (val: string) => updateField('defaultTargetNodeId', val || undefined),
})

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
  <div class="condition-config-panel">
    <el-form label-position="top" size="default">
      <!-- 条件列表 -->
      <el-form-item label="条件分支">
        <div class="conditions-section">
          <div
            v-for="(condition, index) in conditions"
            :key="index"
            class="condition-row"
          >
            <div class="condition-header">
              <span class="condition-index">条件 {{ index + 1 }}</span>
              <el-button
                :icon="Delete"
                type="danger"
                text
                circle
                size="small"
                @click="removeCondition(index)"
              />
            </div>
            <div class="condition-fields">
              <ExpressionInput
                :model-value="condition.expression"
                @update:model-value="updateExpression(index, $event)"
                :variables="variables"
                placeholder="SpEL 表达式，如: #result.score > 0.8"
                class="expression-input"
              />
              <el-input
                :model-value="condition.targetNodeId ?? ''"
                @update:model-value="updateTargetNodeId(index, $event)"
                placeholder="目标节点 ID（可选）"
                class="target-input"
              />
            </div>
          </div>

          <!-- 空状态提示 -->
          <div v-if="conditions.length === 0" class="empty-state">
            暂无条件，点击下方按钮添加
          </div>

          <el-button
            :icon="Plus"
            type="primary"
            text
            size="small"
            @click="addCondition"
          >
            添加条件
          </el-button>
        </div>
      </el-form-item>

      <!-- 默认分支 -->
      <el-form-item label="默认分支（兜底）">
        <el-input
          v-model="defaultTargetNodeId"
          placeholder="当所有条件不匹配时的目标节点 ID（可选）"
        />
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
.condition-config-panel {
  padding: 12px;
}

.condition-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.condition-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.conditions-section {
  width: 100%;
}

.condition-row {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fafafa;
  transition: border-color 0.2s;
}

.condition-row:hover {
  border-color: #c0c4cc;
}

.condition-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.condition-index {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
}

.condition-fields {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.expression-input {
  width: 100%;
}

.target-input {
  width: 100%;
}

.empty-state {
  padding: 16px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  border: 1px dashed #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;
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
