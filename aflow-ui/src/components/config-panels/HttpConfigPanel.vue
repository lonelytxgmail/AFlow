<script setup lang="ts">
/**
 * HTTP 节点配置面板
 *
 * 提供 HTTP 节点的完整配置界面，包含：
 * - URL 输入
 * - Method 下拉（GET/POST/PUT/DELETE）
 * - Headers：动态 key-value 表格（支持增删行）
 * - Body：textarea / JSON 编辑器
 * - Timeout：数字输入（ms）
 * - 高级 JSON 编辑折叠入口
 */
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

interface HeaderEntry {
  key: string
  value: string
}

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 可选项 ---

const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
]

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- 计算属性：字段绑定 ---

const url = computed({
  get: () => props.modelValue.url ?? '',
  set: (val: string) => updateField('url', val),
})

const method = computed({
  get: () => props.modelValue.method ?? 'GET',
  set: (val: string) => updateField('method', val),
})

const headers = computed({
  get: (): HeaderEntry[] => {
    const h = props.modelValue.headers
    if (Array.isArray(h)) return h
    if (h && typeof h === 'object') {
      return Object.entries(h).map(([key, value]) => ({ key, value: String(value) }))
    }
    return []
  },
  set: (val: HeaderEntry[]) => updateField('headers', val),
})

const body = computed({
  get: () => {
    const b = props.modelValue.body
    if (typeof b === 'string') return b
    if (b && typeof b === 'object') return JSON.stringify(b, null, 2)
    return ''
  },
  set: (val: string) => {
    // Try to parse as JSON; if fails, store as string
    try {
      const parsed = val.trim() ? JSON.parse(val) : ''
      bodyError.value = null
      updateField('body', parsed)
    } catch {
      bodyError.value = null // body can be plain text
      updateField('body', val)
    }
  },
})

const bodyError = ref<string | null>(null)

const timeout = computed({
  get: () => props.modelValue.timeout ?? 30000,
  set: (val: number) => updateField('timeout', val),
})

// --- Headers 操作 ---

function addHeader() {
  const current = [...headers.value]
  current.push({ key: '', value: '' })
  headers.value = current
}

function removeHeader(index: number) {
  const current = [...headers.value]
  current.splice(index, 1)
  headers.value = current
}

function updateHeaderKey(index: number, key: string) {
  const current = [...headers.value]
  current[index] = { ...current[index], key }
  headers.value = current
}

function updateHeaderValue(index: number, value: string) {
  const current = [...headers.value]
  current[index] = { ...current[index], value }
  headers.value = current
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
  <div class="http-config-panel">
    <el-form label-position="top" size="default">
      <!-- URL -->
      <el-form-item label="URL">
        <el-input
          v-model="url"
          placeholder="https://api.example.com/endpoint"
          clearable
        >
          <template #prepend>
            <el-select v-model="method" style="width: 110px">
              <el-option
                v-for="opt in methodOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
        </el-input>
      </el-form-item>

      <!-- Headers -->
      <el-form-item label="Headers">
        <div class="headers-section">
          <div
            v-for="(header, index) in headers"
            :key="index"
            class="header-row"
          >
            <el-input
              :model-value="header.key"
              @update:model-value="updateHeaderKey(index, $event)"
              placeholder="Header Name"
              class="header-key"
            />
            <el-input
              :model-value="header.value"
              @update:model-value="updateHeaderValue(index, $event)"
              placeholder="Header Value"
              class="header-value"
            />
            <el-button
              :icon="Delete"
              type="danger"
              text
              circle
              size="small"
              @click="removeHeader(index)"
            />
          </div>
          <el-button
            :icon="Plus"
            type="primary"
            text
            size="small"
            @click="addHeader"
          >
            添加 Header
          </el-button>
        </div>
      </el-form-item>

      <!-- Body -->
      <el-form-item label="Body">
        <el-input
          v-model="body"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 12 }"
          placeholder='请求体（支持纯文本或 JSON 格式）'
          spellcheck="false"
          class="json-input"
        />
        <div v-if="bodyError" class="field-error">
          {{ bodyError }}
        </div>
      </el-form-item>

      <!-- Timeout -->
      <el-form-item label="超时 (ms)">
        <el-input-number
          v-model="timeout"
          :min="1000"
          :max="300000"
          :step="1000"
          controls-position="right"
          style="width: 200px"
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
.http-config-panel {
  padding: 12px;
}

.http-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.http-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.headers-section {
  width: 100%;
}

.header-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.header-key {
  flex: 2;
}

.header-value {
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
