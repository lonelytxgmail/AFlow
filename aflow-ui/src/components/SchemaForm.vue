<script setup lang="ts">
/**
 * SchemaForm — 根据 JSON Schema 动态生成表单
 *
 * 支持的 x- 扩展:
 * - x-component: input | select | textarea | key-value | number | switch
 * - x-order: 字段排序权重
 * - x-group: 分组标签（相同 group 的字段归为一组，非首组可折叠）
 */
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

export interface SchemaProperty {
  type?: string
  title?: string
  description?: string
  enum?: string[]
  default?: any
  additionalProperties?: Record<string, any>
  'x-component'?: string
  'x-order'?: number
  'x-group'?: string
}

export interface JsonSchema {
  type?: string
  properties?: Record<string, SchemaProperty>
  required?: string[]
}

interface FieldDef extends SchemaProperty {
  key: string
  required: boolean
}

interface FieldGroup {
  name: string
  fields: FieldDef[]
  collapsible: boolean
}

interface KeyValuePair {
  key: string
  value: string
}

const props = defineProps<{
  schema: JsonSchema
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

/** 收起/展开的分组 */
const collapsedGroups = ref<Set<string>>(new Set())

function toggleGroup(name: string) {
  if (collapsedGroups.value.has(name)) {
    collapsedGroups.value.delete(name)
  } else {
    collapsedGroups.value.add(name)
  }
}

/** 排序后的字段列表 */
const sortedFields = computed<FieldDef[]>(() => {
  const properties = props.schema?.properties
  if (!properties) return []

  return Object.entries(properties)
    .map(([key, def]) => ({
      key,
      ...def,
      required: props.schema.required?.includes(key) ?? false,
    }))
    .sort((a, b) => (a['x-order'] ?? 99) - (b['x-order'] ?? 99))
})

/** 按 x-group 分组 */
const fieldGroups = computed<FieldGroup[]>(() => {
  const groups: FieldGroup[] = []
  const groupMap = new Map<string, FieldDef[]>()

  for (const field of sortedFields.value) {
    const groupName = field['x-group'] || '基本配置'
    if (!groupMap.has(groupName)) {
      groupMap.set(groupName, [])
    }
    groupMap.get(groupName)!.push(field)
  }

  let isFirst = true
  for (const [name, fields] of groupMap) {
    groups.push({ name, fields, collapsible: !isFirst })
    isFirst = false
  }
  return groups
})

function getValue(key: string) {
  return props.modelValue?.[key]
}

function updateField(key: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

/** key-value 类型的辅助方法 */
function getKeyValuePairs(key: string): KeyValuePair[] {
  const val = props.modelValue?.[key]
  if (!val || typeof val !== 'object') return []
  return Object.entries(val).map(([k, v]) => ({ key: k, value: String(v) }))
}

function updateKeyValuePairs(fieldKey: string, pairs: KeyValuePair[]) {
  const obj: Record<string, string> = {}
  for (const pair of pairs) {
    if (pair.key.trim()) {
      obj[pair.key.trim()] = pair.value
    }
  }
  updateField(fieldKey, obj)
}

function addKeyValuePair(fieldKey: string) {
  const pairs = [...getKeyValuePairs(fieldKey), { key: '', value: '' }]
  updateKeyValuePairs(fieldKey, pairs)
}

function removeKeyValuePair(fieldKey: string, index: number) {
  const pairs = [...getKeyValuePairs(fieldKey)]
  pairs.splice(index, 1)
  updateKeyValuePairs(fieldKey, pairs)
}

function updateKvKey(fieldKey: string, index: number, newKey: string) {
  const pairs = [...getKeyValuePairs(fieldKey)]
  pairs[index] = { ...pairs[index], key: newKey }
  updateKeyValuePairs(fieldKey, pairs)
}

function updateKvValue(fieldKey: string, index: number, newValue: string) {
  const pairs = [...getKeyValuePairs(fieldKey)]
  pairs[index] = { ...pairs[index], value: newValue }
  updateKeyValuePairs(fieldKey, pairs)
}

/** 推断组件类型 */
function inferComponent(field: FieldDef): string {
  if (field['x-component']) return field['x-component']
  if (field.enum && field.enum.length > 0) return 'select'
  if (field.type === 'boolean') return 'switch'
  if (field.type === 'number' || field.type === 'integer') return 'number'
  if (field.type === 'object') return 'key-value'
  return 'input'
}
</script>

<template>
  <div class="schema-form">
    <template v-for="group in fieldGroups" :key="group.name">
      <!-- Group header -->
      <div
        class="group-header"
        :class="{ collapsible: group.collapsible, collapsed: collapsedGroups.has(group.name) }"
        @click="group.collapsible && toggleGroup(group.name)"
      >
        <span class="group-title">{{ group.name }}</span>
        <span v-if="group.collapsible" class="group-toggle">
          {{ collapsedGroups.has(group.name) ? '▸' : '▾' }}
        </span>
      </div>

      <!-- Group fields -->
      <el-form
        v-show="!collapsedGroups.has(group.name)"
        label-position="top"
        size="default"
        class="group-form"
      >
        <template v-for="field in group.fields" :key="field.key">
          <!-- Select (enum) -->
          <el-form-item
            v-if="inferComponent(field) === 'select'"
            :label="(field.title || field.key) + (field.required ? ' *' : '')"
          >
            <el-select
              :model-value="getValue(field.key) ?? field.default ?? ''"
              @update:model-value="updateField(field.key, $event)"
              placeholder="请选择"
              style="width: 100%"
            >
              <el-option
                v-for="opt in field.enum"
                :key="opt"
                :label="opt"
                :value="opt"
              />
            </el-select>
            <div v-if="field.description" class="field-hint">{{ field.description }}</div>
          </el-form-item>

          <!-- Switch (boolean) -->
          <el-form-item
            v-else-if="inferComponent(field) === 'switch'"
            :label="(field.title || field.key) + (field.required ? ' *' : '')"
          >
            <el-switch
              :model-value="getValue(field.key) ?? field.default ?? false"
              @update:model-value="updateField(field.key, $event)"
            />
            <span v-if="field.description" class="switch-hint">{{ field.description }}</span>
          </el-form-item>

          <!-- Number -->
          <el-form-item
            v-else-if="inferComponent(field) === 'number'"
            :label="(field.title || field.key) + (field.required ? ' *' : '')"
          >
            <el-input-number
              :model-value="getValue(field.key) ?? field.default ?? 0"
              @update:model-value="updateField(field.key, $event)"
              controls-position="right"
              :min="0"
            />
            <div v-if="field.description" class="field-hint">{{ field.description }}</div>
          </el-form-item>

          <!-- Textarea -->
          <el-form-item
            v-else-if="inferComponent(field) === 'textarea'"
            :label="(field.title || field.key) + (field.required ? ' *' : '')"
          >
            <el-input
              :model-value="getValue(field.key) ?? field.default ?? ''"
              @update:model-value="updateField(field.key, $event)"
              type="textarea"
              :rows="4"
              :placeholder="field.description || `请输入${field.title || field.key}`"
            />
          </el-form-item>

          <!-- Key-Value pairs -->
          <el-form-item
            v-else-if="inferComponent(field) === 'key-value'"
            :label="(field.title || field.key) + (field.required ? ' *' : '')"
          >
            <div class="kv-section">
              <div
                v-for="(pair, idx) in getKeyValuePairs(field.key)"
                :key="idx"
                class="kv-row"
              >
                <el-input
                  :model-value="pair.key"
                  @update:model-value="updateKvKey(field.key, idx, $event)"
                  placeholder="Key"
                  class="kv-key"
                />
                <span class="kv-sep">:</span>
                <el-input
                  :model-value="pair.value"
                  @update:model-value="updateKvValue(field.key, idx, $event)"
                  placeholder="Value"
                  class="kv-value"
                />
                <el-button
                  :icon="Delete"
                  type="danger"
                  text
                  circle
                  size="small"
                  @click="removeKeyValuePair(field.key, idx)"
                />
              </div>
              <el-button
                :icon="Plus"
                type="primary"
                text
                size="small"
                @click="addKeyValuePair(field.key)"
              >
                添加
              </el-button>
            </div>
            <div v-if="field.description" class="field-hint">{{ field.description }}</div>
          </el-form-item>

          <!-- Default: text input -->
          <el-form-item
            v-else
            :label="(field.title || field.key) + (field.required ? ' *' : '')"
          >
            <el-input
              :model-value="getValue(field.key) ?? field.default ?? ''"
              @update:model-value="updateField(field.key, $event)"
              :placeholder="field.description || `请输入${field.title || field.key}`"
            />
          </el-form-item>
        </template>
      </el-form>
    </template>

    <div v-if="sortedFields.length === 0" class="empty-schema">
      暂无配置项
    </div>
  </div>
</template>

<style scoped>
.schema-form {
  width: 100%;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  margin-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}

.group-header.collapsible {
  cursor: pointer;
  border-radius: 4px;
}

.group-header.collapsible:hover {
  background: #f5f7fa;
}

.group-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.group-toggle {
  font-size: 12px;
  color: #909399;
}

.group-form {
  padding: 8px 0 4px 0;
}

.group-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.group-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.switch-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.kv-section {
  width: 100%;
  position: relative;
  z-index: 1;
  padding-bottom: 4px;
}

.kv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.kv-key {
  flex: 2;
}

.kv-sep {
  color: #909399;
  font-weight: bold;
  flex-shrink: 0;
}

.kv-value {
  flex: 3;
}

.empty-schema {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 20px;
  border: 1px dashed #e4e7ed;
  border-radius: 6px;
}
</style>
