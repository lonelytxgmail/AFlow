<script setup lang="ts">
/**
 * 日志输出配置面板
 *
 * 提供日志消息和级别配置，消息支持 #{variable} 插值语法。
 * Config 结构: { message: string, level: 'DEBUG'|'INFO'|'WARN'|'ERROR' }
 */
import { computed, ref } from 'vue'

type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 可选项 ---

const levelOptions: { label: string; value: LogLevel; color: string }[] = [
  { label: 'DEBUG', value: 'DEBUG', color: '#909399' },
  { label: 'INFO', value: 'INFO', color: '#409eff' },
  { label: 'WARN', value: 'WARN', color: '#e6a23c' },
  { label: 'ERROR', value: 'ERROR', color: '#f56c6c' },
]

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- 计算属性 ---

const message = computed({
  get: () => props.modelValue.message ?? '',
  set: (val: string) => updateField('message', val),
})

const level = computed({
  get: (): LogLevel => props.modelValue.level ?? 'INFO',
  set: (val: LogLevel) => updateField('level', val),
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
  <div class="log-config-panel">
    <el-form label-position="top" size="default">
      <!-- 日志级别 -->
      <el-form-item label="日志级别">
        <el-select v-model="level" style="width: 160px">
          <el-option
            v-for="opt in levelOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          >
            <span :style="{ color: opt.color, fontWeight: 500 }">{{ opt.label }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 日志消息 -->
      <el-form-item label="日志消息">
        <el-input
          v-model="message"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 10 }"
          placeholder="输入日志消息，支持 #{variableName} 变量插值"
          spellcheck="false"
        />
        <div class="field-hint">
          提示：使用 <code>#{variableName}</code> 引用流程变量
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
.log-config-panel {
  padding: 12px;
}

.log-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.log-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.field-hint code {
  padding: 1px 4px;
  font-size: 11px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 3px;
  font-family: 'Fira Code', 'Consolas', monospace;
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
