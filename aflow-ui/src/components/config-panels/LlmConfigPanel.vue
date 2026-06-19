<script setup lang="ts">
/**
 * LLM 节点配置面板
 *
 * Config 结构: { model, systemPrompt, userPrompt, temperature, outputSchema }
 *
 * 支持 #{variable} 变量插值语法。
 */
import { computed, ref } from 'vue'

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- Reactive config fields ---

const model = computed({
  get: () => props.modelValue.model ?? 'gpt-4o-mini',
  set: (val: string) => updateConfig('model', val),
})

const systemPrompt = computed({
  get: () => props.modelValue.systemPrompt ?? '',
  set: (val: string) => updateConfig('systemPrompt', val),
})

const userPrompt = computed({
  get: () => props.modelValue.userPrompt ?? '',
  set: (val: string) => updateConfig('userPrompt', val),
})

const temperature = computed({
  get: () => props.modelValue.temperature ?? 0,
  set: (val: number) => updateConfig('temperature', val),
})

const outputSchema = computed({
  get: () => {
    const schema = props.modelValue.outputSchema
    if (!schema) return ''
    if (typeof schema === 'string') return schema
    return JSON.stringify(schema, null, 2)
  },
  set: (val: string) => {
    if (!val.trim()) {
      updateConfig('outputSchema', undefined)
      outputSchemaError.value = null
      return
    }
    try {
      const parsed = JSON.parse(val)
      outputSchemaError.value = null
      updateConfig('outputSchema', parsed)
    } catch (e) {
      outputSchemaError.value = (e as Error).message
    }
  },
})

const outputSchemaError = ref<string | null>(null)

// --- Model options ---

const modelOptions = [
  { label: 'GPT-4o', value: 'gpt-4o' },
  { label: 'GPT-4o Mini', value: 'gpt-4o-mini' },
  { label: 'GPT-4 Turbo', value: 'gpt-4-turbo' },
  { label: 'GPT-3.5 Turbo', value: 'gpt-3.5-turbo' },
  { label: 'Claude 3.5 Sonnet', value: 'claude-3-5-sonnet' },
  { label: 'Qwen Max', value: 'qwen-max' },
  { label: 'Qwen Plus', value: 'qwen-plus' },
]

// --- Helper ---

function updateConfig(key: string, value: any) {
  const updated = { ...props.modelValue, [key]: value }
  // Remove undefined values
  if (value === undefined) {
    delete updated[key]
  }
  emit('update:modelValue', updated)
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
  <div class="llm-config-panel">
    <!-- 模型选择 -->
    <div class="config-section">
      <label class="config-label">模型</label>
      <el-select v-model="model" placeholder="选择模型" style="width: 100%" filterable allow-create>
        <el-option
          v-for="opt in modelOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <!-- System Prompt -->
    <div class="config-section">
      <label class="config-label">System Prompt</label>
      <el-input
        v-model="systemPrompt"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 8 }"
        placeholder="系统提示词（可选）"
      />
    </div>

    <!-- User Prompt -->
    <div class="config-section">
      <label class="config-label">
        User Prompt
        <span class="label-hint">支持 #{variable} 变量插值</span>
      </label>
      <el-input
        v-model="userPrompt"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 12 }"
        placeholder="用户提示词模板，如：请将 #{input} 翻译成英文"
      />
    </div>

    <!-- Temperature -->
    <div class="config-section">
      <label class="config-label">Temperature: {{ temperature }}</label>
      <el-slider
        v-model="temperature"
        :min="0"
        :max="2"
        :step="0.1"
        :show-tooltip="true"
      />
    </div>

    <!-- Output Schema -->
    <div class="config-section">
      <label class="config-label">
        Output Schema
        <span class="label-hint">JSON Schema（可选，用于结构化输出）</span>
      </label>
      <el-input
        :model-value="outputSchema"
        @update:model-value="outputSchema = $event"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 10 }"
        placeholder='{"type": "object", "properties": {...}}'
        spellcheck="false"
        class="schema-input"
      />
      <div v-if="outputSchemaError" class="field-error">
        JSON 格式错误: {{ outputSchemaError }}
      </div>
    </div>

    <!-- 高级 JSON 折叠入口 -->
    <div class="advanced-json-section">
      <el-button
        text
        type="primary"
        size="small"
        @click="showAdvancedJson = !showAdvancedJson"
      >
        {{ showAdvancedJson ? '收起' : '展开' }} 高级 JSON 编辑
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
.llm-config-panel {
  padding: 12px;
}

.config-section {
  margin-bottom: 16px;
}

.config-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 6px;
}

.label-hint {
  font-size: 11px;
  font-weight: 400;
  color: #909399;
  margin-left: 6px;
}

.schema-input :deep(textarea) {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
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
