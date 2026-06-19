<script setup lang="ts">
/**
 * 原子能力配置面板
 *
 * 提供 Composite 节点的完整配置界面，包含：
 * - 原子能力 ID 选择器（从 PUBLISHED 组件列表加载）
 * - 参数表单（根据组件 inputSchema 动态生成，或 key-value 对）
 * - 高级 JSON 编辑折叠入口
 */
import { computed, ref, onMounted, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { atomicApi } from '@/api/atomic'
import type { AtomicComponent } from '@/types'

interface ParamMapping {
  key: string
  value: string
}

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 组件列表加载 ---

const components = ref<AtomicComponent[]>([])
const loadingComponents = ref(false)

async function loadComponents() {
  loadingComponents.value = true
  try {
    const result = await atomicApi.listComponents({ status: 'PUBLISHED' })
    components.value = Array.isArray(result) ? result : (result as any)?.data ?? []
  } catch (e) {
    console.error('Failed to load atomic components:', e)
    components.value = []
  } finally {
    loadingComponents.value = false
  }
}

onMounted(loadComponents)

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- 选中的组件详情 ---

const selectedComponent = computed<AtomicComponent | undefined>(() => {
  const id = props.modelValue.componentId
  if (!id) return undefined
  return components.value.find((c) => c.id === id)
})

const hasInputSchema = computed(() => {
  const schema = selectedComponent.value?.inputSchema
  return schema && typeof schema === 'object' && Object.keys(schema).length > 0
})

/** 从 inputSchema 提取字段定义列表 */
const schemaFields = computed(() => {
  const schema = selectedComponent.value?.inputSchema
  if (!schema || typeof schema !== 'object') return []

  // 支持 JSON Schema properties 格式
  const properties = schema.properties ?? schema
  if (typeof properties !== 'object') return []

  return Object.entries(properties).map(([key, def]) => {
    const fieldDef = def as Record<string, any> | undefined
    return {
      key,
      type: fieldDef?.type ?? 'string',
      description: fieldDef?.description ?? '',
      required: Array.isArray(schema.required) && schema.required.includes(key),
    }
  })
})

// --- 计算属性：字段绑定 ---

const componentId = computed({
  get: () => props.modelValue.componentId ?? '',
  set: (val: string) => updateField('componentId', val),
})

const params = computed({
  get: (): ParamMapping[] => {
    const p = props.modelValue.params
    if (Array.isArray(p)) return p
    if (p && typeof p === 'object') {
      return Object.entries(p).map(([key, value]) => ({
        key,
        value: String(value),
      }))
    }
    return []
  },
  set: (val: ParamMapping[]) => {
    const record: Record<string, string> = {}
    for (const item of val) {
      if (item.key.trim()) {
        record[item.key.trim()] = item.value
      }
    }
    updateField('params', record)
  },
})

// --- Schema-based 参数操作 ---

function getSchemaParamValue(key: string): string {
  const p = props.modelValue.params
  if (p && typeof p === 'object') {
    return String(p[key] ?? '')
  }
  return ''
}

function updateSchemaParam(key: string, value: string) {
  const currentParams = props.modelValue.params && typeof props.modelValue.params === 'object'
    ? { ...props.modelValue.params }
    : {}
  currentParams[key] = value
  updateField('params', currentParams)
}

// --- Key-value 参数操作 ---

function addParam() {
  const current = [...params.value]
  current.push({ key: '', value: '' })
  params.value = current
}

function removeParam(index: number) {
  const current = [...params.value]
  current.splice(index, 1)
  params.value = current
}

function updateParamKey(index: number, key: string) {
  const current = [...params.value]
  current[index] = { ...current[index], key }
  params.value = current
}

function updateParamValue(index: number, value: string) {
  const current = [...params.value]
  current[index] = { ...current[index], value }
  params.value = current
}

// --- 组件切换时清空参数 ---

watch(componentId, (newId, oldId) => {
  if (newId !== oldId && oldId) {
    updateField('params', {})
  }
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
  <div class="composite-config-panel">
    <el-form label-position="top" size="default">
      <!-- 原子能力选择 -->
      <el-form-item label="原子能力组件">
        <el-select
          v-model="componentId"
          placeholder="选择原子能力组件"
          filterable
          clearable
          :loading="loadingComponents"
          style="width: 100%"
        >
          <el-option
            v-for="comp in components"
            :key="comp.id"
            :label="comp.name"
            :value="comp.id"
          >
            <div class="component-option">
              <span class="component-name">{{ comp.name }}</span>
              <span v-if="comp.description" class="component-desc">{{ comp.description }}</span>
            </div>
          </el-option>
        </el-select>
        <div v-if="selectedComponent?.description" class="component-description">
          {{ selectedComponent.description }}
        </div>
      </el-form-item>

      <!-- 参数表单：Schema-based -->
      <el-form-item v-if="hasInputSchema" label="参数配置">
        <div class="schema-params-section">
          <div
            v-for="field in schemaFields"
            :key="field.key"
            class="schema-param-row"
          >
            <el-form-item
              :label="field.key + (field.required ? ' *' : '')"
              class="schema-field-item"
            >
              <el-input
                :model-value="getSchemaParamValue(field.key)"
                @update:model-value="updateSchemaParam(field.key, $event)"
                :placeholder="field.description || `输入 ${field.key} 的值`"
              />
              <div v-if="field.description" class="field-description">
                {{ field.description }}
              </div>
            </el-form-item>
          </div>
        </div>
      </el-form-item>

      <!-- 参数表单：Key-value 模式（无 schema 时） -->
      <el-form-item v-else-if="componentId" label="参数 (Key → Value)">
        <div class="params-section">
          <div
            v-for="(param, index) in params"
            :key="index"
            class="param-row"
          >
            <el-input
              :model-value="param.key"
              @update:model-value="updateParamKey(index, $event)"
              placeholder="参数名"
              class="param-key"
            />
            <span class="param-arrow">→</span>
            <el-input
              :model-value="param.value"
              @update:model-value="updateParamValue(index, $event)"
              placeholder="参数值"
              class="param-value"
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
            暂无参数配置，点击下方按钮添加
          </div>

          <el-button
            :icon="Plus"
            type="primary"
            text
            size="small"
            @click="addParam"
          >
            添加参数
          </el-button>
        </div>
      </el-form-item>

      <!-- 未选择组件时的提示 -->
      <div v-if="!componentId" class="empty-state">
        请先选择原子能力组件
      </div>
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
.composite-config-panel {
  padding: 12px;
}

.composite-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.composite-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.component-option {
  display: flex;
  flex-direction: column;
  padding: 2px 0;
}

.component-name {
  font-size: 13px;
  line-height: 1.4;
}

.component-desc {
  font-size: 11px;
  color: #909399;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.component-description {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.schema-params-section {
  width: 100%;
}

.schema-param-row {
  margin-bottom: 4px;
}

.schema-field-item {
  margin-bottom: 12px !important;
}

.field-description {
  margin-top: 2px;
  font-size: 11px;
  color: #a8abb2;
  line-height: 1.3;
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

.param-key {
  flex: 2;
}

.param-arrow {
  font-size: 14px;
  color: #909399;
  flex-shrink: 0;
}

.param-value {
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
