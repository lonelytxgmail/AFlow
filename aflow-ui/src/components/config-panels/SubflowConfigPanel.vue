<script setup lang="ts">
/**
 * 子流程配置面板
 *
 * 提供 Subflow 节点的完整配置界面，包含：
 * - 子流程定义 ID 选择器（从定义列表 API 加载可用定义）
 * - 输入参数映射表格（paramName → SpEL 表达式，支持增删行）
 * - 高级 JSON 编辑折叠入口
 */
import { computed, ref, onMounted } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { definitionApi } from '@/api/definitions'
import type { FlowDefinition } from '@/types'

interface ParamMapping {
  paramName: string
  expression: string
}

interface DefinitionOption {
  id: string
  name: string
}

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 定义列表加载 ---

const definitions = ref<DefinitionOption[]>([])
const loadingDefinitions = ref(false)

async function loadDefinitions() {
  loadingDefinitions.value = true
  try {
    const result = await definitionApi.list()
    const list: FlowDefinition[] = Array.isArray(result) ? result : (result as any)?.data ?? []
    definitions.value = list.map((d) => ({
      id: d.id ?? '',
      name: d.name || d.id || '未命名',
    }))
  } catch (e) {
    console.error('Failed to load definitions:', e)
    definitions.value = []
  } finally {
    loadingDefinitions.value = false
  }
}

onMounted(loadDefinitions)

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- 计算属性：字段绑定 ---

const definitionId = computed({
  get: () => props.modelValue.definitionId ?? '',
  set: (val: string) => updateField('definitionId', val),
})

const params = computed({
  get: (): ParamMapping[] => {
    const p = props.modelValue.params
    if (Array.isArray(p)) return p
    if (p && typeof p === 'object') {
      return Object.entries(p).map(([paramName, expression]) => ({
        paramName,
        expression: String(expression),
      }))
    }
    return []
  },
  set: (val: ParamMapping[]) => {
    // Store as Record<string, string> for the config
    const record: Record<string, string> = {}
    for (const item of val) {
      if (item.paramName.trim()) {
        record[item.paramName.trim()] = item.expression
      }
    }
    updateField('params', record)
  },
})

// --- 参数映射操作 ---

function addParam() {
  const current = [...params.value]
  current.push({ paramName: '', expression: '' })
  params.value = current
}

function removeParam(index: number) {
  const current = [...params.value]
  current.splice(index, 1)
  params.value = current
}

function updateParamName(index: number, paramName: string) {
  const current = [...params.value]
  current[index] = { ...current[index], paramName }
  params.value = current
}

function updateParamExpression(index: number, expression: string) {
  const current = [...params.value]
  current[index] = { ...current[index], expression }
  params.value = current
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
  <div class="subflow-config-panel">
    <el-form label-position="top" size="default">
      <!-- 子流程定义选择 -->
      <el-form-item label="子流程定义">
        <el-select
          v-model="definitionId"
          placeholder="选择子流程定义"
          filterable
          clearable
          :loading="loadingDefinitions"
          style="width: 100%"
        >
          <el-option
            v-for="def in definitions"
            :key="def.id"
            :label="def.name"
            :value="def.id"
          >
            <span>{{ def.name }}</span>
            <span class="definition-id-hint">{{ def.id }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 输入参数映射 -->
      <el-form-item label="输入参数映射">
        <div class="params-section">
          <div
            v-for="(param, index) in params"
            :key="index"
            class="param-row"
          >
            <el-input
              :model-value="param.paramName"
              @update:model-value="updateParamName(index, $event)"
              placeholder="参数名"
              class="param-name"
            />
            <span class="param-arrow">→</span>
            <el-input
              :model-value="param.expression"
              @update:model-value="updateParamExpression(index, $event)"
              placeholder="SpEL 表达式，如: #inputData.name"
              class="param-expression"
            />
            <el-button
              :icon="Delete"
              type="danger"
              text
              circle
              size="small"
              @click="removeParam(index)"
            />
          </div>

          <!-- 空状态提示 -->
          <div v-if="params.length === 0" class="empty-state">
            暂无参数映射，点击下方按钮添加
          </div>

          <el-button
            :icon="Plus"
            type="primary"
            text
            size="small"
            @click="addParam"
          >
            添加参数映射
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
.subflow-config-panel {
  padding: 12px;
}

.subflow-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.subflow-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.definition-id-hint {
  float: right;
  font-size: 11px;
  color: #909399;
}

.params-section {
  width: 100%;
}

.param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.param-name {
  flex: 2;
}

.param-arrow {
  font-size: 14px;
  color: #909399;
  flex-shrink: 0;
}

.param-expression {
  flex: 3;
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
