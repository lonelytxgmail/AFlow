<script setup lang="ts">
/**
 * 脚本节点配置面板
 *
 * 提供脚本节点的完整配置界面，包含：
 * - 语言选择（Groovy / JavaScript）
 * - Monaco Editor 编辑脚本内容
 * - 脚本内容存储为 config.script
 * - 高级 JSON 编辑折叠入口
 */
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps<{
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>]
}>()

// --- 可选项 ---

const languageOptions = [
  { label: 'Groovy', value: 'groovy' },
  { label: 'JavaScript', value: 'javascript' },
]

// --- Monaco Editor ---

const editorRef = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let isSyncing = false

// --- 辅助函数 ---

function updateField(field: string, value: any) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

// --- 计算属性：字段绑定 ---

const language = computed({
  get: () => props.modelValue.language ?? 'groovy',
  set: (val: string) => {
    updateField('language', val)
    // Update Monaco editor language
    if (editor) {
      const model = editor.getModel()
      if (model) {
        // Monaco doesn't have 'groovy' built-in, use 'java' as fallback for groovy
        const monacoLang = val === 'groovy' ? 'java' : val
        monaco.editor.setModelLanguage(model, monacoLang)
      }
    }
  },
})

const script = computed({
  get: () => props.modelValue.script ?? '',
  set: (val: string) => updateField('script', val),
})

// --- Monaco Editor 初始化 ---

function getMonacoLanguage(lang: string): string {
  // Monaco doesn't have groovy; use java as the closest syntax highlighter
  return lang === 'groovy' ? 'java' : lang
}

function initEditor() {
  if (!editorRef.value || editor) return

  editor = monaco.editor.create(editorRef.value, {
    value: script.value,
    language: getMonacoLanguage(language.value),
    theme: 'vs-light',
    automaticLayout: true,
    minimap: { enabled: false },
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    fontSize: 13,
    tabSize: 2,
    renderLineHighlight: 'line',
    folding: true,
    scrollbar: {
      verticalScrollbarSize: 8,
      horizontalScrollbarSize: 8,
    },
  })

  editor.onDidChangeModelContent(() => {
    if (isSyncing) return
    const value = editor!.getValue()
    updateField('script', value)
  })
}

function syncEditorValue() {
  if (!editor) return
  const currentValue = editor.getValue()
  const newValue = script.value
  if (currentValue !== newValue) {
    isSyncing = true
    editor.setValue(newValue)
    isSyncing = false
  }
}

// Watch for external changes to script (e.g. from advanced JSON editor)
watch(() => props.modelValue.script, () => {
  syncEditorValue()
})

// Watch for language changes from outside
watch(() => props.modelValue.language, (newLang) => {
  if (editor && newLang) {
    const model = editor.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, getMonacoLanguage(newLang))
    }
  }
})

onMounted(async () => {
  await nextTick()
  initEditor()
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
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
  <div class="script-config-panel">
    <el-form label-position="top" size="default">
      <!-- 语言选择 -->
      <el-form-item label="脚本语言">
        <el-select v-model="language" placeholder="选择语言" style="width: 200px">
          <el-option
            v-for="opt in languageOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>

      <!-- Monaco Editor 脚本编辑 -->
      <el-form-item label="脚本内容">
        <div class="script-editor" ref="editorRef"></div>
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
.script-config-panel {
  padding: 12px;
}

.script-config-panel :deep(.el-form-item) {
  margin-bottom: 16px;
}

.script-config-panel :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.script-editor {
  height: 360px;
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
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
