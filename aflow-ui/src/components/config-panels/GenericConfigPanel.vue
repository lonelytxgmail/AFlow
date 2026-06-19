<script setup lang="ts">
/**
 * 通用配置面板 — JSON 编辑器兜底
 *
 * 用于尚未实现专用面板的节点类型，
 * 提供原始 JSON 编辑能力。
 */
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

const jsonText = ref(JSON.stringify(props.modelValue, null, 2))
const parseError = ref<string | null>(null)

watch(() => props.modelValue, (val) => {
  jsonText.value = JSON.stringify(val, null, 2)
  parseError.value = null
}, { deep: true })

function onInput(event: Event) {
  const text = (event.target as HTMLTextAreaElement).value
  jsonText.value = text
  try {
    const parsed = JSON.parse(text)
    parseError.value = null
    emit('update:modelValue', parsed)
  } catch (e) {
    parseError.value = (e as Error).message
  }
}
</script>

<template>
  <div class="generic-config-panel">
    <div class="panel-header">
      <span class="panel-title">节点配置 (JSON)</span>
    </div>
    <textarea
      class="json-editor"
      :value="jsonText"
      @input="onInput"
      spellcheck="false"
    />
    <div v-if="parseError" class="parse-error">
      JSON 格式错误: {{ parseError }}
    </div>
  </div>
</template>

<style scoped>
.generic-config-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px;
}

.panel-header {
  margin-bottom: 8px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.json-editor {
  flex: 1;
  min-height: 200px;
  padding: 12px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  resize: vertical;
  background: #fafafa;
  color: #303133;
}

.json-editor:focus {
  border-color: #409eff;
  outline: none;
}

.parse-error {
  margin-top: 8px;
  padding: 8px;
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
  border-radius: 4px;
}
</style>
