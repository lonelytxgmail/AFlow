<script setup lang="ts">
/**
 * 审批节点配置面板
 *
 * 提供审批标题、描述、选项列表和超时配置。
 * Config 结构:
 * {
 *   title: string,
 *   description: string,
 *   options: Array<{label: string, value: string}>,
 *   timeoutMinutes: number,
 *   timeoutAction: "approve" | "reject" | "escalate"
 * }
 */
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- Helper ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- Computed properties ---

const title = computed({
  get: () => props.modelValue.title ?? '',
  set: (val: string) => updateField('title', val),
})

const description = computed({
  get: () => props.modelValue.description ?? '',
  set: (val: string) => updateField('description', val),
})

const options = computed({
  get: () => props.modelValue.options ?? [
    { label: '批准', value: 'approve' },
    { label: '拒绝', value: 'reject' },
  ],
  set: (val: Array<{ label: string; value: string }>) => updateField('options', val),
})

const timeoutMinutes = computed({
  get: () => props.modelValue.timeoutMinutes ?? 1440,
  set: (val: number) => updateField('timeoutMinutes', val),
})

const timeoutAction = computed({
  get: () => props.modelValue.timeoutAction ?? 'reject',
  set: (val: string) => updateField('timeoutAction', val),
})

// --- Option management ---

function addOption() {
  const current = [...options.value]
  current.push({ label: '', value: '' })
  options.value = current
}

function removeOption(index: number) {
  const current = [...options.value]
  current.splice(index, 1)
  options.value = current
}

function updateOptionField(index: number, field: 'label' | 'value', val: string) {
  const current = [...options.value]
  current[index] = { ...current[index], [field]: val }
  options.value = current
}

// Human-readable timeout
const timeoutHumanReadable = computed(() => {
  const mins = timeoutMinutes.value
  if (mins < 60) return `${mins} 分钟`
  if (mins < 1440) return `${(mins / 60).toFixed(1)} 小时`
  return `${(mins / 1440).toFixed(1)} 天`
})

// --- Advanced JSON fallback ---

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
  <div class="approval-config-panel">
    <el-form label-position="top" size="default">
      <!-- 标题 -->
      <el-form-item label="审批标题">
        <el-input v-model="title" placeholder="请输入审批标题" />
      </el-form-item>

      <!-- 描述 -->
      <el-form-item label="审批描述">
        <el-input
          v-model="description"
          type="textarea"
          :rows="3"
          placeholder="请输入审批描述（支持 SpEL 变量引用）"
        />
      </el-form-item>

      <!-- 选项列表 -->
      <el-form-item label="审批选项">
        <div class="options-list">
          <div v-for="(opt, index) in options" :key="index" class="option-row">
            <el-input
              :model-value="opt.label"
              placeholder="显示标签"
              style="flex: 1"
              @update:model-value="updateOptionField(index, 'label', $event)"
            />
            <el-input
              :model-value="opt.value"
              placeholder="值"
              style="flex: 1"
              @update:model-value="updateOptionField(index, 'value', $event)"
            />
            <el-button
              :icon="Delete"
              type="danger"
              text
              size="small"
              @click="removeOption(index)"
            />
          </div>
          <el-button :icon="Plus" type="primary" text size="small" @click="addOption">
            添加选项
          </el-button>
        </div>
      </el-form-item>

      <!-- 超时配置 -->
      <el-form-item label="超时时间 (分钟)">
        <el-input-number
          v-model="timeoutMinutes"
          :min="1"
          :step="60"
          controls-position="right"
          style="width: 200px"
        />
        <span class="human-readable">{{ timeoutHumanReadable }}</span>
      </el-form-item>

      <!-- 超时动作 -->
      <el-form-item label="超时处理方式">
        <el-select v-model="timeoutAction" style="width: 200px">
          <el-option value="reject" label="自动拒绝" />
          <el-option value="approve" label="自动批准" />
          <el-option value="escalate" label="升级通知（延长截止时间）" />
        </el-select>
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
.approval-config-panel {
  padding: 12px;
}

.approval-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.approval-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.options-list {
  width: 100%;
}

.option-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.human-readable {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
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
