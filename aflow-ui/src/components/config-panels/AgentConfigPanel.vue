<script setup lang="ts">
/**
 * Agent 节点配置面板
 *
 * 提供 Agent 节点的完整配置界面，包含：
 * - 基础配置：模型 / System Prompt / User Prompt / 最大迭代
 * - 工具配置：tools 多选 / 超时 / 频率限制
 * - 韧性配置：Token 预算 / Tool 结果截断长度
 * - Guardrails：outputSchema / outputValidation 策略
 * - 高级配置：temperature / 自定义 JSON 字段
 */
import { computed, ref } from 'vue'

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 可选项 ---

const modelOptions = [
  { label: 'GPT-4o', value: 'gpt-4o' },
  { label: 'GPT-4o Mini', value: 'gpt-4o-mini' },
  { label: 'Claude 3.5 Sonnet', value: 'claude-3.5-sonnet' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
]

const toolOptions = [
  { label: '全部 (*)', value: '*' },
  { label: 'HTTP', value: 'http' },
  { label: 'Script', value: 'script' },
  { label: 'Search', value: 'search' },
  { label: 'Calculator', value: 'calculator' },
]

const validationStrategyOptions = [
  { label: '无验证', value: 'none' },
  { label: '重试', value: 'retry' },
  { label: '报错', value: 'error' },
]

// --- 折叠面板状态 ---

const activeCollapse = ref<string[]>(['basic', 'tools', 'resilience', 'guardrails', 'advanced'])

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

function updateNestedField(parent: string, field: string, value: any) {
  const parentObj = props.modelValue[parent] ?? {}
  emit('update:modelValue', {
    ...props.modelValue,
    [parent]: { ...parentObj, [field]: value },
  })
}

// --- 计算属性：字段绑定 ---

const model = computed({
  get: () => props.modelValue.model ?? '',
  set: (val: string) => updateField('model', val),
})

const systemPrompt = computed({
  get: () => props.modelValue.systemPrompt ?? '',
  set: (val: string) => updateField('systemPrompt', val),
})

const userPrompt = computed({
  get: () => props.modelValue.userPrompt ?? '',
  set: (val: string) => updateField('userPrompt', val),
})

const maxIterations = computed({
  get: () => props.modelValue.maxIterations ?? 10,
  set: (val: number) => updateField('maxIterations', val),
})

const tools = computed({
  get: () => {
    const t = props.modelValue.tools
    if (Array.isArray(t)) return t
    if (typeof t === 'string') return [t]
    return []
  },
  set: (val: string[]) => updateField('tools', val),
})

const toolTimeout = computed({
  get: () => props.modelValue.toolTimeout ?? 30000,
  set: (val: number) => updateField('toolTimeout', val),
})

const toolRateLimitMaxCalls = computed({
  get: () => props.modelValue.toolRateLimit?.maxCallsPerTool ?? 10,
  set: (val: number) => updateNestedField('toolRateLimit', 'maxCallsPerTool', val),
})

const toolRateLimitMaxTotal = computed({
  get: () => props.modelValue.toolRateLimit?.maxTotalCalls ?? 50,
  set: (val: number) => updateNestedField('toolRateLimit', 'maxTotalCalls', val),
})

const maxTokenBudget = computed({
  get: () => props.modelValue.maxTokenBudget ?? 100000,
  set: (val: number) => updateField('maxTokenBudget', val),
})

const toolResultMaxLength = computed({
  get: () => props.modelValue.toolResultMaxLength ?? 4000,
  set: (val: number) => updateField('toolResultMaxLength', val),
})

const outputSchemaText = computed({
  get: () => {
    const schema = props.modelValue.outputSchema
    return schema ? JSON.stringify(schema, null, 2) : ''
  },
  set: (val: string) => {
    try {
      const parsed = val.trim() ? JSON.parse(val) : undefined
      outputSchemaError.value = null
      updateField('outputSchema', parsed)
    } catch (e) {
      outputSchemaError.value = (e as Error).message
    }
  },
})

const outputSchemaError = ref<string | null>(null)

const validationStrategy = computed({
  get: () => props.modelValue.outputValidation?.strategy ?? 'none',
  set: (val: string) => {
    if (val === 'none') {
      // 移除 outputValidation
      const { outputValidation: _, ...rest } = props.modelValue
      emit('update:modelValue', rest)
    } else {
      updateNestedField('outputValidation', 'strategy', val)
    }
  },
})

const temperature = computed({
  get: () => props.modelValue.temperature ?? 0.7,
  set: (val: number) => updateField('temperature', val),
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
  <div class="agent-config-panel">
    <el-collapse v-model="activeCollapse">
      <!-- 基础配置 -->
      <el-collapse-item title="基础配置" name="basic">
        <el-form label-position="top" size="default">
          <el-form-item label="模型">
            <el-select v-model="model" placeholder="选择模型" style="width: 100%">
              <el-option
                v-for="opt in modelOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="System Prompt">
            <el-input
              v-model="systemPrompt"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              placeholder="系统提示词，定义 Agent 角色和行为..."
            />
          </el-form-item>

          <el-form-item label="User Prompt">
            <el-input
              v-model="userPrompt"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              placeholder="用户提示词，支持 #{variable} 变量插值..."
            />
          </el-form-item>

          <el-form-item label="最大迭代次数">
            <el-input-number
              v-model="maxIterations"
              :min="1"
              :max="100"
              :step="1"
              controls-position="right"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- 工具配置 -->
      <el-collapse-item title="工具配置" name="tools">
        <el-form label-position="top" size="default">
          <el-form-item label="可用工具">
            <el-select
              v-model="tools"
              multiple
              placeholder="选择工具（* = 全部）"
              style="width: 100%"
            >
              <el-option
                v-for="opt in toolOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="工具超时 (ms)">
            <el-input-number
              v-model="toolTimeout"
              :min="1000"
              :max="300000"
              :step="1000"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item label="单工具最大调用次数">
            <el-input-number
              v-model="toolRateLimitMaxCalls"
              :min="1"
              :max="100"
              :step="1"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item label="总工具调用次数上限">
            <el-input-number
              v-model="toolRateLimitMaxTotal"
              :min="1"
              :max="500"
              :step="5"
              controls-position="right"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- 韧性配置 -->
      <el-collapse-item title="韧性配置" name="resilience">
        <el-form label-position="top" size="default">
          <el-form-item label="Token 预算上限">
            <el-input-number
              v-model="maxTokenBudget"
              :min="1000"
              :max="1000000"
              :step="10000"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item label="Tool 结果最大长度 (字符)">
            <el-input-number
              v-model="toolResultMaxLength"
              :min="100"
              :max="50000"
              :step="500"
              controls-position="right"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- Guardrails -->
      <el-collapse-item title="Guardrails" name="guardrails">
        <el-form label-position="top" size="default">
          <el-form-item label="Output Schema (JSON)">
            <el-input
              :model-value="outputSchemaText"
              @update:model-value="outputSchemaText = $event"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 10 }"
              placeholder='{"type": "object", "properties": {...}}'
              spellcheck="false"
              class="json-input"
            />
            <div v-if="outputSchemaError" class="field-error">
              JSON 格式错误: {{ outputSchemaError }}
            </div>
          </el-form-item>

          <el-form-item label="输出验证策略">
            <el-select v-model="validationStrategy" style="width: 100%">
              <el-option
                v-for="opt in validationStrategyOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- 高级配置 -->
      <el-collapse-item title="高级配置" name="advanced">
        <el-form label-position="top" size="default">
          <el-form-item label="Temperature">
            <div class="slider-row">
              <el-slider
                v-model="temperature"
                :min="0"
                :max="2"
                :step="0.1"
                :show-tooltip="true"
                style="flex: 1"
              />
              <span class="slider-value">{{ temperature.toFixed(1) }}</span>
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
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.agent-config-panel {
  padding: 12px;
}

.agent-config-panel :deep(.el-collapse-item__header) {
  font-weight: 600;
  font-size: 14px;
}

.agent-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.agent-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.slider-value {
  min-width: 32px;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  text-align: right;
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
