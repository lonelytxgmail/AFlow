<template>
  <div class="expression-input-wrapper" ref="wrapperRef">
    <el-input
      ref="inputRef"
      :model-value="modelValue"
      :placeholder="placeholder"
      @input="handleInput"
      @keydown="handleKeydown"
      @blur="handleBlur"
      class="expression-field"
    >
      <template #prefix>
        <span class="expression-prefix">SpEL</span>
      </template>
    </el-input>

    <!-- Variable autocomplete dropdown -->
    <div
      v-if="showDropdown && filteredVariables.length > 0"
      class="variable-dropdown"
      ref="dropdownRef"
    >
      <div
        v-for="(variable, index) in filteredVariables"
        :key="`${variable.source}:${variable.name}`"
        :class="['dropdown-item', { active: index === activeIndex }]"
        @mousedown.prevent="selectVariable(variable)"
        @mouseenter="activeIndex = index"
      >
        <span class="dropdown-item-name">{{ variable.name }}</span>
        <span class="dropdown-item-source">{{ variable.source }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import type { FlowVariable } from '@/composables/useFlowVariables'

const props = withDefaults(defineProps<{
  /** Current expression value (v-model) */
  modelValue: string
  /** Available flow variables for autocomplete */
  variables: FlowVariable[]
  /** Input placeholder text */
  placeholder?: string
}>(), {
  modelValue: '',
  variables: () => [],
  placeholder: 'SpEL 表达式，如: #result.score > 0.8',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// --- Refs ---
const inputRef = ref<InstanceType<typeof import('element-plus')['ElInput']> | null>(null)
const wrapperRef = ref<HTMLElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)

// --- Autocomplete state ---
const showDropdown = ref(false)
const activeIndex = ref(0)
const triggerPosition = ref(-1)

// --- Computed: filter variables based on what user has typed after '#' ---
const searchQuery = computed(() => {
  if (triggerPosition.value < 0) return ''
  const afterTrigger = props.modelValue.slice(triggerPosition.value + 1)
  // Take characters until a non-identifier char is found
  const match = afterTrigger.match(/^[\w.]*/)
  return match ? match[0].toLowerCase() : ''
})

const filteredVariables = computed(() => {
  const query = searchQuery.value
  if (!query) return props.variables
  return props.variables.filter(v =>
    v.name.toLowerCase().includes(query)
  )
})

// --- Handlers ---

function handleInput(value: string) {
  emit('update:modelValue', value)

  // Detect '#' trigger for autocomplete
  const cursorPos = getCursorPosition()
  const lastHashIdx = findTriggerHash(value, cursorPos)

  if (lastHashIdx >= 0) {
    triggerPosition.value = lastHashIdx
    showDropdown.value = true
    activeIndex.value = 0
  } else {
    closeDropdown()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (!showDropdown.value) return

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      activeIndex.value = Math.min(activeIndex.value + 1, filteredVariables.value.length - 1)
      scrollActiveIntoView()
      break
    case 'ArrowUp':
      event.preventDefault()
      activeIndex.value = Math.max(activeIndex.value - 1, 0)
      scrollActiveIntoView()
      break
    case 'Enter':
    case 'Tab':
      if (filteredVariables.value.length > 0) {
        event.preventDefault()
        selectVariable(filteredVariables.value[activeIndex.value])
      }
      break
    case 'Escape':
      event.preventDefault()
      closeDropdown()
      break
  }
}

function handleBlur() {
  // Delay to allow mousedown on dropdown items to fire first
  setTimeout(() => {
    closeDropdown()
  }, 150)
}

function selectVariable(variable: FlowVariable) {
  const value = props.modelValue
  const start = triggerPosition.value
  // Find how far the user has typed after '#'
  const afterTrigger = value.slice(start + 1)
  const match = afterTrigger.match(/^[\w.]*/)
  const typedLength = match ? match[0].length : 0
  const end = start + 1 + typedLength

  // Build new value: keep everything before '#', insert #variableName, keep rest
  const before = value.slice(0, start)
  const after = value.slice(end)
  const insertion = `#${variable.name}`
  const newValue = before + insertion + after

  emit('update:modelValue', newValue)
  closeDropdown()

  // Restore focus and set cursor after inserted variable
  nextTick(() => {
    const nativeInput = getNativeInput()
    if (nativeInput) {
      nativeInput.focus()
      const cursorPos = before.length + insertion.length
      nativeInput.setSelectionRange(cursorPos, cursorPos)
    }
  })
}

// --- Utility functions ---

function closeDropdown() {
  showDropdown.value = false
  activeIndex.value = 0
  triggerPosition.value = -1
}

function getCursorPosition(): number {
  const nativeInput = getNativeInput()
  return nativeInput?.selectionStart ?? props.modelValue.length
}

function getNativeInput(): HTMLInputElement | null {
  const el = inputRef.value?.$el
  if (!el) return null
  return el.querySelector('input') as HTMLInputElement | null
}

/**
 * Find the '#' that triggered autocomplete.
 * Only triggers if '#' is not preceded by a word character (to avoid matching
 * in the middle of tokens like 'color#fff').
 */
function findTriggerHash(value: string, cursorPos: number): number {
  // Search backwards from cursor for '#'
  for (let i = cursorPos - 1; i >= 0; i--) {
    const ch = value[i]
    if (ch === '#') {
      // Ensure '#' is not preceded by a word character
      if (i === 0 || !/\w/.test(value[i - 1])) {
        return i
      }
      return -1
    }
    // If we hit a non-identifier character (space, operator, etc.) before finding '#', stop
    if (!/[\w.]/.test(ch)) {
      return -1
    }
  }
  return -1
}

function scrollActiveIntoView() {
  nextTick(() => {
    const dropdown = dropdownRef.value
    if (!dropdown) return
    const activeEl = dropdown.querySelector('.dropdown-item.active') as HTMLElement | null
    activeEl?.scrollIntoView({ block: 'nearest' })
  })
}
</script>

<style scoped>
.expression-input-wrapper {
  position: relative;
  width: 100%;
}

.expression-field :deep(.el-input__prefix) {
  font-size: 11px;
  font-weight: 600;
  color: #409eff;
}

.expression-prefix {
  font-size: 11px;
  font-weight: 600;
  color: #409eff;
}

.variable-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 2000;
  margin-top: 4px;
  max-height: 200px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.dropdown-item:hover,
.dropdown-item.active {
  background-color: #f5f7fa;
}

.dropdown-item-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  font-family: 'Fira Code', 'Consolas', monospace;
}

.dropdown-item-source {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
