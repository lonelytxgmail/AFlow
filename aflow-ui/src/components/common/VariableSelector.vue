<template>
  <el-select
    v-model="selected"
    filterable
    clearable
    placeholder="选择变量..."
    class="variable-selector"
    @change="handleSelect"
  >
    <el-option
      v-for="variable in variables"
      :key="`${variable.source}:${variable.name}`"
      :label="variable.name"
      :value="variable.name"
    >
      <div class="variable-option">
        <span class="variable-name">{{ variable.name }}</span>
        <span class="variable-source">{{ variable.source }}</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FlowVariable } from '@/composables/useFlowVariables'

const props = withDefaults(defineProps<{
  /** List of available flow variables */
  variables: FlowVariable[]
  /** Format for the emitted variable reference: '#' → #varName, '{{}}' → {{varName}} */
  format?: '#' | '{{}}'
}>(), {
  variables: () => [],
  format: '#'
})

const emit = defineEmits<{
  select: [variableRef: string]
}>()

const selected = ref<string>('')

function formatVariable(name: string): string {
  if (props.format === '{{}}') {
    return `{{${name}}}`
  }
  return `#${name}`
}

function handleSelect(value: string) {
  if (!value) return
  const formatted = formatVariable(value)
  emit('select', formatted)
  // Reset selection so user can pick the same variable again
  selected.value = ''
}
</script>

<style scoped>
.variable-selector {
  width: 100%;
}

.variable-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.variable-name {
  font-weight: 600;
  color: #303133;
}

.variable-source {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
