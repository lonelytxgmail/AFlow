<script setup lang="ts">
/**
 * 延时等待配置面板
 *
 * 提供延时时长配置（毫秒），用于流程中的等待节点。
 * Config 结构: { delayMs: number }
 */
import { computed, ref } from 'vue'

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

const delayMs = computed({
  get: () => props.modelValue.delayMs ?? 1000,
  set: (val: number) => updateField('delayMs', val),
})

// 人性化时间显示
const humanReadable = computed(() => {
  const ms = delayMs.value
  if (ms < 1000) return `${ms} 毫秒`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} 秒`
  return `${(ms / 60000).toFixed(1)} 分钟`
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
  <div class="delay-config-panel">
    <el-form label-position="top" size="default">
      <!-- 延时时长 -->
      <el-form-item label="延时时长 (ms)">
        <el-input-number
          v-model="delayMs"
          :min="0"
          :step="1000"
          controls-position="right"
          style="width: 220px"
        />
        <span class="human-readable">{{ humanReadable }}</span>
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
.delay-config-panel {
  padding: 12px;
}

.delay-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.delay-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
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
